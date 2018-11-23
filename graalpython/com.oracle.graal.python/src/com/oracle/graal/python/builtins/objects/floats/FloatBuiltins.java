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
package com.oracle.graal.python.builtins.objects.floats;

import static com.oracle.graal.python.nodes.SpecialMethodNames.__ABS__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__ADD__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__BOOL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__EQ__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__FLOAT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__FLOORDIV__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__FORMAT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__GETFORMAT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__GE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__GT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__INT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__LE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__LT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__MOD__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__MUL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__NEG__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__NE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__POS__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__POW__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__RADD__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__REPR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__RFLOORDIV__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__RMOD__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__RMUL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__ROUND__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__RSUB__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__RTRUEDIV__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__STR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__SUB__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__TRUEDIV__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__TRUNC__;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.ByteOrder;
import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.MathGuards;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes.FromNativeSubclassNode;
import com.oracle.graal.python.builtins.objects.cext.PythonNativeObject;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.type.LazyPythonClass;
import com.oracle.graal.python.builtins.objects.type.PythonClass;
import com.oracle.graal.python.nodes.call.special.LookupAndCallVarargsNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.object.GetLazyClassNode;
import com.oracle.graal.python.nodes.truffle.PythonArithmeticTypes;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.graal.python.runtime.formatting.FloatFormatter;
import com.oracle.graal.python.runtime.formatting.InternalFormat;
import com.oracle.graal.python.runtime.formatting.InternalFormat.Formatter;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.profiles.ConditionProfile;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PFloat)
public final class FloatBuiltins extends PythonBuiltins {
    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return FloatBuiltinsFactory.getFactories();
    }

    public static double asDouble(boolean right) {
        return right ? 1.0 : 0.0;
    }

    @Builtin(name = __STR__, fixedNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class StrNode extends PythonUnaryBuiltinNode {
        @Specialization
        String str(double self) {
            InternalFormat.Spec spec = new InternalFormat.Spec(' ', '>', InternalFormat.Spec.NONE, false, InternalFormat.Spec.UNSPECIFIED, false, 0, 'r');
            FloatFormatter f = new FloatFormatter(getCore(), spec);
            f.setMinFracDigits(1);
            return f.format(self).getResult();
        }

        public static StrNode create() {
            return FloatBuiltinsFactory.StrNodeFactory.create();
        }

        @Specialization(guards = "getFloat.isSubtype(object)", limit = "1")
        String doNativeFloat(PythonNativeObject object,
                        @SuppressWarnings("unused") @Cached("nativeFloat()") FromNativeSubclassNode<Double> getFloat) {
            return PFloat.doubleToString(getFloat.execute(object));
        }
    }

    @Builtin(name = __REPR__, fixedNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class ReprNode extends StrNode {
    }

    @Builtin(name = __FORMAT__, fixedNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class FormatNode extends PythonBinaryBuiltinNode {

        @Specialization
        @TruffleBoundary
        String format(double self, String formatString,
                        @Cached("create()") StrNode strNode,
                        @Cached("createBinaryProfile()") ConditionProfile strProfile,
                        @Cached("createBinaryProfile()") ConditionProfile unknownProfile) {
            if (strProfile.profile(shouldBeAsStr(formatString))) {
                return strNode.str(self);
            }
            InternalFormat.Spec spec = InternalFormat.fromText(getCore(), formatString, __FORMAT__);
            FloatFormatter formatter = prepareFormatter(spec);
            if (unknownProfile.profile(formatter == null)) {
                // The type code was not recognised in prepareFormatter
                throw Formatter.unknownFormat(getCore(), spec.type, "float");
            }
            formatter.format(self);
            return formatter.pad().getResult();
        }

        private FloatFormatter prepareFormatter(InternalFormat.Spec spec) {
            // Slight differences between format types
            switch (spec.type) {
                case 'n':
                case InternalFormat.Spec.NONE:
                case 'e':
                case 'f':
                case 'g':
                case 'E':
                case 'F':
                case 'G':
                case '%':
                    if (spec.type == 'n' && spec.grouping) {
                        throw Formatter.notAllowed(getCore(), "Grouping", "float", spec.type);
                    }
                    // Check for disallowed parts of the specification
                    if (spec.alternate) {
                        throw Formatter.alternateFormNotAllowed(getCore(), "float");
                    }
                    // spec may be incomplete. The defaults are those commonly used for numeric
                    // formats.
                    InternalFormat.Spec usedSpec = spec.withDefaults(InternalFormat.Spec.NUMERIC);
                    return new FloatFormatter(getCore(), usedSpec);
                default:
                    return null;
            }
        }

        private static boolean shouldBeAsStr(String spec) {
            if (spec.isEmpty()) {
                return true;
            }
            if (spec.length() == 1) {
                char c = spec.charAt(0);
                return ((c >= '0' && c <= '9') || c == ' ' || c == '_' || c == '+' || c == '-' || c == '<' || c == '>' || c == '=' || c == '^');
            }
            return false;
        }
    }

    @Builtin(name = __ABS__, fixedNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class AbsNode extends PythonUnaryBuiltinNode {

        @Specialization
        double abs(double arg) {
            return Math.abs(arg);
        }
    }

    @Builtin(name = __BOOL__, fixedNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class BoolNode extends PythonUnaryBuiltinNode {
        @Specialization
        boolean bool(double self) {
            return self != 0.0;
        }
    }

    @Builtin(name = __INT__, fixedNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    @ImportStatic(MathGuards.class)
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class IntNode extends PythonUnaryBuiltinNode {

        @Specialization(guards = "fitInt(self)")
        int doIntRange(double self) {
            return (int) self;
        }

        @Specialization(guards = "fitLong(self)")
        long doLongRange(double self) {
            return (long) self;
        }

        @Specialization(guards = "!fitLong(self)")
        @TruffleBoundary
        PInt doGeneric(double self) {
            return factory().createInt(BigDecimal.valueOf(self).toBigInteger());
        }
    }

    @Builtin(name = __FLOAT__, fixedNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class FloatNode extends PythonUnaryBuiltinNode {
        @Specialization
        double doDouble(double self) {
            return self;
        }

        @Specialization
        PFloat doPFloat(PFloat self) {
            return self;
        }

        @Specialization(guards = "getFloat.isSubtype(possibleBase)", limit = "1")
        PythonNativeObject doNativeFloat(PythonNativeObject possibleBase,
                        @SuppressWarnings("unused") @Cached("nativeFloat()") FromNativeSubclassNode<Double> getFloat) {
            return possibleBase;
        }

        @Fallback
        Object doFallback(Object possibleBase) {
            throw raise(PythonErrorType.TypeError, "must be real number, not %p", possibleBase);
        }
    }

    @Builtin(name = __ADD__, fixedNumOfPositionalArgs = 2)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
    abstract static class AddNode extends PythonBinaryBuiltinNode {
        @Specialization
        double doDD(double left, double right) {
            return left + right;
        }

        @Specialization
        double doDL(double left, long right) {
            return left + right;
        }

        @Specialization
        double doDPi(double left, PInt right) {
            return left + right.doubleValue();
        }

        @Specialization
        Object doDP(PythonNativeObject left, long right,
                        @Cached("nativeFloat()") FromNativeSubclassNode<Double> getFloat) {
            Double leftPrimitive = getFloat.execute(left);
            if (leftPrimitive != null) {
                return leftPrimitive + right;
            } else {
                return PNotImplemented.NOT_IMPLEMENTED;
            }
        }

        @Specialization
        Object doDP(PythonNativeObject left, double right,
                        @Cached("nativeFloat()") FromNativeSubclassNode<Double> getFloat) {
            Double leftPrimitive = getFloat.execute(left);
            if (leftPrimitive != null) {
                return leftPrimitive + right;
            } else {
                return PNotImplemented.NOT_IMPLEMENTED;
            }
        }

        @SuppressWarnings("unused")
        @Fallback
        PNotImplemented doGeneric(Object left, Object right) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = __RADD__, fixedNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class RAddNode extends AddNode {
    }

    @Builtin(name = __SUB__, fixedNumOfPositionalArgs = 2)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
    abstract static class SubNode extends PythonBinaryBuiltinNode {
        @Specialization
        double doDD(double left, double right) {
            return left - right;
        }

        @Specialization
        double doDL(double left, long right) {
            return left - right;
        }

        @Specialization
        double doDPi(double left, PInt right) {
            return left - right.doubleValue();
        }

        @SuppressWarnings("unused")
        @Fallback
        PNotImplemented doGeneric(Object left, Object right) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = __RSUB__, fixedNumOfPositionalArgs = 2)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
    abstract static class RSubNode extends PythonBinaryBuiltinNode {
        @Specialization
        double doDD(double right, double left) {
            return left - right;
        }

        @Specialization
        double doDL(double right, long left) {
            return left - right;
        }

        @Specialization
        double doDPi(double right, PInt left) {
            return left.doubleValue() - right;
        }

        @SuppressWarnings("unused")
        @Fallback
        PNotImplemented doGeneric(Object left, Object right) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = __MUL__, fixedNumOfPositionalArgs = 2)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
    abstract static class MulNode extends PythonBinaryBuiltinNode {
        @Specialization
        double doDL(double left, long right) {
            return left * right;
        }

        @Specialization
        double doDD(double left, double right) {
            return left * right;
        }

        @Specialization
        double doDP(double left, PInt right) {
            return left * right.doubleValue();
        }

        @Specialization
        Object doDP(PythonNativeObject left, double right,
                        @Cached("nativeFloat()") FromNativeSubclassNode<Double> getFloat) {
            Double leftPrimitive = getFloat.execute(left);
            if (leftPrimitive != null) {
                return leftPrimitive * right;
            } else {
                return PNotImplemented.NOT_IMPLEMENTED;
            }
        }

        @SuppressWarnings("unused")
        @Fallback
        PNotImplemented doGeneric(Object left, Object right) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = __RMUL__, fixedNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class RMulNode extends MulNode {
    }

    @Builtin(name = __POW__, minNumOfPositionalArgs = 2, maxNumOfPositionalArgs = 3)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
    abstract static class PowerNode extends PythonTernaryBuiltinNode {
        @Specialization
        double doDL(double left, long right, @SuppressWarnings("unused") PNone none) {
            return Math.pow(left, right);
        }

        @Specialization
        double doDPi(double left, PInt right, @SuppressWarnings("unused") PNone none) {
            return Math.pow(left, right.doubleValue());
        }

        @Specialization
        double doDD(double left, double right, @SuppressWarnings("unused") PNone none) {
            return Math.pow(left, right);
        }

        @Specialization
        double doDL(double left, long right, long mod) {
            return Math.pow(left, right) % mod;
        }

        @Specialization
        double doDPi(double left, PInt right, long mod) {
            return Math.pow(left, right.doubleValue()) % mod;
        }

        @Specialization
        double doDD(double left, double right, long mod) {
            return Math.pow(left, right) % mod;
        }

        @Specialization
        double doDL(double left, long right, PInt mod) {
            return Math.pow(left, right) % mod.doubleValue();
        }

        @Specialization
        double doDPi(double left, PInt right, PInt mod) {
            return Math.pow(left, right.doubleValue()) % mod.doubleValue();
        }

        @Specialization
        double doDD(double left, double right, PInt mod) {
            return Math.pow(left, right) % mod.doubleValue();
        }

        @Specialization
        double doDL(double left, long right, double mod) {
            return Math.pow(left, right) % mod;
        }

        @Specialization
        double doDPi(double left, PInt right, double mod) {
            return Math.pow(left, right.doubleValue()) % mod;
        }

        @Specialization
        double doDD(double left, double right, double mod) {
            return Math.pow(left, right) % mod;
        }

        @SuppressWarnings("unused")
        @Fallback
        PNotImplemented doGeneric(Object left, Object right, Object none) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = __FLOORDIV__, fixedNumOfPositionalArgs = 2)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
    abstract static class FloorDivNode extends FloatBinaryBuiltinNode {
        @Specialization
        double doDL(double left, long right) {
            raiseDivisionByZero(right == 0);
            return Math.floor(left / right);
        }

        @Specialization
        double doDL(double left, PInt right) {
            raiseDivisionByZero(right.isZero());
            return Math.floor(left / right.doubleValue());
        }

        @Specialization
        double doDD(double left, double right) {
            raiseDivisionByZero(right == 0.0);
            return Math.floor(left / right);
        }

        @SuppressWarnings("unused")
        @Fallback
        PNotImplemented doGeneric(Object left, Object right) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = "fromhex", fixedNumOfPositionalArgs = 2, isClassmethod = true)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    public abstract static class FromHexNode extends PythonBuiltinNode {

        private static final String INVALID_STRING = "invalid hexadecimal floating-point string";

        @TruffleBoundary
        private double fromHex(String arg) {
            boolean negative = false;
            String str = arg.trim().toLowerCase();

            if (str.isEmpty()) {
                throw raise(PythonErrorType.ValueError, INVALID_STRING);
            } else if (str.equals("inf") || str.equals("infinity") || str.equals("+inf") || str.equals("+infinity")) {
                return Double.POSITIVE_INFINITY;
            } else if (str.equals("-inf") || str.equals("-infinity")) {
                return Double.NEGATIVE_INFINITY;
            } else if (str.equals("nan") || str.equals("+nan") || str.equals("-nan")) {
                return Double.NaN;
            }

            if (str.charAt(0) == '+') {
                str = str.substring(1);
            } else if (str.charAt(0) == '-') {
                str = str.substring(1);
                negative = true;
            }

            if (str.isEmpty()) {
                throw raise(PythonErrorType.ValueError, INVALID_STRING);
            }

            if (!str.startsWith("0x")) {
                str = "0x" + str;
            }

            if (negative) {
                str = "-" + str;
            }

            if (str.indexOf('p') == -1) {
                str = str + "p0";
            }

            try {
                double result = Double.parseDouble(str);
                if (Double.isInfinite(result)) {
                    throw raise(PythonErrorType.OverflowError, "hexadecimal value too large to represent as a float");
                }

                return result;
            } catch (NumberFormatException ex) {
                throw raise(PythonErrorType.ValueError, INVALID_STRING);
            }
        }

        @Specialization(guards = "isPythonBuiltinClass(cl)")
        @SuppressWarnings("unused")
        public double fromhexFloat(PythonClass cl, String arg) {
            return fromHex(arg);
        }

        @Specialization(guards = "!isPythonBuiltinClass(cl)")
        public Object fromhexO(PythonClass cl, String arg,
                        @Cached("create(__CALL__)") LookupAndCallVarargsNode constr) {
            double value = fromHex(arg);
            return constr.execute(null, cl, new Object[]{cl, value});
        }

        @Fallback
        @SuppressWarnings("unused")
        public double fromhex(Object object, Object arg) {
            throw raise(PythonErrorType.TypeError, "bad argument type for built-in operation");
        }
    }

    @Builtin(name = "hex", fixedNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    public abstract static class HexNode extends PythonBuiltinNode {

        @TruffleBoundary
        private static String makeHexNumber(double value) {
            String result = Double.toHexString(value);
            String lresult = result.toLowerCase();
            if (lresult.equals("nan")) {
                return lresult;
            } else if (lresult.equals("infinity")) {
                return "inf";
            } else if (lresult.equals("-infinity")) {
                return "-inf";
            } else if (lresult.equals("0x0.0p0")) {
                return "0x0.0p+0";
            } else if (lresult.equals("-0x0.0p0")) {
                return "-0x0.0p+0";
            }

            int length = result.length();
            boolean start_exponent = false;
            StringBuilder sb = new StringBuilder(length + 1);
            int padding = value > 0 ? 17 : 18;
            for (int i = 0; i < length; i++) {
                char c = result.charAt(i);
                if (c == 'p') {
                    for (int pad = i; pad < padding; pad++) {
                        sb.append('0');
                    }
                    start_exponent = true;
                } else if (start_exponent) {
                    if (c != '-') {
                        sb.append('+');
                    }
                    start_exponent = false;
                }
                sb.append(c);
            }
            return sb.toString();
        }

        @Specialization
        public String hexD(double value) {
            return makeHexNumber(value);
        }
    }

    @Builtin(name = __RFLOORDIV__, fixedNumOfPositionalArgs = 2)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
    abstract static class RFloorDivNode extends FloatBinaryBuiltinNode {
        @Specialization
        double doDL(double right, long left) {
            raiseDivisionByZero(right == 0.0);
            return Math.floor(left / right);
        }

        @Specialization
        double doDPi(double right, PInt left) {
            raiseDivisionByZero(right == 0.0);
            return Math.floor(left.doubleValue() / right);
        }

        @Specialization
        double doDD(double left, double right) {
            // Cannot be reached via standard dispatch but it can be called directly.
            raiseDivisionByZero(right == 0.0);
            return Math.floor(left / right);
        }

        @SuppressWarnings("unused")
        @Fallback
        PNotImplemented doGeneric(Object right, Object left) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = __MOD__, fixedNumOfPositionalArgs = 2)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
    abstract static class ModNode extends FloatBinaryBuiltinNode {
        @Specialization
        double doDL(double left, long right) {
            raiseDivisionByZero(right == 0);
            return left % right;
        }

        @Specialization
        double doDL(double left, PInt right) {
            raiseDivisionByZero(right.isZero());
            return left % right.doubleValue();
        }

        @Specialization
        double doDD(double left, double right) {
            raiseDivisionByZero(right == 0.0);
            return left % right;
        }

        @SuppressWarnings("unused")
        @Fallback
        PNotImplemented doGeneric(Object right, Object left) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = __RMOD__, fixedNumOfPositionalArgs = 2)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
    abstract static class RModNode extends FloatBinaryBuiltinNode {
        @Specialization
        double doDL(double right, long left) {
            raiseDivisionByZero(right == 0.0);
            return left % right;
        }

        @Specialization
        double doGeneric(double right, PInt left) {
            raiseDivisionByZero(right == 0.0);
            return left.doubleValue() % right;
        }

        @Specialization
        double doDD(double left, double right) {
            raiseDivisionByZero(right == 0.0);
            return left % right;
        }

        @SuppressWarnings("unused")
        @Fallback
        PNotImplemented doGeneric(Object right, Object left) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = __TRUEDIV__, fixedNumOfPositionalArgs = 2)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
    abstract static class DivNode extends FloatBinaryBuiltinNode {
        @Specialization
        double doDD(double left, double right) {
            return left / right;
        }

        @Specialization
        double doDL(double left, long right) {
            return left / right;
        }

        @Specialization
        double doDPi(double left, PInt right) {
            return left / right.doubleValue();
        }

        @SuppressWarnings("unused")
        @Fallback
        PNotImplemented doGeneric(Object left, Object right) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = __RTRUEDIV__, fixedNumOfPositionalArgs = 2)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
    abstract static class RDivNode extends PythonBinaryBuiltinNode {
        @Specialization
        double div(double right, double left) {
            return left / right;
        }

        @Specialization
        double div(double right, long left) {
            return left / right;
        }

        @Specialization
        double div(double right, PInt left) {
            return left.doubleValue() / right;
        }

        @SuppressWarnings("unused")
        @Fallback
        PNotImplemented doGeneric(Object right, Object left) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = __ROUND__, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class RoundNode extends PythonBinaryBuiltinNode {
        /**
         * The logic is borrowed from Jython.
         */
        @TruffleBoundary
        @Specialization
        double round(double x, long n) {
            if (Double.isNaN(x) || Double.isInfinite(x) || x == 0.0) {
                // nans, infinities and zeros round to themselves
                return x;
            } else {
                // (Slightly less than) n*log2(10).
                float nlog2_10 = 3.3219f * n;

                // x = a * 2^b and a<2.
                int b = Math.getExponent(x);

                if (nlog2_10 > 52 - b) {
                    // When n*log2(10) > nmax, the lsb of abs(x) is >1, so x rounds to itself.
                    return x;
                } else if (nlog2_10 < -(b + 2)) {
                    // When n*log2(10) < -(b+2), abs(x)<0.5*10^n so x rounds to (signed) zero.
                    return Math.copySign(0.0, x);
                } else {
                    // We have to work it out properly.
                    BigDecimal xx = new BigDecimal(x);
                    BigDecimal rr = xx.setScale((int) n, RoundingMode.HALF_UP);
                    return rr.doubleValue();
                }
            }
        }

        @TruffleBoundary
        @Specialization
        double round(double x, PInt n) {
            return round(x, n.longValue());
        }

        @Specialization
        long round(double x, @SuppressWarnings("unused") PNone none) {
            return (long) round(x, 0);
        }

        @Fallback
        double roundFallback(Object x, Object n) {
            if (MathGuards.isFloat(x)) {
                throw raise(PythonErrorType.TypeError, "'%p' object cannot be interpreted as an integer", n);
            } else {
                throw raise(PythonErrorType.TypeError, "descriptor '__round__' requires a 'float' but received a '%p'", x);
            }
        }
    }

    @Builtin(name = __EQ__, fixedNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class EqNode extends PythonBinaryBuiltinNode {

        @Specialization
        boolean eqDbDb(double a, double b) {
            return a == b;
        }

        @Specialization
        boolean eqDbLn(double a, long b) {
            return a == b;
        }

        @Specialization
        boolean eqDbPI(double a, PInt b) {
            return a == b.doubleValue();
        }

        @Fallback
        @SuppressWarnings("unused")
        PNotImplemented eq(Object a, Object b) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = __NE__, fixedNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class NeNode extends PythonBinaryBuiltinNode {
        @Specialization
        boolean eqDbDb(double a, double b) {
            return a != b;
        }

        @Specialization
        boolean eqDbLn(double a, long b) {
            return a != b;
        }

        @Specialization
        boolean eqDbPI(double a, PInt b) {
            return a != b.doubleValue();
        }

        @Fallback
        @SuppressWarnings("unused")
        PNotImplemented eq(Object a, Object b) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = __LT__, fixedNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    public abstract static class LtNode extends PythonBinaryBuiltinNode {
        @Specialization
        boolean doDD(double x, double y) {
            return x < y;
        }

        @Specialization
        boolean doDL(double x, long y) {
            return x < y;
        }

        @Specialization(guards = "fromNativeNode.isSubtype(y)", limit = "1")
        boolean doDN(double x, PythonNativeObject y,
                        @Cached("nativeFloat()") FromNativeSubclassNode<Double> fromNativeNode) {
            return x < fromNativeNode.execute(y);
        }

        @Specialization(guards = {"nativeLeft.isSubtype(x)", "nativeRight.isSubtype(y)"}, limit = "1")
        boolean doDN(PythonNativeObject x, PythonNativeObject y,
                        @Cached("nativeFloat()") FromNativeSubclassNode<Double> nativeLeft,
                        @Cached("nativeFloat()") FromNativeSubclassNode<Double> nativeRight) {
            return nativeLeft.execute(x) < nativeRight.execute(y);
        }

        @Specialization(guards = "fromNativeNode.isSubtype(x)", limit = "1")
        boolean doDN(PythonNativeObject x, double y,
                        @Cached("nativeFloat()") FromNativeSubclassNode<Double> fromNativeNode) {
            return fromNativeNode.execute(x) < y;
        }

        @Specialization(guards = "fromNativeNode.isSubtype(x)", limit = "1")
        boolean doDN(PythonNativeObject x, long y,
                        @Cached("nativeFloat()") FromNativeSubclassNode<Double> fromNativeNode) {
            return fromNativeNode.execute(x) < y;
        }

        @Fallback
        @SuppressWarnings("unused")
        PNotImplemented doGeneric(Object a, Object b) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = __LE__, fixedNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    public abstract static class LeNode extends PythonBinaryBuiltinNode {
        @Specialization
        boolean doDD(double x, double y) {
            return x <= y;
        }

        @Specialization
        boolean doDL(double x, long y) {
            return x <= y;
        }

        @Specialization(guards = "fromNativeNode.isSubtype(y)", limit = "1")
        boolean doDN(double x, PythonNativeObject y,
                        @Cached("nativeFloat()") FromNativeSubclassNode<Double> fromNativeNode) {
            return x <= fromNativeNode.execute(y);
        }

        @Specialization(guards = {"nativeLeft.isSubtype(x)", "nativeRight.isSubtype(y)"}, limit = "1")
        boolean doNN(PythonNativeObject x, PythonNativeObject y,
                        @Cached("nativeFloat()") FromNativeSubclassNode<Double> nativeLeft,
                        @Cached("nativeFloat()") FromNativeSubclassNode<Double> nativeRight) {
            return nativeLeft.execute(x) <= nativeRight.execute(y);
        }

        @Specialization(guards = "fromNativeNode.isSubtype(x)", limit = "1")
        boolean doND(PythonNativeObject x, double y,
                        @Cached("nativeFloat()") FromNativeSubclassNode<Double> fromNativeNode) {
            return fromNativeNode.execute(x) <= y;
        }

        @Specialization(guards = "fromNativeNode.isSubtype(x)", limit = "1")
        boolean doNL(PythonNativeObject x, long y,
                        @Cached("nativeFloat()") FromNativeSubclassNode<Double> fromNativeNode) {
            return fromNativeNode.execute(x) <= y;
        }

        @Fallback
        @SuppressWarnings("unused")
        PNotImplemented doGeneric(Object a, Object b) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = __GT__, fixedNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    public abstract static class GtNode extends PythonBinaryBuiltinNode {
        @Specialization
        boolean doDD(double x, double y) {
            return x > y;
        }

        @Specialization
        boolean doDL(double x, long y) {
            return x > y;
        }

        @Specialization(guards = "fromNativeNode.isSubtype(y)", limit = "1")
        boolean doDN(double x, PythonNativeObject y,
                        @Cached("nativeFloat()") FromNativeSubclassNode<Double> fromNativeNode) {
            return x > fromNativeNode.execute(y);
        }

        @Specialization(guards = {"nativeLeft.isSubtype(x)", "nativeRight.isSubtype(y)"}, limit = "1")
        boolean doNN(PythonNativeObject x, PythonNativeObject y,
                        @Cached("nativeFloat()") FromNativeSubclassNode<Double> nativeLeft,
                        @Cached("nativeFloat()") FromNativeSubclassNode<Double> nativeRight) {
            return nativeLeft.execute(x) > nativeRight.execute(y);
        }

        @Specialization(guards = "fromNativeNode.isSubtype(x)", limit = "1")
        boolean doND(PythonNativeObject x, double y,
                        @Cached("nativeFloat()") FromNativeSubclassNode<Double> fromNativeNode) {
            return fromNativeNode.execute(x) > y;
        }

        @Specialization(guards = "fromNativeNode.isSubtype(x)", limit = "1")
        boolean doNL(PythonNativeObject x, long y,
                        @Cached("nativeFloat()") FromNativeSubclassNode<Double> fromNativeNode) {
            return fromNativeNode.execute(x) > y;
        }

        @Fallback
        @SuppressWarnings("unused")
        PNotImplemented doGeneric(Object a, Object b) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = __GE__, fixedNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    public abstract static class GeNode extends PythonBinaryBuiltinNode {
        @Specialization
        boolean doDD(double x, double y) {
            return x >= y;
        }

        @Specialization
        boolean doDL(double x, long y) {
            return x >= y;
        }

        @Fallback
        @SuppressWarnings("unused")
        PNotImplemented doGeneric(Object a, Object b) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = __POS__, fixedNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class PosNode extends PythonUnaryBuiltinNode {
        @Specialization
        double pos(double arg) {
            return arg;
        }
    }

    @Builtin(name = __NEG__, fixedNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class NegNode extends PythonUnaryBuiltinNode {
        @Specialization
        double neg(double arg) {
            return -arg;
        }
    }

    @GenerateNodeFactory
    @Builtin(name = "real", fixedNumOfPositionalArgs = 1, isGetter = true, doc = "the real part of a complex number")
    static abstract class RealNode extends PythonBuiltinNode {

        @Child private GetLazyClassNode getClassNode;

        protected LazyPythonClass getClass(Object value) {
            if (getClassNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getClassNode = insert(GetLazyClassNode.create());
            }
            return getClassNode.execute(value);
        }

        @Specialization
        double get(double self) {
            return self;
        }

        @Specialization(guards = "cannotBeOverridden(getClass(self))")
        PFloat getPFloat(PFloat self) {
            return self;
        }

        @Specialization(guards = "!cannotBeOverridden(getClass(self))")
        PFloat getPFloatOverriden(PFloat self) {
            return factory().createFloat(self.getValue());
        }
    }

    @GenerateNodeFactory
    @Builtin(name = "imag", fixedNumOfPositionalArgs = 1, isGetter = true, doc = "the imaginary part of a complex number")
    static abstract class ImagNode extends PythonBuiltinNode {

        @Specialization
        double get(@SuppressWarnings("unused") Object self) {
            return 0;
        }

    }

    @GenerateNodeFactory
    @Builtin(name = "conjugate", fixedNumOfPositionalArgs = 1, doc = "Returns self, the complex conjugate of any float.")
    static abstract class ConjugateNode extends RealNode {

    }

    @Builtin(name = __TRUNC__, fixedNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class TruncNode extends PythonUnaryBuiltinNode {

        @TruffleBoundary
        protected static int truncate(double value) {
            return (int) (value < 0 ? Math.ceil(value) : Math.floor(value));
        }

        @Specialization
        int trunc(double value,
                        @Cached("createBinaryProfile()") ConditionProfile nanProfile,
                        @Cached("createBinaryProfile()") ConditionProfile infProfile) {
            if (nanProfile.profile(Double.isNaN(value))) {
                throw raise(PythonErrorType.ValueError, "cannot convert float NaN to integer");
            }
            if (infProfile.profile(Double.isInfinite(value))) {
                throw raise(PythonErrorType.OverflowError, "cannot convert float infinity to integer");
            }
            return truncate(value);
        }

        @Specialization
        int trunc(PFloat pValue,
                        @Cached("createBinaryProfile()") ConditionProfile nanProfile,
                        @Cached("createBinaryProfile()") ConditionProfile infProfile) {
            double value = pValue.getValue();
            if (nanProfile.profile(Double.isNaN(value))) {
                throw raise(PythonErrorType.ValueError, "cannot convert float NaN to integer");
            }
            if (infProfile.profile(Double.isInfinite(value))) {
                throw raise(PythonErrorType.OverflowError, "cannot convert float infinity to integer");
            }
            return truncate(value);
        }

    }

    @Builtin(name = __GETFORMAT__, fixedNumOfPositionalArgs = 2, isClassmethod = true)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class GetFormatNode extends PythonBinaryBuiltinNode {
        private static String getDetectedEndianess() {
            try {
                ByteOrder byteOrder = ByteOrder.nativeOrder();
                if (byteOrder.equals(ByteOrder.BIG_ENDIAN)) {
                    return "IEEE, big-endian";
                } else if (byteOrder.equals(ByteOrder.LITTLE_ENDIAN)) {
                    return "IEEE, little-endian";
                }
            } catch (Error ignored) {
            }
            return "unknown";
        }

        protected boolean isValidTypeStr(String typeStr) {
            return typeStr.equals("float") || typeStr.equals("double");
        }

        @Specialization(guards = "isValidTypeStr(typeStr)")
        String getFormat(@SuppressWarnings("unused") PythonClass cls, @SuppressWarnings("unused") String typeStr) {
            return getDetectedEndianess();
        }

        @Fallback
        String getFormat(@SuppressWarnings("unused") Object cls, @SuppressWarnings("unused") Object typeStr) {
            throw raise(PythonErrorType.ValueError, "__getformat__() argument 1 must be 'double' or 'float'");
        }
    }

    private abstract static class FloatBinaryBuiltinNode extends PythonBinaryBuiltinNode {
        protected void raiseDivisionByZero(boolean cond) {
            if (cond) {
                throw raise(PythonErrorType.ZeroDivisionError, "division by zero");
            }
        }
    }
}
