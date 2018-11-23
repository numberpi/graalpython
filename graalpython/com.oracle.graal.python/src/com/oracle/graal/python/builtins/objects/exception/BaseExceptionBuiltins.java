/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates.
 * Copyright (c) 2014, Regents of the University of California
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
package com.oracle.graal.python.builtins.objects.exception;

import static com.oracle.graal.python.nodes.SpecialAttributeNames.__CAUSE__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__CONTEXT__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__TRACEBACK__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__INIT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__REPR__;

import java.util.IllegalFormatException;
import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.traceback.PTraceback;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromObjectNode;
import com.oracle.graal.python.nodes.attributes.WriteAttributeToObjectNode;
import com.oracle.graal.python.nodes.expression.CastToListNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.object.GetLazyClassNode;
import com.oracle.graal.python.runtime.formatting.ErrorMessageFormatter;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.profiles.ConditionProfile;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PBaseException)
public class BaseExceptionBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return BaseExceptionBuiltinsFactory.getFactories();
    }

    @Builtin(name = __INIT__, minNumOfPositionalArgs = 1, takesVarArgs = true)
    @GenerateNodeFactory
    public abstract static class InitNode extends PythonBuiltinNode {
        @Specialization
        Object init(PBaseException self, Object[] args) {
            self.setArgs(factory().createTuple(args));
            return PNone.NONE;
        }
    }

    @Builtin(name = __REPR__, fixedNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ReprNode extends PythonUnaryBuiltinNode {
        @Specialization
        @TruffleBoundary
        public Object repr(PBaseException self) {
            return self.toString();
        }
    }

    @Builtin(name = "args", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true)
    @GenerateNodeFactory
    public abstract static class ArgsNode extends PythonBuiltinNode {

        @Child private GetLazyClassNode getClassNode;

        private final ErrorMessageFormatter formatter = new ErrorMessageFormatter();

        private GetLazyClassNode getGetClassNode() {
            if (getClassNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getClassNode = insert(GetLazyClassNode.create());
            }
            return getClassNode;
        }

        @TruffleBoundary
        private String getFormattedMessage(String format, Object... args) {
            try {
                // pre-format for '%p' which retrieves the Python class of an argument
                if (format.contains("%p")) {
                    return formatter.format(getGetClassNode(), format, args);
                }
                return String.format(format, args);
            } catch (IllegalFormatException e) {
                throw new RuntimeException("error while formatting \"" + format + "\"", e);
            }
        }

        @Specialization(guards = "isNoValue(none)")
        public Object args(PBaseException self, @SuppressWarnings("unused") PNone none,
                        @Cached("createBinaryProfile()") ConditionProfile nullArgsProfile) {
            PTuple args = self.getArgs();
            if (nullArgsProfile.profile(args == null)) {
                // lazily format the exception message:
                args = factory().createTuple(new Object[]{getFormattedMessage(self.getMessageFormat(), self.getMessageArgs())});
                self.setArgs(args);
            }
            return args;
        }

        @Specialization(guards = "!isNoValue(value)")
        public Object args(PBaseException self, Object value,
                        @Cached("create()") CastToListNode castToList) {
            PList list = castToList.executeWith(value);
            self.setArgs(factory().createTuple(list.getSequenceStorage().getCopyOfInternalArray()));
            return PNone.NONE;
        }
    }

    @Builtin(name = __CAUSE__, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true)
    @GenerateNodeFactory
    public abstract static class CauseNode extends PythonBuiltinNode {
        @Specialization(guards = "isNoValue(value)")
        public Object cause(PBaseException self, @SuppressWarnings("unused") PNone value,
                        @Cached("create()") ReadAttributeFromObjectNode readCause) {
            Object cause = readCause.execute(self, __CAUSE__);
            if (cause == PNone.NO_VALUE) {
                return PNone.NONE;
            } else {
                return cause;
            }
        }

        @Specialization
        public Object cause(PBaseException self, PBaseException value,
                        @Cached("create()") WriteAttributeToObjectNode writeCause) {
            writeCause.execute(self, __CAUSE__, value);
            return PNone.NONE;
        }
    }

    @Builtin(name = __CONTEXT__, fixedNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class ContextNode extends PythonBuiltinNode {

        @Specialization
        public Object context(@SuppressWarnings("unused") PBaseException self) {
            return PNone.NONE;
        }
    }

    @Builtin(name = "__suppress_context__", fixedNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class SuppressContextNode extends PythonBuiltinNode {

        @Specialization
        public boolean suppressContext(@SuppressWarnings("unused") PBaseException self) {
            return false;
        }
    }

    @Builtin(name = __TRACEBACK__, fixedNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class TracebackNode extends PythonBuiltinNode {

        @Specialization
        public Object traceback(PBaseException self) {
            return self.getTraceback(factory());
        }
    }

    @Builtin(name = "with_traceback", fixedNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class WithTracebackNode extends PythonBuiltinNode {

        @Specialization
        public Object withTraceback(PBaseException self, @SuppressWarnings("unused") PNone tb) {
            self.clearTraceback();
            return PNone.NONE;
        }

        @Specialization
        public Object withTraceback(PBaseException self, PTraceback tb) {
            self.setTraceback(tb);
            return PNone.NONE;
        }
    }
}
