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
package com.oracle.graal.python.builtins.objects.bytes;

import java.util.Arrays;

import com.oracle.graal.python.builtins.objects.type.LazyPythonClass;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.runtime.sequence.PSequence;
import com.oracle.graal.python.runtime.sequence.storage.ByteSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.NativeSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage.ListStorageType;
import com.oracle.truffle.api.CompilerAsserts;

public final class PByteArray extends PSequence implements PIBytesLike {

    private SequenceStorage store;

    public PByteArray(LazyPythonClass cls, byte[] bytes) {
        super(cls);
        store = new ByteSequenceStorage(bytes);
    }

    public PByteArray(LazyPythonClass cls, SequenceStorage store) {
        super(cls);
        this.store = store;
    }

    @Override
    public SequenceStorage getSequenceStorage() {
        return store;
    }

    @Override
    public final void setSequenceStorage(SequenceStorage store) {
        assert store instanceof ByteSequenceStorage || store instanceof NativeSequenceStorage && ((NativeSequenceStorage) store).getElementType() == ListStorageType.Byte;
        this.store = store;
    }

    @Override
    public String toString() {
        CompilerAsserts.neverPartOfCompilation();
        if (store instanceof ByteSequenceStorage) {
            byte[] barr = ((ByteSequenceStorage) store).getInternalByteArray();
            return String.format("bytearray(%s)", BytesUtils.bytesRepr(barr, barr.length));
        } else {
            return String.format("bytearray(%s)", store);
        }
    }

    @Override
    public final int hashCode() {
        return Arrays.hashCode(store.getInternalArray());
    }

    public final void reverse() {
        store.reverse();
    }

    @Override
    public PIBytesLike createFromBytes(PythonObjectFactory factory, byte[] bytes) {
        return factory.createByteArray(bytes);
    }
}
