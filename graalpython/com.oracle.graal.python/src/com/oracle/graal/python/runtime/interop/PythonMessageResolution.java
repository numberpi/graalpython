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
package com.oracle.graal.python.runtime.interop;

import static com.oracle.graal.python.nodes.SpecialMethodNames.__CALL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__DELITEM__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__GETATTRIBUTE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__GETITEM__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__SETITEM__;

import com.oracle.graal.python.builtins.modules.BuiltinFunctions;
import com.oracle.graal.python.builtins.modules.BuiltinFunctionsFactory;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PythonAbstractObject;
import com.oracle.graal.python.builtins.objects.cext.PythonNativeObject;
import com.oracle.graal.python.builtins.objects.common.PHashingCollection;
import com.oracle.graal.python.builtins.objects.function.Arity;
import com.oracle.graal.python.builtins.objects.function.Arity.KeywordName;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.function.PFunction;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.function.PythonCallable;
import com.oracle.graal.python.builtins.objects.method.PBuiltinMethod;
import com.oracle.graal.python.builtins.objects.method.PMethod;
import com.oracle.graal.python.builtins.objects.object.PythonBuiltinObject;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.type.PythonBuiltinClass;
import com.oracle.graal.python.builtins.objects.type.PythonClass;
import com.oracle.graal.python.nodes.SpecialAttributeNames;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.attributes.DeleteAttributeNode;
import com.oracle.graal.python.nodes.attributes.LookupInheritedAttributeNode;
import com.oracle.graal.python.nodes.attributes.SetAttributeNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallBinaryNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode;
import com.oracle.graal.python.nodes.datamodel.IsCallableNode;
import com.oracle.graal.python.nodes.datamodel.IsMappingNode;
import com.oracle.graal.python.nodes.datamodel.IsSequenceNode;
import com.oracle.graal.python.nodes.expression.CastToListNode;
import com.oracle.graal.python.nodes.interop.PForeignToPTypeNode;
import com.oracle.graal.python.nodes.interop.PTypeToForeignNode;
import com.oracle.graal.python.nodes.interop.PTypeToForeignNodeGen;
import com.oracle.graal.python.nodes.interop.PTypeUnboxNode;
import com.oracle.graal.python.nodes.subscript.DeleteItemNode;
import com.oracle.graal.python.nodes.subscript.GetItemNode;
import com.oracle.graal.python.nodes.subscript.SetItemNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.interop.PythonMessageResolutionFactory.ArgumentsFromForeignNodeGen;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.runtime.sequence.PSequence;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.CanResolve;
import com.oracle.truffle.api.interop.KeyInfo;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.MessageResolution;
import com.oracle.truffle.api.interop.Resolve;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.profiles.ValueProfile;

@MessageResolution(receiverType = PythonObject.class)
public class PythonMessageResolution {
    private static final class HasSetItem extends Node {
        @Child private LookupInheritedAttributeNode getSetItemNode = LookupInheritedAttributeNode.create(__SETITEM__);
        final ConditionProfile profile = ConditionProfile.createBinaryProfile();

        public boolean execute(Object object) {
            return profile.profile(getSetItemNode.execute(object) != PNone.NO_VALUE);
        }
    }

    private static final class HasDelItem extends Node {
        @Child private LookupInheritedAttributeNode getDelItemNode = LookupInheritedAttributeNode.create(__DELITEM__);
        final ConditionProfile profile = ConditionProfile.createBinaryProfile();

        public boolean execute(Object object) {
            return profile.profile(getDelItemNode.execute(object) != PNone.NO_VALUE);
        }
    }

    private static final class IsImmutable extends Node {
        @Child private IsSequenceNode isSequence = IsSequenceNode.create();
        @Child private HasSetItem hasSetItem = new HasSetItem();
        final ConditionProfile builtinProfile = ConditionProfile.createBinaryProfile();

        public boolean execute(Object object) {
            if (builtinProfile.profile(object instanceof PythonBuiltinClass || object instanceof PythonBuiltinObject)) {
                return true;
            }
            return isSequence.execute(object) && !hasSetItem.execute(object);
        }
    }

    private abstract static class KeyForForcedAccess extends Node {
        final ConditionProfile profile = ConditionProfile.createBinaryProfile();
        final char prefix;

        KeyForForcedAccess(char prefix) {
            this.prefix = prefix;
        }

        public String execute(Object object) {
            if (object instanceof String) {
                if (profile.profile(((String) object).length() > 1 && ((String) object).charAt(0) == prefix)) {
                    return ((String) object).substring(1);
                }
            }
            return null;
        }
    }

    private static final class KeyForAttributeAccess extends KeyForForcedAccess {
        KeyForAttributeAccess() {
            super('@');
        }
    }

    private static final class KeyForItemAccess extends KeyForForcedAccess {
        KeyForItemAccess() {
            super('[');
        }
    }

    private static final class ReadNode extends Node {
        private static final Object NONEXISTING_IDENTIFIER = new Object();

        @Child private IsSequenceNode isSequence = IsSequenceNode.create();
        @Child private LookupAndCallBinaryNode readNode = LookupAndCallBinaryNode.create(__GETATTRIBUTE__);
        @Child private GetItemNode getItemNode = GetItemNode.create();
        @Child private KeyForAttributeAccess getAttributeKey = new KeyForAttributeAccess();
        @Child private KeyForItemAccess getItemKey = new KeyForItemAccess();
        final ConditionProfile strProfile = ConditionProfile.createBinaryProfile();
        @Child private PTypeToForeignNode toForeign = PTypeToForeignNodeGen.create();

        public Object execute(Object object, Object key) {
            String attrKey = getAttributeKey.execute(key);
            if (attrKey != null) {
                try {
                    return toForeign.executeConvert(readNode.executeObject(object, attrKey));
                } catch (PException e) {
                    // pass, we might be reading an item that starts with "@"
                }
            }

            String itemKey = getItemKey.execute(key);
            if (itemKey != null) {
                return toForeign.executeConvert(getItemNode.execute(object, itemKey));
            }

            if (strProfile.profile(key instanceof String)) {
                try {
                    return toForeign.executeConvert(readNode.executeObject(object, key));
                } catch (PException e) {
                    // pass
                }
            }
            if (isSequence.execute(object)) {
                try {
                    return toForeign.executeConvert(getItemNode.execute(object, key));
                } catch (PException e) {
                    // pass
                }
            }
            return NONEXISTING_IDENTIFIER;
        }
    }

    private static final class KeysNode extends Node {
        @Child private IsMappingNode isMapping = IsMappingNode.create();
        @Child private LookupAndCallUnaryNode keysNode = LookupAndCallUnaryNode.create(SpecialMethodNames.KEYS);
        @Child private CastToListNode castToList = CastToListNode.create();
        @Child private PythonObjectFactory factory = PythonObjectFactory.create();

        public Object execute(Object obj) {
            if (obj instanceof PythonNativeObject || !(obj instanceof PythonObject)) {
                return factory.createTuple(new Object[0]);
            }
            PythonObject object = (PythonObject) obj;
            return factory.createTuple(object.getAllAttributeNames().stream().distinct().toArray());
        }
    }

    abstract static class ArgumentsFromForeignNode extends Node {
        @Child private PForeignToPTypeNode fromForeign = PForeignToPTypeNode.create();

        public abstract Object[] execute(Object[] arguments);

        @Specialization(guards = {"arguments.length == cachedLen", "cachedLen < 6"}, limit = "3")
        @ExplodeLoop
        Object[] cached(Object[] arguments,
                        @Cached("arguments.length") int cachedLen) {
            Object[] convertedArgs = new Object[cachedLen];
            for (int i = 0; i < cachedLen; i++) {
                convertedArgs[i] = fromForeign.executeConvert(arguments[i]);
            }
            return convertedArgs;
        }

        @Specialization(replaces = "cached")
        Object[] cached(Object[] arguments) {
            Object[] convertedArgs = new Object[arguments.length];
            for (int i = 0; i < arguments.length; i++) {
                convertedArgs[i] = fromForeign.executeConvert(arguments[i]);
            }
            return convertedArgs;
        }
    }

    public static final class ExecuteNode extends Node {
        @Child private PTypeToForeignNode toForeign = PTypeToForeignNodeGen.create();
        @Child private CallNode callNode = CallNode.create();
        @Child private LookupInheritedAttributeNode callAttrGetterNode = LookupInheritedAttributeNode.create(__CALL__);
        @Child private ArgumentsFromForeignNode convertArgsNode = ArgumentsFromForeignNodeGen.create();

        public Object execute(PFunction receiver, Object[] arguments) {
            return doCall(receiver, arguments);
        }

        public Object execute(PBuiltinFunction receiver, Object[] arguments) {
            return doCall(receiver, arguments);
        }

        public Object execute(PMethod receiver, Object[] arguments) {
            return doCall(receiver, arguments);
        }

        public Object execute(PBuiltinMethod receiver, Object[] arguments) {
            return doCall(receiver, arguments);
        }

        public Object execute(Object receiver, Object[] arguments) {
            Object isCallable = callAttrGetterNode.execute(receiver);
            if (isCallable == PNone.NO_VALUE) {
                throw UnsupportedMessageException.raise(Message.EXECUTE);
            }
            return doCall(receiver, arguments);
        }

        private Object doCall(Object receiver, Object[] arguments) {
            Object[] convertedArgs = convertArgsNode.execute(arguments);
            return toForeign.executeConvert(callNode.execute(null, receiver, convertedArgs, new PKeyword[0]));
        }
    }

    @Resolve(message = "READ")
    abstract static class PForeignReadNode extends Node {
        @Child private ReadNode readNode = new ReadNode();

        public Object access(Object object, Object key) {
            Object result = readNode.execute(object, key);
            if (result != ReadNode.NONEXISTING_IDENTIFIER) {
                return result;
            }
            throw UnknownIdentifierException.raise(key.toString());
        }
    }

    @Resolve(message = "UNBOX")
    abstract static class UnboxNode extends Node {
        @Child private PTypeUnboxNode unboxNode = PTypeUnboxNode.create();

        Object access(Object object) {
            Object result = unboxNode.execute(object);
            if (result == object) {
                throw UnsupportedTypeException.raise(new Object[]{object});
            } else {
                return result;
            }
        }
    }

    @Resolve(message = "WRITE")
    abstract static class WriteNode extends Node {
        @Child private SetItemNode setItemNode = SetItemNode.create();
        @Child private SetAttributeNode.Dynamic writeNode = new SetAttributeNode.Dynamic();
        @Child private IsMappingNode isMapping = IsMappingNode.create();
        @Child private HasSetItem hasSetItem = new HasSetItem();
        @Child private KeyForAttributeAccess getAttributeKey = new KeyForAttributeAccess();
        @Child private KeyForItemAccess getItemKey = new KeyForItemAccess();
        final ConditionProfile strProfile = ConditionProfile.createBinaryProfile();

        public Object access(Object object, Object field, Object value) {
            String attrKey = getAttributeKey.execute(field);
            if (attrKey != null) {
                return writeNode.execute(object, attrKey, value);
            }

            String itemKey = getItemKey.execute(field);
            if (itemKey != null) {
                setItemNode.executeWith(object, itemKey, value);
                return value;
            }

            if (object instanceof PythonObject) {
                if (((PythonObject) object).getAttributeNames().contains(field)) {
                    writeNode.execute(object, field, value);
                    return value;
                }
            }
            if (strProfile.profile(field instanceof String)) {
                if (isMapping.execute(object)) {
                    setItemNode.executeWith(object, field, value);
                } else {
                    writeNode.execute(object, field, value);
                }
            } else {
                if (hasSetItem.execute(object)) {
                    setItemNode.executeWith(object, field, value);
                } else {
                    writeNode.execute(object, field, value);
                }
            }
            return value;
        }
    }

    @Resolve(message = "REMOVE")
    abstract static class PRemoveNode extends Node {
        @Child private DeleteItemNode delItemNode = DeleteItemNode.create();
        @Child private DeleteAttributeNode delNode = DeleteAttributeNode.create();
        @Child private IsMappingNode isMapping = IsMappingNode.create();
        @Child private HasDelItem hasDelItem = new HasDelItem();
        @Child private KeyForAttributeAccess getAttributeKey = new KeyForAttributeAccess();
        @Child private KeyForItemAccess getItemKey = new KeyForItemAccess();
        final ConditionProfile strProfile = ConditionProfile.createBinaryProfile();

        public boolean access(Object object, Object field) {
            String attrKey = getAttributeKey.execute(field);
            if (attrKey != null) {
                delNode.execute(object, attrKey);
                return true;
            }

            String itemKey = getItemKey.execute(field);
            if (itemKey != null) {
                delItemNode.executeWith(object, itemKey);
                return true;
            }

            if (object instanceof PythonObject) {
                if (((PythonObject) object).getAttributeNames().contains(field)) {
                    delNode.execute(object, field);
                    return true;
                }
            }
            if (strProfile.profile(field instanceof String)) {
                if (isMapping.execute(object) && hasDelItem.execute(object)) {
                    delItemNode.executeWith(object, field);
                } else {
                    delNode.execute(object, field);
                }
            } else {
                if (hasDelItem.execute(object)) {
                    delItemNode.executeWith(object, field);
                } else {
                    delNode.execute(object, field);
                }
            }
            return true;
        }
    }

    @Resolve(message = "EXECUTE")
    abstract static class PForeignFunctionExecuteNode extends Node {
        @Child private ExecuteNode execNode = new ExecuteNode();

        public Object access(Object receiver, Object[] arguments) {
            return execNode.execute(receiver, arguments);
        }
    }

    @Resolve(message = "IS_EXECUTABLE")
    abstract static class PForeignIsExecutableNode extends Node {
        @Child private IsCallableNode isCallableNode = IsCallableNode.create();

        public Object access(Object receiver) {
            return isCallableNode.execute(receiver);
        }
    }

    @Resolve(message = "IS_INSTANTIABLE")
    abstract static class IsInstantiableNode extends Node {
        public Object access(Object obj) {
            if (obj instanceof PythonClass) {
                return true;
            } else {
                return false;
            }
        }
    }

    @Resolve(message = "INVOKE")
    abstract static class PForeignInvokeNode extends Node {
        @Child private LookupAndCallBinaryNode getattr = LookupAndCallBinaryNode.create(__GETATTRIBUTE__);
        @Child private ExecuteNode execNode = new ExecuteNode();

        public Object access(Object receiver, String name, Object[] arguments) {
            Object attribute = getattr.executeObject(receiver, name);
            return execNode.execute(attribute, arguments);
        }
    }

    @Resolve(message = "NEW")
    abstract static class NewNode extends Node {
        @Child private ExecuteNode execNode = new ExecuteNode();

        public Object access(Object receiver, Object[] arguments) {
            if (receiver instanceof PythonClass) {
                return execNode.execute(receiver, arguments);
            } else {
                throw UnsupportedTypeException.raise(new Object[]{receiver});
            }
        }
    }

    @Resolve(message = "IS_NULL")
    abstract static class PForeignIsNullNode extends Node {
        public Object access(Object object) {
            return object == PNone.NONE || object == PNone.NO_VALUE;
        }
    }

    @Resolve(message = "HAS_SIZE")
    abstract static class PForeignHasSizeNode extends Node {
        @Child private IsSequenceNode isSequenceNode;
        @Child private IsMappingNode isMappingNode;
        @Child private BuiltinFunctions.LenNode lenNode;
        @Child private PTypeUnboxNode unboxNode;
        @Child private LookupAndCallBinaryNode callGetItemNode;

        private final ValueProfile profile = ValueProfile.createClassProfile();

        public Object access(Object object) {
            Object profiled = profile.profile(object);
            // A sequence object always has a size even if there is no '__len__' attribute. This is,
            // e.g., the case for 'array'.
            if (profiled instanceof PSequence) {
                return true;
            }
            if (profiled instanceof PHashingCollection) {
                return false;
            }
            if (getIsSequenceNode().execute(profiled) && !getIsMappingNode().execute(profiled)) {
                // also try to access using an integer index
                int len = (int) getUnboxNode().execute(getLenNode().executeWith(profiled));
                if (len > 0) {
                    try {
                        getCallGetItemNode().executeObject(profiled, 0);
                        return true;
                    } catch (PException e) {
                        return false;
                    }
                }
                return true;
            }
            return false;
        }

        private BuiltinFunctions.LenNode getLenNode() {
            if (lenNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                lenNode = insert(BuiltinFunctionsFactory.LenNodeFactory.create());
            }
            return lenNode;
        }

        private PTypeUnboxNode getUnboxNode() {
            if (unboxNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                unboxNode = insert(PTypeUnboxNode.create());
            }
            return unboxNode;
        }

        private LookupAndCallBinaryNode getCallGetItemNode() {
            if (callGetItemNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                callGetItemNode = insert(LookupAndCallBinaryNode.create(__GETITEM__));
            }
            return callGetItemNode;
        }

        private IsSequenceNode getIsSequenceNode() {
            if (isSequenceNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                isSequenceNode = insert(IsSequenceNode.create());
            }
            return isSequenceNode;
        }

        private IsMappingNode getIsMappingNode() {
            if (isMappingNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                isMappingNode = insert(IsMappingNode.create());
            }
            return isMappingNode;
        }
    }

    @Resolve(message = "GET_SIZE")
    abstract static class PForeignGetSizeNode extends Node {
        @Child IsSequenceNode isSeq = IsSequenceNode.create();
        @Child private BuiltinFunctions.LenNode lenNode = BuiltinFunctionsFactory.LenNodeFactory.create();
        @Child private PTypeUnboxNode unboxNode = PTypeUnboxNode.create();

        public Object access(Object object) {
            if (isSeq.execute(object)) {
                return unboxNode.execute(lenNode.executeWith(object));
            }
            throw UnsupportedMessageException.raise(Message.GET_SIZE);
        }
    }

    @Resolve(message = "IS_BOXED")
    abstract static class IsBoxedNode extends Node {
        public Object access(Object object) {
            return PTypeToForeignNode.isBoxed(object);
        }
    }

    @Resolve(message = "KEY_INFO")
    abstract static class PKeyInfoNode extends Node {
        @Child private LookupAndCallBinaryNode getCallNode = LookupAndCallBinaryNode.create(__GETATTRIBUTE__);

        @Child ReadNode readNode = new ReadNode();
        @Child IsImmutable isImmutable = new IsImmutable();

        public int access(Object object, Object fieldName) {
            Object attr = readNode.execute(object, fieldName);
            int info = KeyInfo.NONE;
            if (attr != ReadNode.NONEXISTING_IDENTIFIER) {
                info |= KeyInfo.READABLE;
                try {
                    getCallNode.executeObject(attr, SpecialMethodNames.__CALL__);
                    info |= KeyInfo.INVOCABLE;
                } catch (PException e) {
                }
            }
            if (!isImmutable.execute(object)) {
                if (KeyInfo.isReadable(info)) {
                    info |= KeyInfo.REMOVABLE;
                    info |= KeyInfo.MODIFIABLE;
                } else {
                    info |= KeyInfo.INSERTABLE;
                }
            }
            return info;
        }
    }

    @Resolve(message = "HAS_KEYS")
    abstract static class HasKeysNode extends Node {
        public Object access(Object obj) {
            return obj instanceof PythonAbstractObject;
        }
    }

    @Resolve(message = "KEYS")
    abstract static class PForeignKeysNode extends Node {
        @Child KeysNode keysNode = new KeysNode();
        @Child private PythonObjectFactory factory = PythonObjectFactory.create();

        @SuppressWarnings("unused")
        public Object access(PNone object) {
            return factory.createTuple(new Object[0]);
        }

        public Object access(Object object) {
            return keysNode.execute(object);
        }
    }

    @Resolve(message = "org.graalvm.tools.lsp.interop.GetSignature")
    @SuppressWarnings("unknown-message")
    public abstract static class PForeignGetSignatureNode extends Node {

        public Object access(@SuppressWarnings("unused") VirtualFrame frame, Object receiver) {
            if (receiver instanceof PythonCallable) {
                PythonCallable callable = (PythonCallable) receiver;
                Arity arity = callable.getArity();
                SignatureDescriptor signature = new SignatureDescriptor(callable.getName());

                for (String paramId : arity.getParameterIds()) {
                    if ((receiver instanceof PMethod || receiver instanceof PBuiltinMethod || receiver instanceof PythonBuiltinClass) && paramId.equals("self")) {
                        continue;
                    }
                    signature.addParameter(paramId);
                }

                if (arity.takesVarArgs()) {
                    signature.addVarArgs();
                }
                for (KeywordName keywordName : arity.getKeywordNames()) {
                    signature.addKeyword(keywordName.name);
                }
                if (arity.takesKeywordArgs()) {
                    signature.addKeywordArgs();
                }
                return signature;
            }
            throw UnsupportedTypeException.raise(new Object[]{receiver});
        }
    }

    @Resolve(message = "org.graalvm.tools.lsp.interop.GetDocumentation")
    @SuppressWarnings("unknown-message")
    public abstract static class PForeignGetDocumentationNode extends Node {

        public Object access(@SuppressWarnings("unused") VirtualFrame frame, PythonObject receiver) {
            return receiver.getAttribute(SpecialAttributeNames.__DOC__);
        }
    }

    @CanResolve
    abstract static class CheckFunction extends Node {
        protected static boolean test(TruffleObject receiver) {
            return receiver instanceof PythonAbstractObject;
        }
    }
}
