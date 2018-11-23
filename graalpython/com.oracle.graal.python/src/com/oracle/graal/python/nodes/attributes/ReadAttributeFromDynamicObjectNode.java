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

import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.Location;
import com.oracle.truffle.api.object.Shape;

@ImportStatic({PGuards.class, PythonOptions.class})
public abstract class ReadAttributeFromDynamicObjectNode extends ObjectAttributeNode {
    public static ReadAttributeFromDynamicObjectNode create() {
        return ReadAttributeFromDynamicObjectNodeGen.create();
    }

    public abstract Object execute(Object object, Object key);

    protected static Object readFinalValue(DynamicObject object, Location location) {
        Object value = location.get(object);
        return value == null ? PNone.NO_VALUE : value;
    }

    /*
     * Includes "dynamicObject" as a parameter so that Truffle DSL sees this as a dynamic check.
     */
    protected static boolean checkShape(@SuppressWarnings("unused") DynamicObject dynamicObject, DynamicObject cachedObject, Shape cachedShape) {
        return cachedObject.getShape() == cachedShape;
    }

    private static boolean assertFinal(DynamicObject dynamicObject, Object key, Object cachedValue) {
        Object other = dynamicObject.get(key) == null ? PNone.NO_VALUE : dynamicObject.get(key);
        return cachedValue == other || cachedValue instanceof Number && other instanceof Number && ((Number) cachedValue).doubleValue() == ((Number) other).doubleValue();
    }

    @SuppressWarnings("unused")
    @Specialization(limit = "1", //
                    guards = {
                                    "dynamicObject == cachedDynamicObject",
                                    "checkShape(dynamicObject, cachedDynamicObject, cachedShape)",
                                    "key == cachedKey",
                                    "loc != null",
                                    "loc.isAssumedFinal()",
                    }, //
                    assumptions = {
                                    "layoutAssumption",
                                    "finalAssumption"
                    })
    protected Object readDirectFinal(DynamicObject dynamicObject, Object key,
                    @Cached("dynamicObject") DynamicObject cachedDynamicObject,
                    @Cached("key") Object cachedKey,
                    @Cached("attrKey(key)") Object attrKey,
                    @Cached("dynamicObject.getShape()") Shape cachedShape,
                    @Cached("cachedShape.getValidAssumption()") Assumption layoutAssumption,
                    @Cached("getLocationOrNull(cachedShape.getProperty(attrKey))") Location loc,
                    @Cached("loc.getFinalAssumption()") Assumption finalAssumption,
                    @Cached("readFinalValue(dynamicObject, loc)") Object cachedValue) {
        assert assertFinal(dynamicObject, attrKey, cachedValue);
        return cachedValue;
    }

    @SuppressWarnings("unused")
    @Specialization(limit = "getIntOption(getContext(), AttributeAccessInlineCacheMaxDepth)", //
                    guards = {
                                    "dynamicObject.getShape() == cachedShape",
                                    "key == cachedKey",
                                    "loc == null || !loc.isAssumedFinal()",
                    }, //
                    assumptions = {
                                    "layoutAssumption"
                    })
    protected Object readDirect(DynamicObject dynamicObject, Object key,
                    @Cached("key") Object cachedKey,
                    @Cached("attrKey(cachedKey)") Object attrKey,
                    @Cached("dynamicObject.getShape()") Shape cachedShape,
                    @Cached("cachedShape.getValidAssumption()") Assumption layoutAssumption,
                    @Cached("getLocationOrNull(cachedShape.getProperty(attrKey))") Location loc) {
        if (loc == null) {
            return PNone.NO_VALUE;
        } else {
            return loc.get(dynamicObject);
        }
    }

    @SuppressWarnings("unused")
    @Specialization(guards = {
                    "dynamicObject.getShape() == cachedShape",
                    "!layoutAssumption.isValid()"
    })
    protected Object updateShapeAndRead(DynamicObject dynamicObject, Object key,
                    @Cached("dynamicObject.getShape()") Shape cachedShape,
                    @Cached("cachedShape.getValidAssumption()") Assumption layoutAssumption,
                    @Cached("create()") ReadAttributeFromDynamicObjectNode nextNode) {
        CompilerDirectives.transferToInterpreter();
        dynamicObject.updateShape();
        return nextNode.execute(dynamicObject, key);
    }

    @Specialization(replaces = "readDirect")
    protected Object readIndirect(DynamicObject dynamicObject, Object key) {
        Object value = dynamicObject.get(attrKey(key));
        if (value == null) {
            return PNone.NO_VALUE;
        } else {
            return value;
        }
    }
}
