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
package com.oracle.graal.python.nodes.attributes;

import static com.oracle.graal.python.nodes.SpecialMethodNames.__GETATTRIBUTE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__GETATTR__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.AttributeError;

import com.oracle.graal.python.nodes.call.special.LookupAndCallBinaryNode;
import com.oracle.graal.python.nodes.expression.ExpressionNode;
import com.oracle.graal.python.nodes.frame.ReadNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.nodes.statement.StatementNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.UnexpectedResultException;

@NodeChild(value = "object", type = ExpressionNode.class)
public abstract class GetAttributeNode extends ExpressionNode implements ReadNode {

    private final String key;

    @Child LookupAndCallBinaryNode dispatchNode = LookupAndCallBinaryNode.create(__GETATTRIBUTE__);
    @Child LookupAndCallBinaryNode dispatchGetAttr;
    @CompilationFinal private IsBuiltinClassProfile isBuiltinClassProfile = IsBuiltinClassProfile.create();

    protected GetAttributeNode(String key) {
        this.key = key;
    }

    public final String getKey() {
        return key;
    }

    public final StatementNode makeWriteNode(ExpressionNode rhs) {
        return SetAttributeNode.create(key, getObject(), rhs);
    }

    public static GetAttributeNode create(String key, ExpressionNode object) {
        return GetAttributeNodeGen.create(key, object);
    }

    public abstract ExpressionNode getObject();

    @Specialization(rewriteOn = UnexpectedResultException.class)
    protected int doItInt(Object object) throws UnexpectedResultException {
        try {
            return dispatchNode.executeInt(object, key);
        } catch (PException pe) {
            pe.expect(AttributeError, isBuiltinClassProfile);
            return getDispatchGetAttr().executeInt(object, key);
        }
    }

    @Specialization(rewriteOn = UnexpectedResultException.class)
    protected boolean doItBoolean(Object object) throws UnexpectedResultException {
        try {
            return dispatchNode.executeBool(object, key);
        } catch (PException pe) {
            pe.expect(AttributeError, isBuiltinClassProfile);
            return getDispatchGetAttr().executeBool(object, key);
        }
    }

    @Specialization(replaces = {"doItInt", "doItBoolean"})
    protected Object doIt(Object object) {
        try {
            return dispatchNode.executeObject(object, key);
        } catch (PException pe) {
            pe.expect(AttributeError, isBuiltinClassProfile);
            return getDispatchGetAttr().executeObject(object, key);
        }
    }

    private LookupAndCallBinaryNode getDispatchGetAttr() {
        if (dispatchGetAttr == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            dispatchGetAttr = insert(LookupAndCallBinaryNode.create(__GETATTR__));
        }
        return dispatchGetAttr;
    }
}
