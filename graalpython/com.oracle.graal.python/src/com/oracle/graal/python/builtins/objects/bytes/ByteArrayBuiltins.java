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

import static com.oracle.graal.python.builtins.objects.slice.PSlice.MISSING_INDEX;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__ADD__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__BOOL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__DELITEM__;
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
import static com.oracle.graal.python.nodes.SpecialMethodNames.__REPR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__RMUL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__SETITEM__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__STR__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.SystemError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;

import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes.GetItemNode;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes.NoGeneralizationNode;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes.NormalizeIndexNode;
import com.oracle.graal.python.builtins.objects.memoryview.PMemoryView;
import com.oracle.graal.python.builtins.objects.range.PRange;
import com.oracle.graal.python.builtins.objects.slice.PSlice;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode;
import com.oracle.graal.python.nodes.expression.BinaryComparisonNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.object.GetLazyClassNode;
import com.oracle.graal.python.nodes.truffle.PythonArithmeticTypes;
import com.oracle.graal.python.runtime.sequence.PSequence;
import com.oracle.graal.python.runtime.sequence.storage.ByteSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.IntSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.profiles.ValueProfile;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PByteArray)
public class ByteArrayBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return ByteArrayBuiltinsFactory.getFactories();
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

    @Builtin(name = __DELITEM__, fixedNumOfPositionalArgs = 2)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
    public abstract static class DelItemNode extends PythonBinaryBuiltinNode {
        @Specialization
        protected PNone doGeneric(PByteArray self, Object key,
                        @Cached("create()") SequenceStorageNodes.DeleteNode deleteNode) {
            deleteNode.execute(self.getSequenceStorage(), key);
            return PNone.NONE;
        }

        @SuppressWarnings("unused")
        @Fallback
        protected Object doGeneric(Object self, Object idx) {
            throw raise(TypeError, "descriptor '__delitem__' requires a 'bytearray' object but received a '%p'", idx);
        }
    }

    @Builtin(name = __EQ__, fixedNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class EqNode extends PythonBinaryBuiltinNode {
        @Child SequenceStorageNodes.CmpNode eqNode;

        @Specialization
        public boolean eq(PByteArray self, PByteArray other) {
            return getEqNode().execute(self.getSequenceStorage(), other.getSequenceStorage());
        }

        @Specialization
        public boolean eq(PByteArray self, PBytes other) {
            return getEqNode().execute(self.getSequenceStorage(), other.getSequenceStorage());
        }

        @SuppressWarnings("unused")
        @Fallback
        public Object eq(Object self, Object other) {
            if (self instanceof PByteArray) {
                return PNotImplemented.NOT_IMPLEMENTED;
            }
            throw raise(TypeError, "descriptor '__eq__' requires a 'bytearray' object but received a '%p'", self);
        }

        private SequenceStorageNodes.CmpNode getEqNode() {
            if (eqNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                eqNode = insert(SequenceStorageNodes.CmpNode.createEq());
            }
            return eqNode;
        }
    }

    public abstract static class CmpNode extends PythonBinaryBuiltinNode {
        @Child private BytesNodes.CmpNode cmpNode;

        int cmp(PByteArray self, PIBytesLike other) {
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
        boolean doBytes(PByteArray self, PIBytesLike other) {
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
        boolean doBytes(PByteArray self, PIBytesLike other) {
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
        boolean doBytes(PByteArray self, PIBytesLike other) {
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
        boolean doBytes(PByteArray self, PIBytesLike other) {
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
        public Object add(PByteArray self, PIBytesLike other,
                        @Cached("create()") SequenceStorageNodes.ConcatNode concatNode) {
            SequenceStorage res = concatNode.execute(self.getSequenceStorage(), other.getSequenceStorage());
            return factory().createByteArray(res);
        }

        @SuppressWarnings("unused")
        @Fallback
        public Object add(Object self, Object other) {
            throw raise(TypeError, "can't concat bytearray to %p", other);
        }
    }

    @Builtin(name = __MUL__, fixedNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class MulNode extends PythonBuiltinNode {

        @Specialization
        public Object mul(PByteArray self, int times,
                        @Cached("create()") SequenceStorageNodes.RepeatNode repeatNode) {
            SequenceStorage res = repeatNode.execute(self.getSequenceStorage(), times);
            return factory().createByteArray(res);
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

    @Builtin(name = __STR__, fixedNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class StrNode extends PythonUnaryBuiltinNode {
        @Specialization
        public Object str(PByteArray self) {
            return self.toString();
        }
    }

    @Builtin(name = __REPR__, fixedNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ReprNode extends PythonUnaryBuiltinNode {
        @Specialization
        @TruffleBoundary
        public Object repr(PByteArray self) {
            return self.toString();
        }
    }

    // bytearray.append(x)
    @Builtin(name = "append", fixedNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class ByteArrayAppendNode extends PythonBinaryBuiltinNode {
        @Specialization
        public PByteArray append(PByteArray byteArray, Object arg,
                        @Cached("createAppend()") SequenceStorageNodes.AppendNode appendNode) {
            appendNode.execute(byteArray.getSequenceStorage(), arg);
            return byteArray;
        }

        protected static SequenceStorageNodes.AppendNode createAppend() {
            return SequenceStorageNodes.AppendNode.create(() -> NoGeneralizationNode.create("byte must be in range(0, 256)"));
        }
    }

    // bytearray.extend(L)
    @Builtin(name = "extend", fixedNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class ByteArrayExtendNode extends PythonBinaryBuiltinNode {

        @Specialization
        public PNone doGeneric(PByteArray byteArray, Object source,
                        @Cached("createExtend()") SequenceStorageNodes.ExtendNode extendNode) {
            SequenceStorage execute = extendNode.execute(byteArray.getSequenceStorage(), source);
            assert byteArray.getSequenceStorage() == execute;
            return PNone.NONE;
        }

        protected static SequenceStorageNodes.ExtendNode createExtend() {
            return SequenceStorageNodes.ExtendNode.create(() -> NoGeneralizationNode.create("byte must be in range(0, 256)"));
        }

        protected boolean isPSequenceWithStorage(Object source) {
            return (source instanceof PSequence && !(source instanceof PTuple || source instanceof PRange));
        }

    }

    // bytearray.copy()
    @Builtin(name = "copy", fixedNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ByteArrayCopyNode extends PythonBuiltinNode {
        @Specialization
        public PByteArray copy(PByteArray byteArray,
                        @Cached("create()") GetLazyClassNode getClass,
                        @Cached("create()") SequenceStorageNodes.ToByteArrayNode toByteArray) {
            return factory().createByteArray(getClass.execute(byteArray), toByteArray.execute(byteArray.getSequenceStorage()));
        }
    }

    // bytearray.index(x)
    @Builtin(name = "index", fixedNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class ByteArrayIndexNode extends PythonBuiltinNode {
        @Child private SequenceStorageNodes.LenNode lenNode;

        @Specialization
        public int index(PByteArray byteArray, Object arg,
                        @Cached("create()") BytesNodes.FindNode findNode) {
            return findNode.execute(byteArray, arg, 0, getLength(byteArray.getSequenceStorage()));
        }

        private int getLength(SequenceStorage s) {
            if (lenNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                lenNode = insert(SequenceStorageNodes.LenNode.create());
            }
            return lenNode.execute(s);
        }
    }

    // bytearray.count(x)
    @Builtin(name = "count", fixedNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    @ImportStatic(SpecialMethodNames.class)
    public abstract static class ByteArrayCountNode extends PythonBinaryBuiltinNode {

        @Specialization
        public int count(PByteArray byteArray, Object arg,
                        @Cached("createClassProfile()") ValueProfile storeProfile,
                        @Cached("createNotNormalized()") SequenceStorageNodes.GetItemNode getItemNode,
                        @Cached("create(__EQ__, __EQ__, __EQ__)") BinaryComparisonNode eqNode) {

            SequenceStorage profiled = storeProfile.profile(byteArray.getSequenceStorage());
            int cnt = 0;
            for (int i = 0; i < profiled.length(); i++) {
                if (eqNode.executeBool(arg, getItemNode.execute(profiled, i))) {
                    cnt++;
                }
            }
            return cnt;
        }
    }

    // bytearray.reverse()
    @Builtin(name = "reverse", fixedNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ByteArrayReverseNode extends PythonBuiltinNode {

        @Specialization
        public PNone reverse(PByteArray byteArray) {
            byteArray.reverse();
            return PNone.NONE;
        }
    }

    // bytearray.clear()
    @Builtin(name = "clear", fixedNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ByteArrayClearNode extends PythonUnaryBuiltinNode {

        @Specialization
        public PNone clear(PByteArray byteArray,
                        @Cached("create()") SequenceStorageNodes.DeleteNode deleteNode) {
            deleteNode.execute(byteArray.getSequenceStorage(), factory().createSlice(MISSING_INDEX, MISSING_INDEX, 1));
            return PNone.NONE;
        }
    }

    @Builtin(name = __ITER__, fixedNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ByteArrayIterNode extends PythonUnaryBuiltinNode {
        @Specialization
        public Object iter(PByteArray byteArray) {
            return factory().createSequenceIterator(byteArray);
        }
    }

    @Builtin(name = "startswith", minNumOfPositionalArgs = 2, maxNumOfPositionalArgs = 4)
    @GenerateNodeFactory
    abstract static class StartsWithNode extends PythonBuiltinNode {
        @Child private SequenceStorageNodes.LenNode lenNode;

        @Specialization
        boolean startswith(PByteArray self, PIBytesLike prefix, @SuppressWarnings("unused") PNone start, @SuppressWarnings("unused") PNone end,
                        @Cached("create()") BytesNodes.FindNode findNode) {
            return findNode.execute(self, prefix, 0, getLength(self.getSequenceStorage())) == 0;
        }

        @Specialization
        boolean startswith(PByteArray self, PIBytesLike prefix, int start, @SuppressWarnings("unused") PNone end,
                        @Cached("create()") BytesNodes.FindNode findNode) {
            return findNode.execute(self, prefix, start, getLength(self.getSequenceStorage())) == start;
        }

        @Specialization
        boolean startswith(PByteArray self, PIBytesLike prefix, int start, int end,
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
        boolean endswith(PByteArray self, PIBytesLike suffix, @SuppressWarnings("unused") PNone start, @SuppressWarnings("unused") PNone end,
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

    // bytearray.join(iterable)
    @Builtin(name = "join", fixedNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class JoinNode extends PythonBinaryBuiltinNode {
        @Specialization
        public PByteArray join(PByteArray bytes, Object iterable,
                        @Cached("create()") SequenceStorageNodes.ToByteArrayNode toByteArrayNode,
                        @Cached("create()") BytesNodes.BytesJoinNode bytesJoinNode) {
            return factory().createByteArray(bytesJoinNode.execute(toByteArrayNode.execute(bytes.getSequenceStorage()), iterable));
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
        public int len(PByteArray self,
                        @Cached("create()") SequenceStorageNodes.LenNode lenNode) {
            return lenNode.execute(self.getSequenceStorage());
        }
    }

    @Builtin(name = SpecialMethodNames.__CONTAINS__, fixedNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class ContainsNode extends PythonBinaryBuiltinNode {
        @Child private SequenceStorageNodes.LenNode lenNode;

        @Specialization
        boolean contains(PByteArray self, PBytes other,
                        @Cached("create()") BytesNodes.FindNode findNode) {
            return findNode.execute(self, other, 0, getLength(self.getSequenceStorage())) != -1;
        }

        @Specialization
        boolean contains(PByteArray self, PByteArray other,
                        @Cached("create()") BytesNodes.FindNode findNode) {
            return findNode.execute(self, other, 0, getLength(self.getSequenceStorage())) != -1;
        }

        private int getLength(SequenceStorage s) {
            if (lenNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                lenNode = insert(SequenceStorageNodes.LenNode.create());
            }
            return lenNode.execute(s);
        }

        @Specialization(guards = {"!isBytes(other)"})
        boolean contains(PByteArray self, Object other,
                        @Cached("create()") BranchProfile errorProfile,
                        @Cached("create()") SequenceStorageNodes.ContainsNode containsNode) {

            if (!containsNode.execute(self.getSequenceStorage(), other)) {
                errorProfile.enter();
                throw raise(ValueError, "%s is not in bytes literal", other);
            }
            return true;
        }
    }

    @Builtin(name = "find", minNumOfPositionalArgs = 2, maxNumOfPositionalArgs = 4)
    @GenerateNodeFactory
    abstract static class FindNode extends PythonBuiltinNode {
        @Child private BytesNodes.FindNode findNode;
        @Child private SequenceStorageNodes.LenNode lenNode;

        @Specialization
        int find(PByteArray self, Object sub, @SuppressWarnings("unused") PNone start, @SuppressWarnings("unused") PNone end) {
            return find(self, sub, 0, getLength(self.getSequenceStorage()));
        }

        @Specialization
        int find(PByteArray self, Object sub, int start, @SuppressWarnings("unused") PNone end) {
            return find(self, sub, start, getLength(self.getSequenceStorage()));
        }

        @Specialization
        int find(PByteArray self, Object sub, int start, int ending) {
            return getFindNode().execute(self, sub, start, ending);
        }

        @Fallback
        @SuppressWarnings("unused")
        Object doGeneric(Object self, Object sub, Object start, Object ending) {
            throw raise(TypeError, "argument should be integer or bytes-like object, not '%p'", sub);
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

    @Builtin(name = "translate", minNumOfPositionalArgs = 2, maxNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    abstract static class TranslateNode extends PythonBuiltinNode {

        @Child private SequenceStorageNodes.GetItemNode getSelfItemNode;
        @Child private SequenceStorageNodes.GetItemNode getTableItemNode;

        @Specialization
        PByteArray translate(PByteArray self, PBytes table, @SuppressWarnings("unused") PNone delete) {
            return translate(self.getSequenceStorage(), table.getSequenceStorage());
        }

        @Specialization
        PByteArray translate(PByteArray self, PByteArray table, @SuppressWarnings("unused") PNone delete) {
            return translate(self.getSequenceStorage(), table.getSequenceStorage());
        }

        private PByteArray translate(SequenceStorage selfStorage, SequenceStorage tableStorage) {
            if (tableStorage.length() != 256) {
                throw raise(ValueError, "translation table must be 256 characters long");
            }
            byte[] result = new byte[selfStorage.length()];
            for (int i = 0; i < selfStorage.length(); i++) {
                int b = getGetSelfItemNode().executeInt(selfStorage, i);
                int t = getGetTableItemNode().executeInt(tableStorage, b);
                assert t >= 0 && t < 256;
                result[i] = (byte) t;
            }
            return factory().createByteArray(result);
        }

        private SequenceStorageNodes.GetItemNode getGetSelfItemNode() {
            if (getSelfItemNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getSelfItemNode = insert(SequenceStorageNodes.GetItemNode.create());
            }
            return getSelfItemNode;
        }

        private SequenceStorageNodes.GetItemNode getGetTableItemNode() {
            if (getTableItemNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getTableItemNode = insert(SequenceStorageNodes.GetItemNode.create());
            }
            return getTableItemNode;
        }

    }

    @Builtin(name = __GETITEM__, fixedNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class GetitemNode extends PythonBinaryBuiltinNode {
        @Specialization
        Object doSlice(PByteArray self, Object key,
                        @Cached("createGetItem()") SequenceStorageNodes.GetItemNode getSequenceItemNode) {
            return getSequenceItemNode.execute(self.getSequenceStorage(), key);
        }

        protected static GetItemNode createGetItem() {
            return SequenceStorageNodes.GetItemNode.create(NormalizeIndexNode.forBytearray(), (s, f) -> f.createByteArray(s));
        }
    }

    @Builtin(name = __SETITEM__, fixedNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    @ImportStatic(SpecialMethodNames.class)
    abstract static class SetItemNode extends PythonTernaryBuiltinNode {
        @Specialization(guards = {"!isPSlice(idx)", "!isMemoryView(value)"})
        PNone doItem(PByteArray self, Object idx, Object value,
                        @Cached("createSetItem()") SequenceStorageNodes.SetItemNode setItemNode) {
            setItemNode.execute(self.getSequenceStorage(), idx, value);
            return PNone.NONE;
        }

        @Specialization
        PNone doSliceMemoryview(PByteArray self, PSlice slice, PMemoryView value,
                        @Cached("create(TOBYTES)") LookupAndCallUnaryNode callToBytesNode,
                        @Cached("createBinaryProfile()") ConditionProfile isBytesProfile,
                        @Cached("createSetSlice()") SequenceStorageNodes.SetItemNode setItemNode) {
            Object bytesObj = callToBytesNode.executeObject(value);
            if (isBytesProfile.profile(bytesObj instanceof PBytes)) {
                doSlice(self, slice, bytesObj, setItemNode);
                return PNone.NONE;
            }
            throw raise(SystemError, "could not get bytes of memoryview");
        }

        @Specialization(guards = "!isMemoryView(value)")
        PNone doSlice(PByteArray self, PSlice idx, Object value,
                        @Cached("createSetSlice()") SequenceStorageNodes.SetItemNode setItemNode) {
            // this is really just a separate specialization due to the different error message
            setItemNode.execute(self.getSequenceStorage(), idx, value);
            return PNone.NONE;
        }

        @Fallback
        @SuppressWarnings("unused")
        Object doGeneric(Object self, Object idx, Object value) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }

        protected SequenceStorageNodes.SetItemNode createSetItem() {
            return SequenceStorageNodes.SetItemNode.create(NormalizeIndexNode.forBytearray(), "an integer is required");
        }

        protected SequenceStorageNodes.SetItemNode createSetSlice() {
            return SequenceStorageNodes.SetItemNode.create(NormalizeIndexNode.forBytearray(), "can assign only bytes, buffers, or iterables of ints in range(0, 256)");
        }

        protected static boolean isMemoryView(Object value) {
            return value instanceof PMemoryView;
        }
    }

    @Builtin(name = __BOOL__, fixedNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class BoolNode extends PythonBuiltinNode {
        @Specialization(guards = "isEmptyStorage(byteArray)")
        public boolean doEmpty(@SuppressWarnings("unused") PByteArray byteArray) {
            return false;
        }

        @Specialization(guards = "isIntStorage(byteArray)")
        public boolean doInt(PByteArray byteArray) {
            IntSequenceStorage store = (IntSequenceStorage) byteArray.getSequenceStorage();
            return store.length() != 0;
        }

        @Specialization(guards = "isByteStorage(byteArray)")
        public boolean doByte(PByteArray byteArray) {
            ByteSequenceStorage store = (ByteSequenceStorage) byteArray.getSequenceStorage();
            return store.length() != 0;
        }

        @Specialization
        boolean doLen(PByteArray operand,
                        @Cached("create()") SequenceStorageNodes.LenNode lenNode) {
            return lenNode.execute(operand.getSequenceStorage()) != 0;
        }

        @Fallback
        Object doGeneric(@SuppressWarnings("unused") Object self) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    abstract static class AStripNode extends PythonBinaryBuiltinNode {
        int mod() {
            throw new RuntimeException();
        }

        int stop(@SuppressWarnings("unused") byte[] bs) {
            throw new RuntimeException();
        }

        int start(@SuppressWarnings("unused") byte[] bs) {
            throw new RuntimeException();
        }

        PByteArray newBytesFrom(@SuppressWarnings("unused") byte[] bs, @SuppressWarnings("unused") int i) {
            throw new RuntimeException();
        }

        @Specialization
        PByteArray strip(PByteArray self, @SuppressWarnings("unused") PNone bytes,
                        @Cached("create()") BytesNodes.ToBytesNode toBytesNode) {
            byte[] bs = toBytesNode.execute(self);
            int i = start(bs);
            int stop = stop(bs);
            for (; i != stop; i += mod()) {
                if (!isWhitespace(bs[i])) {
                    break;
                }
            }
            return newBytesFrom(bs, i);
        }

        @TruffleBoundary
        private static boolean isWhitespace(byte b) {
            return Character.isWhitespace(b);
        }

        @Specialization
        PByteArray strip(PByteArray self, PBytes bytes,
                        @Cached("create()") BytesNodes.ToBytesNode selfToBytesNode,
                        @Cached("create()") BytesNodes.ToBytesNode otherToBytesNode) {
            byte[] stripBs = selfToBytesNode.execute(bytes);
            byte[] bs = otherToBytesNode.execute(self);
            int i = start(bs);
            int stop = stop(bs);
            outer: for (; i != stop; i += mod()) {
                for (byte b : stripBs) {
                    if (b == bs[i]) {
                        continue outer;
                    }
                }
                break;
            }
            return newBytesFrom(bs, i);
        }

    }

    @Builtin(name = "lstrip", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, keywordArguments = {"bytes"})
    @GenerateNodeFactory
    abstract static class LStripNode extends AStripNode {
        @Override
        PByteArray newBytesFrom(byte[] bs, int i) {
            byte[] out;
            if (i != 0) {
                int len = bs.length - i;
                out = new byte[len];
                System.arraycopy(bs, i, out, 0, len);
            } else {
                out = bs;
            }
            return factory().createByteArray(out);
        }

        @Override
        int mod() {
            return 1;
        }

        @Override
        int stop(byte[] bs) {
            return bs.length;
        }

        @Override
        int start(byte[] bs) {
            return 0;
        }
    }

    @Builtin(name = "rstrip", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, keywordArguments = {"bytes"})
    @GenerateNodeFactory
    abstract static class RStripNode extends AStripNode {
        @Override
        PByteArray newBytesFrom(byte[] bs, int i) {
            byte[] out;
            int len = i + 1;
            if (len != bs.length) {
                out = new byte[len];
                System.arraycopy(bs, 0, out, 0, len);
            } else {
                out = bs;
            }
            return factory().createByteArray(out);
        }

        @Override
        int mod() {
            return -1;
        }

        @Override
        int stop(byte[] bs) {
            return -1;
        }

        @Override
        int start(byte[] bs) {
            return bs.length - 1;
        }
    }
}
