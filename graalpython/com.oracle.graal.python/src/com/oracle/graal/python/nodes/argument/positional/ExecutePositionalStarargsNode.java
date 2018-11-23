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
package com.oracle.graal.python.nodes.argument.positional;

import java.util.ArrayList;
import java.util.Iterator;

import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.set.PSet;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.control.GetIteratorNode;
import com.oracle.graal.python.nodes.control.GetNextNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;

public abstract class ExecutePositionalStarargsNode extends PNodeWithContext {
    public abstract Object[] executeWith(Object starargs);

    @Specialization
    Object[] starargs(Object[] starargs) {
        return starargs;
    }

    @Specialization
    Object[] starargs(PTuple starargs) {
        return starargs.getArray();
    }

    @Specialization
    Object[] starargs(PList starargs) {
        int length = starargs.getSequenceStorage().length();
        Object[] internalArray = starargs.getSequenceStorage().getInternalArray();
        if (internalArray.length != length) {
            return starargs.getSequenceStorage().getCopyOfInternalArray();
        }
        return internalArray;
    }

    @Specialization
    Object[] starargs(PDict starargs) {
        int length = starargs.size();
        Object[] args = new Object[length];
        Iterator<Object> iterator = starargs.getDictStorage().keys().iterator();
        for (int i = 0; i < args.length; i++) {
            assert iterator.hasNext();
            args[i] = iterator.next();
        }
        return args;
    }

    @Specialization
    Object[] starargs(PSet starargs) {
        int length = starargs.size();
        Object[] args = new Object[length];
        Iterator<Object> iterator = starargs.getDictStorage().keys().iterator();
        for (int i = 0; i < args.length; i++) {
            assert iterator.hasNext();
            args[i] = iterator.next();
        }
        return args;
    }

    @SuppressWarnings("unused")
    @Specialization
    Object[] starargs(PNone none) {
        return new Object[0];
    }

    @Specialization
    @TruffleBoundary(allowInlining = true)
    Object[] starargs(Object object,
                    @Cached("create()") GetIteratorNode getIterator,
                    @Cached("create()") GetNextNode next,
                    @Cached("create()") IsBuiltinClassProfile errorProfile) {
        Object iterator = getIterator.executeWith(object);
        if (iterator != PNone.NO_VALUE && iterator != PNone.NONE) {
            ArrayList<Object> internalStorage = new ArrayList<>();
            while (true) {
                try {
                    internalStorage.add(next.execute(iterator));
                } catch (PException e) {
                    e.expectStopIteration(errorProfile);
                    return internalStorage.toArray();
                }
            }
        }
        throw raise(PythonErrorType.TypeError, "argument after * must be an iterable, not %p", object);
    }

    public static ExecutePositionalStarargsNode create() {
        return ExecutePositionalStarargsNodeGen.create();
    }
}
