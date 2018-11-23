/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.nodes;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PythonAbstractObject;
import com.oracle.graal.python.builtins.objects.array.PArray;
import com.oracle.graal.python.builtins.objects.bytes.PByteArray;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.cext.PythonNativeClass;
import com.oracle.graal.python.builtins.objects.cext.PythonNativeObject;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.floats.PFloat;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.function.PythonCallable;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.iterator.PSequenceIterator;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.object.PythonBuiltinObject;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.range.PRange;
import com.oracle.graal.python.builtins.objects.set.PFrozenSet;
import com.oracle.graal.python.builtins.objects.slice.PSlice;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.LazyPythonClass;
import com.oracle.graal.python.builtins.objects.type.PythonBuiltinClass;
import com.oracle.graal.python.builtins.objects.type.PythonClass;
import com.oracle.graal.python.runtime.sequence.PSequence;
import com.oracle.graal.python.runtime.sequence.storage.BasicSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.ByteSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.DoubleSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.EmptySequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.IntSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.ListSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.LongSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.ObjectSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.TupleSequenceStorage;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.UnexpectedResultException;

public abstract class PGuards {

    /**
     * Specialization guards.
     */

    public static boolean isEmpty(Object[] array) {
        return array.length == 0;
    }

    public static boolean isEmpty(String string) {
        return string.length() == 0;
    }

    public static boolean isNone(Object value) {
        return value == PNone.NONE;
    }

    public static boolean isNoValue(Object object) {
        return object == PNone.NO_VALUE;
    }

    public static boolean isDict(Object object) {
        return object instanceof PDict;
    }

    public static boolean isCallable(Object value) {
        return value instanceof PythonCallable;
    }

    public static boolean isClass(Object value) {
        return value instanceof PythonClass;
    }

    public static boolean isEmptyStorage(PSequence sequence) {
        return sequence.getSequenceStorage() instanceof EmptySequenceStorage;
    }

    public static boolean isEmptyStorage(PArray array) {
        return array.getSequenceStorage() instanceof EmptySequenceStorage;
    }

    public static boolean isBasicStorage(PSequence sequence) {
        return sequence.getSequenceStorage() instanceof BasicSequenceStorage;
    }

    public static boolean isIntStorage(PSequence sequence) {
        return sequence.getSequenceStorage() instanceof IntSequenceStorage;
    }

    public static boolean isByteStorage(PSequence array) {
        return array.getSequenceStorage() instanceof ByteSequenceStorage;
    }

    public static boolean areBothIntStorage(PSequence first, PSequence second) {
        return first.getSequenceStorage() instanceof IntSequenceStorage && second.getSequenceStorage() instanceof IntSequenceStorage;
    }

    public static boolean areBothByteStorage(PSequence first, PSequence second) {
        return first.getSequenceStorage() instanceof ByteSequenceStorage && second.getSequenceStorage() instanceof ByteSequenceStorage;
    }

    public static boolean isLongStorage(PSequence sequence) {
        return sequence.getSequenceStorage() instanceof LongSequenceStorage;
    }

    public static boolean areBothLongStorage(PList first, PList second) {
        return first.getSequenceStorage() instanceof LongSequenceStorage && second.getSequenceStorage() instanceof LongSequenceStorage;
    }

    public static boolean isDoubleStorage(PSequence sequence) {
        return sequence.getSequenceStorage() instanceof DoubleSequenceStorage;
    }

    public static boolean areBothDoubleStorage(PList first, PList second) {
        return first.getSequenceStorage() instanceof DoubleSequenceStorage && second.getSequenceStorage() instanceof DoubleSequenceStorage;
    }

    public static boolean isListStorage(PList list) {
        return list.getSequenceStorage() instanceof ListSequenceStorage;
    }

    public static boolean isTupleStorage(PList list) {
        return list.getSequenceStorage() instanceof TupleSequenceStorage;
    }

    public static boolean isObjectStorage(PList list) {
        return list.getSequenceStorage() instanceof ObjectSequenceStorage;
    }

    public static boolean areBothObjectStorage(PList first, PList second) {
        return first.getSequenceStorage() instanceof ObjectSequenceStorage && second.getSequenceStorage() instanceof ObjectSequenceStorage;
    }

    public static boolean isList(Object o) {
        return o instanceof PList;
    }

    public static boolean isObjectStorageIterator(PSequenceIterator iterator) {
        if (!iterator.isPSequence()) {
            return false;
        }

        PSequence sequence = iterator.getPSequence();

        if (sequence instanceof PList) {
            PList list = (PList) sequence;
            return list.getSequenceStorage() instanceof ObjectSequenceStorage;
        }

        return false;
    }

    public static boolean isPythonObject(Object obj) {
        return obj instanceof PythonObject;
    }

    /**
     * Argument guards.
     */
    public static boolean emptyArguments(VirtualFrame frame) {
        return PArguments.getUserArgumentLength(frame) == 0;
    }

    public static boolean argGiven(Object object) {
        return object == PNone.NO_VALUE;
    }

    public static boolean emptyArguments(PNone none) {
        return none == PNone.NO_VALUE;
    }

    public static boolean emptyArguments(Object arg) {
        return arg instanceof PFrozenSet && ((PFrozenSet) arg).size() == 0;
    }

    @SuppressWarnings("unused")
    public static boolean isForJSON(Object obj, String id, Object defaultValue) {
        return id.equals("for_json");
    }

    public static boolean is2ndNotTuple(@SuppressWarnings("unused") Object first, Object second) {
        return !(second instanceof PTuple);
    }

    public static boolean isIndexPositive(int idx) {
        return idx >= 0;
    }

    public static boolean isIndexNegative(int idx) {
        return idx < 0;
    }

    public static boolean isIndexPositive(long idx) {
        return idx >= 0;
    }

    public static boolean isIndexNegative(long idx) {
        return idx < 0;
    }

    public static boolean isPythonUserClass(Object klass) {
        return !isPythonBuiltinClass(klass);
    }

    public static boolean isPythonBuiltinClass(Object klass) {
        return klass instanceof PythonBuiltinClass;
    }

    public static boolean isNativeObject(Object object) {
        return object instanceof PythonNativeObject;
    }

    public static boolean isNativeClass(Object klass) {
        return klass instanceof PythonNativeClass;
    }

    public static boolean isPRange(Object obj) {
        return obj instanceof PRange;
    }

    public static boolean isString(Object obj) {
        return obj instanceof String || obj instanceof PString;
    }

    public static boolean isBuiltinFunction(Object obj) {
        return obj instanceof PBuiltinFunction;
    }

    public static boolean isBuiltinObject(Object obj) {
        return obj instanceof PythonBuiltinObject;
    }

    public static boolean isForeignObject(Object obj) {
        return obj instanceof TruffleObject && !(obj instanceof PythonAbstractObject);
    }

    public static boolean isPInt(Object obj) {
        return obj instanceof PInt;
    }

    public static boolean isPString(Object obj) {
        return obj instanceof PString;
    }

    public static boolean isPFloat(Object obj) {
        return obj instanceof PFloat;
    }

    public static boolean isPNone(Object obj) {
        return obj instanceof PNone;
    }

    public static boolean isPTuple(Object obj) {
        return obj instanceof PTuple;
    }

    public static boolean isInteger(Object obj) {
        return obj instanceof Long || obj instanceof Integer;
    }

    public static boolean isBytes(Object obj) {
        return obj instanceof PBytes || obj instanceof PByteArray;
    }

    public static boolean isPSlice(Object obj) {
        return obj instanceof PSlice;
    }

    public static boolean expectBoolean(Object result) throws UnexpectedResultException {
        if (result instanceof Boolean) {
            return (Boolean) result;
        }
        throw new UnexpectedResultException(result);
    }

    public static int expectInteger(Object result) throws UnexpectedResultException {
        if (result instanceof Integer) {
            return (Integer) result;
        }
        throw new UnexpectedResultException(result);
    }

    public static long expectLong(Object result) throws UnexpectedResultException {
        if (result instanceof Long) {
            return (Long) result;
        }
        throw new UnexpectedResultException(result);
    }

    public static double expectDouble(Object result) throws UnexpectedResultException {
        if (result instanceof Double) {
            return (Double) result;
        }
        throw new UnexpectedResultException(result);
    }

    /**
     * Tests if the class of a Python object is a builtin class, i.e., any magic methods cannot be
     * overridden.
     */
    public static boolean cannotBeOverridden(LazyPythonClass clazz) {
        return clazz instanceof PythonBuiltinClassType || clazz instanceof PythonBuiltinClass;
    }
}
