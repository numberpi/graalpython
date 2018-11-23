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
package com.oracle.graal.python.builtins.objects.bytes;

import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;

import java.util.ArrayList;

import com.oracle.graal.python.builtins.objects.bytes.BytesNodesFactory.BytesJoinNodeGen;
import com.oracle.graal.python.builtins.objects.bytes.BytesNodesFactory.FindNodeGen;
import com.oracle.graal.python.builtins.objects.bytes.BytesNodesFactory.ToBytesNodeGen;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes.NormalizeIndexNode;
import com.oracle.graal.python.builtins.objects.memoryview.PMemoryView;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode;
import com.oracle.graal.python.nodes.control.GetIteratorNode;
import com.oracle.graal.python.nodes.control.GetNextNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.sequence.PSequence;
import com.oracle.graal.python.runtime.sequence.storage.ByteSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.ValueProfile;

public abstract class BytesNodes {

    public abstract static class BytesJoinNode extends PNodeWithContext {

        public abstract byte[] execute(byte[] sep, Object iterable);

        @Specialization
        public byte[] join(byte[] sep, Object iterable,
                        @Cached("create()") GetIteratorNode getIteratorNode,
                        @Cached("create()") GetNextNode getNextNode,
                        @Cached("create()") ToBytesNode toBytesNode,
                        @Cached("create()") IsBuiltinClassProfile errorProfile) {
            ArrayList<byte[]> parts = new ArrayList<>();
            int partsTotalSize = 0;
            Object iterator = getIteratorNode.executeWith(iterable);
            while (true) {
                try {
                    partsTotalSize += append(parts, toBytesNode.execute(getNextNode.execute(iterator)));
                } catch (PException e) {
                    e.expectStopIteration(errorProfile);
                    return joinArrays(sep, parts, partsTotalSize);
                }
            }
        }

        @TruffleBoundary(allowInlining = true, transferToInterpreterOnException = false)
        private static int append(ArrayList<byte[]> parts, byte[] barr) {
            parts.add(barr);
            return barr.length;
        }

        @TruffleBoundary(allowInlining = true, transferToInterpreterOnException = false)
        private static byte[] joinArrays(byte[] sep, ArrayList<byte[]> parts, int partsTotalSize) {
            byte[] joinedBytes = new byte[Math.max(0, partsTotalSize + (parts.size() - 1) * sep.length)];
            if (parts.size() > 0) {
                int offset = 0;
                byte[] array = parts.get(0);
                System.arraycopy(array, 0, joinedBytes, offset, array.length);
                offset += array.length;
                for (int i = 1; i < parts.size(); i++) {
                    array = parts.get(i);
                    System.arraycopy(sep, 0, joinedBytes, offset, sep.length);
                    offset += sep.length;
                    System.arraycopy(array, 0, joinedBytes, offset, array.length);
                    offset += array.length;
                }
            }
            return joinedBytes;
        }

        public static BytesJoinNode create() {
            return BytesJoinNodeGen.create();
        }
    }

    @ImportStatic({PGuards.class, SpecialMethodNames.class})
    public abstract static class ToBytesNode extends PNodeWithContext {
        @Child private SequenceStorageNodes.ToByteArrayNode toByteArrayNode;

        protected final boolean allowRecursive;

        ToBytesNode(boolean allowRecursive) {
            this.allowRecursive = allowRecursive;
        }

        public abstract byte[] execute(Object obj);

        @Specialization
        byte[] doBytes(PBytes bytes,
                        @Cached("create()") IsBuiltinClassProfile exceptionProfile) {
            return doBytesLike(bytes, exceptionProfile);
        }

        @Specialization
        byte[] doByteArray(PByteArray byteArray,
                        @Cached("create()") IsBuiltinClassProfile exceptionProfile) {
            return doBytesLike(byteArray, exceptionProfile);
        }

        private byte[] doBytesLike(PIBytesLike bytes, IsBuiltinClassProfile exceptionProfile) {
            try {
                return getToByteArrayNode().execute(bytes.getSequenceStorage());
            } catch (PException e) {
                e.expect(TypeError, exceptionProfile);
                return doError(bytes);
            }
        }

        @Specialization(guards = "allowRecursive")
        byte[] doMemoryView(PMemoryView memoryView,
                        @Cached("createRecursive()") ToBytesNode recursive,
                        @Cached("create(TOBYTES)") LookupAndCallUnaryNode callToBytesNode) {
            return recursive.execute(callToBytesNode.executeObject(memoryView));
        }

        @Fallback
        byte[] doError(Object obj) {
            throw raise(TypeError, "expected a bytes-like object, %p found", obj);
        }

        private SequenceStorageNodes.ToByteArrayNode getToByteArrayNode() {
            if (toByteArrayNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                toByteArrayNode = insert(SequenceStorageNodes.ToByteArrayNode.create());
            }
            return toByteArrayNode;
        }

        protected ToBytesNode createRecursive() {
            return ToBytesNode.create(false);
        }

        public static ToBytesNode create() {
            return ToBytesNodeGen.create(true);
        }

        public static ToBytesNode create(boolean allowRecursive) {
            return ToBytesNodeGen.create(allowRecursive);
        }
    }

    public abstract static class FindNode extends PNodeWithContext {

        @Child private NormalizeIndexNode normalizeIndexNode;
        @Child private SequenceStorageNodes.GetItemNode getLeftItemNode;
        @Child private SequenceStorageNodes.GetItemNode getRightItemNode;

        public abstract int execute(PIBytesLike bytes, Object sub, Object starting, Object ending);

        @Specialization
        int find(PIBytesLike primary, PIBytesLike sub, Object starting, Object ending) {
            SequenceStorage haystack = primary.getSequenceStorage();
            int len1 = haystack.length();

            SequenceStorage needle = sub.getSequenceStorage();
            int len2 = needle.length();

            int start = getNormalizeIndexNode().execute(starting, len1);
            int end = getNormalizeIndexNode().execute(ending, len1);

            if (start >= len1 || len1 < len2) {
                return -1;
            } else if (end > len1) {
                end = len1;
            }

            // TODO implement a more efficient algorithm
            outer: for (int i = start; i < end; i++) {
                for (int j = 0; j < len2; j++) {
                    int hb = getGetLeftItemNode().executeInt(haystack, i + j);
                    int nb = getGetRightItemNode().executeInt(needle, j);
                    if (nb != hb || i + j >= end) {
                        continue outer;
                    }
                }
                return i;
            }
            return -1;
        }

        @Specialization
        int find(PIBytesLike primary, int sub, Object starting, @SuppressWarnings("unused") Object ending) {
            SequenceStorage haystack = primary.getSequenceStorage();
            int len1 = haystack.length();

            int start = getNormalizeIndexNode().execute(starting, len1);
            if (start >= len1) {
                return -1;
            }

            for (int i = start; i < len1; i++) {
                int hb = getGetLeftItemNode().executeInt(haystack, i);
                if (hb == sub) {
                    return i;
                }
            }
            return -1;
        }

        @Fallback
        int doError(@SuppressWarnings("unused") PIBytesLike bytes, Object sub, @SuppressWarnings("unused") Object starting, @SuppressWarnings("unused") Object ending) {
            throw raise(TypeError, "expected a bytes-like object, %p found", sub);
        }

        private NormalizeIndexNode getNormalizeIndexNode() {
            if (normalizeIndexNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                normalizeIndexNode = insert(NormalizeIndexNode.create(false));
            }
            return normalizeIndexNode;
        }

        private SequenceStorageNodes.GetItemNode getGetLeftItemNode() {
            if (getLeftItemNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getLeftItemNode = insert(SequenceStorageNodes.GetItemNode.create());
            }
            return getLeftItemNode;
        }

        private SequenceStorageNodes.GetItemNode getGetRightItemNode() {
            if (getRightItemNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getRightItemNode = insert(SequenceStorageNodes.GetItemNode.create());
            }
            return getRightItemNode;
        }

        public static FindNode create() {
            return FindNodeGen.create();
        }
    }

    public static class FromSequenceStorageNode extends Node {

        @Node.Child private SequenceStorageNodes.GetItemNode getItemNode;
        @Node.Child private SequenceStorageNodes.CastToByteNode castToByteNode;
        @Node.Child private SequenceStorageNodes.LenNode lenNode;

        public byte[] execute(SequenceStorage storage) {
            int len = getLenNode().execute(storage);
            byte[] bytes = new byte[len];
            for (int i = 0; i < len; i++) {
                Object item = getGetItemNode().execute(storage, i);
                bytes[i] = getCastToByteNode().execute(item);
            }
            return bytes;
        }

        private SequenceStorageNodes.GetItemNode getGetItemNode() {
            if (getItemNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getItemNode = insert(SequenceStorageNodes.GetItemNode.create(NormalizeIndexNode.forList()));
            }
            return getItemNode;
        }

        private SequenceStorageNodes.CastToByteNode getCastToByteNode() {
            if (castToByteNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                castToByteNode = insert(SequenceStorageNodes.CastToByteNode.create());
            }
            return castToByteNode;
        }

        private SequenceStorageNodes.LenNode getLenNode() {
            if (lenNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                lenNode = insert(SequenceStorageNodes.LenNode.create());
            }
            return lenNode;
        }

        public static FromSequenceStorageNode create() {
            return new FromSequenceStorageNode();
        }
    }

    public static class FromSequenceNode extends Node {

        @Child private FromSequenceStorageNode fromSequenceStorageNode;

        public byte[] execute(PSequence sequence) {
            if (fromSequenceStorageNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                fromSequenceStorageNode = insert(FromSequenceStorageNode.create());
            }

            return fromSequenceStorageNode.execute(sequence.getSequenceStorage());
        }

        public static FromSequenceNode create() {
            return new FromSequenceNode();
        }
    }

    public abstract static class FromIteratorNode extends PNodeWithContext {

        @Child private SequenceStorageNodes.AppendNode appendByteNode;

        public abstract byte[] execute(Object iterator);

        public SequenceStorageNodes.AppendNode getAppendByteNode() {
            if (appendByteNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                appendByteNode = insert(SequenceStorageNodes.AppendNode.create(() -> SequenceStorageNodes.NoGeneralizationNode.create("byte must be in range(0, 256)")));
            }
            return appendByteNode;
        }

        @Specialization
        public byte[] doIt(Object iterObject,
                        @Cached("create()") GetNextNode getNextNode,
                        @Cached("create()") IsBuiltinClassProfile errorProfile) {
            ByteSequenceStorage bss = new ByteSequenceStorage(16);
            while (true) {
                try {
                    getAppendByteNode().execute(bss, getNextNode.execute(iterObject));
                } catch (PException e) {
                    e.expectStopIteration(errorProfile);
                    return bss.getInternalByteArray();
                }
            }
        }

        public static FromIteratorNode create() {
            return BytesNodesFactory.FromIteratorNodeGen.create();
        }
    }

    public static class CmpNode extends PNodeWithContext {
        @Child private SequenceStorageNodes.GetItemNode getLeftItemNode;
        @Child private SequenceStorageNodes.GetItemNode getRightItemNode;
        @Child private SequenceStorageNodes.LenNode leftLenNode;
        @Child private SequenceStorageNodes.LenNode rightLenNode;

        private final ValueProfile leftProfile = ValueProfile.createClassProfile();
        private final ValueProfile rightProfile = ValueProfile.createClassProfile();

        public int execute(PIBytesLike left, PIBytesLike right) {
            PIBytesLike leftProfiled = leftProfile.profile(left);
            PIBytesLike rightProfiled = rightProfile.profile(right);
            int leftLen = getleftLenNode().execute(leftProfiled.getSequenceStorage());
            int rightLen = getRightLenNode().execute(rightProfiled.getSequenceStorage());
            for (int i = 0; i < Math.min(leftLen, rightLen); i++) {
                int a = getGetLeftItemNode().executeInt(leftProfiled.getSequenceStorage(), i);
                int b = getGetRightItemNode().executeInt(rightProfiled.getSequenceStorage(), i);
                if (a != b) {
                    // CPython uses 'memcmp'; so do unsigned comparison
                    return a & 0xFF - b & 0xFF;
                }
            }
            return leftLen - rightLen;
        }

        private SequenceStorageNodes.GetItemNode getGetLeftItemNode() {
            if (getLeftItemNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getLeftItemNode = insert(SequenceStorageNodes.GetItemNode.create());
            }
            return getLeftItemNode;
        }

        private SequenceStorageNodes.GetItemNode getGetRightItemNode() {
            if (getRightItemNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getRightItemNode = insert(SequenceStorageNodes.GetItemNode.create());
            }
            return getRightItemNode;
        }

        private SequenceStorageNodes.LenNode getleftLenNode() {
            if (leftLenNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                leftLenNode = insert(SequenceStorageNodes.LenNode.create());
            }
            return leftLenNode;
        }

        private SequenceStorageNodes.LenNode getRightLenNode() {
            if (rightLenNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                rightLenNode = insert(SequenceStorageNodes.LenNode.create());
            }
            return rightLenNode;
        }

        public static CmpNode create() {
            return new CmpNode();
        }
    }
}
