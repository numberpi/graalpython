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
package com.oracle.graal.python.nodes.builtins;

import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;

import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.control.GetIteratorNode;
import com.oracle.graal.python.nodes.control.GetNextNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.nodes.truffle.PythonArithmeticTypes;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;

@ImportStatic(PGuards.class)
@TypeSystemReference(PythonArithmeticTypes.class)
public abstract class JoinInternalNode extends PNodeWithContext {

    public abstract String execute(Object self, Object iterable);

    @Specialization
    @TruffleBoundary
    protected String join(String string, String arg) {
        if (arg.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        char[] joinString = arg.toCharArray();

        for (int i = 0; i < joinString.length - 1; i++) {
            sb.append(Character.toString(joinString[i]));
            sb.append(string);
        }

        sb.append(Character.toString(joinString[joinString.length - 1]));
        return sb.toString();
    }

    private String checkItem(Object item, int pos) {
        if (PGuards.isString(item)) {
            return item.toString();
        }
        throw raise(TypeError, "sequence item %d: expected str instance, %p found", pos, item);
    }

    @Specialization
    @TruffleBoundary
    protected String join(String string, PythonObject iterable,
                    @Cached("create()") GetIteratorNode getIterator,
                    @Cached("create()") GetNextNode next,
                    @Cached("create()") IsBuiltinClassProfile errorProfile1,
                    @Cached("create()") IsBuiltinClassProfile errorProfile2) {

        Object iterator = getIterator.executeWith(iterable);
        StringBuilder str = new StringBuilder();
        try {
            str.append(checkItem(next.execute(iterator), 0));
        } catch (PException e) {
            e.expectStopIteration(errorProfile1);
            return "";
        }
        int i = 1;
        while (true) {
            Object value;
            try {
                value = next.execute(iterator);
            } catch (PException e) {
                e.expectStopIteration(errorProfile2);
                return str.toString();
            }
            str.append(string);
            str.append(checkItem(value, i++));
        }
    }

    @Fallback
    @SuppressWarnings("unused")
    protected String join(Object self, Object arg) {
        throw raise(TypeError, "can only join an iterable");
    }

    public static JoinInternalNode create() {
        return JoinInternalNodeGen.create();
    }

}
