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
package com.oracle.graal.python.parser;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.oracle.graal.python.nodes.argument.ReadDefaultArgumentNode;
import com.oracle.graal.python.nodes.expression.ExpressionNode;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlot;

public final class ScopeInfo {

    public enum ScopeKind {
        Module,
        Function,
        Class,
        // generator expression or generator function
        Generator,
        // list comprehension
        ListComp
    }

    private final String scopeId;
    private final FrameDescriptor frameDescriptor;
    private final ArrayList<String> identifierToIndex;
    private ScopeKind scopeKind;
    private final ScopeInfo parent;

    private ScopeInfo firstChildScope; // start of a linked list
    private ScopeInfo nextChildScope; // next pointer for the linked list

    /**
     * Symbols declared using 'global' or 'nonlocal' statements.
     */
    private Set<String> explicitGlobalVariables;
    private Set<String> explicitNonlocalVariables;

    /**
     * Symbols which are local variables but are closed over in nested scopes
     */
    // variables that are referenced in enclosed contexts
    private LinkedHashSet<String> cellVars;
    // variables that are referenced from enclosing contexts
    private LinkedHashSet<String> freeVars;

    /**
     * An optional field that stores translated nodes of default argument values.
     * {@link #defaultArgumentNodes} is not null only when {@link #scopeKind} is Function, and the
     * function has default arguments.
     */
    private List<ExpressionNode> defaultArgumentNodes;
    private ReadDefaultArgumentNode[] defaultArgumentReads;

    private int loopCount = 0;

    public ScopeInfo(String scopeId, ScopeKind kind, FrameDescriptor frameDescriptor, ScopeInfo parent) {
        this.scopeId = scopeId;
        this.scopeKind = kind;
        this.frameDescriptor = frameDescriptor == null ? new FrameDescriptor() : frameDescriptor;
        this.parent = parent;
        this.identifierToIndex = new ArrayList<>();
        // register current scope as child to parent scope
        if (this.parent != null) {
            this.nextChildScope = this.parent.firstChildScope;
            this.parent.firstChildScope = this;
        }
    }

    public void incLoopCount() {
        loopCount++;
    }

    public int getLoopCount() {
        return loopCount;
    }

    public void resetLoopCount() {
        this.loopCount = 0;
    }

    public ScopeInfo getFirstChildScope() {
        return firstChildScope;
    }

    public ScopeInfo getNextChildScope() {
        return nextChildScope;
    }

    public String getScopeId() {
        return scopeId;
    }

    public ScopeKind getScopeKind() {
        return scopeKind;
    }

    public void setAsGenerator() {
        assert scopeKind == ScopeKind.Function || scopeKind == ScopeKind.Generator;
        scopeKind = ScopeKind.Generator;
    }

    public FrameDescriptor getFrameDescriptor() {
        return frameDescriptor;
    }

    public ScopeInfo getParent() {
        return parent;
    }

    public FrameSlot findFrameSlot(String identifier) {
        assert identifier != null : "identifier is null!";
        return this.getFrameDescriptor().findFrameSlot(identifier);
    }

    FrameSlot createSlotIfNotPresent(String identifier) {
        assert identifier != null : "identifier is null!";
        FrameSlot frameSlot = this.getFrameDescriptor().findFrameSlot(identifier);
        if (frameSlot == null) {
            identifierToIndex.add(identifier);
            return getFrameDescriptor().addFrameSlot(identifier);
        } else {
            return frameSlot;
        }
    }

    public void addExplicitGlobalVariable(String identifier) {
        if (explicitGlobalVariables == null) {
            explicitGlobalVariables = new HashSet<>();
        }
        explicitGlobalVariables.add(identifier);
    }

    public void addExplicitNonlocalVariable(String identifier) {
        if (explicitNonlocalVariables == null) {
            explicitNonlocalVariables = new HashSet<>();
        }
        explicitNonlocalVariables.add(identifier);
    }

    public boolean isExplicitGlobalVariable(String identifier) {
        return explicitGlobalVariables != null && explicitGlobalVariables.contains(identifier);
    }

    public boolean isExplicitNonlocalVariable(String identifier) {
        return explicitNonlocalVariables != null && explicitNonlocalVariables.contains(identifier);
    }

    public void addCellVar(String identifier) {
        addCellVar(identifier, false);
    }

    public void addCellVar(String identifier, boolean createFrameSlot) {
        if (cellVars == null) {
            cellVars = new LinkedHashSet<>();
        }
        cellVars.add(identifier);
        if (createFrameSlot) {
            this.createSlotIfNotPresent(identifier);
        }
    }

    public void addFreeVar(String identifier) {
        addFreeVar(identifier, false);
    }

    protected void addFreeVar(String identifier, boolean createFrameSlot) {
        if (freeVars == null) {
            freeVars = new LinkedHashSet<>();
        }
        freeVars.add(identifier);
        if (createFrameSlot) {
            this.createSlotIfNotPresent(identifier);
        }
    }

    public boolean isCellVar(String identifier) {
        return cellVars != null && cellVars.contains(identifier);
    }

    public boolean isFreeVar(String identifier) {
        return freeVars != null && freeVars.contains(identifier);
    }

    private static final FrameSlot[] EMPTY = new FrameSlot[0];

    private static FrameSlot[] getFrameSlots(Collection<String> identifiers, ScopeInfo scope) {
        if (identifiers == null) {
            return EMPTY;
        }
        assert scope != null : "getting frame slots: scope cannot be null!";
        FrameSlot[] slots = new FrameSlot[identifiers.size()];
        int i = 0;
        for (String identifier : identifiers) {
            slots[i++] = scope.findFrameSlot(identifier);
        }
        return slots;
    }

    public FrameSlot[] getCellVarSlots() {
        return getFrameSlots(cellVars, this);
    }

    public FrameSlot[] getFreeVarSlots() {
        return getFrameSlots(freeVars, this);
    }

    public FrameSlot[] getFreeVarSlotsInParentScope() {
        assert parent != null : "cannot get current freeVars in parent scope, parent scope cannot be null!";
        return getFrameSlots(freeVars, parent);
    }

    public void setDefaultArgumentNodes(List<ExpressionNode> defaultArgumentNodes) {
        this.defaultArgumentNodes = defaultArgumentNodes;
    }

    public List<ExpressionNode> getDefaultArgumentNodes() {
        return defaultArgumentNodes;
    }

    public void setDefaultArgumentReads(ReadDefaultArgumentNode[] defaultArgumentReads) {
        this.defaultArgumentReads = defaultArgumentReads;
    }

    public ReadDefaultArgumentNode[] getDefaultArgumentReads() {
        return this.defaultArgumentReads;
    }

    public void createFrameSlotsForCellAndFreeVars() {
        if (cellVars != null) {
            for (String identifier : cellVars) {
                createSlotIfNotPresent(identifier);
            }
        }
        if (freeVars != null) {
            for (String identifier : freeVars) {
                createSlotIfNotPresent(identifier);
            }
        }
    }

    @Override
    public String toString() {
        CompilerAsserts.neverPartOfCompilation();
        return scopeKind.toString() + " " + scopeId;
    }

    public Integer getVariableIndex(String name) {
        for (int i = 0; i < identifierToIndex.size(); i++) {
            if (identifierToIndex.get(i).equals(name)) {
                return i;
            }
        }
        throw new IllegalStateException("Cannot find argument for name " + name + " in scope " + getScopeId());
    }
}
