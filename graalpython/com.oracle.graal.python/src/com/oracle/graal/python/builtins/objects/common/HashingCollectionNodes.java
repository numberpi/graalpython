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

import com.oracle.graal.python.builtins.objects.common.HashingCollectionNodesFactory.LenNodeGen;
import com.oracle.graal.python.builtins.objects.common.HashingCollectionNodesFactory.SetItemNodeGen;
import com.oracle.graal.python.builtins.objects.common.HashingCollectionNodesFactory.GetDictStorageNodeGen;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;

public abstract class HashingCollectionNodes {

    @ImportStatic(PGuards.class)
    public abstract static class LenNode extends PNodeWithContext {
        private @Child HashingStorageNodes.LenNode lenNode;

        public HashingStorageNodes.LenNode getLenNode() {
            if (lenNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                lenNode = insert(HashingStorageNodes.LenNode.create());
            }
            return lenNode;
        }

        public abstract int execute(PHashingCollection c);

        @Specialization(limit = "4", guards = {"c.getClass() == cachedClass"})
        int getLenCached(PHashingCollection c,
                        @Cached("c.getClass()") Class<? extends PHashingCollection> cachedClass) {
            return getLenNode().execute(cachedClass.cast(c).getDictStorage());
        }

        @Specialization(replaces = "getLenCached")
        int getLenGeneric(PHashingCollection c) {
            return getLenNode().execute(c.getDictStorage());
        }

        public static LenNode create() {
            return LenNodeGen.create();
        }
    }

    @ImportStatic(PGuards.class)
    public abstract static class SetItemNode extends PNodeWithContext {
        private @Child HashingStorageNodes.SetItemNode setItemNode;

        public HashingStorageNodes.SetItemNode getSetItemNode() {
            if (setItemNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                setItemNode = insert(HashingStorageNodes.SetItemNode.create());
            }
            return setItemNode;
        }

        public abstract void execute(PHashingCollection c, Object key, Object value);

        @Specialization(limit = "4", guards = {"c.getClass() == cachedClass"})
        void doSetItemCached(PHashingCollection c, Object key, Object value,
                        @Cached("c.getClass()") Class<? extends PHashingCollection> cachedClass) {
            cachedClass.cast(c).setDictStorage(getSetItemNode().execute(cachedClass.cast(c).getDictStorage(), key, value));
        }

        @Specialization(replaces = "doSetItemCached")
        void doSetItemGeneric(PHashingCollection c, Object key, Object value) {
            c.setDictStorage(getSetItemNode().execute(c.getDictStorage(), key, value));
        }

        public static SetItemNode create() {
            return SetItemNodeGen.create();
        }
    }

    @ImportStatic({PGuards.class})
    public abstract static class GetDictStorageNode extends PNodeWithContext {

        public abstract HashingStorage execute(PHashingCollection c);

        @Specialization(limit = "4", guards = {"c.getClass() == cachedClass"})
        HashingStorage getStorageCached(PHashingCollection c,
                        @Cached("c.getClass()") Class<? extends PHashingCollection> cachedClass) {
            return cachedClass.cast(c).getDictStorage();
        }

        @Specialization(replaces = "getStorageCached")
        HashingStorage getStorageGeneric(PHashingCollection c) {
            return c.getDictStorage();
        }

        public static GetDictStorageNode create() {
            return GetDictStorageNodeGen.create();
        }
    }
}
