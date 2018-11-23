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

import static com.oracle.graal.python.nodes.frame.FrameSlotIDs.RETURN_SLOT_ID;
import static com.oracle.graal.python.nodes.frame.FrameSlotIDs.TEMP_LOCAL_PREFIX;

import java.util.Iterator;
import java.util.NoSuchElementException;

import com.oracle.graal.python.builtins.objects.cell.PCell;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameSlot;

// XXX: remove special case for RETURN_SLOT_ID
public class LocalsStorage extends HashingStorage {

    private final Frame frame;
    private int length = -1;
    private final boolean skipCells;

    public LocalsStorage(Frame frame, boolean skipCells) {
        this.frame = frame;
        this.skipCells = skipCells;
    }

    private Object getValue(FrameSlot slot) {
        if (slot != null) {
            Object value = frame.getValue(slot);
            if (value instanceof PCell) {
                if (skipCells) {
                    return null;
                }
                return ((PCell) value).getRef();
            }
            return value;
        }
        return null;
    }

    public Frame getFrame() {
        return frame;
    }

    @Override
    public void addAll(HashingStorage other, Equivalence eq) {
        throw UnmodifiableStorageException.INSTANCE;
    }

    @Override
    @TruffleBoundary
    public Object getItem(Object key, Equivalence eq) {
        assert eq == DEFAULT_EQIVALENCE;
        if (RETURN_SLOT_ID.equals(key)) {
            return null;
        } else if (isTempLocal(key)) {
            return null;
        }
        FrameSlot slot = frame.getFrameDescriptor().findFrameSlot(key);
        return getValue(slot);
    }

    private static boolean isTempLocal(Object key) {
        return key instanceof String && ((String) key).startsWith(TEMP_LOCAL_PREFIX);
    }

    @Override
    public void setItem(Object key, Object value, Equivalence eq) {
        throw UnmodifiableStorageException.INSTANCE;
    }

    @Override
    public Iterable<Object> keys() {
        return new Iterable<Object>() {

            public Iterator<Object> iterator() {
                return new KeysIterator();
            }
        };
    }

    @Override
    @TruffleBoundary
    public boolean hasKey(Object key, Equivalence eq) {
        assert eq == DEFAULT_EQIVALENCE;
        if (RETURN_SLOT_ID.equals(key)) {
            return false;
        } else if (isTempLocal(key)) {
            return false;
        }
        return frame.getFrameDescriptor().findFrameSlot(key) != null;
    }

    @Override
    @TruffleBoundary
    public int length() {
        if (length == -1) {
            length = frame.getFrameDescriptor().getSize();
            for (FrameSlot slot : frame.getFrameDescriptor().getSlots()) {
                Object identifier = slot.getIdentifier();
                if (identifier.equals(RETURN_SLOT_ID) || isTempLocal(identifier) || frame.getValue(slot) == null) {
                    length--;
                }
            }
        }
        return length;
    }

    @Override
    public boolean remove(Object key, Equivalence eq) {
        throw UnmodifiableStorageException.INSTANCE;
    }

    @Override
    @TruffleBoundary
    public Iterable<Object> values() {
        return new Iterable<Object>() {

            public Iterator<Object> iterator() {
                return new ValuesIterator();
            }
        };
    }

    @Override
    @TruffleBoundary
    public Iterable<DictEntry> entries() {
        return new Iterable<DictEntry>() {

            public Iterator<DictEntry> iterator() {
                return new ItemsIterator();
            }
        };
    }

    @Override
    @TruffleBoundary
    public void clear() {
        throw UnmodifiableStorageException.INSTANCE;
    }

    @Override
    public HashingStorage copy(Equivalence eq) {
        assert eq == DEFAULT_EQIVALENCE;
        return new LocalsStorage(frame, skipCells);
    }

    private abstract class LocalsIterator<T> implements Iterator<T> {

        private Iterator<? extends FrameSlot> keys;
        private FrameSlot nextSlot = null;

        @Override
        public boolean hasNext() {
            if (frame.getFrameDescriptor().getSize() == 0) {
                return false;
            }
            if (nextSlot == null) {
                return loadNext();
            }
            return true;
        }

        @TruffleBoundary
        public FrameSlot nextSlot() {
            if (hasNext()) {
                assert nextSlot != null;
                FrameSlot value = nextSlot;
                nextSlot = null;
                return value;
            }
            throw new NoSuchElementException();
        }

        @TruffleBoundary
        private boolean loadNext() {
            while (keysIterator().hasNext()) {
                FrameSlot nextCandidate = keysIterator().next();
                Object identifier = nextCandidate.getIdentifier();
                if (identifier instanceof String) {
                    if (!RETURN_SLOT_ID.equals(identifier) && !isTempLocal(identifier)) {
                        Object nextValue = frame.getValue(nextCandidate);
                        if (skipCells && nextValue instanceof PCell) {
                            continue;
                        }
                        if (nextValue != null) {
                            nextSlot = nextCandidate;
                            return true;
                        }
                    }
                }
            }
            return false;
        }

        protected final Iterator<? extends FrameSlot> keysIterator() {
            if (keys == null) {
                keys = frame.getFrameDescriptor().getSlots().iterator();
            }
            return keys;
        }
    }

    private class KeysIterator extends LocalsIterator<Object> {
        @Override
        @TruffleBoundary
        public Object next() {
            return nextSlot().getIdentifier();
        }
    }

    private class ValuesIterator extends LocalsIterator<Object> {
        @Override
        @TruffleBoundary
        public Object next() {
            return getValue(nextSlot());
        }
    }

    private class ItemsIterator extends LocalsIterator<DictEntry> {
        @Override
        @TruffleBoundary
        public DictEntry next() {
            FrameSlot slot = nextSlot();
            return new DictEntry(slot.getIdentifier(), getValue(slot));
        }
    }
}
