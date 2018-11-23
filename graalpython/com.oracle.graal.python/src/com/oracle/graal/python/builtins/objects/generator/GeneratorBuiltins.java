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
package com.oracle.graal.python.builtins.objects.generator;

import static com.oracle.graal.python.nodes.SpecialMethodNames.__ITER__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__NEXT__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.StopIteration;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;

import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.code.PCode;
import com.oracle.graal.python.builtins.objects.exception.PBaseException;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.traceback.PTraceback;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.PythonClass;
import com.oracle.graal.python.nodes.call.special.LookupAndCallVarargsNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.profiles.ConditionProfile;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PGenerator)
public class GeneratorBuiltins extends PythonBuiltins {

    private static Object resumeGenerator(PGenerator self) {
        try {
            return self.getCallTarget().call(self.getArguments());
        } finally {
            PArguments.setSpecialArgument(self.getArguments(), null);
        }
    }

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return GeneratorBuiltinsFactory.getFactories();
    }

    @Builtin(name = __ITER__, fixedNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class IterNode extends PythonUnaryBuiltinNode {

        @Specialization
        public Object iter(PGenerator self) {
            return self;
        }
    }

    @Builtin(name = __NEXT__, fixedNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class NextNode extends PythonUnaryBuiltinNode {

        private final IsBuiltinClassProfile errorProfile = IsBuiltinClassProfile.create();

        protected static DirectCallNode createDirectCall(CallTarget target) {
            return Truffle.getRuntime().createDirectCallNode(target);
        }

        protected static IndirectCallNode createIndirectCall() {
            return Truffle.getRuntime().createIndirectCallNode();
        }

        protected static boolean sameCallTarget(RootCallTarget target1, CallTarget target2) {
            return target1 == target2;
        }

        @Specialization(guards = "sameCallTarget(self.getCallTarget(), call.getCallTarget())", limit = "getCallSiteInlineCacheMaxDepth()")
        public Object nextCached(PGenerator self,
                        @Cached("createDirectCall(self.getCallTarget())") DirectCallNode call) {
            if (self.isFinished()) {
                throw raise(StopIteration);
            }
            try {
                return call.call(self.getArguments());
            } catch (PException e) {
                e.expectStopIteration(errorProfile);
                self.markAsFinished();
                throw raise(StopIteration);
            }
        }

        @Specialization(replaces = "nextCached")
        public Object next(PGenerator self,
                        @Cached("createIndirectCall()") IndirectCallNode call) {
            if (self.isFinished()) {
                throw raise(StopIteration);
            }
            try {
                return call.call(self.getCallTarget(), self.getArguments());
            } catch (PException e) {
                e.expectStopIteration(errorProfile);
                self.markAsFinished();
                throw raise(StopIteration);
            }
        }
    }

    @Builtin(name = "send", fixedNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class SendNode extends PythonBuiltinNode {

        @Specialization
        public Object send(PGenerator self, Object value) {
            PArguments.setSpecialArgument(self.getArguments(), value);
            return resumeGenerator(self);
        }
    }

    // throw(typ[,val[,tb]])
    @Builtin(name = "throw", minNumOfPositionalArgs = 2, maxNumOfPositionalArgs = 4)
    @GenerateNodeFactory
    abstract static class ThrowNode extends PythonBuiltinNode {
        @Specialization
        Object sendThrow(VirtualFrame frame, PGenerator self, PythonClass typ, @SuppressWarnings("unused") PNone val, @SuppressWarnings("unused") PNone tb,
                        @Cached("create(__CALL__)") LookupAndCallVarargsNode callTyp) {
            Object instance = callTyp.execute(frame, typ, new Object[0]);
            if (instance instanceof PBaseException) {
                PException pException = PException.fromObject((PBaseException) instance, this);
                PArguments.setSpecialArgument(self.getArguments(), pException);
            } else {
                throw raise(TypeError, "exceptions must derive from BaseException");
            }
            return resumeGenerator(self);
        }

        @Specialization
        Object sendThrow(VirtualFrame frame, PGenerator self, PythonClass typ, PTuple val, @SuppressWarnings("unused") PNone tb,
                        @Cached("create(__CALL__)") LookupAndCallVarargsNode callTyp) {
            Object instance = callTyp.execute(frame, typ, val.getArray());
            if (instance instanceof PBaseException) {
                PException pException = PException.fromObject((PBaseException) instance, this);
                PArguments.setSpecialArgument(self.getArguments(), pException);
            } else {
                throw raise(TypeError, "exceptions must derive from BaseException");
            }
            return resumeGenerator(self);
        }

        @Specialization(guards = {"!isPNone(val)", "!isPTuple(val)"})
        Object sendThrow(VirtualFrame frame, PGenerator self, PythonClass typ, Object val, @SuppressWarnings("unused") PNone tb,
                        @Cached("create(__CALL__)") LookupAndCallVarargsNode callTyp) {
            Object instance = callTyp.execute(frame, typ, new Object[]{val});
            if (instance instanceof PBaseException) {
                PException pException = PException.fromObject((PBaseException) instance, this);
                PArguments.setSpecialArgument(self.getArguments(), pException);
            } else {
                throw raise(TypeError, "exceptions must derive from BaseException");
            }
            return resumeGenerator(self);
        }

        @Specialization
        Object sendThrow(PGenerator self, PBaseException instance, @SuppressWarnings("unused") PNone val, @SuppressWarnings("unused") PNone tb) {
            PException pException = PException.fromObject(instance, this);
            PArguments.setSpecialArgument(self.getArguments(), pException);
            return resumeGenerator(self);
        }

        @Specialization
        Object sendThrow(PGenerator self, @SuppressWarnings("unused") PythonClass typ, PBaseException instance, PTraceback tb) {
            PException pException = PException.fromObject(instance, this);
            instance.setTraceback(tb);
            PArguments.setSpecialArgument(self.getArguments(), pException);
            return resumeGenerator(self);
        }
    }

    @Builtin(name = "gi_code", fixedNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class GetCodeNode extends PythonBuiltinNode {
        @Specialization
        Object getCode(PGenerator self,
                        @Cached("createBinaryProfile()") ConditionProfile hasCodeProfile) {
            PCode code = self.getCode();
            if (hasCodeProfile.profile(code == null)) {
                code = factory().createCode(self.getGeneratorRootNode());
                self.setCode(code);
            }
            return code;
        }
    }
}
