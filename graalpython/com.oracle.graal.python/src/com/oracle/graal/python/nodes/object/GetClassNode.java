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
package com.oracle.graal.python.nodes.object;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PEllipsis;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes.GetNativeClassNode;
import com.oracle.graal.python.builtins.objects.cext.PythonNativeObject;
import com.oracle.graal.python.builtins.objects.cext.PythonNativeVoidPtr;
import com.oracle.graal.python.builtins.objects.getsetdescriptor.GetSetDescriptor;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.type.PythonClass;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.truffle.PythonTypes;
import com.oracle.graal.python.runtime.PythonCore;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.profiles.ValueProfile;

@TypeSystemReference(PythonTypes.class)
@ImportStatic({PGuards.class})
public abstract class GetClassNode extends PNodeWithContext {
    private final ValueProfile classProfile = ValueProfile.createClassProfile();

    /*
     * =============== Changes in this class must be mirrored in GetLazyClassNode ===============
     */

    public static GetClassNode create() {
        return GetClassNodeGen.create();
    }

    public abstract PythonClass execute(boolean object);

    public abstract PythonClass execute(int object);

    public abstract PythonClass execute(long object);

    public abstract PythonClass execute(double object);

    public final PythonClass execute(Object object) {
        return executeGetClass(classProfile.profile(object));
    }

    protected abstract PythonClass executeGetClass(Object object);

    @Specialization(assumptions = "singleContextAssumption()")
    protected PythonClass getIt(@SuppressWarnings("unused") GetSetDescriptor object,
                    @Cached("getIt(object)") PythonClass klass) {
        return klass;
    }

    @Specialization
    protected PythonClass getIt(@SuppressWarnings("unused") GetSetDescriptor object) {
        return getCore().lookupType(PythonBuiltinClassType.GetSetDescriptor);
    }

    @Specialization(assumptions = "singleContextAssumption()")
    protected PythonClass getIt(@SuppressWarnings("unused") PNone object,
                    @Cached("getIt(object)") PythonClass klass) {
        return klass;
    }

    @Specialization
    protected PythonClass getIt(@SuppressWarnings("unused") PNone object) {
        return getCore().lookupType(PythonBuiltinClassType.PNone);
    }

    @Specialization(assumptions = "singleContextAssumption()")
    protected PythonClass getIt(@SuppressWarnings("unused") PNotImplemented object,
                    @Cached("getIt(object)") PythonClass klass) {
        return klass;
    }

    @SuppressWarnings("unused")
    @Specialization
    protected PythonClass getIt(PNotImplemented object) {
        return getCore().lookupType(PythonBuiltinClassType.PNotImplemented);
    }

    @Specialization(assumptions = "singleContextAssumption()")
    protected PythonClass getIt(@SuppressWarnings("unused") PEllipsis object,
                    @Cached("getIt(object)") PythonClass klass) {
        return klass;
    }

    @SuppressWarnings("unused")
    @Specialization
    protected PythonClass getIt(PEllipsis object) {
        return getCore().lookupType(PythonBuiltinClassType.PEllipsis);
    }

    @Specialization(assumptions = "singleContextAssumption()")
    protected PythonClass getIt(@SuppressWarnings("unused") boolean object,
                    @Cached("getIt(object)") PythonClass klass) {
        return klass;
    }

    @SuppressWarnings("unused")
    @Specialization
    protected PythonClass getIt(boolean object) {
        return getCore().lookupType(PythonBuiltinClassType.Boolean);
    }

    @Specialization(assumptions = "singleContextAssumption()")
    protected PythonClass getIt(@SuppressWarnings("unused") int object,
                    @Cached("getIt(object)") PythonClass klass) {
        return klass;
    }

    @SuppressWarnings("unused")
    @Specialization
    protected PythonClass getIt(int object) {
        return getCore().lookupType(PythonBuiltinClassType.PInt);
    }

    @Specialization(assumptions = "singleContextAssumption()")
    protected PythonClass getIt(@SuppressWarnings("unused") long object,
                    @Cached("getIt(object)") PythonClass klass) {
        return klass;
    }

    @SuppressWarnings("unused")
    @Specialization
    protected PythonClass getIt(long object) {
        return getCore().lookupType(PythonBuiltinClassType.PInt);
    }

    @Specialization(assumptions = "singleContextAssumption()")
    protected PythonClass getIt(@SuppressWarnings("unused") double object,
                    @Cached("getIt(object)") PythonClass klass) {
        return klass;
    }

    @SuppressWarnings("unused")
    @Specialization
    protected PythonClass getIt(double object) {
        return getCore().lookupType(PythonBuiltinClassType.PFloat);
    }

    @Specialization(assumptions = "singleContextAssumption()")
    protected PythonClass getIt(@SuppressWarnings("unused") String object,
                    @Cached("getIt(object)") PythonClass klass) {
        return klass;
    }

    @SuppressWarnings("unused")
    @Specialization
    protected PythonClass getIt(String object) {
        return getCore().lookupType(PythonBuiltinClassType.PString);
    }

    @Specialization
    protected PythonClass getIt(PythonNativeObject object,
                    @Cached("create()") GetNativeClassNode getNativeClassNode) {
        return getNativeClassNode.execute(object);
    }

    @Specialization(assumptions = "singleContextAssumption()")
    protected PythonClass getIt(@SuppressWarnings("unused") PythonNativeVoidPtr object,
                    @Cached("getIt(object)") PythonClass klass) {
        return klass;
    }

    @Specialization
    protected PythonClass getIt(@SuppressWarnings("unused") PythonNativeVoidPtr object) {
        return getCore().lookupType(PythonBuiltinClassType.PInt);
    }

    @Specialization
    protected PythonClass getPythonClassGeneric(PythonObject object,
                    @Cached("create()") GetLazyClassNode getLazyClass,
                    @Cached("createIdentityProfile()") ValueProfile profile,
                    @Cached("createBinaryProfile()") ConditionProfile getClassProfile) {
        return profile.profile(getPythonClass(getLazyClass.execute(object), getClassProfile));
    }

    @Specialization(guards = "isForeignObject(object)", assumptions = "singleContextAssumption()")
    protected PythonClass getIt(@SuppressWarnings("unused") TruffleObject object,
                    @Cached("getIt(object)") PythonClass klass) {
        return klass;
    }

    @SuppressWarnings("unused")
    @Specialization(guards = "isForeignObject(object)")
    protected PythonClass getIt(TruffleObject object) {
        return getCore().lookupType(PythonBuiltinClassType.TruffleObject);
    }

    @TruffleBoundary
    public static PythonClass getItSlowPath(Object o) {
        PythonCore core = PythonLanguage.getContextRef().get().getCore();
        if (PGuards.isForeignObject(o)) {
            return core.lookupType(PythonBuiltinClassType.TruffleObject);
        } else if (o instanceof String) {
            return core.lookupType(PythonBuiltinClassType.PString);
        } else if (o instanceof Boolean) {
            return core.lookupType(PythonBuiltinClassType.Boolean);
        } else if (o instanceof Double || o instanceof Float) {
            return core.lookupType(PythonBuiltinClassType.PFloat);
        } else if (o instanceof Integer || o instanceof Long || o instanceof Short || o instanceof Byte) {
            return core.lookupType(PythonBuiltinClassType.PInt);
        } else if (o instanceof PythonObject) {
            return ((PythonObject) o).getPythonClass();
        } else if (o instanceof PEllipsis) {
            return core.lookupType(PythonBuiltinClassType.PEllipsis);
        } else if (o instanceof PNotImplemented) {
            return core.lookupType(PythonBuiltinClassType.PNotImplemented);
        } else if (o instanceof PNone) {
            return core.lookupType(PythonBuiltinClassType.PNone);
        } else {
            CompilerDirectives.transferToInterpreter();
            throw new IllegalStateException("unknown type " + o.getClass().getName());
        }
    }
}
