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
package com.oracle.graal.python.nodes.function;

import java.util.HashMap;
import java.util.Map;

import com.oracle.graal.python.builtins.objects.function.Arity;
import com.oracle.graal.python.nodes.expression.ExpressionNode;
import com.oracle.graal.python.nodes.statement.StatementNode;
import com.oracle.graal.python.parser.DefinitionCellSlots;
import com.oracle.graal.python.parser.ExecutionCellSlots;
import com.oracle.graal.python.runtime.interop.InteropMap;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.instrumentation.StandardTags.DeclarationTag;

public class ClassDefinitionNode extends FunctionDefinitionNode {

    public ClassDefinitionNode(String functionName, String enclosingClassName, ExpressionNode doc, Arity arity, StatementNode defaults, RootCallTarget callTarget,
                    DefinitionCellSlots definitionCellSlots, ExecutionCellSlots executionCellSlots) {
        super(functionName, enclosingClassName, doc, arity, defaults, callTarget, definitionCellSlots, executionCellSlots);
    }

    @Override
    public Object getNodeObject() {
        Map<String, Object> descriptor = new HashMap<>();
        descriptor.put(DeclarationTag.NAME, functionName);
        if (enclosingClassName != null) {
            descriptor.put(DeclarationTag.CONTAINER, enclosingClassName);
            descriptor.put(DeclarationTag.KIND, "method");
        } else {
            descriptor.put(DeclarationTag.KIND, "function");
        }
        return new InteropMap(descriptor);
    }
}
