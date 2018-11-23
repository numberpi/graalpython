/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.runtime.interop;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.MessageResolution;
import com.oracle.truffle.api.interop.Resolve;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.Node;

public final class InteropArray implements TruffleObject {
    @CompilationFinal(dimensions = 1) private final Object[] array;

    InteropArray(Object[] array) {
        this.array = array;
    }

    @Override
    public ForeignAccess getForeignAccess() {
        return InteropArrayMRForeign.ACCESS;
    }

    private Object get(int pos) {
        return array[pos];
    }

    private int size() {
        return array.length;
    }

    static boolean isInstance(TruffleObject object) {
        return object instanceof InteropArray;
    }

    @MessageResolution(receiverType = InteropArray.class)
    static class InteropArrayMR {

        @Resolve(message = "READ")
        abstract static class Read extends Node {
            Object access(InteropArray target, int index) {
                return target.get(index);
            }

            Object access(InteropArray target, long index) {
                return target.get((int) index);
            }
        }

        @Resolve(message = "HAS_SIZE")
        abstract static class HasSize extends Node {
            boolean access(@SuppressWarnings("unused") InteropArray target) {
                return true;
            }
        }

        @Resolve(message = "GET_SIZE")
        abstract static class GetSize extends Node {
            int access(InteropArray target) {
                return target.size();
            }
        }
    }

}
