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
package com.oracle.graal.python.nodes;

import com.oracle.graal.python.builtins.objects.cell.PCell;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.nodes.ExplodeLoop;

public abstract class PClosureRootNode extends PRootNode {
    @CompilerDirectives.CompilationFinal(dimensions = 1) protected final FrameSlot[] freeVarSlots;
    private final int length;

    protected PClosureRootNode(TruffleLanguage<?> language, FrameDescriptor frameDescriptor, FrameSlot[] freeVarSlots) {
        super(language, frameDescriptor);
        this.freeVarSlots = freeVarSlots;
        this.length = freeVarSlots != null ? freeVarSlots.length : 0;
    }

    protected void addClosureCellsToLocals(Frame frame) {
        PCell[] closure = PArguments.getClosure(frame);
        if (closure != null) {
            assert freeVarSlots != null : "closure root node: the free var slots cannot be null when the closure is not null";
            assert closure.length == freeVarSlots.length : "closure root node: the closure must have the same length as the free var slots array";
            if (freeVarSlots.length < 32) {
                addClosureCellsToLocalsExploded(frame, closure);
            } else {
                addClosureCellsToLocalsLoop(frame, closure);
            }
        }
    }

    protected void addClosureCellsToLocalsLoop(Frame frame, PCell[] closure) {
        for (int i = 0; i < length; i++) {
            frame.setObject(freeVarSlots[i], closure[i]);
        }
    }

    @ExplodeLoop
    protected void addClosureCellsToLocalsExploded(Frame frame, PCell[] closure) {
        for (int i = 0; i < length; i++) {
            frame.setObject(freeVarSlots[i], closure[i]);
        }
    }

    public boolean hasFreeVars() {
        return freeVarSlots != null && freeVarSlots.length > 0;
    }

    public String[] getFreeVars() {
        String[] freeVars = new String[freeVarSlots.length];
        for (int i = 0; i < freeVarSlots.length; i++) {
            freeVars[i] = (String) freeVarSlots[i].getIdentifier();
        }
        return freeVars;
    }
}
