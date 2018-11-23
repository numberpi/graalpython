/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates.
 * Copyright (c) 2013, Regents of the University of California
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.graal.python.nodes.statement;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.LazyPythonClass;
import com.oracle.graal.python.builtins.objects.type.PythonBuiltinClass;
import com.oracle.graal.python.builtins.objects.type.PythonClass;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.expression.ExpressionNode;
import com.oracle.graal.python.nodes.frame.WriteNode;
import com.oracle.graal.python.nodes.object.GetLazyClassNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.exception.ExceptionHandledException;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.GenerateWrapper;
import com.oracle.truffle.api.instrumentation.InstrumentableNode;
import com.oracle.truffle.api.instrumentation.ProbeNode;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;

@GenerateWrapper
public class ExceptNode extends PNodeWithContext implements InstrumentableNode {

    @Child private StatementNode body;
    @Child private ExpressionNode exceptType;
    @Child private WriteNode exceptName;
    @Child private GetLazyClassNode getClass;

    // "object" is the uninitialized value (since it's not a valid error type)
    @CompilationFinal private PythonBuiltinClassType singleBuiltinError = PythonBuiltinClassType.PythonObject;
    private final IsBuiltinClassProfile isClassProfile = IsBuiltinClassProfile.create();
    private final ConditionProfile isTupleProfile = ConditionProfile.createBinaryProfile();
    private final ConditionProfile isBuiltinTypeProfile = ConditionProfile.createBinaryProfile();
    private final ConditionProfile isBuiltinClassProfile = ConditionProfile.createBinaryProfile();
    private final ConditionProfile builtinClassMatchesProfile = ConditionProfile.createBinaryProfile();
    private final ConditionProfile equalsProfile = ConditionProfile.createBinaryProfile();
    private final BranchProfile errorProfile = BranchProfile.create();
    private final ConditionProfile matchesProfile = ConditionProfile.createBinaryProfile();

    public ExceptNode(StatementNode body, ExpressionNode exceptType, WriteNode exceptName) {
        this.body = body;
        this.exceptName = exceptName;
        this.exceptType = exceptType;
    }

    public ExceptNode(ExceptNode original) {
        this.body = original.body;
        this.exceptName = original.exceptName;
        this.exceptType = original.exceptType;
    }

    public void executeExcept(VirtualFrame frame, PException e) {
        PythonContext context = getContext();
        context.setCurrentException(e);
        body.executeVoid(frame);
        throw ExceptionHandledException.INSTANCE;
    }

    private GetLazyClassNode getClassNode() {
        if (getClass == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            getClass = GetLazyClassNode.create();
        }
        return getClass;
    }

    public boolean matchesException(VirtualFrame frame, PException e) {
        if (exceptType == null) {
            return true;
        }
        Object expectedType = exceptType.execute(frame);
        LazyPythonClass lazyClass = getClassNode().execute(e.getExceptionObject());

        if (singleBuiltinError == PythonBuiltinClassType.PythonObject) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            PythonBuiltinClassType error = expectedType instanceof PythonBuiltinClass ? ((PythonBuiltinClass) expectedType).getType() : null;

            if (error != null) {
                // check that the class is a subclass of BaseException
                if (!derivesFromBaseException(error)) {
                    throw raise(PythonErrorType.TypeError, "catching classes that do not inherit from BaseException is not allowed");
                }
                singleBuiltinError = error;
            }
        }

        PythonBuiltinClassType cachedError = singleBuiltinError;
        if (cachedError != null) {
            if (expectedType instanceof PythonBuiltinClass && ((PythonBuiltinClass) expectedType).getType() == cachedError) {
                return matchesExceptionCached(frame, lazyClass, cachedError, e);
            }
            CompilerDirectives.transferToInterpreterAndInvalidate();
            // the exception type we're looking for has changed
            singleBuiltinError = null;
            // fall through to normal case
        }

        return matchesExceptionFallback(frame, e, expectedType, lazyClass);
    }

    private boolean matchesExceptionCached(VirtualFrame frame, LazyPythonClass lazyClass, PythonBuiltinClassType cachedError, PException e) {
        boolean matches = false;
        if (isBuiltinTypeProfile.profile(lazyClass instanceof PythonBuiltinClassType)) {
            // builtin class: look through base classes
            PythonBuiltinClassType builtinClass = (PythonBuiltinClassType) lazyClass;
            while (builtinClass != PythonBuiltinClassType.PythonObject) {
                if (builtinClassMatchesProfile.profile(builtinClass == cachedError)) {
                    matches = true;
                    break;
                }
                builtinClass = builtinClass.getBase();
            }
        } else if (isBuiltinClassProfile.profile(lazyClass instanceof PythonBuiltinClass)) {
            // builtin class: look through base classes
            PythonBuiltinClassType builtinClass = ((PythonBuiltinClass) lazyClass).getType();
            while (builtinClass != PythonBuiltinClassType.PythonObject) {
                if (builtinClassMatchesProfile.profile(builtinClass == cachedError)) {
                    matches = true;
                    break;
                }
                builtinClass = builtinClass.getBase();
            }
        } else {
            // non-builtin class: look through MRO
            PythonClass[] mro = ((PythonClass) lazyClass).getMethodResolutionOrder();
            for (PythonClass current : mro) {
                if (isClassProfile.profileClass(current, cachedError)) {
                    matches = true;
                    break;
                }
            }
        }
        return writeResult(frame, e, matches);
    }

    private boolean writeResult(VirtualFrame frame, PException e, boolean matches) {
        if (matchesProfile.profile(matches)) {
            if (exceptName != null) {
                exceptName.doWrite(frame, e.getExceptionObject());
                e.getExceptionObject().reifyException();
            }
            return true;
        } else {
            return false;
        }
    }

    /**
     * Fallback case for non-builtin classes and changing types.
     */
    private boolean matchesExceptionFallback(VirtualFrame frame, PException e, Object expectedType, LazyPythonClass lazyClass) {
        boolean matches = false;
        if (isBuiltinTypeProfile.profile(lazyClass instanceof PythonBuiltinClassType)) {
            PythonBuiltinClassType builtinType = (PythonBuiltinClassType) lazyClass;
            if (isTupleProfile.profile(expectedType instanceof PTuple)) {
                // check for every type in the tuple
                for (Object etype : ((PTuple) expectedType).getArray()) {
                    if (matches(etype, builtinType)) {
                        matches = true;
                        break;
                    }
                }
            } else {
                matches = matches(expectedType, builtinType);
            }
        } else {
            PythonClass clazz = (PythonClass) lazyClass;
            if (isTupleProfile.profile(expectedType instanceof PTuple)) {
                // check for every type in the tuple
                for (Object etype : ((PTuple) expectedType).getArray()) {
                    if (matches(etype, clazz)) {
                        matches = true;
                        break;
                    }
                }
            } else {
                matches = matches(expectedType, clazz);
            }
        }

        return writeResult(frame, e, matches);
    }

    private boolean matches(Object expectedType, PythonClass clazz) {
        // TODO: check whether expected type derives from BaseException
        if (equalsProfile.profile(expectedType == clazz)) {
            return true;
        }
        PythonClass[] mro = clazz.getMethodResolutionOrder();
        for (PythonClass current : mro) {
            if (expectedType == current) {
                return true;
            }
        }
        return false;
    }

    private boolean matches(Object expectedType, PythonBuiltinClassType clazz) {
        if (!(expectedType instanceof PythonClass)) {
            errorProfile.enter();
            throw raise(PythonErrorType.TypeError, "catching classes that do not inherit from BaseException is not allowed");
        }

        PythonClass expectedClass = (PythonClass) expectedType;

        // TODO: check whether expected type derives from BaseException
        PythonBuiltinClassType builtinClass = clazz;
        while (builtinClass != PythonBuiltinClassType.PythonObject) {
            if (isClassProfile.profileClass(expectedClass, clazz)) {
                return true;
            }
            builtinClass = builtinClass.getBase();
        }
        return false;
    }

    private boolean derivesFromBaseException(PythonBuiltinClassType error) {
        if (error == PythonBuiltinClassType.PBaseException) {
            return true;
        }
        if (error == PythonBuiltinClassType.PythonObject) {
            return false;
        }
        return derivesFromBaseException(error.getBase());
    }

    public StatementNode getBody() {
        return body;
    }

    public ExpressionNode getExceptType() {
        return exceptType;
    }

    public WriteNode getExceptName() {
        return exceptName;
    }

    @Override
    public WrapperNode createWrapper(ProbeNode probeNode) {
        return new ExceptNodeWrapper(this, this, probeNode);
    }

    public boolean isInstrumentable() {
        return getSourceSection() != null;
    }
}
