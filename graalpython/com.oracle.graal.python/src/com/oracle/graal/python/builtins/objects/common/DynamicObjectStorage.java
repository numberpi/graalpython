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
package com.oracle.graal.python.builtins.objects.common;

import java.util.ArrayList;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.Layout;
import com.oracle.truffle.api.object.ObjectType;
import com.oracle.truffle.api.object.Shape;

public abstract class DynamicObjectStorage extends HashingStorage {

    public static final int SIZE_THRESHOLD = 100;

    private static final Layout LAYOUT = Layout.createLayout();
    private static final Shape EMPTY_SHAPE = LAYOUT.createShape(new ObjectType());

    private final DynamicObject store;

    private DynamicObjectStorage() {
        store = LAYOUT.newInstance(EMPTY_SHAPE);
    }

    private DynamicObjectStorage(DynamicObject store) {
        this.store = store;
    }

    @Override
    public int length() {
        return store.size();
    }

    @Override
    public boolean hasKey(Object key, Equivalence eq) {
        assert eq == HashingStorage.DEFAULT_EQIVALENCE;
        return store.containsKey(key);
    }

    @Override
    @TruffleBoundary
    public Object getItem(Object key, Equivalence eq) {
        assert eq == HashingStorage.DEFAULT_EQIVALENCE;
        return store.get(key);
    }

    @Override
    @TruffleBoundary
    public void setItem(Object key, Object value, Equivalence eq) {
        assert eq == HashingStorage.DEFAULT_EQIVALENCE;
        if (store.containsKey(key)) {
            store.set(key, value);
        } else {
            store.define(key, value);
            store.updateShape();
        }
    }

    @Override
    @TruffleBoundary
    public boolean remove(Object key, Equivalence eq) {
        assert eq == HashingStorage.DEFAULT_EQIVALENCE;
        boolean result = store.delete(key);
        store.updateShape();
        return result;
    }

    @Override
    public Iterable<Object> keys() {
        return wrapJavaIterable(store.getShape().getKeys());
    }

    @Override
    @TruffleBoundary
    public Iterable<Object> values() {
        ArrayList<Object> entries = new ArrayList<>(store.size());
        Shape shape = store.getShape();
        for (Object key : shape.getKeys()) {
            entries.add(store.get(key));
        }
        return wrapJavaIterable(entries);
    }

    @Override
    @TruffleBoundary
    public Iterable<DictEntry> entries() {
        ArrayList<DictEntry> entries = new ArrayList<>(store.size());
        Shape shape = store.getShape();
        for (Object key : shape.getKeys()) {
            entries.add(new DictEntry(key, store.get(key)));
        }
        return wrapJavaIterable(entries);
    }

    @Override
    @TruffleBoundary
    public void clear() {
        store.setShapeAndResize(store.getShape(), EMPTY_SHAPE);
        store.updateShape();
    }

    public DynamicObject getStore() {
        return store;
    }

    public static class FastDictStorage extends DynamicObjectStorage {
        public FastDictStorage() {
        }

        public FastDictStorage(DynamicObject copy) {
            super(copy);
        }

        @Override
        @TruffleBoundary
        public HashingStorage copy(Equivalence eq) {
            assert eq == HashingStorage.DEFAULT_EQIVALENCE;
            return new FastDictStorage(getStore().copy(getStore().getShape()));
        }
    }

    public static class PythonObjectDictStorage extends DynamicObjectStorage {
        private final Assumption dictUnsetOrSameAsStorage;

        public PythonObjectDictStorage(DynamicObject store) {
            this(store, null);
        }

        public PythonObjectDictStorage(DynamicObject store, Assumption dictUnsetOrSameAsStorage) {
            super(store);
            this.dictUnsetOrSameAsStorage = dictUnsetOrSameAsStorage;
        }

        public Assumption getDictUnsetOrSameAsStorage() {
            return dictUnsetOrSameAsStorage;
        }

        @Override
        @TruffleBoundary
        public HashingStorage copy(Equivalence eq) {
            assert eq == HashingStorage.DEFAULT_EQIVALENCE;
            return new PythonObjectDictStorage(getStore().copy(getStore().getShape()));
        }
    }

    public static class PythonObjectHybridDictStorage extends DynamicObjectStorage {
        private final EconomicMapStorage nonAttributesStorage;

        public PythonObjectHybridDictStorage(PythonObjectDictStorage storage) {
            this(storage.getStore());
        }

        public PythonObjectHybridDictStorage(DynamicObject store) {
            this(store, EconomicMapStorage.create(false));
        }

        PythonObjectHybridDictStorage(DynamicObject store, EconomicMapStorage nonAttributesStorage) {
            super(store);
            this.nonAttributesStorage = nonAttributesStorage;
        }

        @Override
        public int length() {
            return super.length() + nonAttributesStorage.length();
        }

        @Override
        public boolean hasKey(Object key, Equivalence eq) {
            if (super.hasKey(key, DEFAULT_EQIVALENCE)) {
                return true;
            }
            return hasKeyNonAttr(key, eq);
        }

        @TruffleBoundary
        private boolean hasKeyNonAttr(Object key, Equivalence eq) {
            return nonAttributesStorage.hasKey(key, eq);
        }

        @Override
        public Object getItem(Object key, Equivalence eq) {
            Object value = super.getItem(key, DEFAULT_EQIVALENCE);
            if (value != null) {
                return value;
            }
            return this.nonAttributesStorage.getItem(key, eq);
        }

        @Override
        public void setItem(Object key, Object value, Equivalence eq) {
            if (key instanceof String) {
                super.setItem(key, value, DEFAULT_EQIVALENCE);
            } else {
                setItemNonAttr(key, value, eq);
            }
        }

        @TruffleBoundary
        private void setItemNonAttr(Object key, Object value, Equivalence eq) {
            this.nonAttributesStorage.setItem(key, value, eq);
        }

        @Override
        public boolean remove(Object key, Equivalence eq) {
            if (super.remove(key, DEFAULT_EQIVALENCE)) {
                return true;
            }
            return removeNonAttr(key, eq);
        }

        @TruffleBoundary
        private boolean removeNonAttr(Object key, Equivalence eq) {
            return this.nonAttributesStorage.remove(key, eq);
        }

        @Override
        @TruffleBoundary
        public Iterable<Object> keys() {
            if (this.nonAttributesStorage.length() == 0) {
                return super.keys();
            } else {
                ArrayList<Object> entries = new ArrayList<>(this.length());
                for (Object entry : super.keys()) {
                    entries.add(entry);
                }
                for (Object entry : this.nonAttributesStorage.keys()) {
                    entries.add(entry);
                }
                return wrapJavaIterable(entries);
            }
        }

        @Override
        @TruffleBoundary
        public Iterable<Object> values() {
            if (this.nonAttributesStorage.length() == 0) {
                return super.values();
            } else {
                ArrayList<Object> entries = new ArrayList<>(this.length());
                for (Object entry : super.values()) {
                    entries.add(entry);
                }
                for (Object entry : this.nonAttributesStorage.values()) {
                    entries.add(entry);
                }
                return wrapJavaIterable(entries);
            }
        }

        @Override
        @TruffleBoundary
        public Iterable<DictEntry> entries() {
            if (this.nonAttributesStorage.length() == 0) {
                return super.entries();
            } else {
                ArrayList<DictEntry> entries = new ArrayList<>(this.length());
                for (DictEntry entry : super.entries()) {
                    entries.add(entry);
                }
                for (DictEntry entry : this.nonAttributesStorage.entries()) {
                    entries.add(entry);
                }
                return wrapJavaIterable(entries);
            }
        }

        @Override
        public void clear() {
            super.clear();
            clearNonAttrs();
        }

        @TruffleBoundary
        private void clearNonAttrs() {
            this.nonAttributesStorage.clear();
        }

        @Override
        public HashingStorage copy(Equivalence eq) {
            return new PythonObjectHybridDictStorage(getStore().copy(getStore().getShape()), (EconomicMapStorage) copyNonAttrs(eq));
        }

        @TruffleBoundary
        private HashingStorage copyNonAttrs(Equivalence eq) {
            return nonAttributesStorage.copy(eq);
        }
    }
}
