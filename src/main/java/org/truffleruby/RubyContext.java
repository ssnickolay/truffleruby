/*
 * Copyright (c) 2013, 2019 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby;

import java.io.File;
import java.nio.ByteBuffer;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;

import org.graalvm.options.OptionDescriptor;
import org.joni.Regex;
import org.truffleruby.builtins.PrimitiveManager;
import org.truffleruby.cext.ValueWrapperManager;
import org.truffleruby.collections.WeakValueCache;
import org.truffleruby.core.CoreLibrary;
import org.truffleruby.core.FinalizationService;
import org.truffleruby.core.Hashing;
import org.truffleruby.core.MarkingService;
import org.truffleruby.core.ReferenceProcessingService.ReferenceProcessor;
import org.truffleruby.core.array.ArrayOperations;
import org.truffleruby.core.encoding.EncodingManager;
import org.truffleruby.core.exception.CoreExceptions;
import org.truffleruby.core.hash.PreInitializationManager;
import org.truffleruby.core.hash.ReHashable;
import org.truffleruby.core.inlined.CoreMethods;
import org.truffleruby.core.kernel.AtExitManager;
import org.truffleruby.core.kernel.TraceManager;
import org.truffleruby.core.module.ModuleOperations;
import org.truffleruby.core.objectspace.ObjectSpaceManager;
import org.truffleruby.core.proc.ProcOperations;
import org.truffleruby.core.regexp.RegexpCacheKey;
import org.truffleruby.core.rope.PathToRopeCache;
import org.truffleruby.core.rope.Rope;
import org.truffleruby.core.rope.RopeCache;
import org.truffleruby.core.string.CoreStrings;
import org.truffleruby.core.string.FrozenStringLiterals;
import org.truffleruby.core.symbol.RubySymbol;
import org.truffleruby.core.symbol.SymbolTable;
import org.truffleruby.core.thread.ThreadManager;
import org.truffleruby.core.time.GetTimeZoneNode;
import org.truffleruby.debug.MetricsProfiler;
import org.truffleruby.interop.InteropManager;
import org.truffleruby.language.CallStackManager;
import org.truffleruby.language.LexicalScope;
import org.truffleruby.language.RubyNode;
import org.truffleruby.language.SafepointManager;
import org.truffleruby.language.arguments.RubyArguments;
import org.truffleruby.language.backtrace.BacktraceFormatter;
import org.truffleruby.language.control.JavaException;
import org.truffleruby.language.control.RaiseException;
import org.truffleruby.language.loader.CodeLoader;
import org.truffleruby.language.loader.FeatureLoader;
import org.truffleruby.language.methods.InternalMethod;
import org.truffleruby.language.objects.shared.SharedObjects;
import org.truffleruby.options.Options;
import org.truffleruby.parser.TranslatorDriver;
import org.truffleruby.platform.DarwinNativeConfiguration;
import org.truffleruby.platform.LinuxNativeConfiguration;
import org.truffleruby.platform.NativeConfiguration;
import org.truffleruby.platform.Platform;
import org.truffleruby.platform.TruffleNFIPlatform;
import org.truffleruby.shared.Metrics;
import org.truffleruby.shared.TruffleRuby;
import org.truffleruby.shared.options.OptionsCatalog;
import org.truffleruby.shared.options.RubyOptionTypes;
import org.truffleruby.stdlib.CoverageManager;
import org.truffleruby.stdlib.readline.ConsoleHolder;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.instrumentation.Instrumenter;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

public class RubyContext {

    private final RubyLanguage language;
    @CompilationFinal private TruffleLanguage.Env env;

    @CompilationFinal private Options options;
    @CompilationFinal private String rubyHome;
    @CompilationFinal private TruffleFile rubyHomeTruffleFile;
    @CompilationFinal private boolean hadHome;

    private final PrimitiveManager primitiveManager = new PrimitiveManager();
    private final SafepointManager safepointManager = new SafepointManager(this);
    private final InteropManager interopManager = new InteropManager(this);
    private final CodeLoader codeLoader = new CodeLoader(this);
    private final FeatureLoader featureLoader = new FeatureLoader(this);
    private final TraceManager traceManager;
    private final ReferenceProcessor referenceProcessor;
    private final FinalizationService finalizationService;
    private final MarkingService markingService;
    private final ObjectSpaceManager objectSpaceManager = new ObjectSpaceManager();
    private final SharedObjects sharedObjects = new SharedObjects(this);
    private final AtExitManager atExitManager = new AtExitManager(this);
    private final CallStackManager callStack = new CallStackManager(this);
    private final CoreStrings coreStrings = new CoreStrings(this);
    private final FrozenStringLiterals frozenStringLiterals = new FrozenStringLiterals(this);
    private final CoreExceptions coreExceptions = new CoreExceptions(this);
    private final EncodingManager encodingManager = new EncodingManager(this);
    private final MetricsProfiler metricsProfiler = new MetricsProfiler(this);
    private final WeakValueCache<RegexpCacheKey, Regex> regexpCache = new WeakValueCache<>();
    private final PreInitializationManager preInitializationManager;
    private final NativeConfiguration nativeConfiguration;
    private final ValueWrapperManager valueWrapperManager;

    @CompilationFinal private SecureRandom random;
    private final Hashing hashing;
    @CompilationFinal private BacktraceFormatter defaultBacktraceFormatter;
    private final BacktraceFormatter userBacktraceFormatter;
    private final PathToRopeCache pathToRopeCache = new PathToRopeCache(this);
    @CompilationFinal private TruffleNFIPlatform truffleNFIPlatform;
    private final CoreLibrary coreLibrary;
    @CompilationFinal private CoreMethods coreMethods;
    private final ThreadManager threadManager;
    private final LexicalScope rootLexicalScope;
    private final CoverageManager coverageManager;
    private volatile ConsoleHolder consoleHolder;

    private final Object classVariableDefinitionLock = new Object();
    private final ReentrantLock cExtensionsLock = new ReentrantLock();

    private final boolean preInitialized;
    @CompilationFinal private boolean preInitializing;
    private boolean initialized;
    private volatile boolean finalizing;

    private static boolean preInitializeContexts = TruffleRuby.PRE_INITIALIZE_CONTEXTS;

    public RubyContext(RubyLanguage language, TruffleLanguage.Env env) {
        Metrics.printTime("before-context-constructor");

        this.preInitializing = preInitializeContexts;
        RubyContext.preInitializeContexts = false; // Only the first context is pre-initialized
        this.preInitialized = preInitializing;

        preInitializationManager = preInitializing ? new PreInitializationManager(this) : null;

        this.language = language;
        this.env = env;

        options = createOptions(env);

        referenceProcessor = new ReferenceProcessor(this);
        finalizationService = new FinalizationService(this, referenceProcessor);
        markingService = new MarkingService(this, referenceProcessor);

        // We need to construct this at runtime
        random = createRandomInstance();

        hashing = new Hashing(generateHashingSeed());

        defaultBacktraceFormatter = BacktraceFormatter.createDefaultFormatter(this);
        userBacktraceFormatter = new BacktraceFormatter(this, BacktraceFormatter.USER_BACKTRACE_FLAGS);

        rubyHome = findRubyHome(options);
        rubyHomeTruffleFile = rubyHome == null ? null : env.getInternalTruffleFile(rubyHome);

        // Load the core library classes

        Metrics.printTime("before-create-core-library");
        coreLibrary = new CoreLibrary(this);
        nativeConfiguration = loadNativeConfiguration();
        coreLibrary.initialize();
        valueWrapperManager = new ValueWrapperManager(this);
        Metrics.printTime("after-create-core-library");

        rootLexicalScope = new LexicalScope(null, coreLibrary.objectClass);

        // Create objects that need core classes

        truffleNFIPlatform = isPreInitializing() ? null : createNativePlatform();

        // The encoding manager relies on POSIX having been initialized, so we can't process it during
        // normal core library initialization.
        Metrics.printTime("before-initialize-encodings");
        encodingManager.defineEncodings();
        encodingManager.initializeDefaultEncodings(truffleNFIPlatform, nativeConfiguration);
        Metrics.printTime("after-initialize-encodings");

        Metrics.printTime("before-thread-manager");
        threadManager = new ThreadManager(this);
        threadManager.initialize(truffleNFIPlatform, nativeConfiguration);
        threadManager.initializeMainThread(Thread.currentThread());
        Metrics.printTime("after-thread-manager");

        Metrics.printTime("before-instruments");
        final Instrumenter instrumenter = env.lookup(Instrumenter.class);
        traceManager = new TraceManager(this, instrumenter);
        coverageManager = new CoverageManager(this, instrumenter);
        Metrics.printTime("after-instruments");

        Metrics.printTime("after-context-constructor");
    }

    public void initialize() {
        assert !initialized : "Already initialized";
        // Load the nodes

        Metrics.printTime("before-load-nodes");
        coreLibrary.loadCoreNodes(primitiveManager);
        Metrics.printTime("after-load-nodes");

        // Capture known builtin methods

        coreMethods = new CoreMethods(this);

        // Load the part of the core library defined in Ruby

        Metrics.printTime("before-load-core");
        coreLibrary.loadRubyCoreLibraryAndPostBoot();
        Metrics.printTime("after-load-core");

        // Share once everything is loaded
        if (options.SHARED_OBJECTS_ENABLED && options.SHARED_OBJECTS_FORCE) {
            sharedObjects.startSharing(OptionsCatalog.SHARED_OBJECTS_FORCE.getName() + " being true");
        }

        if (isPreInitializing()) {
            // Cannot save the file descriptor in this SecureRandom in the image
            random = null;
            // Cannot save the root Java Thread instance in the image
            threadManager.resetMainThread();
            // Do not save image generator paths in the image heap
            hadHome = rubyHome != null;
            rubyHome = null;
            rubyHomeTruffleFile = null;
            featureLoader.setWorkingDirectory(null);
        } else {
            initialized = true;
        }

        this.preInitializing = false;
    }

    /** Re-initialize parts of the RubyContext depending on the running process. This is a small subset of the full
     * initialization which needs to be performed to adapt to the new process and external environment. Calls are kept
     * in the same order as during normal initialization. */
    protected boolean patch(Env newEnv) {
        this.env = newEnv;

        final Options oldOptions = this.options;
        final Options newOptions = createOptions(newEnv);
        final String newHome = findRubyHome(newOptions);
        if (!compatibleOptions(oldOptions, newOptions, this.hadHome, newHome != null)) {
            return false;
        }
        this.options = newOptions;
        this.rubyHome = newHome;
        this.rubyHomeTruffleFile = newHome == null ? null : newEnv.getInternalTruffleFile(newHome);

        // Re-read the value of $TZ as it can be different in the new process
        GetTimeZoneNode.invalidateTZ();

        random = createRandomInstance();
        hashing.patchSeed(generateHashingSeed());

        this.defaultBacktraceFormatter = BacktraceFormatter.createDefaultFormatter(this);

        this.truffleNFIPlatform = createNativePlatform();
        encodingManager.initializeDefaultEncodings(truffleNFIPlatform, nativeConfiguration);

        threadManager.initialize(truffleNFIPlatform, nativeConfiguration);
        threadManager.restartMainThread(Thread.currentThread());

        Metrics.printTime("before-rehash");
        preInitializationManager.rehash();
        Metrics.printTime("after-rehash");

        Metrics.printTime("before-run-delayed-initialization");
        final Object toRunAtInit = Layouts.MODULE
                .getFields(coreLibrary.truffleBootModule)
                .getConstant("TO_RUN_AT_INIT")
                .getValue();
        for (Object proc : ArrayOperations.toIterable((DynamicObject) toRunAtInit)) {
            final Source source = Layouts.PROC
                    .getMethod((DynamicObject) proc)
                    .getSharedMethodInfo()
                    .getSourceSection()
                    .getSource();
            TranslatorDriver.printParseTranslateExecuteMetric("before-run-delayed-initialization", this, source);
            ProcOperations.rootCall((DynamicObject) proc);
            TranslatorDriver.printParseTranslateExecuteMetric("after-run-delayed-initialization", this, source);
        }
        Metrics.printTime("after-run-delayed-initialization");

        initialized = true;
        return true;
    }

    protected boolean patchContext(Env newEnv) {
        try {
            return patch(newEnv);
        } catch (RaiseException e) {
            System.err.println("Exception during RubyContext.patch():");
            getDefaultBacktraceFormatter().printRubyExceptionOnEnvStderr(e.getException());
            throw e;
        } catch (Throwable e) {
            System.err.println("Exception during RubyContext.patch():");
            e.printStackTrace();
            throw e;
        }
    }

    private boolean compatibleOptions(Options oldOptions, Options newOptions, boolean hadHome, boolean hasHome) {
        final String notReusingContext = "not reusing pre-initialized context: ";

        if (!newOptions.PREINITIALIZATION) {
            RubyLanguage.LOGGER.fine(notReusingContext + "--preinit is false");
            return false;
        }

        if (!newOptions.CORE_LOAD_PATH.equals(OptionsCatalog.CORE_LOAD_PATH_KEY.getDefaultValue())) {
            RubyLanguage.LOGGER.fine(notReusingContext + "--core-load-path is set: " + newOptions.CORE_LOAD_PATH);
            return false; // Should load the specified core files
        }

        if (hadHome != hasHome) {
            RubyLanguage.LOGGER.fine(notReusingContext + "Ruby home is " + (hasHome ? "set" : "unset"));
            return false;
        }

        // Libraries loaded during pre-initialization

        if (newOptions.PATCHING != oldOptions.PATCHING) {
            RubyLanguage.LOGGER.fine(notReusingContext + "loading patching is " + newOptions.PATCHING);
            return false;
        }

        if (newOptions.DID_YOU_MEAN != oldOptions.DID_YOU_MEAN) {
            RubyLanguage.LOGGER.fine(notReusingContext + "loading did_you_mean is " + newOptions.DID_YOU_MEAN);
            return false;
        }

        return true;
    }

    private Options createOptions(TruffleLanguage.Env env) {
        Metrics.printTime("before-options");
        final Options options = new Options(env, env.getOptions());

        if (options.OPTIONS_LOG && RubyLanguage.LOGGER.isLoggable(Level.CONFIG)) {
            for (OptionDescriptor descriptor : OptionsCatalog.allDescriptors()) {
                assert descriptor.getName().startsWith(TruffleRuby.LANGUAGE_ID);
                final String xName = descriptor.getName().substring(TruffleRuby.LANGUAGE_ID.length() + 1);
                RubyLanguage.LOGGER.config(
                        "option " + xName + "=" + RubyOptionTypes.valueToString(options.fromDescriptor(descriptor)));
            }
        }

        Metrics.printTime("after-options");
        return options;
    }

    private long generateHashingSeed() {
        if (options.HASHING_DETERMINISTIC) {
            RubyLanguage.LOGGER.severe(
                    "deterministic hashing is enabled - this may make you vulnerable to denial of service attacks");
            return 7114160726623585955L;
        } else {
            final byte[] bytes = getRandomSeedBytes(Long.BYTES);
            final ByteBuffer buffer = ByteBuffer.wrap(bytes);
            return buffer.getLong();
        }
    }

    private TruffleNFIPlatform createNativePlatform() {
        Metrics.printTime("before-create-native-platform");
        final TruffleNFIPlatform truffleNFIPlatform = options.NATIVE_PLATFORM ? new TruffleNFIPlatform(this) : null;
        featureLoader.initialize(nativeConfiguration, truffleNFIPlatform);
        Metrics.printTime("after-create-native-platform");
        return truffleNFIPlatform;
    }

    private NativeConfiguration loadNativeConfiguration() {
        final NativeConfiguration nativeConfiguration = new NativeConfiguration();

        switch (Platform.OS) {
            case LINUX:
                LinuxNativeConfiguration.load(nativeConfiguration, this);
                break;
            case DARWIN:
                DarwinNativeConfiguration.load(nativeConfiguration, this);
                break;
            default:
                RubyLanguage.LOGGER.severe("no native configuration for this platform");
                break;
        }

        return nativeConfiguration;
    }

    @TruffleBoundary
    public Object send(Object object, String methodName, Object... arguments) {
//        System.out.println("======== RubyConext call send: " + methodName);
        final InternalMethod method = ModuleOperations
                .lookupMethodUncached(coreLibrary.getMetaClass(object), methodName, null);
        if (method == null || method.isUndefined()) {
            return null;
        }

        return method.getCallTarget().call(
                RubyArguments.pack(null, null, method, null, object, null, arguments));
    }

    public void finalizeContext() {
        if (!initialized) {
            // The RubyContext will be finalized and disposed if patching fails (potentially for
            // another language). In that case, there is nothing to clean or execute.
            return;
        }

        finalizing = true;

        atExitManager.runSystemExitHooks();
        threadManager.killAndWaitOtherThreads();
    }

    private final ReentrantLock disposeLock = new ReentrantLock();
    private boolean disposed = false;

    public void disposeContext() {
        disposeLock.lock();
        try {
            if (!disposed) {
                dispose();
                disposed = true;
            }
        } finally {
            disposeLock.unlock();
        }
    }

    private void dispose() {
        if (!initialized) {
            // The RubyContext will be finalized and disposed if patching fails (potentially for
            // another language). In that case, there is nothing to clean or execute.
            return;
        }

        threadManager.cleanupMainThread();
        safepointManager.checkNoRunningThreads();

        if (options.ROPE_PRINT_INTERN_STATS) {
            RubyLanguage.LOGGER.info("ropes re-used: " + getRopeCache().getRopesReusedCount());
            RubyLanguage.LOGGER.info("rope byte arrays re-used: " + getRopeCache().getByteArrayReusedCount());
            RubyLanguage.LOGGER.info("rope bytes saved: " + getRopeCache().getRopeBytesSaved());
            RubyLanguage.LOGGER.info("total ropes interned: " + getRopeCache().totalRopes());
        }

        if (options.CEXTS_TONATIVE_STATS) {
            RubyLanguage.LOGGER.info(
                    "Total VALUE object to native conversions: " + getValueWrapperManager().totalHandleAllocations());
        }

        if (options.COVERAGE_GLOBAL) {
            coverageManager.print(System.out);
        }
    }

    public boolean isPreInitializing() {
        return preInitializing;
    }

    public boolean wasPreInitialized() {
        return preInitialized;
    }

    public Hashing getHashing() {
        return hashing;
    }

    public RubyLanguage getLanguage() {
        return language;
    }

    public Options getOptions() {
        return options;
    }

    public TruffleLanguage.Env getEnv() {
        return env;
    }

    /** Hashing for a RubyNode, the seed should only be used for a Ruby-level #hash method */
    public Hashing getHashing(RubyNode node) {
        return hashing;
    }

    /** With context pre-initialization, the random seed must be reset at runtime. So every use of the random seed
     * through Hashing should provide a way to rehash to take the new random seed in account. */
    public Hashing getHashing(ReHashable reHashable) {
        if (isPreInitializing()) {
            preInitializationManager.addReHashable(reHashable);
        }
        return hashing;
    }

    public BacktraceFormatter getDefaultBacktraceFormatter() {
        return defaultBacktraceFormatter;
    }

    public BacktraceFormatter getUserBacktraceFormatter() {
        return userBacktraceFormatter;
    }

    public CoreLibrary getCoreLibrary() {
        return coreLibrary;
    }

    public CoreMethods getCoreMethods() {
        return coreMethods;
    }

    public FeatureLoader getFeatureLoader() {
        return featureLoader;
    }

    public FinalizationService getFinalizationService() {
        return finalizationService;
    }

    public MarkingService getMarkingService() {
        return markingService;
    }

    public ObjectSpaceManager getObjectSpaceManager() {
        return objectSpaceManager;
    }

    public SharedObjects getSharedObjects() {
        return sharedObjects;
    }

    public ThreadManager getThreadManager() {
        return threadManager;
    }

    public AtExitManager getAtExitManager() {
        return atExitManager;
    }

    public TraceManager getTraceManager() {
        return traceManager;
    }

    public SafepointManager getSafepointManager() {
        return safepointManager;
    }

    public LexicalScope getRootLexicalScope() {
        return rootLexicalScope;
    }

    public PrimitiveManager getPrimitiveManager() {
        return primitiveManager;
    }

    public CoverageManager getCoverageManager() {
        return coverageManager;
    }

    public RopeCache getRopeCache() {
        return language.ropeCache;
    }

    public PathToRopeCache getPathToRopeCache() {
        return pathToRopeCache;
    }

    public SymbolTable getSymbolTable() {
        return language.symbolTable;
    }

    @TruffleBoundary
    public RubySymbol getSymbol(String string) {
        return language.getSymbol(string);
    }

    @TruffleBoundary
    public RubySymbol getSymbol(Rope rope) {
        return language.getSymbol(rope);
    }

    public CodeLoader getCodeLoader() {
        return codeLoader;
    }

    public InteropManager getInteropManager() {
        return interopManager;
    }

    public CallStackManager getCallStack() {
        return callStack;
    }

    public CoreStrings getCoreStrings() {
        return coreStrings;
    }

    public DynamicObject getFrozenStringLiteral(Rope rope) {
        return frozenStringLiterals.getFrozenStringLiteral(rope);
    }

    public DynamicObject getInternedString(DynamicObject string) {
        return frozenStringLiterals.getFrozenStringLiteral(string);
    }

    public Object getClassVariableDefinitionLock() {
        return classVariableDefinitionLock;
    }

    public ReentrantLock getCExtensionsLock() {
        return cExtensionsLock;
    }

    public Instrumenter getInstrumenter() {
        return env.lookup(Instrumenter.class);
    }

    public CoreExceptions getCoreExceptions() {
        return coreExceptions;
    }

    public EncodingManager getEncodingManager() {
        return encodingManager;
    }

    public MetricsProfiler getMetricsProfiler() {
        return metricsProfiler;
    }

    public PreInitializationManager getPreInitializationManager() {
        return preInitializationManager;
    }

    public String getRubyHome() {
        return rubyHome;
    }

    public TruffleFile getRubyHomeTruffleFile() {
        return rubyHomeTruffleFile;
    }

    public ConsoleHolder getConsoleHolder() {
        if (consoleHolder == null) {
            synchronized (this) {
                if (consoleHolder == null) {
                    consoleHolder = ConsoleHolder.create(this);
                }
            }
        }

        return consoleHolder;
    }

    public void setConsoleHolder(ConsoleHolder consoleHolder) {
        synchronized (this) {
            this.consoleHolder = consoleHolder;
        }
    }

    public boolean isInitialized() {
        return initialized;
    }

    public boolean isFinalizing() {
        return finalizing;
    }

    private String findRubyHome(Options options) {
        final String home = searchRubyHome(options);
        if (RubyLanguage.LOGGER.isLoggable(Level.CONFIG)) {
            RubyLanguage.LOGGER.config("home: " + home);
        }
        return home;
    }

    // Returns a canonical path to the home
    private String searchRubyHome(Options options) {
        if (options.NO_HOME_PROVIDED) {
            RubyLanguage.LOGGER.config("--ruby.no-home-provided set");
            return null;
        }

        final String truffleReported = language.getTruffleLanguageHome();
        if (truffleReported != null) {
            final File home = new File(truffleReported);
            if (isRubyHome(home)) {
                RubyLanguage.LOGGER.config(
                        () -> String.format("Using Truffle-reported home %s as the Ruby home", truffleReported));
                return truffleReported;
            } else {
                RubyLanguage.LOGGER.warning(
                        String.format(
                                "Truffle-reported home %s does not look like TruffleRuby's home",
                                truffleReported));
            }
        } else {
            RubyLanguage.LOGGER.config("Truffle-reported home not set, cannot determine home from it");
        }

        RubyLanguage.LOGGER.warning(
                "could not determine TruffleRuby's home - the standard library will not be available - use --log.level=CONFIG to see details");
        return null;
    }

    private boolean isRubyHome(File path) {
        final File lib = new File(path, "lib");
        return new File(lib, "truffle").isDirectory() &&
                new File(lib, "gems").isDirectory() &&
                new File(lib, "patches").isDirectory();
    }

    public TruffleNFIPlatform getTruffleNFI() {
        return truffleNFIPlatform;
    }

    public NativeConfiguration getNativeConfiguration() {
        return nativeConfiguration;
    }

    public ValueWrapperManager getValueWrapperManager() {
        return valueWrapperManager;
    }

    private static SecureRandom createRandomInstance() {
        try {
            /* We want to use a non-blocking source because this is what MRI does (via /dev/urandom) and it's been found
             * in practice that blocking sources are a problem for deploying JRuby. */
            return SecureRandom.getInstance("NativePRNGNonBlocking");
        } catch (NoSuchAlgorithmException e) {
            throw new JavaException(e);
        }
    }

    @TruffleBoundary
    public byte[] getRandomSeedBytes(int numBytes) {
        final byte[] bytes = new byte[numBytes];
        random.nextBytes(bytes);
        return bytes;
    }

    public WeakValueCache<RegexpCacheKey, Regex> getRegexpCache() {
        return regexpCache;
    }

    /** Returns the path of a Source. Returns the short, potentially relative, path for the main script. Note however
     * that the path of {@code eval(code, nil, filename)} is just {@code filename} and might not be absolute. */
    public static String getPath(Source source) {
        final String path = source.getPath();
        if (path != null) {
            return path;
        } else {
            // non-file sources: eval(), main_boot_source, etc
            final String name = source.getName();
            assert name != null;
            return name;
        }
    }

    public String getPathRelativeToHome(String path) {
        if (path.startsWith(rubyHome) && path.length() > rubyHome.length()) {
            return path.substring(rubyHome.length() + 1);
        } else {
            return path;
        }
    }

    @TruffleBoundary
    public static String fileLine(SourceSection section) {
        if (section == null) {
            return "no source section";
        } else {
            final String path = getPath(section.getSource());

            if (section.isAvailable()) {
                return path + ":" + section.getStartLine();
            } else {
                return path;
            }
        }
    }

}
