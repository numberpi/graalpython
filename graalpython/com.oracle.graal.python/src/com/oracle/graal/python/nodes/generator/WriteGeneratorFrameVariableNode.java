/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates.
 * Copyright (c) 2013, Regents of the University of California
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
package com.oracle.graal.python.nodes.generator;

import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.nodes.expression.ExpressionNode;
import com.oracle.graal.python.nodes.frame.FrameSlotNode;
import com.oracle.graal.python.nodes.frame.WriteIdentifierNode;
import com.oracle.graal.python.nodes.generator.WriteGeneratorFrameVariableNodeGen.WriteGeneraterFrameSlotNodeGen;
import com.oracle.graal.python.nodes.statement.StatementNode;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.NodeCost;
import com.oracle.truffle.api.profiles.ValueProfile;

@NodeChild(value = "rightNode", type = ExpressionNode.class)
public abstract class WriteGeneratorFrameVariableNode extends StatementNode implements WriteIdentifierNode {
    @Child private WriteGeneraterFrameSlotNode writeNode;

    public WriteGeneratorFrameVariableNode(FrameSlot slot) {
        writeNode = WriteGeneraterFrameSlotNodeGen.create(slot);
    }

    public static WriteGeneratorFrameVariableNode create(FrameSlot slot, ExpressionNode right) {
        return WriteGeneratorFrameVariableNodeGen.create(slot, right);
    }

    protected static abstract class WriteGeneraterFrameSlotNode extends FrameSlotNode {
        public WriteGeneraterFrameSlotNode(FrameSlot slot) {
            super(slot);
        }

        @Override
        public final Object execute(VirtualFrame frame) {
            throw new IllegalStateException();
        }

        @Override
        public final int executeInt(VirtualFrame frame) {
            throw new IllegalStateException();
        }

        @Override
        public final long executeLong(VirtualFrame frame) {
            throw new IllegalStateException();
        }

        @Override
        public final double executeDouble(VirtualFrame frame) {
            throw new IllegalStateException();
        }

        @Override
        public final boolean executeBoolean(VirtualFrame frame) {
            throw new IllegalStateException();
        }

        private final ValueProfile frameProfile = ValueProfile.createClassProfile();

        public abstract Object executeWith(VirtualFrame frame, boolean value);

        public abstract Object executeWith(VirtualFrame frame, int value);

        public abstract Object executeWith(VirtualFrame frame, long value);

        public abstract Object executeWith(VirtualFrame frame, double value);

        public abstract Object executeWith(VirtualFrame frame, Object value);

        private Frame getGeneratorFrame(VirtualFrame frame) {
            return frameProfile.profile(PArguments.getGeneratorFrame(frame));
        }

        @Specialization(guards = "isBooleanKind(frame)")
        public PNone write(VirtualFrame frame, PNone right) {
            getGeneratorFrame(frame).setObject(frameSlot, PNone.NONE);
            return right;
        }

        @Specialization(guards = "isBooleanKind(frame)")
        public boolean write(VirtualFrame frame, boolean right) {
            getGeneratorFrame(frame).setBoolean(frameSlot, right);
            return right;
        }

        @Specialization(guards = "isIntegerKind(frame)")
        public int write(VirtualFrame frame, int value) {
            getGeneratorFrame(frame).setInt(frameSlot, value);
            return value;
        }

        @Specialization(guards = {"isLongOrObjectKind(frame)", "isPrimitiveInt(value)", "!value.isNative()"}, rewriteOn = ArithmeticException.class)
        public PInt writePIntAsLong(VirtualFrame frame, PInt value) {
            Frame generatorFrame = getGeneratorFrame(frame);
            generatorFrame.getFrameDescriptor().setFrameSlotKind(frameSlot, FrameSlotKind.Long);
            generatorFrame.setLong(frameSlot, value.longValueExact());
            return value;
        }

        @Specialization(guards = "isLongOrObjectKind(frame)")
        public PInt writePIntAsObject(VirtualFrame frame, PInt value) {
            Frame generatorFrame = getGeneratorFrame(frame);
            generatorFrame.getFrameDescriptor().setFrameSlotKind(frameSlot, FrameSlotKind.Object);
            generatorFrame.setObject(frameSlot, value);
            return value;
        }

        @Specialization(guards = "isDoubleKind(frame)")
        public double write(VirtualFrame frame, double right) {
            getGeneratorFrame(frame).setDouble(frameSlot, right);
            return right;
        }

        @Specialization(guards = "isObjectKind(frame)")
        public Object write(VirtualFrame frame, Object right) {
            getGeneratorFrame(frame).setObject(frameSlot, right);
            return right;
        }
    }

    public abstract ExpressionNode getRightNode();

    @Override
    public ExpressionNode getRhs() {
        return getRightNode();
    }

    @Override
    public Object getIdentifier() {
        return writeNode.getSlot().getIdentifier();
    }

    @Specialization
    @Override
    public void doWrite(VirtualFrame frame, boolean value) {
        writeNode.executeWith(frame, value);
    }

    @Specialization
    @Override
    public void doWrite(VirtualFrame frame, int value) {
        writeNode.executeWith(frame, value);
    }

    @Specialization
    @Override
    public void doWrite(VirtualFrame frame, long value) {
        writeNode.executeWith(frame, value);
    }

    @Specialization
    @Override
    public void doWrite(VirtualFrame frame, double value) {
        writeNode.executeWith(frame, value);
    }

    @Specialization
    @Override
    public void doWrite(VirtualFrame frame, Object value) {
        writeNode.executeWith(frame, value);
    }

    @Override
    public NodeCost getCost() {
        return NodeCost.NONE;
    }
}
