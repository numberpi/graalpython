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
package com.oracle.graal.python.runtime.object;

import java.math.BigInteger;
import java.util.Map;
import java.util.Optional;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.array.PArray;
import com.oracle.graal.python.builtins.objects.bytes.PByteArray;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.cell.PCell;
import com.oracle.graal.python.builtins.objects.cext.PythonNativeClass;
import com.oracle.graal.python.builtins.objects.cext.PythonNativeObject;
import com.oracle.graal.python.builtins.objects.cext.PythonNativeVoidPtr;
import com.oracle.graal.python.builtins.objects.code.PCode;
import com.oracle.graal.python.builtins.objects.common.DynamicObjectStorage.FastDictStorage;
import com.oracle.graal.python.builtins.objects.common.DynamicObjectStorage.PythonObjectDictStorage;
import com.oracle.graal.python.builtins.objects.common.HashMapStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.LocalsStorage;
import com.oracle.graal.python.builtins.objects.common.PHashingCollection;
import com.oracle.graal.python.builtins.objects.complex.PComplex;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.dict.PDictView;
import com.oracle.graal.python.builtins.objects.dict.PDictView.PDictItemsView;
import com.oracle.graal.python.builtins.objects.dict.PDictView.PDictKeysView;
import com.oracle.graal.python.builtins.objects.dict.PDictView.PDictValuesView;
import com.oracle.graal.python.builtins.objects.enumerate.PEnumerate;
import com.oracle.graal.python.builtins.objects.exception.PBaseException;
import com.oracle.graal.python.builtins.objects.floats.PFloat;
import com.oracle.graal.python.builtins.objects.frame.PFrame;
import com.oracle.graal.python.builtins.objects.function.Arity;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.function.PFunction;
import com.oracle.graal.python.builtins.objects.function.PGeneratorFunction;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.function.PythonCallable;
import com.oracle.graal.python.builtins.objects.generator.PGenerator;
import com.oracle.graal.python.builtins.objects.getsetdescriptor.GetSetDescriptor;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.iterator.PArrayIterator;
import com.oracle.graal.python.builtins.objects.iterator.PBaseSetIterator;
import com.oracle.graal.python.builtins.objects.iterator.PDoubleSequenceIterator;
import com.oracle.graal.python.builtins.objects.iterator.PForeignArrayIterator;
import com.oracle.graal.python.builtins.objects.iterator.PIntegerIterator;
import com.oracle.graal.python.builtins.objects.iterator.PIntegerSequenceIterator;
import com.oracle.graal.python.builtins.objects.iterator.PLongSequenceIterator;
import com.oracle.graal.python.builtins.objects.iterator.PRangeIterator;
import com.oracle.graal.python.builtins.objects.iterator.PRangeIterator.PRangeReverseIterator;
import com.oracle.graal.python.builtins.objects.iterator.PSentinelIterator;
import com.oracle.graal.python.builtins.objects.iterator.PSequenceIterator;
import com.oracle.graal.python.builtins.objects.iterator.PStringIterator;
import com.oracle.graal.python.builtins.objects.iterator.PZip;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.mappingproxy.PMappingproxy;
import com.oracle.graal.python.builtins.objects.memoryview.PBuffer;
import com.oracle.graal.python.builtins.objects.memoryview.PMemoryView;
import com.oracle.graal.python.builtins.objects.method.PBuiltinMethod;
import com.oracle.graal.python.builtins.objects.method.PDecoratedMethod;
import com.oracle.graal.python.builtins.objects.method.PMethod;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.random.PRandom;
import com.oracle.graal.python.builtins.objects.range.PRange;
import com.oracle.graal.python.builtins.objects.referencetype.PReferenceType;
import com.oracle.graal.python.builtins.objects.reversed.PSequenceReverseIterator;
import com.oracle.graal.python.builtins.objects.reversed.PStringReverseIterator;
import com.oracle.graal.python.builtins.objects.set.PBaseSet;
import com.oracle.graal.python.builtins.objects.set.PFrozenSet;
import com.oracle.graal.python.builtins.objects.set.PSet;
import com.oracle.graal.python.builtins.objects.slice.PSlice;
import com.oracle.graal.python.builtins.objects.socket.PSocket;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.builtins.objects.superobject.SuperObject;
import com.oracle.graal.python.builtins.objects.thread.PLock;
import com.oracle.graal.python.builtins.objects.thread.PRLock;
import com.oracle.graal.python.builtins.objects.thread.PThread;
import com.oracle.graal.python.builtins.objects.traceback.PTraceback;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.LazyPythonClass;
import com.oracle.graal.python.builtins.objects.type.PythonClass;
import com.oracle.graal.python.builtins.objects.zipimporter.PZipImporter;
import com.oracle.graal.python.parser.ExecutionCellSlots;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonCore;
import com.oracle.graal.python.runtime.sequence.storage.ByteSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.CharSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.DoubleSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.IntSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.LongSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorageFactory;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.TruffleLanguage.ContextReference;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.instrumentation.AllocationReporter;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeCost;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.Shape;

public final class PythonObjectFactory extends Node {
    @CompilationFinal private ContextReference<PythonContext> contextRef;
    @CompilationFinal private AllocationReporter allocationReporter;

    private PythonObjectFactory() {
    }

    public static PythonObjectFactory create() {
        return new PythonObjectFactory();
    }

    @SuppressWarnings("static-method")
    public final <T> T trace(T allocatedObject) {
        if (reportAllocations()) {
            CompilerAsserts.partialEvaluationConstant(this);
            allocationReporter.onEnter(null, 0, AllocationReporter.SIZE_UNKNOWN);
            allocationReporter.onReturnValue(allocatedObject, 0, AllocationReporter.SIZE_UNKNOWN);
        }
        return allocatedObject;
    }

    @Override
    public NodeCost getCost() {
        return contextRef == null ? NodeCost.UNINITIALIZED : NodeCost.MONOMORPHIC;
    }

    public PythonCore getCore() {
        return getContextRef().get().getCore();
    }

    private ContextReference<PythonContext> getContextRef() {
        if (contextRef == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            contextRef = PythonLanguage.getContextRef();
            allocationReporter = contextRef.get().getEnv().lookup(AllocationReporter.class);
        }
        return contextRef;
    }

    private boolean reportAllocations() {
        return getContextRef() != null && allocationReporter != null && allocationReporter.isActive();
    }

    /*
     * Python objects
     */

    @CompilationFinal private Optional<Shape> cachedInstanceShape = Optional.empty();

    public PythonObject createPythonObject(PythonClass cls) {
        assert cls != null;
        Optional<Shape> cached = cachedInstanceShape;
        if (cached != null) {
            if (cached.isPresent()) {
                if (cached.get() == cls.getInstanceShape()) {
                    return trace(new PythonObject(cls, cached.get()));
                } else {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    cachedInstanceShape = null;
                }
            } else {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                cachedInstanceShape = Optional.of(cls.getInstanceShape());
            }
        }
        return trace(new PythonObject(cls, cls.getInstanceShape()));
    }

    public PythonNativeObject createNativeObjectWrapper(Object obj) {
        return trace(new PythonNativeObject(obj));
    }

    public PythonNativeVoidPtr createNativeVoidPtr(TruffleObject obj) {
        return trace(new PythonNativeVoidPtr(obj));
    }

    public SuperObject createSuperObject(PythonClass self) {
        return trace(new SuperObject(self));
    }

    /*
     * Primitive types
     */
    public PInt createInt(boolean value) {
        PythonCore core = getCore();
        return value ? core.getTrue() : core.getFalse();
    }

    public PInt createInt(int value) {
        return trace(new PInt(PythonBuiltinClassType.PInt, BigInteger.valueOf(value)));
    }

    public PInt createInt(long value) {
        return trace(new PInt(PythonBuiltinClassType.PInt, BigInteger.valueOf(value)));
    }

    public PInt createInt(BigInteger value) {
        return trace(new PInt(PythonBuiltinClassType.PInt, value));
    }

    public Object createInt(LazyPythonClass cls, int value) {
        return trace(new PInt(cls, BigInteger.valueOf(value)));
    }

    public Object createInt(LazyPythonClass cls, long value) {
        return trace(new PInt(cls, BigInteger.valueOf(value)));
    }

    public PInt createInt(LazyPythonClass cls, BigInteger value) {
        return trace(new PInt(cls, value));
    }

    public PFloat createFloat(double value) {
        return trace(new PFloat(PythonBuiltinClassType.PFloat, value));
    }

    public PFloat createFloat(LazyPythonClass cls, double value) {
        return trace(new PFloat(cls, value));
    }

    public PString createString(String string) {
        return trace(new PString(PythonBuiltinClassType.PString, string));
    }

    public PString createString(LazyPythonClass cls, String string) {
        return trace(new PString(cls, string));
    }

    public PString createString(CharSequence string) {
        return trace(new PString(PythonBuiltinClassType.PString, string));
    }

    public PString createString(LazyPythonClass cls, CharSequence string) {
        return trace(new PString(cls, string));
    }

    public PBytes createBytes(byte[] array) {
        return trace(new PBytes(PythonBuiltinClassType.PBytes, array));
    }

    public PBytes createBytes(LazyPythonClass cls, byte[] array) {
        return trace(new PBytes(cls, array));
    }

    public PBytes createBytes(ByteSequenceStorage storage) {
        return trace(new PBytes(PythonBuiltinClassType.PBytes, storage));
    }

    public PBytes createBytes(LazyPythonClass cls, ByteSequenceStorage storage) {
        return trace(new PBytes(cls, storage));
    }

    public final PTuple createEmptyTuple() {
        return createTuple(new Object[0]);
    }

    public final PTuple createEmptyTuple(LazyPythonClass cls) {
        return trace(new PTuple(cls, new Object[0]));
    }

    public final PTuple createTuple(Object[] objects) {
        return trace(new PTuple(PythonBuiltinClassType.PTuple, objects));
    }

    public final PTuple createTuple(SequenceStorage store) {
        return trace(new PTuple(PythonBuiltinClassType.PTuple, store));
    }

    public final PTuple createTuple(LazyPythonClass cls, Object[] objects) {
        return trace(new PTuple(cls, objects));
    }

    public final PTuple createTuple(LazyPythonClass cls, SequenceStorage store) {
        return trace(new PTuple(cls, store));
    }

    public final PComplex createComplex(LazyPythonClass cls, double real, double imag) {
        return trace(new PComplex(cls, real, imag));
    }

    public final PComplex createComplex(double real, double imag) {
        return createComplex(PythonBuiltinClassType.PComplex, real, imag);
    }

    public PRange createRange(int stop) {
        return trace(new PRange(PythonBuiltinClassType.PRange, stop));
    }

    public PRange createRange(int start, int stop) {
        return trace(new PRange(PythonBuiltinClassType.PRange, start, stop));
    }

    public PRange createRange(int start, int stop, int step) {
        return trace(new PRange(PythonBuiltinClassType.PRange, start, stop, step));
    }

    public PSlice createSlice(int start, int stop, int step) {
        return trace(new PSlice(PythonBuiltinClassType.PSlice, start, stop, step));
    }

    public PRandom createRandom(PythonClass cls) {
        return trace(new PRandom(cls));
    }

    /*
     * Classes, methods and functions
     */

    public PythonModule createPythonModule(String name) {
        return trace(new PythonModule(PythonBuiltinClassType.PythonModule, name));
    }

    public PythonModule createPythonModule(PythonClass cls, String name) {
        return trace(new PythonModule(cls, name));
    }

    public PythonClass createPythonClass(LazyPythonClass metaclass, String name, PythonClass[] bases) {
        return trace(new PythonClass(metaclass, name, PythonLanguage.freshShape(), bases));
    }

    public PythonNativeClass createNativeClassWrapper(Object object, PythonClass metaClass, String name, PythonClass[] pythonClasses) {
        return trace(new PythonNativeClass(object, metaClass, name, pythonClasses));
    }

    public PMemoryView createMemoryView(LazyPythonClass metaclass, Object value) {
        return trace(new PMemoryView(metaclass, value));
    }

    public final PMethod createMethod(LazyPythonClass cls, Object self, PFunction function) {
        return trace(new PMethod(cls, self, function));
    }

    public final PMethod createMethod(Object self, PFunction function) {
        return createMethod(PythonBuiltinClassType.PMethod, self, function);
    }

    public final PMethod createBuiltinMethod(Object self, PFunction function) {
        return createMethod(PythonBuiltinClassType.PBuiltinMethod, self, function);
    }

    public final PBuiltinMethod createBuiltinMethod(LazyPythonClass cls, Object self, PBuiltinFunction function) {
        return trace(new PBuiltinMethod(cls, self, function));
    }

    public final PBuiltinMethod createBuiltinMethod(Object self, PBuiltinFunction function) {
        return createBuiltinMethod(PythonBuiltinClassType.PBuiltinMethod, self, function);
    }

    public PFunction createFunction(String name, String enclosingClassName, Arity arity, RootCallTarget callTarget, PythonObject globals, PCell[] closure) {
        return trace(new PFunction(PythonBuiltinClassType.PFunction, name, enclosingClassName, arity, callTarget, globals, closure));
    }

    public PFunction createFunction(String name, String enclosingClassName, Arity arity, RootCallTarget callTarget, PythonObject globals, Object[] defaults,
                    PCell[] closure) {
        return trace(new PFunction(PythonBuiltinClassType.PFunction, name, enclosingClassName, arity, callTarget, globals, defaults, closure));
    }

    public PBuiltinFunction createBuiltinFunction(String name, LazyPythonClass type, Arity arity, RootCallTarget callTarget) {
        return trace(new PBuiltinFunction(PythonBuiltinClassType.PBuiltinFunction, name, type, arity, callTarget));
    }

    public GetSetDescriptor createGetSetDescriptor(PythonCallable get, PythonCallable set, String name, LazyPythonClass type) {
        return trace(new GetSetDescriptor(PythonBuiltinClassType.GetSetDescriptor, get, set, name, type));
    }

    public PDecoratedMethod createClassmethod(LazyPythonClass cls) {
        return trace(new PDecoratedMethod(cls));
    }

    public PDecoratedMethod createClassmethod(Object callable) {
        return trace(new PDecoratedMethod(PythonBuiltinClassType.PClassmethod, callable));
    }

    public PDecoratedMethod createStaticmethod(LazyPythonClass cls) {
        return trace(new PDecoratedMethod(cls));
    }

    public PDecoratedMethod createStaticmethod(Object callable) {
        return trace(new PDecoratedMethod(PythonBuiltinClassType.PStaticmethod, callable));
    }

    /*
     * Lists, sets and dicts
     */

    @CompilationFinal private SequenceStorageFactory sequenceStorageFactory;

    public PList createList() {
        return createList(new Object[0]);
    }

    public PList createList(SequenceStorage storage) {
        return createList(PythonBuiltinClassType.PList, storage);
    }

    public PList createList(LazyPythonClass cls, SequenceStorage storage) {
        return trace(new PList(cls, storage));
    }

    public PList createList(LazyPythonClass cls) {
        return createList(cls, new Object[0]);
    }

    public PList createList(Object[] array) {
        return createList(PythonBuiltinClassType.PList, array);
    }

    public PList createList(LazyPythonClass cls, Object[] array) {
        if (sequenceStorageFactory == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            sequenceStorageFactory = new SequenceStorageFactory();
        }
        return trace(new PList(cls, sequenceStorageFactory.createStorage(array)));
    }

    public PSet createSet() {
        return trace(new PSet(PythonBuiltinClassType.PSet));
    }

    public PSet createSet(LazyPythonClass cls) {
        return trace(new PSet(cls));
    }

    public PSet createSet(PythonClass cls, HashingStorage storage) {
        return trace(new PSet(cls, storage));
    }

    public PSet createSet(HashingStorage storage) {
        return trace(new PSet(PythonBuiltinClassType.PSet, storage));
    }

    public PFrozenSet createFrozenSet(PythonClass cls) {
        return trace(new PFrozenSet(cls));
    }

    public PFrozenSet createFrozenSet(PythonClass cls, HashingStorage storage) {
        return trace(new PFrozenSet(cls, storage));
    }

    public PFrozenSet createFrozenSet(HashingStorage storage) {
        return trace(new PFrozenSet(PythonBuiltinClassType.PFrozenSet, storage));
    }

    public PDict createDict() {
        return trace(new PDict(PythonBuiltinClassType.PDict));
    }

    public PDict createDict(PKeyword[] keywords) {
        return trace(new PDict(PythonBuiltinClassType.PDict, keywords));
    }

    public PDict createDict(PythonClass cls) {
        return trace(new PDict(cls));
    }

    public PDict createDict(Map<? extends Object, ? extends Object> map) {
        return createDict(new HashMapStorage(map));
    }

    public PDict createDictLocals(Frame frame, boolean skipCells) {
        return createDict(new LocalsStorage(frame, skipCells));
    }

    public PDict createDict(DynamicObject dynamicObject) {
        return createDict(new FastDictStorage(dynamicObject));
    }

    public PDict createDictFixedStorage(PythonObject pythonObject) {
        return createDict(new PythonObjectDictStorage(pythonObject.getStorage(), pythonObject.getDictUnsetOrSameAsStorageAssumption()));
    }

    public PDict createDict(HashingStorage storage) {
        return trace(new PDict(PythonBuiltinClassType.PDict, storage));
    }

    public PDictView createDictKeysView(PHashingCollection dict) {
        return trace(new PDictKeysView(PythonBuiltinClassType.PDictKeysView, dict));
    }

    public PDictView createDictValuesView(PHashingCollection dict) {
        return trace(new PDictValuesView(PythonBuiltinClassType.PDictValuesView, dict));
    }

    public PDictView createDictItemsView(PHashingCollection dict) {
        return trace(new PDictItemsView(PythonBuiltinClassType.PDictItemsView, dict));
    }

    /*
     * Special objects: generators, proxies, references
     */

    public PGenerator createGenerator(String name, RootCallTarget callTarget, FrameDescriptor frameDescriptor, Object[] arguments, PCell[] closure, ExecutionCellSlots cellSlots, int numOfActiveFlags,
                    int numOfGeneratorBlockNode, int numOfGeneratorForNode) {
        return trace(PGenerator.create(PythonBuiltinClassType.PGenerator, name, callTarget, frameDescriptor, arguments, closure, cellSlots, numOfActiveFlags, numOfGeneratorBlockNode,
                        numOfGeneratorForNode));
    }

    public PGeneratorFunction createGeneratorFunction(String name, String enclosingClassName, Arity arity, RootCallTarget callTarget, PythonObject globals, PCell[] closure) {
        return trace(PGeneratorFunction.create(PythonBuiltinClassType.PFunction, name, enclosingClassName, arity, callTarget, globals, closure));
    }

    public PMappingproxy createMappingproxy(PythonObject object) {
        return trace(new PMappingproxy(PythonBuiltinClassType.PMappingproxy, new PythonObjectDictStorage(object.getStorage(), object.getDictUnsetOrSameAsStorageAssumption())));
    }

    public PMappingproxy createMappingproxy(HashingStorage storage) {
        return trace(new PMappingproxy(PythonBuiltinClassType.PMappingproxy, storage));
    }

    public PMappingproxy createMappingproxy(PythonClass cls, PythonObject object) {
        return trace(new PMappingproxy(cls, new PythonObjectDictStorage(object.getStorage(), object.getDictUnsetOrSameAsStorageAssumption())));
    }

    public PMappingproxy createMappingproxy(LazyPythonClass cls, HashingStorage storage) {
        return trace(new PMappingproxy(cls, storage));
    }

    public PReferenceType createReferenceType(LazyPythonClass cls, PythonObject object, PFunction callback) {
        return trace(new PReferenceType(cls, object, callback));
    }

    public PReferenceType createReferenceType(PythonObject object, PFunction callback) {
        return createReferenceType(PythonBuiltinClassType.PReferenceType, object, callback);
    }

    /*
     * Frames, traces and exceptions
     */

    public PFrame createPFrame(Object locals) {
        return trace(new PFrame(PythonBuiltinClassType.PFrame, locals));
    }

    public PFrame createPFrame(Frame frame) {
        return trace(new PFrame(PythonBuiltinClassType.PFrame, frame));
    }

    public PFrame createPFrame(Frame frame, Object locals) {
        return trace(new PFrame(PythonBuiltinClassType.PFrame, frame, locals));
    }

    public PFrame createPFrame(PBaseException exception, int index) {
        return trace(new PFrame(PythonBuiltinClassType.PFrame, exception, index));
    }

    public PFrame createPFrame(Object threadState, PCode code, PythonObject globals, Object locals) {
        return trace(new PFrame(PythonBuiltinClassType.PFrame, threadState, code, globals, locals));
    }

    public PTraceback createTraceback(PBaseException exception, int index) {
        return trace(new PTraceback(PythonBuiltinClassType.PTraceback, exception, index));
    }

    public PBaseException createBaseException(LazyPythonClass cls, PTuple args) {
        return trace(new PBaseException(cls, args));
    }

    public PBaseException createBaseException(LazyPythonClass cls, String format, Object[] args) {
        assert format != null;
        return trace(new PBaseException(cls, format, args));
    }

    public PBaseException createBaseException(LazyPythonClass cls) {
        return trace(new PBaseException(cls, createEmptyTuple()));
    }

    /*
     * Arrays
     */

    public PArray createArray(PythonClass cls, byte[] array) {
        return trace(new PArray(cls, new ByteSequenceStorage(array)));
    }

    public PArray createArray(PythonClass cls, int[] array) {
        return trace(new PArray(cls, new IntSequenceStorage(array)));
    }

    public PArray createArray(PythonClass cls, double[] array) {
        return trace(new PArray(cls, new DoubleSequenceStorage(array)));
    }

    public PArray createArray(PythonClass cls, char[] array) {
        return trace(new PArray(cls, new CharSequenceStorage(array)));
    }

    public PArray createArray(PythonClass cls, long[] array) {
        return trace(new PArray(cls, new LongSequenceStorage(array)));
    }

    public PArray createArray(PythonClass cls, SequenceStorage store) {
        return trace(new PArray(cls, store));
    }

    public PByteArray createByteArray(LazyPythonClass cls, byte[] array) {
        return trace(new PByteArray(cls, array));
    }

    public PByteArray createByteArray(SequenceStorage storage) {
        return createByteArray(PythonBuiltinClassType.PByteArray, storage);
    }

    public PByteArray createByteArray(LazyPythonClass cls, SequenceStorage storage) {
        return trace(new PByteArray(cls, storage));
    }

    public PArray createArray(byte[] array) {
        return trace(new PArray(PythonBuiltinClassType.PArray, new ByteSequenceStorage(array)));
    }

    public PArray createArray(int[] array) {
        return trace(new PArray(PythonBuiltinClassType.PArray, new IntSequenceStorage(array)));
    }

    public PArray createArray(double[] array) {
        return trace(new PArray(PythonBuiltinClassType.PArray, new DoubleSequenceStorage(array)));
    }

    public PArray createArray(char[] array) {
        return trace(new PArray(PythonBuiltinClassType.PArray, new CharSequenceStorage(array)));
    }

    public PArray createArray(long[] array) {
        return trace(new PArray(PythonBuiltinClassType.PArray, new LongSequenceStorage(array)));
    }

    public PArray createArray(SequenceStorage store) {
        return trace(new PArray(PythonBuiltinClassType.PArray, store));
    }

    public PByteArray createByteArray(byte[] array) {
        return trace(new PByteArray(PythonBuiltinClassType.PByteArray, array));
    }

    /*
     * Iterators
     */

    public PStringIterator createStringIterator(String str) {
        return trace(new PStringIterator(PythonBuiltinClassType.PIterator, str));
    }

    public PStringReverseIterator createStringReverseIterator(PythonClass cls, String str) {
        return trace(new PStringReverseIterator(cls, str));
    }

    public PIntegerSequenceIterator createIntegerSequenceIterator(IntSequenceStorage storage) {
        return trace(new PIntegerSequenceIterator(PythonBuiltinClassType.PIterator, storage));
    }

    public PLongSequenceIterator createLongSequenceIterator(LongSequenceStorage storage) {
        return trace(new PLongSequenceIterator(PythonBuiltinClassType.PIterator, storage));
    }

    public PDoubleSequenceIterator createDoubleSequenceIterator(DoubleSequenceStorage storage) {
        return trace(new PDoubleSequenceIterator(PythonBuiltinClassType.PIterator, storage));
    }

    public PSequenceIterator createSequenceIterator(Object sequence) {
        return trace(new PSequenceIterator(PythonBuiltinClassType.PIterator, sequence));
    }

    public PSequenceReverseIterator createSequenceReverseIterator(PythonClass cls, Object sequence, int lengthHint) {
        return trace(new PSequenceReverseIterator(cls, sequence, lengthHint));
    }

    public PIntegerIterator createRangeIterator(int start, int stop, int step) {
        PIntegerIterator object;
        if (step > 0) {
            object = new PRangeIterator(PythonBuiltinClassType.PIterator, start, stop, step);
        } else {
            object = new PRangeReverseIterator(PythonBuiltinClassType.PIterator, start, stop, -step);
        }
        return trace(object);
    }

    public PArrayIterator createArrayIterator(PArray array) {
        return trace(new PArrayIterator(PythonBuiltinClassType.PArrayIterator, array));
    }

    public PBaseSetIterator createBaseSetIterator(PBaseSet set) {
        return trace(new PBaseSetIterator(PythonBuiltinClassType.PIterator, set));
    }

    public PDictView.PDictItemsIterator createDictItemsIterator(PHashingCollection dict) {
        return trace(new PDictView.PDictItemsIterator(PythonBuiltinClassType.PDictItemsIterator, dict));
    }

    public PDictView.PDictKeysIterator createDictKeysIterator(PHashingCollection dict) {
        return trace(new PDictView.PDictKeysIterator(PythonBuiltinClassType.PDictKeysIterator, dict));
    }

    public PDictView.PDictValuesIterator createDictValuesIterator(PHashingCollection dict) {
        return trace(new PDictView.PDictValuesIterator(PythonBuiltinClassType.PDictValuesIterator, dict));
    }

    public Object createSentinelIterator(Object callable, Object sentinel) {
        return trace(new PSentinelIterator(PythonBuiltinClassType.PSentinelIterator, callable, sentinel));
    }

    public PEnumerate createEnumerate(PythonClass cls, Object iterator, long start) {
        return trace(new PEnumerate(cls, iterator, start));
    }

    public PZip createZip(PythonClass cls, Object[] iterables) {
        return trace(new PZip(cls, iterables));
    }

    public PForeignArrayIterator createForeignArrayIterator(TruffleObject iterable, int size) {
        return trace(new PForeignArrayIterator(PythonBuiltinClassType.PForeignArrayIterator, iterable, size));
    }

    public PBuffer createBuffer(PythonClass cls, Object iterable, boolean readonly) {
        return trace(new PBuffer(cls, iterable, readonly));
    }

    public PBuffer createBuffer(Object iterable, boolean readonly) {
        return trace(new PBuffer(PythonBuiltinClassType.PBuffer, iterable, readonly));
    }

    public PCode createCode(RootNode result) {
        return trace(new PCode(PythonBuiltinClassType.PCode, result, getCore()));
    }

    public PCode createCode(PythonClass cls, int argcount, int kwonlyargcount,
                    int nlocals, int stacksize, int flags,
                    byte[] codestring, Object[] constants, Object[] names,
                    Object[] varnames, Object[] freevars, Object[] cellvars,
                    String filename, String name, int firstlineno,
                    byte[] lnotab) {
        return trace(new PCode(cls, argcount, kwonlyargcount,
                        nlocals, stacksize, flags,
                        codestring, constants, names,
                        varnames, freevars, cellvars,
                        filename, name, firstlineno, lnotab));
    }

    public PZipImporter createZipImporter(LazyPythonClass cls, PDict zipDirectoryCache) {
        return trace(new PZipImporter(cls, zipDirectoryCache));
    }

    /*
     * Socket
     */

    public PSocket createSocket(int family, int type, int proto) {
        return trace(new PSocket(PythonBuiltinClassType.PSocket, family, type, proto));
    }

    public PSocket createSocket(PythonClass cls, int family, int type, int proto) {
        return trace(new PSocket(cls, family, type, proto));
    }

    /*
     * Threading
     */

    public PLock createLock() {
        return trace(new PLock(PythonBuiltinClassType.PLock));
    }

    public PLock createLock(PythonClass cls) {
        return trace(new PLock(cls));
    }

    public PRLock createRLock() {
        return trace(new PRLock(PythonBuiltinClassType.PRLock));
    }

    public PRLock createRLock(PythonClass cls) {
        return trace(new PRLock(cls));
    }

    public PThread createPythonThread(Thread thread) {
        return trace(new PThread(PythonBuiltinClassType.PThread, thread));
    }

    public PThread createPythonThread(PythonClass cls, Thread thread) {
        return trace(new PThread(cls, thread));
    }
}
