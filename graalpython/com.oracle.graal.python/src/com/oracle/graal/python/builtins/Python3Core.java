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
package com.oracle.graal.python.builtins;

import static com.oracle.graal.python.nodes.BuiltinNames.__BUILTINS_PATCHES__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__PACKAGE__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.SyntaxError;

import java.io.IOException;
import java.math.BigInteger;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.ServiceLoader;
import java.util.function.Supplier;
import java.util.logging.Level;

import org.graalvm.nativeimage.ImageInfo;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.modules.ArrayModuleBuiltins;
import com.oracle.graal.python.builtins.modules.AstModuleBuiltins;
import com.oracle.graal.python.builtins.modules.AtexitModuleBuiltins;
import com.oracle.graal.python.builtins.modules.BinasciiModuleBuiltins;
import com.oracle.graal.python.builtins.modules.BuiltinConstructors;
import com.oracle.graal.python.builtins.modules.BuiltinFunctions;
import com.oracle.graal.python.builtins.modules.CodecsModuleBuiltins;
import com.oracle.graal.python.builtins.modules.CollectionsModuleBuiltins;
import com.oracle.graal.python.builtins.modules.CtypesModuleBuiltins;
import com.oracle.graal.python.builtins.modules.ErrnoModuleBuiltins;
import com.oracle.graal.python.builtins.modules.FaulthandlerModuleBuiltins;
import com.oracle.graal.python.builtins.modules.FunctoolsModuleBuiltins;
import com.oracle.graal.python.builtins.modules.GcModuleBuiltins;
import com.oracle.graal.python.builtins.modules.IOModuleBuiltins;
import com.oracle.graal.python.builtins.modules.ImpModuleBuiltins;
import com.oracle.graal.python.builtins.modules.InteropModuleBuiltins;
import com.oracle.graal.python.builtins.modules.ItertoolsModuleBuiltins;
import com.oracle.graal.python.builtins.modules.JavaModuleBuiltins;
import com.oracle.graal.python.builtins.modules.LocaleModuleBuiltins;
import com.oracle.graal.python.builtins.modules.MarshalModuleBuiltins;
import com.oracle.graal.python.builtins.modules.MathModuleBuiltins;
import com.oracle.graal.python.builtins.modules.PosixModuleBuiltins;
import com.oracle.graal.python.builtins.modules.PosixSubprocessModuleBuiltins;
import com.oracle.graal.python.builtins.modules.PyExpatModuleBuiltins;
import com.oracle.graal.python.builtins.modules.RandomModuleBuiltins;
import com.oracle.graal.python.builtins.modules.ReadlineModuleBuiltins;
import com.oracle.graal.python.builtins.modules.SREModuleBuiltins;
import com.oracle.graal.python.builtins.modules.SelectModuleBuiltins;
import com.oracle.graal.python.builtins.modules.SignalModuleBuiltins;
import com.oracle.graal.python.builtins.modules.SocketModuleBuiltins;
import com.oracle.graal.python.builtins.modules.StringModuleBuiltins;
import com.oracle.graal.python.builtins.modules.SysConfigModuleBuiltins;
import com.oracle.graal.python.builtins.modules.SysModuleBuiltins;
import com.oracle.graal.python.builtins.modules.ThreadModuleBuiltins;
import com.oracle.graal.python.builtins.modules.TimeModuleBuiltins;
import com.oracle.graal.python.builtins.modules.TruffleCextBuiltins;
import com.oracle.graal.python.builtins.modules.UnicodeDataModuleBuiltins;
import com.oracle.graal.python.builtins.modules.WeakRefModuleBuiltins;
import com.oracle.graal.python.builtins.modules.ZipImportModuleBuiltins;
import com.oracle.graal.python.builtins.modules.ZLibModuleBuiltins;
import com.oracle.graal.python.builtins.objects.array.ArrayBuiltins;
import com.oracle.graal.python.builtins.objects.bool.BoolBuiltins;
import com.oracle.graal.python.builtins.objects.bytes.ByteArrayBuiltins;
import com.oracle.graal.python.builtins.objects.bytes.BytesBuiltins;
import com.oracle.graal.python.builtins.objects.cell.CellBuiltins;
import com.oracle.graal.python.builtins.objects.code.CodeBuiltins;
import com.oracle.graal.python.builtins.objects.code.PCode;
import com.oracle.graal.python.builtins.objects.complex.ComplexBuiltins;
import com.oracle.graal.python.builtins.objects.dict.DictBuiltins;
import com.oracle.graal.python.builtins.objects.dict.DictItemsIteratorBuiltins;
import com.oracle.graal.python.builtins.objects.dict.DictKeysIteratorBuiltins;
import com.oracle.graal.python.builtins.objects.dict.DictValuesBuiltins;
import com.oracle.graal.python.builtins.objects.dict.DictValuesIteratorBuiltins;
import com.oracle.graal.python.builtins.objects.dict.DictViewBuiltins;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.enumerate.EnumerateBuiltins;
import com.oracle.graal.python.builtins.objects.exception.BaseExceptionBuiltins;
import com.oracle.graal.python.builtins.objects.exception.PBaseException;
import com.oracle.graal.python.builtins.objects.floats.FloatBuiltins;
import com.oracle.graal.python.builtins.objects.foreign.TruffleObjectBuiltins;
import com.oracle.graal.python.builtins.objects.frame.FrameBuiltins;
import com.oracle.graal.python.builtins.objects.function.AbstractFunctionBuiltins;
import com.oracle.graal.python.builtins.objects.function.BuiltinFunctionBuiltins;
import com.oracle.graal.python.builtins.objects.function.FunctionBuiltins;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.generator.GeneratorBuiltins;
import com.oracle.graal.python.builtins.objects.getsetdescriptor.GetSetDescriptorTypeBuiltins;
import com.oracle.graal.python.builtins.objects.ints.IntBuiltins;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.iterator.ForeignIteratorBuiltins;
import com.oracle.graal.python.builtins.objects.iterator.IteratorBuiltins;
import com.oracle.graal.python.builtins.objects.iterator.PZipBuiltins;
import com.oracle.graal.python.builtins.objects.iterator.SentinelIteratorBuiltins;
import com.oracle.graal.python.builtins.objects.list.ListBuiltins;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.mappingproxy.MappingproxyBuiltins;
import com.oracle.graal.python.builtins.objects.memoryview.BufferBuiltins;
import com.oracle.graal.python.builtins.objects.memoryview.MemoryviewBuiltins;
import com.oracle.graal.python.builtins.objects.method.AbstractMethodBuiltins;
import com.oracle.graal.python.builtins.objects.method.BuiltinMethodBuiltins;
import com.oracle.graal.python.builtins.objects.method.ClassmethodBuiltins;
import com.oracle.graal.python.builtins.objects.method.DecoratedMethodBuiltins;
import com.oracle.graal.python.builtins.objects.method.MethodBuiltins;
import com.oracle.graal.python.builtins.objects.method.StaticmethodBuiltins;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.object.ObjectBuiltins;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.random.RandomBuiltins;
import com.oracle.graal.python.builtins.objects.range.RangeBuiltins;
import com.oracle.graal.python.builtins.objects.referencetype.ReferenceTypeBuiltins;
import com.oracle.graal.python.builtins.objects.reversed.ReversedBuiltins;
import com.oracle.graal.python.builtins.objects.set.FrozenSetBuiltins;
import com.oracle.graal.python.builtins.objects.set.SetBuiltins;
import com.oracle.graal.python.builtins.objects.slice.SliceBuiltins;
import com.oracle.graal.python.builtins.objects.str.StringBuiltins;
import com.oracle.graal.python.builtins.objects.superobject.SuperBuiltins;
import com.oracle.graal.python.builtins.objects.thread.LockBuiltins;
import com.oracle.graal.python.builtins.objects.thread.RLockBuiltins;
import com.oracle.graal.python.builtins.objects.thread.ThreadBuiltins;
import com.oracle.graal.python.builtins.objects.traceback.TracebackBuiltins;
import com.oracle.graal.python.builtins.objects.tuple.TupleBuiltins;
import com.oracle.graal.python.builtins.objects.type.PythonBuiltinClass;
import com.oracle.graal.python.builtins.objects.type.PythonClass;
import com.oracle.graal.python.builtins.objects.type.TypeBuiltins;
import com.oracle.graal.python.builtins.objects.zipimporter.ZipImporterBuiltins;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonCore;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.PythonParser;
import com.oracle.graal.python.runtime.PythonParser.ParserMode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.TruffleOptions;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

/**
 * The core is intended to the immutable part of the interpreter, including most modules and most
 * types.
 */
public final class Python3Core implements PythonCore {
    private final String[] coreFiles;

    private static final String[] initializeCoreFiles() {
        // Order matters!
        List<String> coreFiles = new ArrayList<>(Arrays.asList(
                        "_descriptor",
                        "object",
                        "sys",
                        "dict",
                        "_mappingproxy",
                        "str",
                        "type",
                        "_imp",
                        "function",
                        "_functools",
                        "method",
                        "code",
                        "_warnings",
                        "posix",
                        "_io",
                        "_frozen_importlib",
                        "classes",
                        "_weakref",
                        "set",
                        "itertools",
                        "base_exception",
                        "python_cext",
                        "_collections",
                        "memoryview",
                        "list",
                        "_codecs",
                        "bytes",
                        "bytearray",
                        "time",
                        "unicodedata",
                        "_locale",
                        "_sre",
                        "function",
                        "_sysconfig",
                        "_socket",
                        "_thread",
                        "zipimport"));

        return coreFiles.toArray(new String[coreFiles.size()]);
    }

    private final PythonBuiltins[] builtins;

    private static final PythonBuiltins[] initializeBuiltins() {
        List<PythonBuiltins> builtins = new ArrayList<>(Arrays.asList(
                        new BuiltinConstructors(),
                        new BuiltinFunctions(),
                        new DecoratedMethodBuiltins(),
                        new ClassmethodBuiltins(),
                        new StaticmethodBuiltins(),
                        new InteropModuleBuiltins(),
                        new ObjectBuiltins(),
                        new CellBuiltins(),
                        new BoolBuiltins(),
                        new FloatBuiltins(),
                        new BytesBuiltins(),
                        new ComplexBuiltins(),
                        new ByteArrayBuiltins(),
                        new TypeBuiltins(),
                        new IntBuiltins(),
                        new TruffleObjectBuiltins(),
                        new ListBuiltins(),
                        new DictBuiltins(),
                        new DictViewBuiltins(),
                        new DictValuesBuiltins(),
                        new DictKeysIteratorBuiltins(),
                        new DictValuesIteratorBuiltins(),
                        new DictItemsIteratorBuiltins(),
                        new RangeBuiltins(),
                        new SliceBuiltins(),
                        new TupleBuiltins(),
                        new StringBuiltins(),
                        new SetBuiltins(),
                        new FrozenSetBuiltins(),
                        new IteratorBuiltins(),
                        new ReversedBuiltins(),
                        new PZipBuiltins(),
                        new EnumerateBuiltins(),
                        new SentinelIteratorBuiltins(),
                        new ForeignIteratorBuiltins(),
                        new GeneratorBuiltins(),
                        new AbstractFunctionBuiltins(),
                        new FunctionBuiltins(),
                        new BuiltinFunctionBuiltins(),
                        new AbstractMethodBuiltins(),
                        new MethodBuiltins(),
                        new BuiltinMethodBuiltins(),
                        new CodeBuiltins(),
                        new FrameBuiltins(),
                        new MappingproxyBuiltins(),
                        new GetSetDescriptorTypeBuiltins(),
                        new BaseExceptionBuiltins(),
                        new PosixModuleBuiltins(),
                        new ImpModuleBuiltins(),
                        new ArrayModuleBuiltins(),
                        new ArrayBuiltins(),
                        new TimeModuleBuiltins(),
                        new MathModuleBuiltins(),
                        new MarshalModuleBuiltins(),
                        new RandomModuleBuiltins(),
                        new RandomBuiltins(),
                        new TruffleCextBuiltins(),
                        new WeakRefModuleBuiltins(),
                        new ReferenceTypeBuiltins(),
                        new IOModuleBuiltins(),
                        new StringModuleBuiltins(),
                        new ItertoolsModuleBuiltins(),
                        new FunctoolsModuleBuiltins(),
                        new ErrnoModuleBuiltins(),
                        new CodecsModuleBuiltins(),
                        new CollectionsModuleBuiltins(),
                        new JavaModuleBuiltins(),
                        new SREModuleBuiltins(),
                        new AstModuleBuiltins(),
                        new SelectModuleBuiltins(),
                        new SocketModuleBuiltins(),
                        new SignalModuleBuiltins(),
                        new TracebackBuiltins(),
                        new GcModuleBuiltins(),
                        new AtexitModuleBuiltins(),
                        new FaulthandlerModuleBuiltins(),
                        new UnicodeDataModuleBuiltins(),
                        new LocaleModuleBuiltins(),
                        new SysModuleBuiltins(),
                        new BufferBuiltins(),
                        new MemoryviewBuiltins(),
                        new SuperBuiltins(),
                        new BinasciiModuleBuiltins(),
                        new PosixSubprocessModuleBuiltins(),
                        new CtypesModuleBuiltins(),
                        new ReadlineModuleBuiltins(),
                        new PyExpatModuleBuiltins(),
                        new SysConfigModuleBuiltins(),
                        new ZipImporterBuiltins(),
                        new ZipImportModuleBuiltins(),
                        new ZLibModuleBuiltins()));
        if (!TruffleOptions.AOT) {
            ServiceLoader<PythonBuiltins> providers = ServiceLoader.load(PythonBuiltins.class);
            for (PythonBuiltins builtin : providers) {
                builtins.add(builtin);
            }
        }
        // threads
        if (PythonLanguage.WITH_THREADS) {
            builtins.addAll(new ArrayList<>(Arrays.asList(
                            new ThreadModuleBuiltins(),
                            new ThreadBuiltins(),
                            new LockBuiltins(),
                            new RLockBuiltins())));
        }
        return builtins.toArray(new PythonBuiltins[builtins.size()]);
    }

    // not using EnumMap, HashMap, etc. to allow this to fold away during partial evaluation
    @CompilationFinal(dimensions = 1) private final PythonBuiltinClass[] builtinTypes = new PythonBuiltinClass[PythonBuiltinClassType.VALUES.length];

    private final Map<String, PythonModule> builtinModules = new HashMap<>();
    @CompilationFinal private PythonModule builtinsModule;

    @CompilationFinal private PInt pyTrue;
    @CompilationFinal private PInt pyFalse;

    private final PythonParser parser;

    @CompilationFinal private PythonContext singletonContext;

    /*
     * This field cannot be made CompilationFinal since code might get compiled during context
     * initialization.
     */
    private boolean initialized;

    private final PythonObjectFactory factory = PythonObjectFactory.create();

    public Python3Core(PythonParser parser) {
        this.parser = parser;
        this.builtins = initializeBuiltins();
        this.coreFiles = initializeCoreFiles();
    }

    @Override
    public PythonLanguage getLanguage() {
        return singletonContext.getLanguage();
    }

    @Override
    public PythonContext getContext() {
        return singletonContext;
    }

    @Override
    public PythonParser getParser() {
        return parser;
    }

    @Override
    public boolean isInitialized() {
        return initialized;
    }

    public void initialize(PythonContext context) {
        singletonContext = context;
        initializeJavaCore();
        initializeSysModule();
        initializePythonCore();
        initialized = true;
    }

    private void initializeJavaCore() {
        initializeTypes();
        populateBuiltins();
        publishBuiltinModules();
        builtinsModule = builtinModules.get("builtins");
    }

    private void initializePythonCore() {
        String coreHome = PythonCore.getCoreHomeOrFail();
        loadFile("builtins", coreHome);
        for (String s : coreFiles) {
            loadFile(s, coreHome);
        }
        exportCInterface(getContext());
        initialized = true;
    }

    @Override
    public void postInitialize() {
        if (!TruffleOptions.AOT || ImageInfo.inImageRuntimeCode()) {
            initialized = false;

            loadFile(__BUILTINS_PATCHES__, PythonCore.getCoreHomeOrFail());

            PythonModule os = lookupBuiltinModule("posix");
            Object environAttr = os.getAttribute("environ");
            ((PDict) environAttr).setDictStorage(createEnvironDict().getDictStorage());

            initialized = true;
        }
    }

    public PythonModule initializeSysModule() {
        PythonModule sys = builtinModules.get("sys");
        String[] args = getContext().getEnv().getApplicationArguments();
        sys.setAttribute("argv", factory().createList(Arrays.copyOf(args, args.length, Object[].class)));
        String prefix = PythonCore.getSysPrefix(getContext().getEnv());
        for (String name : SysModuleBuiltins.SYS_PREFIX_ATTRIBUTES) {
            sys.setAttribute(name, prefix);
        }
        initializeSysPath(sys, args);
        return sys;
    }

    private void initializeSysPath(PythonModule sys, String[] args) {
        Env env = getContext().getEnv();
        String option = PythonOptions.getOption(getContext(), PythonOptions.PythonPath);
        Object[] path;
        int pathIdx = 0;
        if (option.length() > 0) {
            String[] split = option.split(PythonCore.PATH_SEPARATOR);
            path = new Object[split.length + 3];
            System.arraycopy(split, 0, path, 0, split.length);
            pathIdx = split.length;
        } else {
            path = new Object[3];
        }
        path[pathIdx] = getScriptPath(env, args);
        path[pathIdx + 1] = PythonCore.getStdlibHome(env);
        path[pathIdx + 2] = PythonCore.getCoreHome(env) + PythonCore.FILE_SEPARATOR + "modules";
        PList sysPaths = factory().createList(path);
        sys.setAttribute("path", sysPaths);
        // sysPaths.append(getPythonLibraryExtrasPath());
    }

    private static String getScriptPath(Env env, String[] args) {
        String scriptPath;
        if (args.length > 0) {
            String argv0 = args[0];
            if (argv0 != null && !argv0.startsWith("-") && !argv0.isEmpty()) {
                TruffleFile scriptFile = env.getTruffleFile(argv0);
                try {
                    scriptPath = scriptFile.getAbsoluteFile().getParent().getPath();
                } catch (SecurityException e) {
                    scriptPath = scriptFile.getParent().getPath();
                }
                if (scriptPath == null) {
                    scriptPath = ".";
                }
            } else {
                scriptPath = "";
            }
        } else {
            scriptPath = "";
        }
        return scriptPath;
    }

    @TruffleBoundary
    public PythonModule lookupBuiltinModule(String name) {
        return builtinModules.get(name);
    }

    public PythonBuiltinClass lookupType(PythonBuiltinClassType type) {
        assert builtinTypes[type.ordinal()] != null;
        return builtinTypes[type.ordinal()];
    }

    @TruffleBoundary
    public String[] builtinModuleNames() {
        return builtinModules.keySet().toArray(new String[0]);
    }

    @Override
    public PythonModule getBuiltins() {
        return builtinsModule;
    }

    @Override
    @TruffleBoundary
    public PException raise(PythonBuiltinClassType type, String format, Object... args) {
        PBaseException instance;
        if (format != null) {
            instance = factory.createBaseException(type, format, args);
        } else {
            instance = factory.createBaseException(type);
        }
        throw PException.fromObject(instance, null);
    }

    private void publishBuiltinModules() {
        PythonModule sysModule = builtinModules.get("sys");
        PDict sysModules = (PDict) sysModule.getAttribute("modules");
        for (Entry<String, PythonModule> entry : builtinModules.entrySet()) {
            sysModules.setItem(entry.getKey(), entry.getValue());
        }
    }

    public void exportCInterface(PythonContext context) {
        Env env = context.getEnv();
        if (env != null) {
            env.exportSymbol("python_cext", builtinModules.get("python_cext"));
            env.exportSymbol("python_builtins", builtinsModule);

            // export all exception classes for the C API
            for (PythonBuiltinClassType errorType : PythonBuiltinClassType.EXCEPTIONS) {
                PythonClass errorClass = lookupType(errorType);
                env.exportSymbol("python_" + errorClass.getName(), errorClass);
            }
        }
    }

    private PythonClass initializeBuiltinClass(PythonBuiltinClassType type) {
        int index = type.ordinal();
        if (builtinTypes[index] == null) {
            if (type.getBase() == type) {
                // object case
                builtinTypes[index] = new PythonBuiltinClass(type, null);
            } else {
                builtinTypes[index] = new PythonBuiltinClass(type, initializeBuiltinClass(type.getBase()));
            }
        }
        return builtinTypes[index];
    }

    private void initializeTypes() {
        // create class objects for builtin types
        for (PythonBuiltinClassType builtinClass : PythonBuiltinClassType.VALUES) {
            initializeBuiltinClass(builtinClass);
        }
        // n.b.: the builtin modules and classes and their constructors are initialized first here,
        // so we have the mapping from java classes to python classes and builtin names to modules
        // available.
        for (PythonBuiltins builtin : builtins) {
            CoreFunctions annotation = builtin.getClass().getAnnotation(CoreFunctions.class);
            if (annotation.defineModule().length() > 0) {
                createModule(annotation.defineModule());
            }
        }
        // publish builtin types in the corresponding modules
        for (PythonBuiltinClassType builtinClass : PythonBuiltinClassType.VALUES) {
            String module = builtinClass.getPublicInModule();
            if (module != null) {
                PythonModule pythonModule = lookupBuiltinModule(module);
                if (pythonModule != null) {
                    pythonModule.setAttribute(builtinClass.getName(), lookupType(builtinClass));
                }
            }
        }
        // now initialize well-known objects
        pyTrue = new PInt(lookupType(PythonBuiltinClassType.Boolean), BigInteger.ONE);
        pyFalse = new PInt(lookupType(PythonBuiltinClassType.Boolean), BigInteger.ZERO);
    }

    private void populateBuiltins() {
        for (PythonBuiltins builtin : builtins) {
            builtin.initialize(this);
            CoreFunctions annotation = builtin.getClass().getAnnotation(CoreFunctions.class);
            if (annotation.defineModule().length() > 0) {
                addBuiltinsTo(builtinModules.get(annotation.defineModule()), builtin);
            }
            for (PythonBuiltinClassType klass : annotation.extendClasses()) {
                addBuiltinsTo(lookupType(klass), builtin);
            }
        }

        // core machinery
        createModule("_descriptor");
        createModule("_warnings");
        PythonModule bootstrapExternal = createModule("importlib._bootstrap_external");
        bootstrapExternal.setAttribute(__PACKAGE__, "importlib");
        builtinModules.put("_frozen_importlib_external", bootstrapExternal);
        PythonModule bootstrap = createModule("importlib._bootstrap");
        bootstrap.setAttribute(__PACKAGE__, "importlib");
        builtinModules.put("_frozen_importlib", bootstrap);
    }

    private PythonModule createModule(String name) {
        PythonModule mod = builtinModules.get(name);
        if (mod == null) {
            mod = factory().createPythonModule(name);
            builtinModules.put(name, mod);
        }
        return mod;
    }

    private void addBuiltinsTo(PythonObject obj, PythonBuiltins builtinsForObj) {
        Map<String, Object> builtinConstants = builtinsForObj.getBuiltinConstants();
        for (Map.Entry<String, Object> entry : builtinConstants.entrySet()) {
            String constantName = entry.getKey();
            obj.setAttribute(constantName, entry.getValue());
        }

        Map<String, BoundBuiltinCallable<?>> builtinFunctions = builtinsForObj.getBuiltinFunctions();
        for (Entry<String, BoundBuiltinCallable<?>> entry : builtinFunctions.entrySet()) {
            String methodName = entry.getKey();
            Object value;
            assert obj instanceof PythonModule || obj instanceof PythonBuiltinClass : "unexpected object while adding builtins";
            if (obj instanceof PythonModule) {
                value = factory.createBuiltinMethod(obj, (PBuiltinFunction) entry.getValue());
            } else {
                value = entry.getValue().boundToObject(((PythonBuiltinClass) obj).getType(), factory());
            }
            obj.setAttribute(methodName, value);
        }

        Map<PythonBuiltinClass, Entry<PythonBuiltinClassType[], Boolean>> builtinClasses = builtinsForObj.getBuiltinClasses();
        for (Entry<PythonBuiltinClass, Entry<PythonBuiltinClassType[], Boolean>> entry : builtinClasses.entrySet()) {
            boolean isPublic = entry.getValue().getValue();
            if (isPublic) {
                PythonBuiltinClass pythonClass = entry.getKey();
                obj.setAttribute(pythonClass.getName(), pythonClass);
            }
        }
    }

    @TruffleBoundary
    private Source getSource(String basename, String prefix) {
        URL url = null;
        try {
            url = new URL(prefix);
        } catch (MalformedURLException e) {
            // pass
        }
        String suffix = FILE_SEPARATOR + basename + ".py";
        PythonContext ctxt = getContext();
        if (url != null) {
            // This path is hit when we load the core library e.g. from a Jar file
            try {
                return getLanguage().newSource(ctxt, new URL(url + suffix), basename);
            } catch (IOException e) {
                throw new RuntimeException("Could not read core library from " + url);
            }
        } else {
            Env env = ctxt.getEnv();
            TruffleFile file = env.getTruffleFile(prefix + suffix);
            try {
                if (file.exists()) {
                    return getLanguage().newSource(ctxt, file, basename);
                }
            } catch (SecurityException | IOException t) {
                // fall through;
            }
            PythonLanguage.getLogger().log(Level.SEVERE, "Startup failed, could not read core library from " + file + ". Maybe you need to set python.CoreHome and python.StdLibHome.");
            throw new RuntimeException();
        }
    }

    private void loadFile(String s, String prefix) {
        Source source = getSource(s, prefix);
        Supplier<PCode> getCode = () -> factory.createCode((RootNode) getParser().parse(ParserMode.File, this, source, null));
        RootCallTarget callTarget = getLanguage().cacheCode(source.getName(), getCode).getRootCallTarget();
        PythonModule mod = lookupBuiltinModule(s);
        if (mod == null) {
            // use an anonymous module for the side-effects
            mod = factory().createPythonModule("__anonymous__");
        }
        callTarget.call(PArguments.withGlobals(mod));
    }

    @TruffleBoundary
    private PDict createEnvironDict() {
        Map<String, String> getenv = System.getenv();
        PDict environ = factory.createDict();
        for (Entry<String, String> entry : getenv.entrySet()) {
            environ.setItem(factory.createBytes(entry.getKey().getBytes()), factory.createBytes(entry.getValue().getBytes()));
        }
        return environ;
    }

    public PythonObjectFactory factory() {
        return factory;
    }

    public void setContext(PythonContext context) {
        assert singletonContext == null;
        singletonContext = context;
    }

    public PInt getTrue() {
        return pyTrue;
    }

    public PInt getFalse() {
        return pyFalse;
    }

    public RuntimeException raiseInvalidSyntax(Source source, SourceSection section, String message, Object... arguments) {
        Node location = new Node() {
            @Override
            public SourceSection getSourceSection() {
                return section;
            }
        };
        PBaseException instance;
        instance = factory().createBaseException(SyntaxError, message, arguments);
        String path = source.getPath();
        instance.setAttribute("filename", path != null ? path : source.getName() != null ? source.getName() : "<string>");
        instance.setAttribute("text", section.isAvailable() ? source.getCharacters(section.getStartLine()) : "");
        instance.setAttribute("lineno", section.getStartLine());
        instance.setAttribute("offset", section.getStartColumn());
        instance.setAttribute("msg", section.getCharIndex() == source.getLength() ? "unexpected EOF while parsing" : "invalid syntax");
        throw PException.fromObject(instance, location);
    }
}
