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
package com.oracle.graal.python.builtins.objects.ints;

import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;

import java.math.BigInteger;
import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.builtins.objects.array.PArray;
import com.oracle.graal.python.builtins.objects.bytes.BytesNodes;
import com.oracle.graal.python.builtins.objects.bytes.PByteArray;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.bytes.PIBytesLike;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes.FromNativeSubclassNode;
import com.oracle.graal.python.builtins.objects.cext.PythonNativeObject;
import com.oracle.graal.python.builtins.objects.cext.PythonNativeVoidPtr;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.memoryview.PMemoryView;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.LazyPythonClass;
import com.oracle.graal.python.builtins.objects.type.PythonClass;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.call.special.LookupAndCallBinaryNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallTernaryNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallVarargsNode;
import com.oracle.graal.python.nodes.control.GetIteratorNode;
import com.oracle.graal.python.nodes.datamodel.IsIterableNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.object.GetLazyClassNode;
import com.oracle.graal.python.nodes.truffle.PythonArithmeticTypes;
import com.oracle.graal.python.nodes.util.CastToIndexNode;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PInt)
public class IntBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return IntBuiltinsFactory.getFactories();
    }

    private abstract static class IntBinaryBuiltinNode extends PythonBinaryBuiltinNode {
        private final BranchProfile divisionByZeroProfile = BranchProfile.create();

        protected void raiseDivisionByZero(boolean cond) {
            if (cond) {
                divisionByZeroProfile.enter();
                throw raise(PythonErrorType.ZeroDivisionError, "division by zero");
            }
        }
    }

    @Builtin(name = SpecialMethodNames.__ROUND__, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class RoundNode extends PythonBinaryBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization
        public int round(int arg, int n) {
            return arg;
        }

        @SuppressWarnings("unused")
        @Specialization
        public long round(long arg, int n) {
            return arg;
        }

        @SuppressWarnings("unused")
        @Specialization
        public PInt round(PInt arg, int n) {
            return factory().createInt(arg.getValue());
        }
    }

    @Builtin(name = SpecialMethodNames.__ADD__, fixedNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    public abstract static class AddNode extends PythonBinaryBuiltinNode {

        @Specialization(rewriteOn = ArithmeticException.class)
        int add(int left, int right) {
            return Math.addExact(left, right);
        }

        @Specialization(rewriteOn = ArithmeticException.class)
        long addLong(long left, long right) {
            return Math.addExact(left, right);
        }

        @Specialization
        PInt addPInt(long left, long right) {
            return factory().createInt(op(BigInteger.valueOf(left), BigInteger.valueOf(right)));
        }

        @Specialization
        PInt add(PInt left, long right) {
            return add(left, factory().createInt(right));
        }

        @Specialization
        PInt add(long left, PInt right) {
            return add(factory().createInt(left), right);
        }

        @Specialization
        PInt add(PInt left, PInt right) {
            return factory().createInt(op(left.getValue(), right.getValue()));
        }

        @TruffleBoundary
        BigInteger op(BigInteger left, BigInteger right) {
            return left.add(right);
        }

        @SuppressWarnings("unused")
        @Fallback
        PNotImplemented doGeneric(Object left, Object right) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = SpecialMethodNames.__RADD__, fixedNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class RAddNode extends AddNode {
    }

    @Builtin(name = SpecialMethodNames.__SUB__, fixedNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    public abstract static class SubNode extends PythonBinaryBuiltinNode {

        @Specialization(rewriteOn = ArithmeticException.class)
        int doII(int x, int y) throws ArithmeticException {
            return Math.subtractExact(x, y);
        }

        @Specialization
        long doIIOvf(int x, int y) {
            return (long) x - (long) y;
        }

        @Specialization(rewriteOn = ArithmeticException.class)
        long doLL(long x, long y) throws ArithmeticException {
            return Math.subtractExact(x, y);
        }

        @Specialization
        PInt doLLOvf(long x, long y) {
            return factory().createInt(op(BigInteger.valueOf(x), BigInteger.valueOf(y)));
        }

        @Specialization
        PInt doPIntLong(PInt left, long right) {
            return doPIntPInt(left, factory().createInt(right));
        }

        @Specialization
        PInt doLongPInt(long left, PInt right) {
            return doPIntPInt(factory().createInt(left), right);
        }

        @Specialization
        PInt doPIntPInt(PInt left, PInt right) {
            return factory().createInt(op(left.getValue(), right.getValue()));
        }

        @TruffleBoundary
        BigInteger op(BigInteger left, BigInteger right) {
            return left.subtract(right);
        }

        @SuppressWarnings("unused")
        @Fallback
        PNotImplemented doGeneric(Object left, Object right) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = SpecialMethodNames.__RSUB__, fixedNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    public abstract static class RSubNode extends PythonBinaryBuiltinNode {

        @Specialization(rewriteOn = ArithmeticException.class)
        int doII(int y, int x) throws ArithmeticException {
            return Math.subtractExact(x, y);
        }

        @Specialization
        long doIIOvf(int y, int x) {
            return (long) x - (long) y;
        }

        @Specialization(rewriteOn = ArithmeticException.class)
        long doLL(long y, long x) throws ArithmeticException {
            return Math.subtractExact(x, y);
        }

        @Specialization
        PInt doLLOvf(long y, long x) {
            return factory().createInt(op(BigInteger.valueOf(x), BigInteger.valueOf(y)));
        }

        @Specialization
        PInt doPIntLong(PInt right, long left) {
            return doPIntPInt(factory().createInt(left), right);
        }

        @Specialization
        PInt doLongPInt(long right, PInt left) {
            return doPIntPInt(factory().createInt(right), left);
        }

        @Specialization
        PInt doPIntPInt(PInt right, PInt left) {
            return factory().createInt(op(left.getValue(), right.getValue()));
        }

        @TruffleBoundary
        BigInteger op(BigInteger left, BigInteger right) {
            return left.subtract(right);
        }

        @SuppressWarnings("unused")
        @Fallback
        PNotImplemented doGeneric(Object right, Object left) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = SpecialMethodNames.__TRUEDIV__, fixedNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    public abstract static class TrueDivNode extends PythonBinaryBuiltinNode {

        @Specialization
        double divII(int x, int y) {
            return divDD(x, y);
        }

        @Specialization
        double divLL(long x, long y) {
            return divDD(x, y);
        }

        double divDD(double x, double y) {
            if (y == 0) {
                throw raise(PythonErrorType.ZeroDivisionError, "division by zero");
            }
            return x / y;
        }

        @Specialization
        double doPI(long left, PInt right) {
            return doPP(factory().createInt(left), right);
        }

        @Specialization
        double doPL(PInt left, long right) {
            if (right == 0) {
                throw raise(PythonErrorType.ZeroDivisionError, "division by zero");
            }
            return doPP(left, factory().createInt(right));
        }

        @Specialization
        double doPP(PInt left, PInt right) {
            if (right.isZero()) {
                throw raise(PythonErrorType.ZeroDivisionError, "division by zero");
            }
            return op(left.getValue(), right.getValue());
        }

        /*
         * We must take special care to do double conversion late (if possible), to avoid loss of
         * precision.
         */
        @TruffleBoundary
        private static double op(BigInteger a, BigInteger b) {
            BigInteger[] divideAndRemainder = a.divideAndRemainder(b);
            if (divideAndRemainder[1].equals(BigInteger.ZERO)) {
                return divideAndRemainder[0].doubleValue();
            } else {
                return a.doubleValue() / b.doubleValue();
            }
        }

        @SuppressWarnings("unused")
        @Fallback
        PNotImplemented doGeneric(Object left, Object right) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = SpecialMethodNames.__RTRUEDIV__, fixedNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    public abstract static class RTrueDivNode extends PythonBinaryBuiltinNode {

        @Specialization
        double divII(int right, int left) {
            return divDD(right, left);
        }

        @Specialization
        double divLL(long right, long left) {
            return divDD(right, left);
        }

        double divDD(double right, double left) {
            if (right == 0) {
                throw raise(PythonErrorType.ZeroDivisionError, "division by zero");
            }
            return left / right;
        }

        @Specialization
        double doPL(PInt right, long left) {
            return doPP(right, factory().createInt(left));
        }

        @Specialization
        double doPP(PInt right, PInt left) {
            if (right.isZero()) {
                throw raise(PythonErrorType.ZeroDivisionError, "division by zero");
            }
            return op(left.getValue(), right.getValue());
        }

        /*
         * We must take special care to do double conversion late (if possible), to avoid loss of
         * precision.
         */
        @TruffleBoundary
        private static double op(BigInteger a, BigInteger b) {
            BigInteger[] divideAndRemainder = a.divideAndRemainder(b);
            if (divideAndRemainder[1].equals(BigInteger.ZERO)) {
                return divideAndRemainder[0].doubleValue();
            } else {
                return a.doubleValue() / b.doubleValue();
            }
        }

        @SuppressWarnings("unused")
        @Fallback
        PNotImplemented doGeneric(Object left, Object right) {
            // TODO: raise error if left is an integer
            // raise(PythonErrorType.TypeError, "descriptor '__rtruediv__' requires a 'int' object
            // but received a '%p'", right);
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = SpecialMethodNames.__FLOORDIV__, fixedNumOfPositionalArgs = 2)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
    abstract static class FloorDivNode extends IntBinaryBuiltinNode {
        @Specialization
        int doLL(int left, int right) {
            raiseDivisionByZero(right == 0);
            return Math.floorDiv(left, right);
        }

        @Specialization
        long doLL(long left, long right) {
            raiseDivisionByZero(right == 0);
            return Math.floorDiv(left, right);
        }

        @Specialization(rewriteOn = ArithmeticException.class)
        int doLPi(int left, PInt right) throws ArithmeticException {
            raiseDivisionByZero(right.isZero());
            return Math.floorDiv(left, right.intValueExact());
        }

        @Specialization
        int doLPiOvf(int left, PInt right) {
            raiseDivisionByZero(right.isZero());
            try {
                return Math.floorDiv(left, right.intValueExact());
            } catch (ArithmeticException e) {
                return 0;
            }
        }

        @Specialization(rewriteOn = ArithmeticException.class)
        long doLPi(long left, PInt right) throws ArithmeticException {
            raiseDivisionByZero(right.isZero());
            return Math.floorDiv(left, right.longValueExact());
        }

        @Specialization
        long doLPiOvf(long left, PInt right) {
            raiseDivisionByZero(right.isZero());
            try {
                return Math.floorDiv(left, right.longValueExact());
            } catch (ArithmeticException e) {
                return 0;
            }
        }

        @Specialization
        PInt doPiL(PInt left, int right) {
            raiseDivisionByZero(right == 0);
            return factory().createInt(op(left.getValue(), BigInteger.valueOf(right)));
        }

        @Specialization
        PInt doPiL(PInt left, long right) {
            raiseDivisionByZero(right == 0);
            return factory().createInt(op(left.getValue(), BigInteger.valueOf(right)));
        }

        @Specialization
        PInt doPiPi(PInt left, PInt right) {
            raiseDivisionByZero(right.isZero());
            return factory().createInt(op(left.getValue(), right.getValue()));
        }

        @TruffleBoundary
        static BigInteger op(BigInteger left, BigInteger right) {
            // Math.floorDiv for BigInteger
            BigInteger r = left.divide(right);
            // if the signs are different and modulo not zero, round down
            if ((left.xor(right)).signum() < 0 && (r.multiply(right).compareTo(left)) != 0) {
                r = r.subtract(BigInteger.ONE);
            }
            return r;
        }

        @SuppressWarnings("unused")
        @Fallback
        PNotImplemented doGeneric(Object right, Object left) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }

    }

    @Builtin(name = SpecialMethodNames.__RFLOORDIV__, fixedNumOfPositionalArgs = 2)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
    abstract static class RFloorDivNode extends IntBinaryBuiltinNode {
        @Specialization
        int doII(int right, int left) {
            raiseDivisionByZero(right == 0);
            return left / right;
        }

        @Specialization
        long doLL(long right, long left) {
            raiseDivisionByZero(right == 0);
            return left / right;
        }

        @Specialization
        PInt doPiL(PInt right, long left) {
            raiseDivisionByZero(right.isZero());
            return factory().createInt(op(BigInteger.valueOf(left), right.getValue()));
        }

        @Specialization
        PInt doLPi(long right, PInt left) {
            raiseDivisionByZero(right == 0);
            return factory().createInt(op(left.getValue(), BigInteger.valueOf(right)));
        }

        @Specialization
        PInt doPiPi(PInt right, PInt left) {
            raiseDivisionByZero(right.isZero());
            return factory().createInt(op(left.getValue(), right.getValue()));
        }

        @TruffleBoundary
        static BigInteger op(BigInteger left, BigInteger right) {
            return left.divide(right);
        }

        @SuppressWarnings("unused")
        @Fallback
        PNotImplemented doGeneric(Object left, Object right) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = SpecialMethodNames.__MOD__, fixedNumOfPositionalArgs = 2)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
    abstract static class ModNode extends IntBinaryBuiltinNode {
        @Specialization
        int doII(int left, int right) {
            raiseDivisionByZero(right == 0);
            return Math.floorMod(left, right);
        }

        @Specialization
        long doLL(long left, long right) {
            raiseDivisionByZero(right == 0);
            return Math.floorMod(left, right);
        }

        @Specialization
        PInt doLPi(long left, PInt right) {
            raiseDivisionByZero(right.isZero());
            return factory().createInt(op(BigInteger.valueOf(left), right.getValue()));
        }

        @Specialization(guards = "right >= 0")
        PInt doPiL(PInt left, long right) {
            raiseDivisionByZero(right == 0);
            return factory().createInt(op(left.getValue(), BigInteger.valueOf(right)));
        }

        @Specialization(guards = "right.isZeroOrPositive()")
        PInt doPiPi(PInt left, PInt right) {
            raiseDivisionByZero(right.isZero());
            return factory().createInt(op(left.getValue(), right.getValue()));
        }

        @Specialization(guards = "right < 0")
        PInt doPiLNeg(PInt left, long right) {
            return factory().createInt(opNeg(left.getValue(), BigInteger.valueOf(right)));
        }

        @Specialization(guards = "!right.isZeroOrPositive()")
        PInt doPiPiNeg(PInt left, PInt right) {
            return factory().createInt(opNeg(left.getValue(), right.getValue()));
        }

        @TruffleBoundary
        static BigInteger op(BigInteger a, BigInteger b) {
            return a.mod(b);
        }

        @TruffleBoundary
        static BigInteger opNeg(BigInteger a, BigInteger b) {
            if (a.equals(BigInteger.ZERO)) {
                return BigInteger.ZERO;
            }
            return a.mod(b.negate()).subtract(b);
        }

        @SuppressWarnings("unused")
        @Fallback
        PNotImplemented doGeneric(Object left, Object right) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = SpecialMethodNames.__MUL__, fixedNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class MulNode extends PythonBinaryBuiltinNode {

        @Specialization(rewriteOn = ArithmeticException.class)
        int doII(int x, int y) throws ArithmeticException {
            return Math.multiplyExact(x, y);
        }

        @Specialization(replaces = "doII")
        long doIIL(int x, int y) {
            return x * (long) y;
        }

        @Specialization(rewriteOn = ArithmeticException.class)
        long doLL(long x, long y) {
            return Math.multiplyExact(x, y);
        }

        @Specialization
        PInt doLLOvf(long x, long y) {
            long r = x * y;
            long ax = Math.abs(x);
            long ay = Math.abs(y);
            if (((ax | ay) >>> 31 != 0)) {
                int leadingZeros = Long.numberOfLeadingZeros(ax) + Long.numberOfLeadingZeros(ay);
                if (leadingZeros < 66) {
                    return factory().createInt(op(BigInteger.valueOf(x), BigInteger.valueOf(y)));
                }
            }
            return factory().createInt(r);
        }

        @Specialization
        PInt doPIntLong(PInt left, long right) {
            return factory().createInt(op(left.getValue(), BigInteger.valueOf(right)));
        }

        @Specialization
        PInt doPIntPInt(PInt left, PInt right) {
            return factory().createInt(op(left.getValue(), right.getValue()));
        }

        @TruffleBoundary
        BigInteger op(BigInteger a, BigInteger b) {
            return a.multiply(b);
        }

        @SuppressWarnings("unused")
        @Fallback
        PNotImplemented doGeneric(Object left, Object right) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = SpecialMethodNames.__RMUL__, fixedNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class RMulNode extends MulNode {
    }

    @Builtin(name = SpecialMethodNames.__POW__, minNumOfPositionalArgs = 2, maxNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class PowNode extends PythonTernaryBuiltinNode {

        protected static PowNode create() {
            return null;
        }

        @Specialization(guards = "right >= 0", rewriteOn = ArithmeticException.class)
        int doIntegerFast(int left, int right, @SuppressWarnings("unused") PNone none) {
            int result = 1;
            int exponent = right;
            int base = left;
            while (exponent != 0) {
                if ((exponent & 1) != 0) {
                    result = Math.multiplyExact(result, base);
                }
                exponent >>= 1;
                base = Math.multiplyExact(base, base);
            }
            return result;
        }

        @Specialization(guards = "right >= 0")
        PInt doInteger(int left, int right, @SuppressWarnings("unused") PNone none) {
            return factory().createInt(op(BigInteger.valueOf(left), right));
        }

        @Specialization(guards = "right >= 0", rewriteOn = ArithmeticException.class)
        long doLongFast(long left, int right, PNone none) {
            return doLongFast(left, (long) right, none);
        }

        @Specialization(guards = "right >= 0")
        PInt doLong(long left, int right, PNone none) {
            return doLong(left, (long) right, none);
        }

        @Specialization(guards = "right >= 0", rewriteOn = ArithmeticException.class)
        long doLongFast(int left, long right, PNone none) {
            return doLongFast((long) left, right, none);
        }

        @Specialization(guards = "right >= 0")
        PInt doLong(int left, long right, PNone none) {
            return doLong((long) left, right, none);
        }

        @Specialization(guards = "right >= 0", rewriteOn = ArithmeticException.class)
        long doLongFast(long left, long right, @SuppressWarnings("unused") PNone none) {
            long result = 1;
            long exponent = right;
            long base = left;
            while (exponent != 0) {
                if ((exponent & 1) != 0) {
                    result = Math.multiplyExact(result, base);
                }
                exponent >>= 1;
                base = Math.multiplyExact(base, base);
            }
            return result;
        }

        @Specialization(guards = "right >= 0")
        PInt doLong(long left, long right, @SuppressWarnings("unused") PNone none) {
            return factory().createInt(op(BigInteger.valueOf(left), right));
        }

        @Specialization
        double doInt(long left, long right, @SuppressWarnings("unused") PNone none) {
            return Math.pow(left, right);
        }

        @Specialization
        double doInt(long left, double right, @SuppressWarnings("unused") PNone none) {
            return Math.pow(left, right);
        }

        @Specialization
        PInt doPInt(PInt left, PInt right, @SuppressWarnings("unused") PNone none) {
            try {
                return factory().createInt(op(left.getValue(), right.getValue().longValueExact()));
            } catch (ArithmeticException e) {
                // fall through to normal computation
            }
            double value = Math.pow(left.doubleValue(), right.doubleValue());
            return factory().createInt((long) value);
        }

        @Specialization
        Object powModulo(Object x, Object y, long z,
                        @Cached("create(__POW__)") LookupAndCallTernaryNode powNode,
                        @Cached("create(__MOD__)") LookupAndCallBinaryNode modNode) {
            Object result = powNode.execute(x, y, PNone.NO_VALUE);
            if (result == PNotImplemented.NOT_IMPLEMENTED) {
                return result;
            }
            return modNode.executeObject(result, z);
        }

        @Specialization
        Object powModuloPInt(Object x, Object y, PInt z,
                        @Cached("create(__POW__)") LookupAndCallTernaryNode powNode,
                        @Cached("create(__MOD__)") LookupAndCallBinaryNode modNode) {
            Object result = powNode.execute(x, y, PNone.NO_VALUE);
            if (result == PNotImplemented.NOT_IMPLEMENTED) {
                return result;
            }
            return modNode.executeObject(result, z);
        }

        @Fallback
        @SuppressWarnings("unused")
        PNotImplemented doFallback(Object x, Object y, Object z) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }

        @TruffleBoundary
        private BigInteger op(BigInteger a, long b) {
            try {
                // handle shortcut cases:
                int value = a.intValueExact();
                if (value == 0) {
                    return BigInteger.ZERO;
                } else if (value == 1) {
                    return BigInteger.ONE;
                } else if (value == -1) {
                    return (b & 1) != 0 ? BigInteger.valueOf(-1) : BigInteger.ONE;
                }
            } catch (ArithmeticException e) {
                // fall through to normal computation
            }
            if (b != (int) b) {
                // exponent does not fit in an int, this is likely going to cause out-of-memory
                throw raise(PythonErrorType.ArithmeticError, "exponent too large");
            }
            return a.pow((int) b);
        }
    }

    @Builtin(name = SpecialMethodNames.__ABS__, fixedNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class AbsNode extends PythonUnaryBuiltinNode {
        @Specialization
        boolean pos(boolean arg) {
            return arg;
        }

        @Specialization(rewriteOn = ArithmeticException.class)
        int pos(int arg) {
            int result = Math.abs(arg);
            if (result < 0) {
                throw new ArithmeticException();
            }
            return result;
        }

        @Specialization
        long posOvf(int arg) {
            return Math.abs((long) arg);
        }

        @Specialization(rewriteOn = ArithmeticException.class)
        long pos(long arg) {
            long result = Math.abs(arg);
            if (result < 0) {
                throw new ArithmeticException();
            }
            return result;
        }

        @Specialization(rewriteOn = IllegalArgumentException.class)
        PInt posOvf(long arg) throws IllegalArgumentException {
            long result = Math.abs(arg);
            if (result < 0) {
                return factory().createInt(op(BigInteger.valueOf(arg)));
            } else {
                return factory().createInt(BigInteger.valueOf(arg));
            }
        }

        @Specialization
        PInt pos(PInt arg) {
            return factory().createInt(op(arg.getValue()));
        }

        @TruffleBoundary
        static BigInteger op(BigInteger value) {
            return value.abs();
        }
    }

    @Builtin(name = SpecialMethodNames.__CEIL__, fixedNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class CeilNode extends PythonUnaryBuiltinNode {
        @Specialization
        int ceil(int arg) {
            return arg;
        }

        @Specialization
        long ceil(long arg) {
            return arg;
        }

        @Specialization
        PInt ceil(PInt arg) {
            return arg;
        }
    }

    @Builtin(name = SpecialMethodNames.__FLOOR__, fixedNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class FloorNode extends PythonUnaryBuiltinNode {
        @Specialization
        int floor(int arg) {
            return arg;
        }

        @Specialization
        long floor(long arg) {
            return arg;
        }

        @Specialization
        PInt floor(PInt arg) {
            return factory().createInt(arg.getValue());
        }
    }

    @Builtin(name = SpecialMethodNames.__POS__, fixedNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class PosNode extends PythonUnaryBuiltinNode {
        @Specialization
        int pos(int arg) {
            return arg;
        }

        @Specialization
        long pos(long arg) {
            return arg;
        }

        @Specialization
        PInt pos(PInt arg) {
            return factory().createInt(arg.getValue());
        }
    }

    @Builtin(name = SpecialMethodNames.__NEG__, fixedNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class NegNode extends PythonUnaryBuiltinNode {
        @Specialization(rewriteOn = ArithmeticException.class)
        int neg(int arg) {
            return Math.negateExact(arg);
        }

        @Specialization
        long negOvf(int arg) {
            return -((long) arg);
        }

        @Specialization(rewriteOn = ArithmeticException.class)
        long neg(long arg) {
            return Math.negateExact(arg);
        }

        @Specialization
        PInt negOvf(long arg) {
            BigInteger value = arg == Long.MIN_VALUE ? negate(BigInteger.valueOf(arg)) : BigInteger.valueOf(-arg);
            return factory().createInt(value);
        }

        @Specialization
        PInt doPInt(PInt operand) {
            return factory().createInt(negate(operand.getValue()));
        }

        @TruffleBoundary
        static BigInteger negate(BigInteger value) {
            return value.negate();
        }
    }

    @Builtin(name = SpecialMethodNames.__INVERT__, fixedNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class InvertNode extends PythonUnaryBuiltinNode {
        @Specialization
        int neg(boolean arg) {
            return ~(arg ? 1 : 0);
        }

        @Specialization
        int neg(int arg) {
            return ~arg;
        }

        @Specialization
        long neg(long arg) {
            return ~arg;
        }

        @Specialization
        PInt doPInt(PInt operand) {
            return factory().createInt(not(operand.getValue()));
        }

        @TruffleBoundary
        static BigInteger not(BigInteger value) {
            return value.not();
        }
    }

    @Builtin(name = SpecialMethodNames.__LSHIFT__, fixedNumOfPositionalArgs = 2)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
    abstract static class LShiftNode extends PythonBinaryBuiltinNode {

        private long leftShiftExact(long left, long right) {
            if (right >= Long.SIZE || right < 0) {
                shiftError(right);
            }

            long result = left << right;

            if (left != result >> right) {
                throw new ArithmeticException("integer overflow");
            }

            return result;
        }

        private int leftShiftExact(int left, int right) {
            if (right >= Integer.SIZE || right < 0) {
                shiftError(right);
            }

            int result = left << right;

            if (left != result >> right) {
                throw new ArithmeticException("integer overflow");
            }

            return result;
        }

        private void shiftError(long shiftCount) {
            if (shiftCount >= Integer.SIZE) {
                throw new ArithmeticException("integer overflow");
            } else if (shiftCount < 0) {
                throw raise(ValueError, "negative shift count");
            }
        }

        @Specialization(rewriteOn = ArithmeticException.class)
        int doII(int left, int right) {
            raiseNegativeShiftCount(right < 0);
            return leftShiftExact(left, right);
        }

        @Specialization
        Object doIIOvf(int left, int right) {
            raiseNegativeShiftCount(right < 0);
            try {
                return leftShiftExact(left, right);
            } catch (ArithmeticException e) {
                return doGuardedBiI(BigInteger.valueOf(left), right);
            }
        }

        @Specialization(rewriteOn = ArithmeticException.class)
        long doLL(long left, long right) {
            raiseNegativeShiftCount(right < 0);
            return leftShiftExact(left, right);
        }

        @Specialization
        Object doLLOvf(long left, long right) {
            raiseNegativeShiftCount(right < 0);
            try {
                return leftShiftExact(left, right);
            } catch (ArithmeticException e) {
                int rightI = (int) right;
                if (rightI == right) {
                    return factory().createInt(op(BigInteger.valueOf(left), rightI));
                } else {
                    throw raise(PythonErrorType.OverflowError);
                }
            }
        }

        @Specialization
        PInt doLPi(long left, PInt right) {
            raiseNegativeShiftCount(!right.isZeroOrPositive());
            try {
                return factory().createInt(op(BigInteger.valueOf(left), right.intValue()));
            } catch (ArithmeticException e) {
                throw raise(PythonErrorType.OverflowError);
            }
        }

        @Specialization
        PInt doPiI(PInt left, int right) {
            raiseNegativeShiftCount(right < 0);
            return doGuardedBiI(left.getValue(), right);
        }

        protected PInt doGuardedBiI(BigInteger left, int right) {
            try {
                return factory().createInt(op(left, right));
            } catch (ArithmeticException e) {
                throw raise(PythonErrorType.OverflowError);
            }
        }

        @Specialization
        PInt doPiL(PInt left, long right) {
            int rightI = (int) right;
            if (rightI == right) {
                return doPiI(left, rightI);
            } else {
                throw raise(PythonErrorType.OverflowError);
            }
        }

        @Specialization
        PInt doPiPi(PInt left, PInt right) {
            raiseNegativeShiftCount(!right.isZeroOrPositive());
            try {
                return factory().createInt(op(left.getValue(), right.intValueExact()));
            } catch (ArithmeticException e) {
                throw raise(PythonErrorType.OverflowError);
            }
        }

        @SuppressWarnings("unused")
        @Fallback
        PNotImplemented doGeneric(Object a, Object b) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }

        @TruffleBoundary
        public static BigInteger op(BigInteger left, int right) {
            return left.shiftLeft(right);
        }

        private void raiseNegativeShiftCount(boolean cond) {
            if (cond) {
                throw raise(PythonErrorType.ValueError, "negative shift count");
            }
        }

    }

    @Builtin(name = SpecialMethodNames.__RSHIFT__, fixedNumOfPositionalArgs = 2)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
    abstract static class RShiftNode extends PythonBinaryBuiltinNode {
        @Specialization(rewriteOn = ArithmeticException.class)
        int doII(int left, int right) {
            raiseNegativeShiftCount(right < 0);
            return left >> right;
        }

        @Specialization(rewriteOn = ArithmeticException.class)
        long doLL(long left, long right) {
            raiseNegativeShiftCount(right < 0);
            return left >> right;
        }

        @Specialization
        PInt doLPi(long left, PInt right) {
            raiseNegativeShiftCount(!right.isZeroOrPositive());
            return factory().createInt(op(BigInteger.valueOf(left), right.intValue()));
        }

        @Specialization
        PInt doPiI(PInt left, int right) {
            raiseNegativeShiftCount(right < 0);
            return factory().createInt(op(left.getValue(), right));
        }

        @Specialization
        PInt doPiL(PInt left, long right) {
            raiseNegativeShiftCount(right < 0);
            return factory().createInt(op(left.getValue(), (int) right));
        }

        @Specialization
        PInt doPInt(PInt left, PInt right) {
            raiseNegativeShiftCount(!right.isZeroOrPositive());
            return factory().createInt(op(left.getValue(), right.intValue()));
        }

        private void raiseNegativeShiftCount(boolean cond) {
            if (cond) {
                throw raise(PythonErrorType.ValueError, "negative shift count");
            }
        }

        @SuppressWarnings("unused")
        @Fallback
        PNotImplemented doGeneric(Object a, Object b) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }

        @TruffleBoundary
        public static BigInteger op(BigInteger left, int right) {
            return left.shiftRight(right);
        }

    }

    abstract static class BinaryBitwiseNode extends PythonBinaryBuiltinNode {

        @SuppressWarnings("unused")
        protected int op(int left, int right) {
            throw new RuntimeException("should not reach here");
        }

        @SuppressWarnings("unused")
        protected long op(long left, long right) {
            throw new RuntimeException("should not reach here");
        }

        @SuppressWarnings("unused")
        protected BigInteger op(BigInteger left, BigInteger right) {
            throw new RuntimeException("should not reach here");
        }

        @Specialization
        int doInteger(int left, int right) {
            return op(left, right);
        }

        @Specialization
        long doInteger(long left, long right) {
            return op(left, right);
        }

        @Specialization
        PInt doPInt(long left, PInt right) {
            return factory().createInt(op(BigInteger.valueOf(left), right.getValue()));
        }

        @Specialization
        PInt doPInt(PInt left, long right) {
            return factory().createInt(op(left.getValue(), BigInteger.valueOf(right)));
        }

        @Specialization
        PInt doPInt(PInt left, PInt right) {
            return factory().createInt(op(left.getValue(), right.getValue()));
        }

        @SuppressWarnings("unused")
        @Fallback
        PNotImplemented doGeneric(Object a, Object b) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = SpecialMethodNames.__AND__, fixedNumOfPositionalArgs = 2)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
    abstract static class AndNode extends BinaryBitwiseNode {

        @Override
        protected int op(int left, int right) {
            return left & right;
        }

        @Override
        protected long op(long left, long right) {
            return left & right;
        }

        @Override
        @TruffleBoundary
        public BigInteger op(BigInteger left, BigInteger right) {
            return left.and(right);
        }
    }

    @Builtin(name = SpecialMethodNames.__RAND__, fixedNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class RAndNode extends AndNode {
    }

    @Builtin(name = SpecialMethodNames.__OR__, fixedNumOfPositionalArgs = 2)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
    abstract static class OrNode extends BinaryBitwiseNode {

        @Override
        protected int op(int left, int right) {
            return left | right;
        }

        @Override
        protected long op(long left, long right) {
            return left | right;
        }

        @Override
        @TruffleBoundary
        public BigInteger op(BigInteger left, BigInteger right) {
            return left.or(right);
        }
    }

    @Builtin(name = SpecialMethodNames.__ROR__, fixedNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class ROrNode extends OrNode {
    }

    @Builtin(name = SpecialMethodNames.__XOR__, fixedNumOfPositionalArgs = 2)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
    abstract static class XorNode extends BinaryBitwiseNode {
        @Override
        protected int op(int left, int right) {
            return left ^ right;
        }

        @Override
        protected long op(long left, long right) {
            return left ^ right;
        }

        @Override
        @TruffleBoundary
        public BigInteger op(BigInteger left, BigInteger right) {
            return left.xor(right);
        }
    }

    @Builtin(name = SpecialMethodNames.__RXOR__, fixedNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class RXorNode extends XorNode {
    }

    @Builtin(name = SpecialMethodNames.__EQ__, fixedNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    public abstract static class EqNode extends PythonBinaryBuiltinNode {
        @Specialization
        boolean eqLL(long a, long b) {
            return a == b;
        }

        @Specialization
        boolean eqPIntBoolean(PInt a, boolean b) {
            return b ? a.isOne() : a.isZero();
        }

        @Specialization
        boolean eqBooleanPInt(boolean a, PInt b) {
            return a ? b.isOne() : b.isZero();
        }

        @Specialization(rewriteOn = ArithmeticException.class)
        boolean eqPiL(PInt a, long b) throws ArithmeticException {
            return a.longValueExact() == b;
        }

        @Specialization
        boolean eqPiLOvf(PInt a, long b) {
            try {
                return a.longValueExact() == b;
            } catch (ArithmeticException e) {
                return false;
            }
        }

        @Specialization(rewriteOn = ArithmeticException.class)
        boolean eqLPi(long b, PInt a) throws ArithmeticException {
            return a.longValueExact() == b;
        }

        @Specialization
        boolean eqPiLOvf(long b, PInt a) {
            try {
                return a.longValueExact() == b;
            } catch (ArithmeticException e) {
                return false;
            }
        }

        @Specialization
        boolean eqPiPi(PInt a, PInt b) {
            return a.equals(b);
        }

        @SuppressWarnings("unused")
        @Fallback
        PNotImplemented eq(Object a, Object b) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = SpecialMethodNames.__NE__, fixedNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class NeNode extends PythonBinaryBuiltinNode {
        @Specialization
        boolean eqLL(long a, long b) {
            return a != b;
        }

        @Specialization(rewriteOn = ArithmeticException.class)
        boolean eqPiL(PInt a, long b) {
            return a.longValueExact() != b;
        }

        @Specialization
        boolean eqPiLOvf(PInt a, long b) {
            try {
                return a.longValueExact() != b;
            } catch (ArithmeticException e) {
                return true;
            }
        }

        @Specialization(rewriteOn = ArithmeticException.class)
        boolean eqPiL(long b, PInt a) {
            return a.longValueExact() != b;
        }

        @Specialization
        boolean eqPiLOvf(long b, PInt a) {
            try {
                return a.longValueExact() != b;
            } catch (ArithmeticException e) {
                return true;
            }
        }

        @Specialization
        boolean eqPiPi(PInt a, PInt b) {
            return !a.equals(b);
        }

        @SuppressWarnings("unused")
        @Fallback
        PNotImplemented eq(Object a, Object b) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = SpecialMethodNames.__LT__, fixedNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class LtNode extends PythonBinaryBuiltinNode {
        @Specialization
        boolean doII(int left, int right) {
            return left < right;
        }

        @Specialization
        boolean doLL(long left, long right) {
            return left < right;
        }

        @Specialization
        boolean doLP(long left, PInt right) {
            try {
                return left < right.longValueExact();
            } catch (ArithmeticException e) {
                return right.doubleValue() > 0;
            }
        }

        @Specialization
        boolean doPL(PInt left, long right) {
            try {
                return left.longValueExact() < right;
            } catch (ArithmeticException e) {
                return left.doubleValue() < 0;
            }
        }

        @Specialization
        @TruffleBoundary
        boolean doPP(PInt left, PInt right) {
            return left.getValue().compareTo(right.getValue()) < 0;
        }

        @Specialization(guards = "fromNativeNode.isSubtype(y)", limit = "1")
        boolean doDN(long x, PythonNativeObject y,
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

        @SuppressWarnings("unused")
        @Fallback
        PNotImplemented doGeneric(Object a, Object b) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = SpecialMethodNames.__LE__, fixedNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class LeNode extends PythonBinaryBuiltinNode {
        @Specialization
        boolean doII(int left, int right) {
            return left <= right;
        }

        @Specialization
        boolean doLL(long left, long right) {
            return left <= right;
        }

        @Specialization
        boolean doLP(long left, PInt right) {
            try {
                return left <= right.longValueExact();
            } catch (ArithmeticException e) {
                return right.doubleValue() > 0;
            }
        }

        @Specialization
        boolean doPL(PInt left, long right) {
            try {
                return left.longValueExact() <= right;
            } catch (ArithmeticException e) {
                return left.doubleValue() < 0;
            }
        }

        @Specialization
        @TruffleBoundary
        boolean doPP(PInt left, PInt right) {
            return left.getValue().compareTo(right.getValue()) <= 0;
        }

        @SuppressWarnings("unused")
        @Fallback
        PNotImplemented doGeneric(Object a, Object b) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = SpecialMethodNames.__GT__, fixedNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class GtNode extends PythonBinaryBuiltinNode {

        @Specialization
        boolean doII(int left, int right) {
            return left > right;
        }

        @Specialization
        boolean doLL(long left, long right) {
            return left > right;
        }

        @Specialization
        boolean doLP(long left, PInt right) {
            try {
                return left > right.longValueExact();
            } catch (ArithmeticException e) {
                return right.doubleValue() < 0;
            }
        }

        @Specialization
        boolean doPL(PInt left, long right) {
            try {
                return left.longValueExact() > right;
            } catch (ArithmeticException e) {
                return left.doubleValue() > 0;
            }
        }

        @Specialization
        @TruffleBoundary
        boolean doPP(PInt left, PInt right) {
            return left.getValue().compareTo(right.getValue()) > 0;
        }

        @SuppressWarnings("unused")
        @Fallback
        PNotImplemented doGeneric(Object a, Object b) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = SpecialMethodNames.__GE__, fixedNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class GeNode extends PythonBinaryBuiltinNode {

        @Specialization
        boolean doII(int left, int right) {
            return left >= right;
        }

        @Specialization
        boolean doLL(long left, long right) {
            return left >= right;
        }

        @Specialization
        boolean doLP(long left, PInt right) {
            try {
                return left >= right.longValueExact();
            } catch (ArithmeticException e) {
                return right.doubleValue() < 0;
            }
        }

        @Specialization
        boolean doPL(PInt left, long right) {
            try {
                return left.longValueExact() >= right;
            } catch (ArithmeticException e) {
                return left.doubleValue() > 0;
            }
        }

        @Specialization
        @TruffleBoundary
        boolean doPP(PInt left, PInt right) {
            return left.getValue().compareTo(right.getValue()) >= 0;
        }

        @SuppressWarnings("unused")
        @Fallback
        PNotImplemented doGeneric(Object a, Object b) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    // to_bytes
    @Builtin(name = "to_bytes", fixedNumOfPositionalArgs = 3, keywordArguments = {"signed"})
    @GenerateNodeFactory
    @SuppressWarnings("unused")
    @TypeSystemReference(PythonArithmeticTypes.class)
    public abstract static class ToBytesNode extends PythonBuiltinNode {

        private static final String MESSAGE_INT_TO_BIG = "int too big to convert";
        private static final String MESSAGE_LENGTH_ARGUMENT = "length argument must be non-negative";
        private static final String MESSAGE_CONVERT_NEGATIVE = "can't convert negative int to unsigned";

        public abstract PBytes execute(Object self, Object byteCount, Object StringOrder, Object signed);

        // used for obtaining int, which will be the size of craeted array
        @Child private CastToIndexNode castToIndexNode;
        @Child private ToBytesNode recursiveNode;

        protected CastToIndexNode getCastToIndexNode() {
            if (castToIndexNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                castToIndexNode = insert(CastToIndexNode.createOverflow());
            }
            return castToIndexNode;
        }

        @TruffleBoundary
        private boolean isBigEndian(String order) {
            if (order.equals("big")) {
                return true;
            }
            if (order.equals("little")) {
                return false;
            }
            throw raise(PythonErrorType.ValueError, "byteorder must be either 'little' or 'big'");
        }

        @Specialization
        public PBytes fromLong(long self, int byteCount, String byteorder, PNone signed) {
            return fromLong(self, byteCount, byteorder, false);
        }

        private final ConditionProfile negativeByteCountProfile = ConditionProfile.createBinaryProfile();
        private final ConditionProfile negativeNumberProfile = ConditionProfile.createBinaryProfile();
        private final ConditionProfile overflowProfile = ConditionProfile.createBinaryProfile();

        @Specialization
        public PBytes fromLong(long self, int byteCount, String byteorder, boolean signed) {
            if (negativeByteCountProfile.profile(byteCount < 0)) {
                throw raise(PythonErrorType.ValueError, MESSAGE_LENGTH_ARGUMENT);
            }
            byte signByte = 0;
            if (self < 0) {
                if (negativeNumberProfile.profile(!signed)) {
                    throw raise(PythonErrorType.OverflowError, MESSAGE_CONVERT_NEGATIVE);
                }
                signByte = -1;
            }
            int index;
            int delta;
            if (isBigEndian(byteorder)) {
                index = byteCount - 1;
                delta = -1;
            } else {
                index = 0;
                delta = 1;
            }

            byte[] bytes = new byte[byteCount];
            long number = self;

            while (number != 0 && 0 <= index && index <= (byteCount - 1)) {
                bytes[index] = (byte) (number & 0xFF);
                if (number == signByte) {
                    number = 0;
                }
                number >>= 8;
                index += delta;
            }
            if (overflowProfile.profile((number != 0 && bytes.length == 1 && bytes[0] != self) || (signed && bytes.length == 1 && bytes[0] != self) || (byteCount == 0 && self != 0))) {

                throw raise(PythonErrorType.OverflowError, MESSAGE_INT_TO_BIG);
            }
            if (signed) {
                while (0 <= index && index <= (byteCount - 1)) {
                    bytes[index] = signByte;
                    index += delta;
                }
            }
            return factory().createBytes(bytes);
        }

        @Specialization
        public PBytes fromLongLong(long self, long byteCount, String byteorder, PNone signed) {
            return fromLongLong(self, byteCount, byteorder, false);
        }

        @Specialization
        public PBytes fromLongLong(long self, long byteCount, String byteorder, boolean signed) {
            int count = getCastToIndexNode().execute(byteCount);
            return fromLong(self, count, byteorder, signed);
        }

        @Specialization
        public PBytes fromLongPInt(long self, PInt byteCount, String byteorder, PNone signed) {
            return fromLongPInt(self, byteCount, byteorder, false);
        }

        @Specialization
        public PBytes fromLongPInt(long self, PInt byteCount, String byteorder, boolean signed) {
            int count = getCastToIndexNode().execute(byteCount);
            return fromLong(self, count, byteorder, signed);
        }

        @Specialization
        public PBytes fromPIntInt(PInt self, int byteCount, String byteorder, PNone signed) throws ArithmeticException {
            return fromPIntInt(self, byteCount, byteorder, false);
        }

        @TruffleBoundary
        private byte getSingByte(BigInteger value, boolean signed) {
            if (value.compareTo(BigInteger.ZERO) == -1) {
                if (!signed) {
                    throw raise(PythonErrorType.OverflowError, MESSAGE_CONVERT_NEGATIVE);
                }
                return -1;
            }
            return 0;
        }

        @TruffleBoundary
        private static byte[] getBytes(BigInteger value) {
            return value.toByteArray();
        }

        @Specialization
        public PBytes fromPIntInt(PInt self, int byteCount, String byteorder, boolean signed) {
            if (negativeByteCountProfile.profile(byteCount < 0)) {
                throw raise(PythonErrorType.ValueError, MESSAGE_LENGTH_ARGUMENT);
            }
            BigInteger value = self.getValue();
            byte signByte = getSingByte(value, signed);
            byte[] bytes = getBytes(value);
            if (bytes.length > byteCount) {
                // Check, whether we need to cut unneeded sign bytes.
                int len = bytes.length;
                int startIndex = 0;
                if (!signed) {
                    for (startIndex = 0; startIndex < bytes.length; startIndex++) {
                        if (bytes[startIndex] != 0) {
                            break;
                        }
                    }
                    len = Math.max(bytes.length - startIndex, byteCount);
                }
                if (overflowProfile.profile(len > byteCount)) {
                    // the corrected len is still bigger then we need.
                    throw raise(PythonErrorType.OverflowError, MESSAGE_INT_TO_BIG);
                }
                if (bytes.length > byteCount) {
                    // the array starts with sign bytes and has to be truncated to the requested
                    // size
                    byte[] tmp = bytes;
                    bytes = new byte[len];
                    System.arraycopy(tmp, startIndex, bytes, 0, len);
                }
            }

            if (isBigEndian(byteorder)) {
                if (byteCount > bytes.length) {
                    // requested array is bigger then we obtained from BigInteger
                    byte[] resultBytes = new byte[byteCount];
                    System.arraycopy(bytes, 0, resultBytes, resultBytes.length - bytes.length, bytes.length);
                    if (signed && signByte == -1) {
                        // add sign bytes
                        for (int i = 0; i < resultBytes.length - bytes.length; i++) {
                            resultBytes[i] = signByte;
                        }
                    }
                    return factory().createBytes(resultBytes);
                } else {
                    return factory().createBytes(bytes);
                }
            } else {
                // little endian -> need to switch bytes
                byte[] resultBytes = new byte[byteCount];
                for (int i = 0; i < bytes.length; i++) {
                    resultBytes[i] = bytes[bytes.length - 1 - i];
                }
                if (byteCount > bytes.length && signed && signByte == -1) {
                    // add sign negative bytes
                    for (int i = bytes.length; i < resultBytes.length; i++) {
                        resultBytes[i] = signByte;
                    }
                }
                return factory().createBytes(resultBytes);
            }
        }

        @Specialization
        public PBytes fromPIntLong(PInt self, long byteCount, String byteorder, PNone signed) {
            return fromPIntLong(self, byteCount, byteorder, false);
        }

        @Specialization
        public PBytes fromPIntLong(PInt self, long byteCount, String byteorder, boolean signed) {
            int count = getCastToIndexNode().execute(byteCount);
            return fromPIntInt(self, count, byteorder, signed);
        }

        @Specialization
        public PBytes fromPIntPInt(PInt self, PInt byteCount, String byteorder, PNone signed) {
            return fromPIntPInt(self, byteCount, byteorder, false);
        }

        @Specialization
        public PBytes fromPIntPInt(PInt self, PInt byteCount, String byteorder, boolean signed) {
            int count = getCastToIndexNode().execute(byteCount);
            return fromPIntInt(self, count, byteorder, signed);
        }

        public static boolean isNumber(Object value) {
            return value instanceof Integer || value instanceof Long || value instanceof PInt;
        }

        @Fallback
        PBytes general(Object self, Object byteCount, Object byteorder, Object oSigned) {
            int count = getCastToIndexNode().execute(byteCount);
            if (!PGuards.isString(byteorder)) {
                throw raise(PythonErrorType.TypeError, "to_bytes() argument 2 must be str, not %p", byteorder);
            }
            boolean signed = oSigned instanceof Boolean ? (boolean) oSigned : false;
            if (recursiveNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                recursiveNode = insert(create());
            }
            return recursiveNode.execute(self, count, byteorder, signed);
        }

        protected static ToBytesNode create() {
            return IntBuiltinsFactory.ToBytesNodeFactory.create(null);
        }
    }

    @Builtin(name = "from_bytes", fixedNumOfPositionalArgs = 3, takesVarArgs = true, keywordArguments = {"signed"}, isClassmethod = true)
    @GenerateNodeFactory
    @SuppressWarnings("unused")
    @TypeSystemReference(PythonArithmeticTypes.class)
    public abstract static class FromBytesNode extends PythonBuiltinNode {

        @Child private GetLazyClassNode getClassNode;

        @Child private LookupAndCallVarargsNode constructNode;
        @Child private BytesNodes.FromSequenceNode fromSequenceNode;
        @Child private BytesNodes.FromIteratorNode fromIteratorNode;
        @Child private GetIteratorNode getIteratorNode;

        @Child private IsIterableNode isIterableNode;
        @Child private BytesNodes.ToBytesNode toBytesNode;
        @Child private LookupAndCallUnaryNode callBytesNode;

        protected LazyPythonClass getClass(Object value) {
            if (getClassNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getClassNode = insert(GetLazyClassNode.create());
            }
            return getClassNode.execute(value);
        }

        protected BytesNodes.FromSequenceNode getFromSequenceNode() {
            if (fromSequenceNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                fromSequenceNode = insert(BytesNodes.FromSequenceNode.create());
            }
            return fromSequenceNode;
        }

        protected BytesNodes.FromIteratorNode getFromIteratorNode() {
            if (fromIteratorNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                fromIteratorNode = insert(BytesNodes.FromIteratorNode.create());
            }
            return fromIteratorNode;
        }

        protected GetIteratorNode getGetIteratorNode() {
            if (getIteratorNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getIteratorNode = insert(GetIteratorNode.create());
            }
            return getIteratorNode;
        }

        protected BytesNodes.ToBytesNode getToBytesNode() {
            if (toBytesNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                toBytesNode = insert(BytesNodes.ToBytesNode.create());
            }
            return toBytesNode;
        }

        private static byte[] littleToBig(byte[] bytes) {
            // PInt uses Java BigInteger which are big-endian
            byte[] bigEndianBytes = new byte[bytes.length];
            for (int i = 0; i < bytes.length; i++) {
                bigEndianBytes[bytes.length - i - 1] = bytes[i];
            }
            return bigEndianBytes;
        }

        @TruffleBoundary
        private static BigInteger createBigInteger(byte[] bytes, boolean isBigEndian, boolean signed) {
            if (bytes.length == 0) {
                // in case of empty byte array
                return BigInteger.ZERO;
            }
            BigInteger result;
            if (isBigEndian) { // big byteorder
                result = signed ? new BigInteger(bytes) : new BigInteger(1, bytes);
            } else { // little byteorder
                byte[] converted = littleToBig(bytes);
                result = signed ? new BigInteger(converted) : new BigInteger(1, converted);
            }
            return result;
        }

        @TruffleBoundary
        private boolean isBigEndian(String order) {
            if (order.equals("big")) {
                return true;
            }
            if (order.equals("little")) {
                return false;
            }
            throw raise(PythonErrorType.ValueError, "byteorder must be either 'little' or 'big'");
        }

        private Object createIntObject(PythonClass cl, BigInteger number) {
            if (PGuards.isPythonBuiltinClass(cl)) {
                return factory().createInt(number);
            }
            if (constructNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                constructNode = insert(LookupAndCallVarargsNode.create(SpecialMethodNames.__CALL__));
            }
            return constructNode.execute(null, cl, new Object[]{cl, factory().createInt(number)});
        }

        private Object compute(PythonClass cl, byte[] bytes, String byteorder, boolean signed) {
            BigInteger bi = createBigInteger(bytes, isBigEndian(byteorder), signed);
            return createIntObject(cl, bi);
        }

        // from String / PString
        // TODO these specialization shouldn't be there. The CPython ends up with TypeError in such
        // case. See GR-12453
        @Specialization
        @TruffleBoundary
        public Object fromString(PythonClass cl, String str, String byteorder, Object[] args, boolean signed) {
            return compute(cl, str.getBytes(), byteorder, signed);
        }

        @Specialization
        public Object fromString(PythonClass cl, String str, String byteorder, Object[] args, PNone keywordArg) {
            return fromString(cl, str, byteorder, args, false);
        }

        // from PBytes
        @Specialization
        public Object fromPBytes(PythonClass cl, PBytes bytes, String byteorder, Object[] args, boolean signed) {
            return compute(cl, getToBytesNode().execute(bytes), byteorder, signed);
        }

        @Specialization
        public Object fromPBytes(PythonClass cl, PBytes bytes, String byteorder, Object[] args, PNone signed) {
            return fromPBytes(cl, bytes, byteorder, args, false);
        }

        // from PByteArray
        @Specialization
        public Object fromPByteArray(PythonClass cl, PByteArray bytes, String byteorder, Object[] args, boolean signed) {
            return compute(cl, getToBytesNode().execute(bytes), byteorder, signed);
        }

        @Specialization
        public Object fromPByteArray(PythonClass cl, PByteArray bytes, String byteorder, Object[] args, PNone signed) {
            return fromPByteArray(cl, bytes, byteorder, args, false);
        }

        // from PArray
        @Specialization
        public Object fromPArray(PythonClass cl, PArray array, String byteorder, Object[] args, boolean signed,
                        @Cached("create()") BytesNodes.FromSequenceStorageNode fromSequenceStorageNode) {
            return compute(cl, fromSequenceStorageNode.execute(array.getSequenceStorage()), byteorder, signed);
        }

        @Specialization
        public Object fromPArray(PythonClass cl, PArray array, String byteorder, Object[] args, PNone signed,
                        @Cached("create()") BytesNodes.FromSequenceStorageNode fromSequenceStorageNode) {
            return fromPArray(cl, array, byteorder, args, false, fromSequenceStorageNode);
        }

        // from PMemoryView
        @Specialization
        public Object fromPMemoryView(PythonClass cl, PMemoryView view, String byteorder, Object[] args, boolean signed) {
            return compute(cl, getToBytesNode().execute(view), byteorder, signed);
        }

        @Specialization
        public Object fromPMemoryView(PythonClass cl, PMemoryView view, String byteorder, Object[] args, PNone signed) {
            return fromPMemoryView(cl, view, byteorder, args, false);
        }

        // from PList, only if it is not extended
        @Specialization(guards = "cannotBeOverridden(getClass(list))")
        public Object fromPList(PythonClass cl, PList list, String byteorder, Object[] args, boolean signed) {
            return compute(cl, getFromSequenceNode().execute(list), byteorder, signed);
        }

        @Specialization(guards = "cannotBeOverridden(getClass(list))")
        public Object fromPList(PythonClass cl, PList list, String byteorder, Object[] args, PNone signed) {
            return fromPList(cl, list, byteorder, args, false);
        }

        // from PTuple, only if it is not extended
        @Specialization(guards = "cannotBeOverridden(getClass(tuple))")
        public Object fromPTuple(PythonClass cl, PTuple tuple, String byteorder, Object[] args, boolean signed) {
            return compute(cl, getFromSequenceNode().execute(tuple), byteorder, signed);
        }

        @Specialization(guards = "cannotBeOverridden(getClass(tuple))")
        public Object fromPTuple(PythonClass cl, PTuple tuple, String byteorder, Object[] args, PNone signed) {
            return fromPTuple(cl, tuple, byteorder, args, false);
        }

        // rest objects
        @Specialization
        public Object fromObject(PythonClass cl, PythonObject object, String byteorder, Object[] args, PNone signed) {
            return fromObject(cl, object, byteorder, args, false);
        }

        @Specialization
        public Object fromObject(PythonClass cl, PythonObject object, String byteorder, Object[] args, boolean signed) {
            if (callBytesNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                callBytesNode = insert(LookupAndCallUnaryNode.create(SpecialMethodNames.__BYTES__));
            }
            Object result = callBytesNode.executeObject(object);
            if (result != PNone.NO_VALUE) { // first try o use __bytes__ call result
                if (!(result instanceof PIBytesLike)) {
                    raise(PythonErrorType.TypeError, "__bytes__ returned non-bytes (type %p)", result);
                }
                BigInteger bi = createBigInteger(getToBytesNode().execute(result), isBigEndian(byteorder), false);
                return createIntObject(cl, bi);
            }
            if (isIterableNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                isIterableNode = insert(IsIterableNode.create());
            }
            if (isIterableNode.execute(object)) {
                byte[] bytes = getFromIteratorNode().execute(getGetIteratorNode().executeWith(object));
                return compute(cl, bytes, byteorder, signed);
            }
            return general(cl, object, byteorder, args, signed);
        }

        @Fallback
        public Object general(Object cl, Object object, Object byteorder, Object args, Object signed) {
            throw raise(PythonErrorType.TypeError, "cannot convert '%p' object to bytes", object);
        }
    }

    @Builtin(name = SpecialMethodNames.__BOOL__, fixedNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class BoolNode extends PythonBuiltinNode {
        @Specialization
        public boolean toBoolean(boolean self) {
            return self;
        }

        @Specialization
        public boolean toBoolean(int self) {
            return self != 0;
        }

        @Specialization
        public boolean toBoolean(long self) {
            return self != 0;
        }

        @Specialization
        public boolean toBoolean(PInt self) {
            return !self.isZero();
        }
    }

    @Builtin(name = SpecialMethodNames.__STR__, fixedNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class StrNode extends PythonBuiltinNode {
        @Specialization
        @TruffleBoundary
        public String doI(int self) {
            return Integer.toString(self);
        }

        @Specialization
        @TruffleBoundary
        public String doL(long self) {
            return Long.toString(self);
        }

        @Specialization
        @TruffleBoundary
        public String doPInt(PInt self) {
            return self.toString();
        }
    }

    @Builtin(name = SpecialMethodNames.__REPR__, fixedNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class ReprNode extends StrNode {
    }

    @Builtin(name = SpecialMethodNames.__HASH__, fixedNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class HashNode extends PythonUnaryBuiltinNode {

        @Specialization
        int hash(int self) {
            return self;
        }

        @Specialization
        long hash(long self) {
            return self;
        }

        @Specialization
        long hash(PInt self) {
            return self.longValue();
        }

        @Specialization
        @TruffleBoundary
        long hash(PythonNativeVoidPtr self) {
            return self.object.hashCode();
        }
    }

    @Builtin(name = "bit_length", fixedNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class BitLengthNode extends PythonBuiltinNode {
        @Specialization
        int bitLength(int argument) {
            return Integer.SIZE - Integer.numberOfLeadingZeros(Math.abs(argument));
        }

        @Specialization
        int bitLength(long argument) {
            return Long.SIZE - Long.numberOfLeadingZeros(Math.abs(argument));
        }

        @Specialization
        @TruffleBoundary
        int bitLength(PInt argument) {
            return argument.getValue().abs().bitLength();
        }
    }

    @GenerateNodeFactory
    @Builtin(name = "real", fixedNumOfPositionalArgs = 1, isGetter = true, doc = "the real part of a complex number")
    static abstract class RealNode extends IntNode {

    }

    @GenerateNodeFactory
    @Builtin(name = "imag", fixedNumOfPositionalArgs = 1, isGetter = true, doc = "the imaginary part of a complex number")
    static abstract class ImagNode extends PythonBuiltinNode {
        @Specialization
        int get(@SuppressWarnings("unused") Object self) {
            return 0;
        }
    }

    @GenerateNodeFactory
    @Builtin(name = "numerator", fixedNumOfPositionalArgs = 1, isGetter = true, doc = "the numerator of a rational number in lowest terms")
    static abstract class NumeratorNode extends IntNode {

    }

    @GenerateNodeFactory
    @Builtin(name = "conjugate", fixedNumOfPositionalArgs = 1, doc = "Returns self, the complex conjugate of any int.")
    static abstract class ConjugateNode extends IntNode {

    }

    @GenerateNodeFactory
    @Builtin(name = "denominator", fixedNumOfPositionalArgs = 1, isGetter = true, doc = "the denominator of a rational number in lowest terms")
    static abstract class DenominatorNode extends PythonBuiltinNode {
        @Specialization
        int get(@SuppressWarnings("unused") Object self) {
            return 1;
        }
    }

    @GenerateNodeFactory
    @Builtin(name = SpecialMethodNames.__TRUNC__, fixedNumOfPositionalArgs = 1, doc = "Truncating an Integral returns itself.")
    static abstract class TruncNode extends IntNode {

    }

    @Builtin(name = SpecialMethodNames.__INT__, fixedNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class IntNode extends PythonBuiltinNode {
        @Child private GetLazyClassNode getClassNode;

        protected LazyPythonClass getClass(Object value) {
            if (getClassNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getClassNode = insert(GetLazyClassNode.create());
            }
            return getClassNode.execute(value);
        }

        @Specialization
        int doB(boolean self) {
            return self ? 1 : 0;
        }

        @Specialization
        int doI(int self) {
            return self;
        }

        @Specialization
        long doL(long self) {
            return self;
        }

        @Specialization(guards = "cannotBeOverridden(getClass(self))")
        PInt doPInt(PInt self) {
            return self;
        }

        @Specialization(guards = "!cannotBeOverridden(getClass(self))")
        PInt doPIntOverriden(PInt self) {
            return factory().createInt(self.getValue());
        }
    }

    @Builtin(name = SpecialMethodNames.__INDEX__, fixedNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class IndexNode extends IntNode {
    }

    @Builtin(name = SpecialMethodNames.__FLOAT__, fixedNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class FloatNode extends PythonBuiltinNode {
        @Specialization
        double doBoolean(boolean self) {
            return self ? 1.0 : 0.0;
        }

        @Specialization
        double doInt(int self) {
            return self;
        }

        @Specialization
        double doLong(long self) {
            return self;
        }

        @Specialization
        double doPInt(PInt self) {
            return self.doubleValue();
        }

        @Fallback
        PNotImplemented doGeneric(@SuppressWarnings("unused") Object self) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }
}
