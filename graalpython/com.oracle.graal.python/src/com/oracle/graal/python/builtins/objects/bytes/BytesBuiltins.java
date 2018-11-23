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

import static com.oracle.graal.python.nodes.SpecialMethodNames.__ADD__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__CONTAINS__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__EQ__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__GETITEM__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__GE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__GT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__INIT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__ITER__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__LEN__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__LE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__LT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__MUL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__NE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__REPR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__RMUL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__SETITEM__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;

import java.io.UnsupportedEncodingException;
import java.nio.charset.CodingErrorAction;
import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.builtins.objects.common.HashingCollectionNodes.SetItemNode;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes.NormalizeIndexNode;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes.ToByteArrayNode;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.iterator.PSequenceIterator;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.graal.python.runtime.sequence.storage.ByteSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PBytes)
public class BytesBuiltins extends PythonBuiltins {

    public static CodingErrorAction toCodingErrorAction(String errors, PNodeWithContext n) {
        switch (errors) {
            case "strict":
                return CodingErrorAction.REPORT;
            case "ignore":
                return CodingErrorAction.IGNORE;
            case "replace":
                return CodingErrorAction.REPLACE;
        }
        throw n.raise(PythonErrorType.LookupError, "unknown error handler name '%s'", errors);
    }

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return BytesBuiltinsFactory.getFactories();
    }

    @Builtin(name = __INIT__, takesVarArgs = true, minNumOfPositionalArgs = 1, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    public abstract static class InitNode extends PythonBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization
        public PNone init(Object self, Object args, Object kwargs) {
            // TODO: tfel: throw an error if we get additional arguments and the __new__
            // method was the same as object.__new__
            return PNone.NONE;
        }
    }

    @Builtin(name = __EQ__, fixedNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class EqNode extends PythonBinaryBuiltinNode {
        @Child SequenceStorageNodes.CmpNode eqNode;

        @Specialization
        public boolean eq(PBytes self, PByteArray other) {
            return getEqNode().execute(self.getSequenceStorage(), other.getSequenceStorage());
        }

        @Specialization
        public boolean eq(PBytes self, PBytes other) {
            return getEqNode().execute(self.getSequenceStorage(), other.getSequenceStorage());
        }

        @SuppressWarnings("unused")
        @Fallback
        public Object eq(Object self, Object other) {
            if (self instanceof PBytes) {
                return PNotImplemented.NOT_IMPLEMENTED;
            }
            throw raise(TypeError, "descriptor '__eq__' requires a 'bytes' object but received a '%p'", self);
        }

        private SequenceStorageNodes.CmpNode getEqNode() {
            if (eqNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                eqNode = insert(SequenceStorageNodes.CmpNode.createEq());
            }
            return eqNode;
        }
    }

    @Builtin(name = __NE__, fixedNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class NeNode extends PythonBinaryBuiltinNode {
        @Specialization
        public boolean eq(PBytes self, PByteArray other) {
            return !self.equals(other);
        }

        @Specialization
        public boolean eq(PBytes self, PBytes other) {
            return !self.equals(other);
        }

        @SuppressWarnings("unused")
        @Fallback
        public Object eq(Object self, Object other) {
            if (self instanceof PBytes) {
                return PNotImplemented.NOT_IMPLEMENTED;
            }
            throw raise(TypeError, "descriptor '__ne__' requires a 'bytes' object but received a '%p'", self);
        }
    }

    public abstract static class CmpNode extends PythonBinaryBuiltinNode {
        @Child private BytesNodes.CmpNode cmpNode;

        int cmp(PBytes self, PBytes other) {
            if (cmpNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                cmpNode = insert(BytesNodes.CmpNode.create());
            }
            return cmpNode.execute(self, other);
        }

    }

    @Builtin(name = __LT__, fixedNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class LtNode extends CmpNode {
        @Specialization
        boolean doBytes(PBytes self, PBytes other) {
            return cmp(self, other) < 0;
        }

        @Fallback
        @SuppressWarnings("unused")
        public Object doGeneric(Object self, Object other) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = __LE__, fixedNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class LeNode extends CmpNode {
        @Specialization
        boolean doBytes(PBytes self, PBytes other) {
            return cmp(self, other) <= 0;
        }

        @Fallback
        @SuppressWarnings("unused")
        public Object doGeneric(Object self, Object other) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = __GT__, fixedNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class GtNode extends CmpNode {
        @Specialization
        boolean doBytes(PBytes self, PBytes other) {
            return cmp(self, other) > 0;
        }

        @Fallback
        @SuppressWarnings("unused")
        public Object doGeneric(Object self, Object other) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = __GE__, fixedNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class GeNode extends CmpNode {
        @Specialization
        boolean doBytes(PBytes self, PBytes other) {
            return cmp(self, other) >= 0;
        }

        @Fallback
        @SuppressWarnings("unused")
        public Object doGeneric(Object self, Object other) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = __ADD__, fixedNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class AddNode extends PythonBinaryBuiltinNode {

        @Specialization
        public Object add(PBytes left, PIBytesLike right,
                        @Cached("create()") SequenceStorageNodes.ConcatNode concatNode) {
            ByteSequenceStorage res = (ByteSequenceStorage) concatNode.execute(left.getSequenceStorage(), right.getSequenceStorage());
            return factory().createBytes(res);
        }

        @SuppressWarnings("unused")
        @Fallback
        public Object add(Object self, Object other) {
            throw raise(TypeError, "can't concat bytes to %p", other);
        }
    }

    @Builtin(name = __MUL__, fixedNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class MulNode extends PythonBinaryBuiltinNode {
        @Specialization
        public Object mul(PBytes self, int times,
                        @Cached("create()") SequenceStorageNodes.RepeatNode repeatNode) {
            ByteSequenceStorage res = (ByteSequenceStorage) repeatNode.execute(self.getSequenceStorage(), times);
            return factory().createBytes(res);
        }

        @SuppressWarnings("unused")
        @Fallback
        public Object mul(Object self, Object other) {
            throw raise(TypeError, "can't multiply sequence by non-int of type '%p'", other);
        }
    }

    @Builtin(name = __RMUL__, fixedNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class RMulNode extends MulNode {
    }

    @Builtin(name = __REPR__, fixedNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ReprNode extends PythonUnaryBuiltinNode {
        @Specialization
        @TruffleBoundary
        public Object repr(PBytes self,
                        @Cached("create()") SequenceStorageNodes.GetItemNode getItemNode) {
            SequenceStorage store = self.getSequenceStorage();
            int len = store.length();
            StringBuilder sb = new StringBuilder("b'");
            for (int i = 0; i < len; i++) {
                BytesUtils.byteRepr(sb, (byte) getItemNode.executeInt(store, i));
            }
            sb.append("'");
            return sb.toString();
        }
    }

    // bytes.join(iterable)
    @Builtin(name = "join", fixedNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class JoinNode extends PythonBinaryBuiltinNode {
        /**
         * @param bytes - the parameter is used to force the DSL to make this a dynamic check
         */
        protected boolean readOpaque(PBytes bytes) {
            return OpaqueBytes.isEnabled(getContext());
        }

        @Specialization(guards = {"readOpaque(bytes)"})
        public Object join(PBytes bytes, PList iterable,
                        @Cached("create()") SequenceStorageNodes.GetItemNode getItemNode,
                        @Cached("create()") SequenceStorageNodes.LenNode lenNode,
                        @Cached("create()") SequenceStorageNodes.ToByteArrayNode toByteArrayNode,
                        @Cached("create()") BytesNodes.BytesJoinNode bytesJoinNode) {
            int len = lenNode.execute(iterable.getSequenceStorage());
            if (len == 1) {
                // branch profiles aren't really needed, because of the specialization
                // happening in the getItemNode on first execution and the assumption
                // in OpaqueBytes.isInstance
                Object firstItem = getItemNode.execute(iterable.getSequenceStorage(), 0);
                if (OpaqueBytes.isInstance(firstItem)) {
                    return firstItem;
                }
            }
            return join(bytes, iterable, toByteArrayNode, bytesJoinNode);
        }

        @Specialization(guards = {"!readOpaque(bytes)"})
        public PBytes join(PBytes bytes, Object iterable,
                        @Cached("create()") SequenceStorageNodes.ToByteArrayNode toByteArrayNode,
                        @Cached("create()") BytesNodes.BytesJoinNode bytesJoinNode) {
            return factory().createBytes(bytesJoinNode.execute(toByteArrayNode.execute(bytes.getSequenceStorage()), iterable));
        }

        @Fallback
        @SuppressWarnings("unused")
        public Object doGeneric(Object self, Object arg) {
            throw raise(TypeError, "can only join an iterable");
        }
    }

    @Builtin(name = __LEN__, fixedNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class LenNode extends PythonUnaryBuiltinNode {
        @Specialization
        public int len(PBytes self,
                        @Cached("create()") SequenceStorageNodes.LenNode lenNode) {
            return lenNode.execute(self.getSequenceStorage());
        }
    }

    @Builtin(name = __CONTAINS__, fixedNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class ContainsNode extends PythonBinaryBuiltinNode {
        @Child private SequenceStorageNodes.LenNode lenNode;

        @Specialization
        @TruffleBoundary
        boolean contains(PBytes self, PBytes other,
                        @Cached("create()") BytesNodes.FindNode findNode) {
            return findNode.execute(self, other, 0, getLength(self.getSequenceStorage())) != -1;
        }

        @Specialization
        @TruffleBoundary
        boolean contains(PBytes self, PByteArray other,
                        @Cached("create()") BytesNodes.FindNode findNode) {
            return findNode.execute(self, other, 0, getLength(self.getSequenceStorage())) != -1;
        }

        @Specialization(guards = "!isBytes(other)")
        boolean contains(@SuppressWarnings("unused") PBytes self, Object other) {
            throw raise(TypeError, "a bytes-like object is required, not '%p'", other);
        }

        private int getLength(SequenceStorage s) {
            if (lenNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                lenNode = insert(SequenceStorageNodes.LenNode.create());
            }
            return lenNode.execute(s);
        }
    }

    @Builtin(name = __ITER__, fixedNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class IterNode extends PythonUnaryBuiltinNode {
        @Specialization
        PSequenceIterator contains(PBytes self) {
            return factory().createSequenceIterator(self);
        }
    }

    @Builtin(name = "startswith", minNumOfPositionalArgs = 2, maxNumOfPositionalArgs = 4)
    @GenerateNodeFactory
    abstract static class StartsWithNode extends PythonBuiltinNode {
        @Child private SequenceStorageNodes.LenNode lenNode;

        @Specialization
        boolean startswith(PBytes self, PIBytesLike prefix, @SuppressWarnings("unused") PNone start, @SuppressWarnings("unused") PNone end,
                        @Cached("create()") BytesNodes.FindNode findNode) {
            return findNode.execute(self, prefix, 0, getLength(self.getSequenceStorage())) == 0;
        }

        @Specialization
        boolean startswith(PBytes self, PIBytesLike prefix, int start, @SuppressWarnings("unused") PNone end,
                        @Cached("create()") BytesNodes.FindNode findNode) {
            return findNode.execute(self, prefix, start, getLength(self.getSequenceStorage())) == start;
        }

        @Specialization
        boolean startswith(PBytes self, PIBytesLike prefix, int start, int end,
                        @Cached("create()") BytesNodes.FindNode findNode) {
            return findNode.execute(self, prefix, start, end) == start;
        }

        private int getLength(SequenceStorage s) {
            if (lenNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                lenNode = insert(SequenceStorageNodes.LenNode.create());
            }
            return lenNode.execute(s);
        }
    }

    @Builtin(name = "endswith", minNumOfPositionalArgs = 2, maxNumOfPositionalArgs = 4)
    @GenerateNodeFactory
    abstract static class EndsWithNode extends PythonBuiltinNode {
        @Child private SequenceStorageNodes.LenNode lenNode;

        @Specialization
        boolean endswith(PBytes self, PIBytesLike suffix, @SuppressWarnings("unused") PNone start, @SuppressWarnings("unused") PNone end,
                        @Cached("create()") BytesNodes.FindNode findNode) {
            return findNode.execute(self, suffix, getLength(self.getSequenceStorage()) - getLength(suffix.getSequenceStorage()), getLength(self.getSequenceStorage())) != -1;
        }

        private int getLength(SequenceStorage s) {
            if (lenNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                lenNode = insert(SequenceStorageNodes.LenNode.create());
            }
            return lenNode.execute(s);
        }
    }

    @Builtin(name = "strip", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, keywordArguments = {"bytes"})
    @GenerateNodeFactory
    abstract static class StripNode extends PythonBuiltinNode {
        @Specialization
        @TruffleBoundary
        PBytes strip(PBytes self, @SuppressWarnings("unused") PNone bytes,
                        @Cached("create()") BytesNodes.ToBytesNode toBytesNode) {
            return factory().createBytes(new String(toBytesNode.execute(self)).trim().getBytes());
        }
    }

    // bytes.find(bytes[, start[, end]])
    @Builtin(name = "find", minNumOfPositionalArgs = 2, maxNumOfPositionalArgs = 4)
    @GenerateNodeFactory
    abstract static class FindNode extends PythonBuiltinNode {
        @Child private BytesNodes.FindNode findNode;
        @Child private SequenceStorageNodes.LenNode lenNode;

        @Specialization
        int find(PBytes self, Object sub, @SuppressWarnings("unused") PNone start, @SuppressWarnings("unused") PNone end) {
            return find(self, sub, 0, getLength(self.getSequenceStorage()));
        }

        @Specialization
        int find(PBytes self, Object sub, int start, @SuppressWarnings("unused") PNone end) {
            return find(self, sub, start, getLength(self.getSequenceStorage()));
        }

        @Specialization
        int find(PBytes self, Object sub, int start, int ending) {
            return getFindNode().execute(self, sub, start, ending);
        }

        private BytesNodes.FindNode getFindNode() {
            if (findNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                findNode = insert(BytesNodes.FindNode.create());
            }
            return findNode;
        }

        private int getLength(SequenceStorage s) {
            if (lenNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                lenNode = insert(SequenceStorageNodes.LenNode.create());
            }
            return lenNode.execute(s);
        }
    }

    @Builtin(name = __GETITEM__, fixedNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class GetitemNode extends PythonBinaryBuiltinNode {
        @Specialization
        Object doSlice(PBytes self, Object key,
                        @Cached("createGetItem()") SequenceStorageNodes.GetItemNode getSequenceItemNode) {
            return getSequenceItemNode.execute(self.getSequenceStorage(), key);
        }

        protected static SequenceStorageNodes.GetItemNode createGetItem() {
            return SequenceStorageNodes.GetItemNode.create(NormalizeIndexNode.create(), (s, f) -> f.createByteArray(s));
        }
    }

    @Builtin(name = __SETITEM__, fixedNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    abstract static class SetitemNode extends PythonTernaryBuiltinNode {
        @Specialization
        @SuppressWarnings("unused")
        Object getitem(PBytes self, Object idx, Object value) {
            throw raise(TypeError, "'bytes' object does not support item assignment");
        }
    }

    // static str.maketrans()
    @Builtin(name = "maketrans", fixedNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class MakeTransNode extends PythonBuiltinNode {

        @Specialization
        public PDict maketrans(PBytes from, PBytes to,
                        @Cached("create()") SetItemNode setItemNode,
                        @Cached("create()") ToByteArrayNode toByteArrayNode) {
            byte[] fromB = toByteArrayNode.execute(from.getSequenceStorage());
            byte[] toB = toByteArrayNode.execute(to.getSequenceStorage());
            if (fromB.length != toB.length) {
                throw new RuntimeException("maketrans arguments must have same length");
            }

            PDict translation = factory().createDict();
            for (int i = 0; i < fromB.length; i++) {
                int key = fromB[i];
                int value = toB[i];
                setItemNode.execute(translation, key, value);
            }

            return translation;
        }
    }

    @Builtin(name = "replace", fixedNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    abstract static class ReplaceNode extends PythonTernaryBuiltinNode {
        @Child BytesNodes.ToBytesNode toBytes = BytesNodes.ToBytesNode.create();

        @Specialization
        @TruffleBoundary
        PBytes replace(PBytes self, PBytes substr, PBytes replacement) {
            byte[] bytes = toBytes.execute(self);
            byte[] subBytes = toBytes.execute(substr);
            byte[] replacementBytes = toBytes.execute(replacement);
            try {
                String string = new String(bytes, "ASCII");
                String subString = new String(subBytes, "ASCII");
                String replacementString = new String(replacementBytes, "ASCII");
                byte[] newBytes = string.replace(subString, replacementString).getBytes("ASCII");
                return factory().createBytes(newBytes);
            } catch (UnsupportedEncodingException e) {
                CompilerDirectives.transferToInterpreter();
                throw new IllegalStateException();
            }
        }
    }
}
