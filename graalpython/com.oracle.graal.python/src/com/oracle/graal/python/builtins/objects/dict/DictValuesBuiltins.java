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
package com.oracle.graal.python.builtins.objects.dict;

import static com.oracle.graal.python.nodes.SpecialMethodNames.__EQ__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__ITER__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__LEN__;

import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes;
import com.oracle.graal.python.builtins.objects.dict.PDictView.PDictValuesView;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PDictValuesView)
public final class DictValuesBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return DictValuesBuiltinsFactory.getFactories();
    }

    @Builtin(name = __LEN__, fixedNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class LenNode extends PythonBuiltinNode {
        @Specialization
        Object run(PDictView self) {
            return self.getWrappedDict().size();
        }
    }

    @Builtin(name = __ITER__, fixedNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class IterNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object doPDictValuesView(PDictValuesView self) {
            if (self.getWrappedDict() != null) {
                return factory().createDictValuesIterator(self.getWrappedDict());
            }
            return PNone.NONE;
        }
    }

    @Builtin(name = __EQ__, fixedNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class EqNode extends PythonBuiltinNode {
        @Specialization
        @TruffleBoundary
        boolean doItemsView(PDictValuesView self, PDictValuesView other,
                        @Cached("create()") HashingStorageNodes.ContainsKeyNode containsKeyNode) {

            for (Object selfKey : self.getWrappedDict().keys()) {
                if (!containsKeyNode.execute(other.getWrappedDict().getDictStorage(), selfKey)) {
                    return false;
                }
            }
            return true;
        }

        @Fallback
        @SuppressWarnings("unused")
        PNotImplemented doGeneric(Object self, Object other) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }
}
