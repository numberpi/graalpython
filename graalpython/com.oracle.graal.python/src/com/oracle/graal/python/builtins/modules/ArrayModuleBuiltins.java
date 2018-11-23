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
package com.oracle.graal.python.builtins.modules;

import static com.oracle.graal.python.runtime.exception.PythonErrorType.OverflowError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;

import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.array.PArray;
import com.oracle.graal.python.builtins.objects.common.SequenceNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes.CastToByteNode;
import com.oracle.graal.python.builtins.objects.range.PRange;
import com.oracle.graal.python.builtins.objects.type.PythonClass;
import com.oracle.graal.python.nodes.control.GetIteratorNode;
import com.oracle.graal.python.nodes.control.GetNextNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.sequence.PSequence;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;

@CoreFunctions(defineModule = "array")
public final class ArrayModuleBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return ArrayModuleBuiltinsFactory.getFactories();
    }

    // array.array(typecode[, initializer])
    @Builtin(name = "array", minNumOfPositionalArgs = 2, maxNumOfPositionalArgs = 3, constructsClass = PythonBuiltinClassType.PArray)
    @GenerateNodeFactory
    abstract static class PythonArrayNode extends PythonBuiltinNode {

        @Specialization(guards = "isNoValue(initializer)")
        PArray array(PythonClass cls, String typeCode, @SuppressWarnings("unused") PNone initializer) {
            /**
             * TODO @param typeCode should be a char, not a string
             */
            return makeEmptyArray(cls, typeCode.charAt(0));
        }

        @Specialization
        PArray arrayWithRangeInitializer(PythonClass cls, String typeCode, PRange range) {
            if (!typeCode.equals("i")) {
                typeError(typeCode, range);
            }

            int[] intArray = new int[range.len()];

            int start = range.getStart();
            int stop = range.getStop();
            int step = range.getStep();

            int index = 0;
            for (int i = start; i < stop; i += step) {
                intArray[index++] = i;
            }

            return factory().createArray(cls, intArray);
        }

        @Specialization
        PArray arrayWithSequenceInitializer(PythonClass cls, String typeCode, String str) {
            if (!typeCode.equals("c")) {
                typeError(typeCode, str);
            }

            return factory().createArray(cls, str.toCharArray());
        }

        protected boolean isIntArray(String typeCode) {
            return typeCode.charAt(0) == 'i';
        }

        protected boolean isLongArray(String typeCode) {
            return typeCode.charAt(0) == 'l';
        }

        protected boolean isByteArray(String typeCode) {
            return typeCode.charAt(0) == 'b';
        }

        protected boolean isDoubleArray(String typeCode) {
            return typeCode.charAt(0) == 'd';
        }

        @Specialization(guards = "isByteArray(typeCode)")
        PArray arrayByteInitializer(PythonClass cls, @SuppressWarnings("unused") String typeCode, PSequence initializer,
                        @Cached("createCast()") CastToByteNode castToByteNode,
                        @Cached("create()") GetIteratorNode getIterator,
                        @Cached("create()") GetNextNode next,
                        @Cached("create()") IsBuiltinClassProfile errorProfile,
                        @Cached("create()") SequenceNodes.LenNode lenNode) {
            Object iter = getIterator.executeWith(initializer);
            int i = 0;
            byte[] byteArray = new byte[lenNode.execute(initializer)];

            while (true) {
                Object nextValue;
                try {
                    nextValue = next.execute(iter);
                } catch (PException e) {
                    e.expectStopIteration(errorProfile);
                    break;
                }
                byteArray[i++] = castToByteNode.execute(nextValue);
            }

            return factory().createArray(cls, byteArray);
        }

        @Specialization(guards = "isIntArray(typeCode)")
        PArray arrayIntInitializer(PythonClass cls, @SuppressWarnings("unused") String typeCode, PSequence initializer,
                        @Cached("create()") GetIteratorNode getIterator,
                        @Cached("create()") GetNextNode next,
                        @Cached("create()") IsBuiltinClassProfile errorProfile,
                        @Cached("create()") SequenceNodes.LenNode lenNode) {
            Object iter = getIterator.executeWith(initializer);
            int i = 0;

            int[] intArray = new int[lenNode.execute(initializer)];

            while (true) {
                Object nextValue;
                try {
                    nextValue = next.execute(iter);
                } catch (PException e) {
                    e.expectStopIteration(errorProfile);
                    break;
                }
                if (nextValue instanceof Integer) {
                    intArray[i++] = (int) nextValue;
                } else {
                    throw raise(ValueError, "integer argument expected, got %p", nextValue);
                }
            }

            return factory().createArray(cls, intArray);
        }

        @Specialization(guards = "isLongArray(typeCode)")
        PArray arrayLongInitializer(PythonClass cls, @SuppressWarnings("unused") String typeCode, PSequence initializer,
                        @Cached("create()") GetIteratorNode getIterator,
                        @Cached("create()") GetNextNode next,
                        @Cached("create()") IsBuiltinClassProfile errorProfile,
                        @Cached("create()") SequenceNodes.LenNode lenNode) {
            Object iter = getIterator.executeWith(initializer);
            int i = 0;

            long[] longArray = new long[lenNode.execute(initializer)];

            while (true) {
                Object nextValue;
                try {
                    nextValue = next.execute(iter);
                } catch (PException e) {
                    e.expectStopIteration(errorProfile);
                    break;
                }
                if (nextValue instanceof Number) {
                    longArray[i++] = longValue((Number) nextValue);
                } else {
                    throw raise(ValueError, "integer argument expected, got %p", nextValue);
                }
            }

            return factory().createArray(cls, longArray);
        }

        @Specialization(guards = "isDoubleArray(typeCode)")
        PArray arrayDoubleInitializer(PythonClass cls, @SuppressWarnings("unused") String typeCode, PSequence initializer,
                        @Cached("create()") GetIteratorNode getIterator,
                        @Cached("create()") GetNextNode next,
                        @Cached("create()") IsBuiltinClassProfile errorProfile,
                        @Cached("create()") SequenceNodes.LenNode lenNode) {
            Object iter = getIterator.executeWith(initializer);
            int i = 0;

            double[] doubleArray = new double[lenNode.execute(initializer)];

            while (true) {
                Object nextValue;
                try {
                    nextValue = next.execute(iter);
                } catch (PException e) {
                    e.expectStopIteration(errorProfile);
                    break;
                }
                if (nextValue instanceof Integer) {
                    doubleArray[i++] = ((Integer) nextValue).doubleValue();
                } else if (nextValue instanceof Double) {
                    doubleArray[i++] = (double) nextValue;
                } else {
                    throw raise(ValueError, "double value expected");
                }
            }

            return factory().createArray(cls, doubleArray);
        }

        @Specialization
        @TruffleBoundary
        PArray arrayWithObjectInitializer(@SuppressWarnings("unused") PythonClass cls, @SuppressWarnings("unused") String typeCode, Object initializer) {
            if (!(isIntArray(typeCode) || isByteArray(typeCode) || isDoubleArray(typeCode))) {
                // TODO implement support for typecodes: b, B, u, h, H, i, I, l, L, q, Q, f or d
                throw raise(ValueError, "bad typecode (must be i, d, b, or l)");
            }
            throw new RuntimeException("Unsupported initializer " + initializer);
        }

        @Specialization(guards = "!isString(typeCode)")
        PArray noArray(@SuppressWarnings("unused") PythonClass cls, Object typeCode, @SuppressWarnings("unused") Object initializer) {
            throw raise(TypeError, "array() argument 1 must be a unicode character, not %p", typeCode);
        }

        @TruffleBoundary
        private static long longValue(Number n) {
            return n.longValue();
        }

        private PArray makeEmptyArray(PythonClass cls, char type) {
            switch (type) {
                case 'c':
                case 'b':
                case 'B':
                    return factory().createArray(cls, new char[0]);
                case 'i':
                    return factory().createArray(cls, new int[0]);
                case 'd':
                    return factory().createArray(cls, new double[0]);
                default:
                    return null;
            }
        }

        protected CastToByteNode createCast() {
            return CastToByteNode.create(val -> {
                throw raise(OverflowError, "signed char is greater than maximum");
            }, null);

        }

        @TruffleBoundary
        private void typeError(String typeCode, Object initializer) {
            throw raise(TypeError, "cannot use a %p to initialize an array with typecode '%s'", initializer, typeCode);
        }
    }
}
