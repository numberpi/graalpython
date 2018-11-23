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
package com.oracle.graal.python.nodes.util;

import static com.oracle.graal.python.runtime.exception.PythonErrorType.IndexError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.OverflowError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__INDEX__;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Specialization;

/**
 * Converts an arbitrary object to an index-sized integer (which is a Java {@code int}).
 */
public abstract class CastToIndexNode extends PNodeWithContext {

    private static final String ERROR_MESSAGE = "cannot fit 'int' into an index-sized integer";

    @Child private LookupAndCallUnaryNode callIndexNode;
    @Child private CastToIndexNode recursiveNode;

    private final PythonBuiltinClassType errorType;
    private final boolean recursive;

    protected CastToIndexNode(PythonBuiltinClassType errorType, boolean recursive) {
        this.errorType = errorType;
        this.recursive = recursive;
    }

    public abstract int execute(Object x);

    public abstract int execute(int x);

    public abstract int execute(long x);

    public abstract int execute(boolean x);

    @Specialization
    int doBoolean(boolean x) {
        return PInt.intValue(x);
    }

    @Specialization
    int doInt(int x) {
        return x;
    }

    @Specialization(rewriteOn = ArithmeticException.class)
    int doLong(long x) {
        return PInt.intValueExact(x);
    }

    @Specialization(replaces = "doLong")
    int doLongOvf(long x) {
        try {
            return PInt.intValueExact(x);
        } catch (ArithmeticException e) {
            throw raise(errorType, ERROR_MESSAGE);
        }
    }

    @Specialization(rewriteOn = ArithmeticException.class)
    int doPInt(PInt x) {
        return x.intValueExact();
    }

    @Specialization(replaces = "doLong")
    int doPIntOvf(PInt x) {
        try {
            return x.intValueExact();
        } catch (ArithmeticException e) {
            throw raise(errorType, ERROR_MESSAGE);
        }
    }

    @Fallback
    int doGeneric(Object x) {
        if (recursive) {
            if (callIndexNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                callIndexNode = insert(LookupAndCallUnaryNode.create(__INDEX__));
            }
            if (recursiveNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                recursiveNode = insert(CastToIndexNodeGen.create(errorType, false));
            }
            return recursiveNode.execute(callIndexNode.executeObject(x));
        }
        throw raise(TypeError, "__index__ returned non-int (type %p)", x);
    }

    public static CastToIndexNode create() {
        return CastToIndexNodeGen.create(IndexError, true);
    }

    public static CastToIndexNode createOverflow() {
        return CastToIndexNodeGen.create(OverflowError, true);
    }
}
