/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.graal.python.nodes.builtins;

import java.util.ArrayList;
import java.util.List;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.LazyPythonClass;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.control.GetIteratorNode;
import com.oracle.graal.python.nodes.control.GetNextNode;
import com.oracle.graal.python.nodes.object.GetLazyClassNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;

@GenerateNodeFactory
public abstract class TupleNodes {

    @ImportStatic({PGuards.class, SpecialMethodNames.class})
    public abstract static class ConstructTupleNode extends PNodeWithContext {

        @Child private GetLazyClassNode getClassNode;

        protected LazyPythonClass getClass(Object value) {
            if (getClassNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getClassNode = insert(GetLazyClassNode.create());
            }
            return getClassNode.execute(value);
        }

        public final PTuple execute(Object value) {
            return execute(PythonBuiltinClassType.PTuple, value);
        }

        public abstract PTuple execute(LazyPythonClass cls, Object value);

        @Specialization(guards = "isNoValue(none)")
        public PTuple tuple(LazyPythonClass cls, @SuppressWarnings("unused") PNone none) {
            return factory().createEmptyTuple(cls);
        }

        @Specialization
        public PTuple tuple(LazyPythonClass cls, String arg) {
            Object[] values = new Object[arg.length()];
            for (int i = 0; i < arg.length(); i++) {
                values[i] = String.valueOf(arg.charAt(i));
            }
            return factory().createTuple(cls, values);
        }

        @Specialization(guards = {"cannotBeOverridden(cls)", "cannotBeOverridden(getClass(iterable))"})
        public PTuple tuple(@SuppressWarnings("unused") LazyPythonClass cls, PTuple iterable) {
            return iterable;
        }

        @TruffleBoundary(allowInlining = true, transferToInterpreterOnException = false)
        @Specialization(guards = {"!isNoValue(iterable)", "createNewTuple(cls, iterable)"})
        public PTuple tuple(LazyPythonClass cls, Object iterable,
                        @Cached("create()") GetIteratorNode getIterator,
                        @Cached("create()") GetNextNode next,
                        @Cached("create()") IsBuiltinClassProfile errorProfile) {

            Object iterator = getIterator.executeWith(iterable);
            List<Object> internalStorage = new ArrayList<>();
            while (true) {
                try {
                    internalStorage.add(next.execute(iterator));
                } catch (PException e) {
                    e.expectStopIteration(errorProfile);
                    return factory().createTuple(cls, internalStorage.toArray());
                }
            }
        }

        @Fallback
        public PTuple tuple(@SuppressWarnings("unused") LazyPythonClass cls, Object value) {
            CompilerDirectives.transferToInterpreter();
            throw new RuntimeException("list does not support iterable object " + value);
        }

        protected boolean createNewTuple(LazyPythonClass cls, Object iterable) {
            if (iterable instanceof PTuple) {
                return !(PGuards.cannotBeOverridden(cls) && PGuards.cannotBeOverridden(getClass(iterable)));
            }
            return true;
        }

        public static ConstructTupleNode create() {
            return TupleNodesFactory.ConstructTupleNodeGen.create();
        }
    }
}
