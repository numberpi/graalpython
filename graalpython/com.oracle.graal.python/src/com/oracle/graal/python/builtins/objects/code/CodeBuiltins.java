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

package com.oracle.graal.python.builtins.objects.code;

import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PCode)
public class CodeBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return CodeBuiltinsFactory.getFactories();
    }

    @Builtin(name = "co_freevars", fixedNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class GetFreeVarsNode extends PythonBuiltinNode {
        @Specialization
        protected Object get(PCode self) {
            Object[] freeVars = self.getFreeVars();
            if (freeVars != null) {
                return factory().createTuple(freeVars);
            }
            return PNone.NONE;
        }
    }

    @Builtin(name = "co_cellvars", fixedNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class GetCellVarsNode extends PythonBuiltinNode {
        @Specialization
        protected Object get(PCode self) {
            Object[] cellVars = self.getCellVars();
            if (cellVars != null) {
                return factory().createTuple(cellVars);
            }
            return PNone.NONE;
        }
    }

    @Builtin(name = "co_filename", fixedNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class GetFilenameNode extends PythonBuiltinNode {
        @Specialization
        protected Object get(PCode self) {
            String filename = self.getFilename();
            if (filename != null) {
                return filename;
            }
            return PNone.NONE;
        }
    }

    @Builtin(name = "co_firstlineno", fixedNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class GetLinenoNode extends PythonBuiltinNode {
        @Specialization
        protected Object get(PCode self) {
            return self.getFirstLineNo();
        }
    }

    @Builtin(name = "co_name", fixedNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class GetNameNode extends PythonBuiltinNode {
        @Specialization
        @TruffleBoundary
        protected Object get(PCode self) {
            String name = self.getName();
            if (name != null) {
                return name;
            }
            return PNone.NONE;
        }
    }

    @Builtin(name = "co_argcount", fixedNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class GetArgCountNode extends PythonBuiltinNode {
        @Specialization
        protected Object get(PCode self) {
            return self.getArgcount();
        }
    }

    @Builtin(name = "co_kwonlyargcount", fixedNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class GetKnownlyArgCountNode extends PythonBuiltinNode {
        @Specialization
        protected Object get(PCode self) {
            return self.getKwonlyargcount();
        }
    }

    @Builtin(name = "co_nlocals", fixedNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class GetNLocalsNode extends PythonBuiltinNode {
        @Specialization
        protected Object get(PCode self) {
            return self.getNlocals();
        }
    }

    @Builtin(name = "co_stacksize", fixedNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class GetStackSizeNode extends PythonBuiltinNode {
        @Specialization
        protected Object get(PCode self) {
            return self.getStacksize();
        }
    }

    @Builtin(name = "co_flags", fixedNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class GetFlagsNode extends PythonBuiltinNode {
        @Specialization
        protected Object get(PCode self) {
            return self.getFlags();
        }
    }

    @Builtin(name = "co_code", fixedNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class GetCodeNode extends PythonBuiltinNode {
        @Specialization
        protected Object get(PCode self) {
            byte[] codestring = self.getCodestring();
            if (codestring == null) {
                // TODO: this is for the moment undefined
                codestring = new byte[0];
            }
            return factory().createBytes(codestring);
        }
    }

    @Builtin(name = "co_consts", fixedNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class GetConstsNode extends PythonBuiltinNode {
        @Specialization
        protected Object get(PCode self) {
            Object[] constants = self.getConstants();
            if (constants == null) {
                // TODO: this is for the moment undefined (see co_code)
                constants = new Object[0];
            }
            return factory().createTuple(constants);
        }
    }

    @Builtin(name = "co_names", fixedNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class GetNamesNode extends PythonBuiltinNode {
        @Specialization
        protected Object get(PCode self) {
            Object[] names = self.getNames();
            if (names == null) {
                // TODO: this is for the moment undefined (see co_code)
                names = new Object[0];
            }
            return factory().createTuple(names);
        }
    }

    @Builtin(name = "co_varnames", fixedNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class GetVarNamesNode extends PythonBuiltinNode {
        @Specialization
        protected Object get(PCode self) {
            Object[] varNames = self.getVarnames();
            if (varNames != null) {
                return factory().createTuple(varNames);
            }
            return PNone.NONE;
        }
    }

    @Builtin(name = "co_lnotab", fixedNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class GetLNoTabNode extends PythonBuiltinNode {
        @Specialization
        protected Object get(PCode self) {
            byte[] lnotab = self.getLnotab();
            if (lnotab == null) {
                // TODO: this is for the moment undefined (see co_code)
                lnotab = new byte[0];
            }
            return factory().createBytes(lnotab);
        }
    }
}
