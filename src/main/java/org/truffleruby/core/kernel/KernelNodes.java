/*
 * Copyright (c) 2013, 2019 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.core.kernel;

import static org.truffleruby.language.dispatch.DispatchConfiguration.PRIVATE;
import static org.truffleruby.language.dispatch.DispatchConfiguration.PRIVATE_DOES_RESPOND;
import static org.truffleruby.language.dispatch.DispatchConfiguration.PUBLIC;
import static org.truffleruby.language.dispatch.DispatchConfiguration.PUBLIC_DOES_RESPOND;

import java.io.File;
import java.io.PrintStream;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.jcodings.specific.UTF8Encoding;
import org.truffleruby.RubyContext;
import org.truffleruby.RubyLanguage;
import org.truffleruby.SuppressFBWarnings;
import org.truffleruby.builtins.CoreMethod;
import org.truffleruby.builtins.CoreMethodArrayArgumentsNode;
import org.truffleruby.builtins.CoreMethodNode;
import org.truffleruby.builtins.CoreModule;
import org.truffleruby.builtins.NonStandard;
import org.truffleruby.builtins.Primitive;
import org.truffleruby.builtins.PrimitiveArrayArgumentsNode;
import org.truffleruby.builtins.UnaryCoreMethodNode;
import org.truffleruby.core.array.ArrayHelpers;
import org.truffleruby.core.array.ArrayUtils;
import org.truffleruby.core.array.RubyArray;
import org.truffleruby.core.basicobject.BasicObjectNodes.ObjectIDNode;
import org.truffleruby.core.basicobject.BasicObjectNodes.ReferenceEqualNode;
import org.truffleruby.core.binding.BindingNodes;
import org.truffleruby.core.binding.RubyBinding;
import org.truffleruby.core.cast.BooleanCastNode;
import org.truffleruby.core.cast.BooleanCastNodeGen;
import org.truffleruby.core.cast.BooleanCastWithDefaultNodeGen;
import org.truffleruby.core.cast.DurationToMillisecondsNodeGen;
import org.truffleruby.core.cast.NameToJavaStringNode;
import org.truffleruby.core.cast.ToStringOrSymbolNodeGen;
import org.truffleruby.core.exception.GetBacktraceException;
import org.truffleruby.core.format.BytesResult;
import org.truffleruby.core.format.FormatExceptionTranslator;
import org.truffleruby.core.format.exceptions.FormatException;
import org.truffleruby.core.format.exceptions.InvalidFormatException;
import org.truffleruby.core.format.printf.PrintfCompiler;
import org.truffleruby.core.kernel.KernelNodesFactory.CopyNodeFactory;
import org.truffleruby.core.kernel.KernelNodesFactory.GetMethodObjectNodeGen;
import org.truffleruby.core.kernel.KernelNodesFactory.SameOrEqualNodeFactory;
import org.truffleruby.core.kernel.KernelNodesFactory.SingletonMethodsNodeFactory;
import org.truffleruby.core.klass.RubyClass;
import org.truffleruby.core.method.MethodFilter;
import org.truffleruby.core.method.RubyMethod;
import org.truffleruby.core.module.RubyModule;
import org.truffleruby.core.numeric.BigIntegerOps;
import org.truffleruby.core.numeric.RubyBignum;
import org.truffleruby.core.proc.ProcNodes.ProcNewNode;
import org.truffleruby.core.proc.ProcOperations;
import org.truffleruby.core.proc.RubyProc;
import org.truffleruby.core.rope.CodeRange;
import org.truffleruby.core.rope.Rope;
import org.truffleruby.core.rope.RopeNodes;
import org.truffleruby.core.rope.RopeOperations;
import org.truffleruby.core.string.RubyString;
import org.truffleruby.core.string.StringCachingGuards;
import org.truffleruby.core.string.StringNodes;
import org.truffleruby.core.string.StringNodes.MakeStringNode;
import org.truffleruby.core.string.StringOperations;
import org.truffleruby.core.support.TypeNodes.CheckFrozenNode;
import org.truffleruby.core.support.TypeNodes.ObjectInstanceVariablesNode;
import org.truffleruby.core.support.TypeNodesFactory.ObjectInstanceVariablesNodeFactory;
import org.truffleruby.core.symbol.RubySymbol;
import org.truffleruby.core.symbol.SymbolTable;
import org.truffleruby.core.thread.GetCurrentRubyThreadNode;
import org.truffleruby.core.thread.RubyThread;
import org.truffleruby.core.thread.ThreadManager.BlockingAction;
import org.truffleruby.interop.ToJavaStringNode;
import org.truffleruby.language.Nil;
import org.truffleruby.language.NotProvided;
import org.truffleruby.language.RubyContextNode;
import org.truffleruby.language.RubyContextSourceNode;
import org.truffleruby.language.RubyDynamicObject;
import org.truffleruby.language.RubyGuards;
import org.truffleruby.language.RubyNode;
import org.truffleruby.language.RubyRootNode;
import org.truffleruby.language.RubySourceNode;
import org.truffleruby.language.Visibility;
import org.truffleruby.language.WarnNode;
import org.truffleruby.language.arguments.ReadCallerFrameNode;
import org.truffleruby.language.arguments.RubyArguments;
import org.truffleruby.language.backtrace.Backtrace;
import org.truffleruby.language.backtrace.BacktraceFormatter;
import org.truffleruby.language.control.RaiseException;
import org.truffleruby.language.dispatch.DispatchConfiguration;
import org.truffleruby.language.dispatch.DispatchNode;
import org.truffleruby.language.dispatch.RubyCallNode;
import org.truffleruby.language.eval.CreateEvalSourceNode;
import org.truffleruby.language.globals.ReadGlobalVariableNodeGen;
import org.truffleruby.language.library.RubyLibrary;
import org.truffleruby.language.loader.CodeLoader;
import org.truffleruby.language.loader.RequireNode;
import org.truffleruby.language.loader.RequireNodeGen;
import org.truffleruby.language.locals.FindDeclarationVariableNodes.FindAndReadDeclarationVariableNode;
import org.truffleruby.language.methods.DeclarationContext;
import org.truffleruby.language.methods.InternalMethod;
import org.truffleruby.language.methods.LookupMethodOnSelfNode;
import org.truffleruby.language.methods.SharedMethodInfo;
import org.truffleruby.language.methods.Split;
import org.truffleruby.language.objects.AllocateHelperNode;
import org.truffleruby.language.objects.CheckIVarNameNode;
import org.truffleruby.language.objects.IsANode;
import org.truffleruby.language.objects.IsImmutableObjectNode;
import org.truffleruby.language.objects.LogicalClassNode;
import org.truffleruby.language.objects.MetaClassNode;
import org.truffleruby.language.objects.PropagateTaintNode;
import org.truffleruby.language.objects.ShapeCachingGuards;
import org.truffleruby.language.objects.SingletonClassNode;
import org.truffleruby.language.objects.WriteObjectFieldNode;
import org.truffleruby.language.objects.shared.SharedObjects;
import org.truffleruby.parser.ParserContext;
import org.truffleruby.parser.RubySource;
import org.truffleruby.parser.TranslatorEnvironment;
import org.truffleruby.utils.Utils;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.CreateCast;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.ReportPolymorphism;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameInstance.FrameAccess;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeUtil;
import com.oracle.truffle.api.object.DynamicObjectLibrary;
import com.oracle.truffle.api.object.Property;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.source.SourceSection;

@CoreModule("Kernel")
public abstract class KernelNodes {

    /** Check if operands are the same object or call #==. Known as rb_equal() in MRI. The fact Kernel#=== uses this is
     * pure coincidence. */
    @Primitive(name = "object_same_or_equal")
    public abstract static class SameOrEqualNode extends PrimitiveArrayArgumentsNode {

        @Child private DispatchNode equalNode;
        @Child private BooleanCastNode booleanCastNode;

        private final ConditionProfile sameProfile = ConditionProfile.create();

        public static SameOrEqualNode create() {
            return SameOrEqualNodeFactory.create(null);
        }

        public abstract boolean executeSameOrEqual(Object a, Object b);

        @Specialization
        protected boolean sameOrEqual(Object a, Object b,
                @Cached ReferenceEqualNode referenceEqualNode) {
            if (sameProfile.profile(referenceEqualNode.executeReferenceEqual(a, b))) {
                return true;
            } else {
                return areEqual(a, b);
            }
        }

        private boolean areEqual(Object left, Object right) {
            if (equalNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                equalNode = insert(DispatchNode.create());
            }

            if (booleanCastNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                booleanCastNode = insert(BooleanCastNode.create());
            }

            return booleanCastNode.executeToBoolean(equalNode.call(left, "==", right));
        }

    }

    @CoreMethod(names = "===", required = 1)
    public abstract static class CaseCompareNode extends CoreMethodArrayArgumentsNode {

        @Child private SameOrEqualNode sameOrEqualNode = SameOrEqualNode.create();

        @Specialization
        protected boolean caseCmp(Object a, Object b) {
            return sameOrEqualNode.executeSameOrEqual(a, b);
        }

    }

    /** Check if operands are the same object or call #eql? */
    public abstract static class SameOrEqlNode extends CoreMethodArrayArgumentsNode {

        @Child private DispatchNode eqlNode;
        @Child private BooleanCastNode booleanCastNode;

        private final ConditionProfile sameProfile = ConditionProfile.create();

        public abstract boolean executeSameOrEql(Object a, Object b);

        @Specialization
        protected boolean sameOrEql(Object a, Object b,
                @Cached ReferenceEqualNode referenceEqualNode) {
            if (sameProfile.profile(referenceEqualNode.executeReferenceEqual(a, b))) {
                return true;
            } else {
                return areEql(a, b);
            }
        }

        private boolean areEql(Object left, Object right) {
            if (eqlNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                eqlNode = insert(DispatchNode.create());
            }

            if (booleanCastNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                booleanCastNode = insert(BooleanCastNode.create());
            }

            return booleanCastNode.executeToBoolean(eqlNode.call(left, "eql?", right));
        }

    }

    @Primitive(name = "find_file")
    public abstract static class FindFileNode extends PrimitiveArrayArgumentsNode {

        @Specialization
        protected Object findFile(RubyString featureString,
                @Cached BranchProfile notFoundProfile,
                @Cached MakeStringNode makeStringNode) {
            String feature = StringOperations.getString(featureString);
            return findFileString(feature, notFoundProfile, makeStringNode);
        }

        @Specialization
        protected Object findFileString(String featureString,
                @Cached BranchProfile notFoundProfile,
                @Cached MakeStringNode makeStringNode) {
            final String expandedPath = getContext().getFeatureLoader().findFeature(featureString);
            if (expandedPath == null) {
                notFoundProfile.enter();
                return nil;
            }
            return makeStringNode
                    .executeMake(expandedPath, UTF8Encoding.INSTANCE, CodeRange.CR_UNKNOWN);
        }

    }

    @Primitive(name = "get_caller_path")
    public abstract static class GetCallerPathNode extends PrimitiveArrayArgumentsNode {

        @Specialization
        @TruffleBoundary
        protected RubyString getCallerPath(RubyString feature,
                @Cached MakeStringNode makeStringNode) {
            final String featureString = StringOperations.getString(feature);
            final String featurePath;
            if (new File(featureString).isAbsolute()) {
                featurePath = featureString;
            } else {
                final SourceSection sourceSection = getContext()
                        .getCallStack()
                        .getCallerNodeIgnoringSend()
                        .getEncapsulatingSourceSection();
                if (!BacktraceFormatter.isAvailable(sourceSection)) {
                    throw new RaiseException(
                            getContext(),
                            coreExceptions().loadError("cannot infer basepath", featureString, this));
                }

                String sourcePath = RubyContext.getPath(sourceSection.getSource());

                sourcePath = getContext().getFeatureLoader().canonicalize(sourcePath);

                featurePath = getContext().getFeatureLoader().dirname(sourcePath) + "/" + featureString;
            }

            // Normalize the path like File.expand_path() (e.g., remove "../"), but do not resolve
            // symlinks. MRI does this for #require_relative always, but not for #require, so we
            // need to do it to be compatible in the case the path does not exist, so the
            // LoadError's #path is the same as MRI's.
            return makeStringNode
                    .executeMake(
                            Paths.get(featurePath).normalize().toString(),
                            UTF8Encoding.INSTANCE,
                            CodeRange.CR_UNKNOWN);
        }

    }

    @Primitive(name = "load_feature")
    public abstract static class LoadFeatureNode extends PrimitiveArrayArgumentsNode {

        @Child private RequireNode requireNode = RequireNodeGen.create();

        @Specialization
        protected boolean loadFeature(RubyString featureString, RubyString expandedPathString) {
            return requireNode.executeRequire(StringOperations.getString(featureString), expandedPathString);
        }

    }

    @CoreMethod(names = { "<=>" }, required = 1)
    public abstract static class CompareNode extends CoreMethodArrayArgumentsNode {

        @Child private SameOrEqualNode sameOrEqualNode = SameOrEqualNode.create();

        @Specialization
        protected Object compare(Object self, Object other) {
            if (sameOrEqualNode.executeSameOrEqual(self, other)) {
                return 0;
            } else {
                return nil;
            }
        }

    }

    @CoreMethod(names = "binding", isModuleFunction = true)
    public abstract static class BindingNode extends CoreMethodArrayArgumentsNode {

        @Child ReadCallerFrameNode callerFrameNode = new ReadCallerFrameNode();

        @Specialization
        protected RubyBinding binding(VirtualFrame frame) {
            final MaterializedFrame callerFrame = callerFrameNode.execute(frame);
            final SourceSection sourceSection = getCallerSourceSection();

            return BindingNodes.createBinding(getContext(), callerFrame, sourceSection);
        }

        @TruffleBoundary
        protected SourceSection getCallerSourceSection() {
            return getContext().getCallStack().getCallerNodeIgnoringSend().getEncapsulatingSourceSection();
        }

    }

    @CoreMethod(names = { "block_given?", "iterator?" }, isModuleFunction = true)
    public abstract static class BlockGivenNode extends CoreMethodArrayArgumentsNode {

        @Child ReadCallerFrameNode callerFrameNode = new ReadCallerFrameNode();

        @Specialization
        protected boolean blockGiven(VirtualFrame frame,
                @Cached("create(nil)") FindAndReadDeclarationVariableNode readNode,
                @Cached ConditionProfile blockProfile) {
            MaterializedFrame callerFrame = callerFrameNode.execute(frame);
            return blockProfile
                    .profile(readNode.execute(callerFrame, TranslatorEnvironment.METHOD_BLOCK_NAME) != nil);
        }
    }

    @CoreMethod(names = "__callee__", isModuleFunction = true)
    public abstract static class CalleeNameNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        protected RubySymbol calleeName() {
            // the "called name" of a method.
            return getSymbol(getContext().getCallStack().getCallingMethodIgnoringSend().getName());
        }
    }

    @Primitive(name = "canonicalize_path")
    public abstract static class CanonicalizePathNode extends PrimitiveArrayArgumentsNode {

        @Specialization
        @TruffleBoundary
        protected RubyString canonicalPath(RubyString string,
                @Cached StringNodes.MakeStringNode makeStringNode) {
            final String expandedPath = getContext().getFeatureLoader().canonicalize(string.getJavaString());
            return makeStringNode.executeMake(expandedPath, UTF8Encoding.INSTANCE, CodeRange.CR_UNKNOWN);
        }

    }

    @Primitive(name = "kernel_caller_locations", lowerFixnum = { 0, 1 })
    public abstract static class CallerLocationsNode extends CoreMethodArrayArgumentsNode {

        @Child private AllocateHelperNode allocateNode = AllocateHelperNode.create();

        @Specialization
        protected Object callerLocations(int omit, NotProvided length) {
            return innerCallerLocations(omit, GetBacktraceException.UNLIMITED);
        }

        @Specialization
        protected Object callerLocations(int omit, int length) {
            return innerCallerLocations(omit, length);
        }

        private Object innerCallerLocations(int omit, int length) {
            // Always skip #caller_locations.
            final int omitted = omit + 1;
            final Backtrace backtrace = getContext().getCallStack().getBacktrace(this, omitted);
            return backtrace.getBacktraceLocations(getContext(), allocateNode, length, this);
        }
    }

    @CoreMethod(names = "class")
    public abstract static class KernelClassNode extends CoreMethodArrayArgumentsNode {

        @Child private LogicalClassNode classNode = LogicalClassNode.create();

        @Specialization
        protected RubyClass getClass(Object self) {
            return classNode.executeLogicalClass(self);
        }

    }

    @ImportStatic(ShapeCachingGuards.class)
    public abstract static class CopyNode extends UnaryCoreMethodNode {

        public static final Property[] EMPTY_PROPERTY_ARRAY = new Property[0];

        public static CopyNode create() {
            return CopyNodeFactory.create(null);
        }

        @Child private DispatchNode allocateNode = DispatchNode.create();

        public abstract RubyDynamicObject executeCopy(RubyDynamicObject self);

        @ExplodeLoop
        @Specialization(guards = "self.getShape() == cachedShape", limit = "getCacheLimit()")
        protected RubyDynamicObject copyCached(RubyDynamicObject self,
                @Cached("self.getShape()") Shape cachedShape,
                @Cached(value = "getCopiedProperties(cachedShape)", dimensions = 1) Property[] properties,
                @Cached("createWriteFieldNodes(properties)") DynamicObjectLibrary[] writeFieldNodes) {
            final RubyDynamicObject newObject = (RubyDynamicObject) allocateNode
                    .call(self.getLogicalClass(), "__allocate__");

            for (int i = 0; i < properties.length; i++) {
                final Property property = properties[i];
                final Object value = property.get(self, cachedShape);
                writeFieldNodes[i].putWithFlags(newObject, property.getKey(), value, property.getFlags());
            }

            return newObject;
        }

        @Specialization(guards = "updateShape(self)")
        protected Object updateShapeAndCopy(RubyDynamicObject self) {
            return executeCopy(self);
        }

        @Specialization(replaces = { "copyCached", "updateShapeAndCopy" })
        protected RubyDynamicObject copyUncached(RubyDynamicObject self) {
            final RubyClass rubyClass = self.getLogicalClass();
            final RubyDynamicObject newObject = (RubyDynamicObject) allocateNode.call(rubyClass, "__allocate__");
            copyInstanceVariables(self, newObject);
            return newObject;
        }

        protected Property[] getCopiedProperties(Shape shape) {
            final List<Property> copiedProperties = new ArrayList<>();

            for (Property property : shape.getProperties()) {
                if (property.getKey() instanceof String) {
                    copiedProperties.add(property);
                }
            }

            return copiedProperties.toArray(EMPTY_PROPERTY_ARRAY);
        }

        protected DynamicObjectLibrary[] createWriteFieldNodes(Property[] properties) {
            final DynamicObjectLibrary[] nodes = new DynamicObjectLibrary[properties.length];
            for (int i = 0; i < properties.length; i++) {
                nodes[i] = DynamicObjectLibrary.getFactory().createDispatched(1);
            }
            return nodes;
        }

        @TruffleBoundary
        private void copyInstanceVariables(RubyDynamicObject from, RubyDynamicObject to) {
            // Concurrency: OK if callers create the object and publish it after copy
            // Only copy user-level instance variables, hidden ones are initialized later with #initialize_copy.
            for (Property property : getCopiedProperties(from.getShape())) {
                DynamicObjectLibrary.getUncached().putWithFlags(
                        to,
                        property.getKey(),
                        property.get(from, from.getShape()),
                        property.getFlags());
            }
        }

        protected int getCacheLimit() {
            return getContext().getOptions().INSTANCE_VARIABLE_CACHE;
        }

    }

    @CoreMethod(names = "clone", keywordAsOptional = "freeze")
    @NodeChild(value = "self", type = RubyNode.class)
    @NodeChild(value = "freeze", type = RubyNode.class)
    public abstract static class CloneNode extends CoreMethodNode {

        @Child private CopyNode copyNode = CopyNode.create();
        @Child private DispatchNode initializeCloneNode = DispatchNode.create();
        @Child private PropagateTaintNode propagateTaintNode = PropagateTaintNode.create();
        @Child private SingletonClassNode singletonClassNode;

        @CreateCast("freeze")
        protected RubyNode coerceToBoolean(RubyNode freeze) {
            return BooleanCastWithDefaultNodeGen.create(true, freeze);
        }

        @Specialization(limit = "getRubyLibraryCacheLimit()")
        protected RubyDynamicObject clone(RubyDynamicObject self, boolean freeze,
                @Cached ConditionProfile isSingletonProfile,
                @Cached ConditionProfile freezeProfile,
                @Cached ConditionProfile isFrozenProfile,
                @Cached ConditionProfile isRubyClass,
                @CachedLibrary("self") RubyLibrary rubyLibrary,
                @CachedLibrary(limit = "getRubyLibraryCacheLimit()") RubyLibrary rubyLibraryFreeze) {
            final RubyDynamicObject newObject = copyNode.executeCopy(self);

            // Copy the singleton class if any.
            final RubyClass selfMetaClass = self.getMetaClass();
            if (isSingletonProfile.profile(selfMetaClass.isSingleton)) {
                final RubyClass newObjectMetaClass = executeSingletonClass(newObject);
                newObjectMetaClass.fields.initCopy(selfMetaClass);
            }

            initializeCloneNode.call(newObject, "initialize_clone", self);

            propagateTaintNode.executePropagate(self, newObject);

            if (freezeProfile.profile(freeze) && isFrozenProfile.profile(rubyLibrary.isFrozen(self))) {
                rubyLibraryFreeze.freeze(newObject);
            }

            if (isRubyClass.profile(self instanceof RubyClass)) {
                ((RubyClass) newObject).superclass = ((RubyClass) self).superclass;
            }

            return newObject;
        }

        @Specialization
        protected Object cloneBoolean(boolean self, boolean freeze,
                @Cached ConditionProfile freezeProfile) {
            if (freezeProfile.profile(!freeze)) {
                raiseCantUnfreezeError(self);
            }
            return self;
        }

        @Specialization
        protected Object cloneInteger(int self, boolean freeze,
                @Cached ConditionProfile freezeProfile) {
            if (freezeProfile.profile(!freeze)) {
                raiseCantUnfreezeError(self);
            }
            return self;
        }

        @Specialization
        protected Object cloneLong(long self, boolean freeze,
                @Cached ConditionProfile freezeProfile) {
            if (freezeProfile.profile(!freeze)) {
                raiseCantUnfreezeError(self);
            }
            return self;
        }

        @Specialization
        protected Object cloneFloat(double self, boolean freeze,
                @Cached ConditionProfile freezeProfile) {
            if (freezeProfile.profile(!freeze)) {
                raiseCantUnfreezeError(self);
            }
            return self;
        }

        @Specialization
        protected Object cloneNil(Nil nil, boolean freeze,
                @Cached ConditionProfile freezeProfile) {
            if (freezeProfile.profile(!freeze)) {
                raiseCantUnfreezeError(nil);
            }
            return nil;
        }

        @Specialization
        protected Object cloneBignum(RubyBignum object, boolean freeze,
                @Cached ConditionProfile freezeProfile) {
            if (freezeProfile.profile(!freeze)) {
                raiseCantUnfreezeError(object);
            }
            return object;
        }

        @Specialization
        protected Object cloneSymbol(RubySymbol symbol, boolean freeze,
                @Cached ConditionProfile freezeProfile) {
            if (freezeProfile.profile(!freeze)) {
                raiseCantUnfreezeError(symbol);
            }
            return symbol;
        }

        private void raiseCantUnfreezeError(Object self) {
            throw new RaiseException(getContext(), coreExceptions().argumentErrorCantUnfreeze(self, this));
        }

        private RubyClass executeSingletonClass(RubyDynamicObject newObject) {
            if (singletonClassNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                singletonClassNode = insert(SingletonClassNode.create());
            }

            return singletonClassNode.executeSingletonClass(newObject);
        }

    }

    @CoreMethod(names = "dup", taintFrom = 0)
    public abstract static class DupNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        protected Object dup(Object self,
                @Cached IsImmutableObjectNode isImmutableObjectNode,
                @Cached ConditionProfile immutableProfile,
                @Cached CopyNode copyNode,
                @Cached DispatchNode initializeDupNode) {
            if (immutableProfile.profile(isImmutableObjectNode.execute(self))) {
                return self;
            }

            final RubyDynamicObject newObject = copyNode.executeCopy((RubyDynamicObject) self);

            initializeDupNode.call(newObject, "initialize_dup", self);

            return newObject;
        }

    }

    @Primitive(name = "kernel_eval", lowerFixnum = 4)
    @ImportStatic({ StringCachingGuards.class, StringOperations.class })
    @ReportPolymorphism
    public abstract static class EvalNode extends PrimitiveArrayArgumentsNode {

        @Child private CreateEvalSourceNode createEvalSourceNode = new CreateEvalSourceNode();

        protected static class RootNodeWrapper {
            private final RubyRootNode rootNode;

            public RootNodeWrapper(RubyRootNode rootNode) {
                this.rootNode = rootNode;
            }

            public RubyRootNode getRootNode() {
                return rootNode;
            }
        }

        public abstract Object execute(VirtualFrame frame, Object target, RubyString source, RubyBinding binding,
                RubyString file, int line);

        // If the source defines new local variables, those should be set in the Binding.
        // So we have 2 specializations for whether or not the code defines new local variables.

        @Specialization(
                guards = {
                        "equalNode.execute(source.rope, cachedSource)",
                        "equalNode.execute(file.rope, cachedFile)",
                        "line == cachedLine",
                        "!assignsNewUserVariables(getDescriptor(cachedRootNode))",
                        "bindingDescriptor == getBindingDescriptor(binding)" },
                limit = "getCacheLimit()")
        protected Object evalBindingNoAddsVarsCached(
                Object target,
                RubyString source,
                RubyBinding binding,
                RubyString file,
                int line,
                @Cached("privatizeRope(source)") Rope cachedSource,
                @Cached("privatizeRope(file)") Rope cachedFile,
                @Cached("line") int cachedLine,
                @Cached("getBindingDescriptor(binding)") FrameDescriptor bindingDescriptor,
                @Cached("compileSource(cachedSource, getBindingFrame(binding), cachedFile, cachedLine)") RootNodeWrapper cachedRootNode,
                @Cached("createCallTarget(cachedRootNode)") RootCallTarget cachedCallTarget,
                @Cached("create(cachedCallTarget)") DirectCallNode callNode,
                @Cached RopeNodes.EqualNode equalNode) {
            final MaterializedFrame parentFrame = binding.getFrame();
            return eval(target, cachedRootNode, cachedCallTarget, callNode, parentFrame);
        }

        @Specialization(
                guards = {
                        "equalNode.execute(source.rope, cachedSource)",
                        "equalNode.execute(file.rope, cachedFile)",
                        "line == cachedLine",
                        "assignsNewUserVariables(getDescriptor(cachedRootNode))",
                        "!assignsNewUserVariables(getDescriptor(rootNodeToEval))",
                        "bindingDescriptor == getBindingDescriptor(binding)" },
                limit = "getCacheLimit()")
        protected Object evalBindingAddsVarsCached(
                Object target,
                RubyString source,
                RubyBinding binding,
                RubyString file,
                int line,
                @Cached("privatizeRope(source)") Rope cachedSource,
                @Cached("privatizeRope(file)") Rope cachedFile,
                @Cached("line") int cachedLine,
                @Cached("getBindingDescriptor(binding)") FrameDescriptor bindingDescriptor,
                @Cached("compileSource(cachedSource, getBindingFrame(binding), cachedFile, cachedLine)") RootNodeWrapper cachedRootNode,
                @Cached("getDescriptor(cachedRootNode).copy()") FrameDescriptor newBindingDescriptor,
                @Cached("compileSource(cachedSource, getBindingFrame(binding), newBindingDescriptor, cachedFile, cachedLine)") RootNodeWrapper rootNodeToEval,
                @Cached("createCallTarget(rootNodeToEval)") RootCallTarget cachedCallTarget,
                @Cached("create(cachedCallTarget)") DirectCallNode callNode,
                @Cached RopeNodes.EqualNode equalNode) {
            final MaterializedFrame parentFrame = BindingNodes.newFrame(binding, newBindingDescriptor);
            return eval(target, rootNodeToEval, cachedCallTarget, callNode, parentFrame);
        }

        @Specialization
        protected Object evalBindingUncached(
                Object target,
                RubyString source,
                RubyBinding binding,
                RubyString file,
                int line,
                @Cached IndirectCallNode callNode) {
            final CodeLoader.DeferredCall deferredCall = doEvalX(
                    target,
                    source.rope,
                    binding,
                    file.rope,
                    line);
            return deferredCall.call(callNode);
        }

        private Object eval(Object target, RootNodeWrapper rootNode, RootCallTarget callTarget, DirectCallNode callNode,
                MaterializedFrame parentFrame) {
            final InternalMethod method = new InternalMethod(
                    getContext(),
                    rootNode.getRootNode().getSharedMethodInfo(),
                    RubyArguments.getMethod(parentFrame).getLexicalScope(),
                    RubyArguments.getDeclarationContext(parentFrame),
                    rootNode.getRootNode().getSharedMethodInfo().getName(),
                    RubyArguments.getMethod(parentFrame).getDeclaringModule(),
                    Visibility.PUBLIC,
                    callTarget);

            return callNode.call(RubyArguments.pack(
                    parentFrame,
                    null,
                    method,
                    null,
                    target,
                    null,
                    EMPTY_ARGUMENTS));
        }

        @TruffleBoundary
        private CodeLoader.DeferredCall doEvalX(Object target, Rope source, RubyBinding binding, Rope file, int line) {
            final MaterializedFrame frame = BindingNodes.newFrame(binding.getFrame());
            final DeclarationContext declarationContext = RubyArguments.getDeclarationContext(frame);
            final FrameDescriptor descriptor = frame.getFrameDescriptor();
            RubyRootNode rootNode = buildRootNode(source, frame, file, line, false);
            if (assignsNewUserVariables(descriptor)) {
                binding.setFrame(frame);
            }
            return getContext().getCodeLoader().prepareExecute(
                    ParserContext.EVAL,
                    declarationContext,
                    rootNode,
                    frame,
                    target);
        }

        protected RubyRootNode buildRootNode(Rope sourceText, MaterializedFrame parentFrame, Rope file, int line,
                boolean ownScopeForAssignments) {
            //intern() to improve footprint
            final String sourceFile = RopeOperations.decodeRope(file).intern();
            final RubySource source = createEvalSourceNode.createEvalSource(sourceText, "eval", sourceFile, line);
            return getContext()
                    .getCodeLoader()
                    .parse(source, ParserContext.EVAL, parentFrame, null, ownScopeForAssignments, this);
        }

        protected RootNodeWrapper compileSource(Rope sourceText, MaterializedFrame parentFrame, Rope file, int line) {
            return new RootNodeWrapper(buildRootNode(sourceText, parentFrame, file, line, true));
        }

        protected RootNodeWrapper compileSource(Rope sourceText, MaterializedFrame parentFrame,
                FrameDescriptor additionalVariables, Rope file, int line) {
            return compileSource(sourceText, BindingNodes.newFrame(parentFrame, additionalVariables), file, line);
        }

        protected RootCallTarget createCallTarget(RootNodeWrapper rootNode) {
            return Truffle.getRuntime().createCallTarget(rootNode.rootNode);
        }

        protected FrameDescriptor getBindingDescriptor(RubyBinding binding) {
            return BindingNodes.getFrameDescriptor(binding);
        }

        protected FrameDescriptor getDescriptor(RootNodeWrapper rootNode) {
            return rootNode.getRootNode().getFrameDescriptor();
        }

        protected MaterializedFrame getBindingFrame(RubyBinding binding) {
            return binding.getFrame();
        }

        protected static boolean assignsNewUserVariables(FrameDescriptor descriptor) {
            for (FrameSlot slot : descriptor.getSlots()) {
                if (!BindingNodes.isHiddenVariable(slot.getIdentifier())) {
                    return true;
                }
            }
            return false;
        }

        protected int getCacheLimit() {
            return getContext().getOptions().EVAL_CACHE;
        }
    }

    @CoreMethod(names = "freeze")
    public abstract static class KernelFreezeNode extends CoreMethodArrayArgumentsNode {

        @Specialization(limit = "getRubyLibraryCacheLimit()", guards = "!isRubyDynamicObject(self)")
        protected Object freeze(Object self,
                @CachedLibrary("self") RubyLibrary rubyLibrary) {
            rubyLibrary.freeze(self);
            return self;
        }

        @Specialization(limit = "getRubyLibraryCacheLimit()", guards = "isRubyDynamicObject(self)")
        protected Object freezeDynamicObject(Object self,
                @CachedLibrary("self") RubyLibrary rubyLibrary,
                @CachedLibrary(limit = "1") RubyLibrary rubyLibraryMetaClass,
                @Cached ConditionProfile singletonProfile,
                @Cached MetaClassNode metaClassNode) {
            final RubyClass metaClass = metaClassNode.execute(self);
            if (singletonProfile.profile(metaClass.isSingleton &&
                    !(RubyGuards.isRubyClass(self) && ((RubyClass) self).isSingleton))) {
                if (!rubyLibraryMetaClass.isFrozen(metaClass)) {
                    rubyLibraryMetaClass.freeze(metaClass);
                }
            }
            rubyLibrary.freeze(self);
            return self;
        }

    }

    @ReportPolymorphism
    @CoreMethod(names = "frozen?")
    public abstract static class KernelFrozenNode extends CoreMethodArrayArgumentsNode {

        @Specialization(limit = "getRubyLibraryCacheLimit()")
        protected boolean isFrozen(Object self,
                @CachedLibrary("self") RubyLibrary rubyLibrary) {
            return rubyLibrary.isFrozen(self);
        }

    }

    @CoreMethod(names = "hash")
    public abstract static class HashNode extends CoreMethodArrayArgumentsNode {

        private static final int CLASS_SALT = 55927484; // random number, stops hashes for similar values but different classes being the same, static because we want deterministic hashes

        @Specialization
        protected long hash(int value) {
            return getContext().getHashing(this).hash(CLASS_SALT, value);
        }

        @Specialization
        protected long hash(long value) {
            return getContext().getHashing(this).hash(CLASS_SALT, value);
        }

        @Specialization
        protected long hash(double value) {
            return getContext().getHashing(this).hash(CLASS_SALT, Double.doubleToRawLongBits(value));
        }

        @Specialization
        protected long hash(boolean value) {
            return getContext().getHashing(this).hash(CLASS_SALT, Boolean.valueOf(value).hashCode());
        }

        @Specialization
        protected long hashBignum(RubyBignum value) {
            return getContext().getHashing(this).hash(CLASS_SALT, BigIntegerOps.hashCode(value));
        }

        @TruffleBoundary
        @Specialization
        protected int hash(Nil self) {
            // TODO(CS 8 Jan 15) we shouldn't use the Java class hierarchy like this - every class should define it's
            // own @CoreMethod hash
            return System.identityHashCode(self);
        }

        @TruffleBoundary
        @Specialization(guards = "!isRubyBignum(self)")
        protected int hash(RubyDynamicObject self) {
            // TODO(CS 8 Jan 15) we shouldn't use the Java class hierarchy like this - every class should define it's
            // own @CoreMethod hash
            return System.identityHashCode(self);
        }
    }

    @CoreMethod(names = "initialize_copy", required = 1)
    public abstract static class InitializeCopyNode extends CoreMethodArrayArgumentsNode {

        @Child protected ReferenceEqualNode equalNode = ReferenceEqualNode.create();

        @Specialization(guards = "equalNode.executeReferenceEqual(self, from)")
        protected Object initializeCopySame(Object self, Object from) {
            return self;
        }

        @Specialization(guards = "!equalNode.executeReferenceEqual(self, from)")
        protected Object initializeCopy(Object self, Object from,
                @Cached CheckFrozenNode checkFrozenNode,
                @Cached LogicalClassNode lhsClassNode,
                @Cached LogicalClassNode rhsClassNode,
                @Cached BranchProfile errorProfile) {
            checkFrozenNode.execute(self);
            if (lhsClassNode.executeLogicalClass(self) != rhsClassNode.executeLogicalClass(from)) {
                errorProfile.enter();
                throw new RaiseException(
                        getContext(),
                        coreExceptions().typeError("initialize_copy should take same class object", this));
            }

            return self;
        }
    }

    @CoreMethod(names = { "initialize_dup", "initialize_clone" }, required = 1)
    public abstract static class InitializeDupCloneNode extends CoreMethodArrayArgumentsNode {

        @Child private DispatchNode initializeCopyNode = DispatchNode.create();

        @Specialization
        protected Object initializeDup(VirtualFrame frame, RubyDynamicObject self, RubyDynamicObject from) {
            return initializeCopyNode.call(self, "initialize_copy", from);
        }

    }

    @CoreMethod(names = "instance_of?", required = 1)
    public abstract static class InstanceOfNode extends CoreMethodArrayArgumentsNode {

        @Child private LogicalClassNode classNode = LogicalClassNode.create();

        @Specialization
        protected boolean instanceOf(Object self, RubyModule module) {
            return classNode.executeLogicalClass(self) == module;
        }

    }

    @CoreMethod(names = "instance_variable_defined?", required = 1)
    @NodeChild(value = "object", type = RubyNode.class)
    @NodeChild(value = "name", type = RubyNode.class)
    public abstract static class InstanceVariableDefinedNode extends CoreMethodNode {

        @CreateCast("name")
        protected RubyNode coerceToString(RubyNode name) {
            return NameToJavaStringNode.create(name);
        }

        @Specialization
        protected boolean isInstanceVariableDefinedBoolean(boolean object, String name) {
            return false;
        }

        @Specialization
        protected boolean isInstanceVariableDefinedInt(int object, String name) {
            return false;
        }

        @Specialization
        protected boolean isInstanceVariableDefinedLong(long object, String name) {
            return false;
        }

        @Specialization
        protected boolean isInstanceVariableDefinedDouble(double object, String name) {
            return false;
        }

        @Specialization
        protected boolean isInstanceVariableDefinedNil(Nil object, String name) {
            return false;
        }

        @Specialization
        protected boolean isInstanceVariableDefinedSymbolOrNil(RubySymbol object, String name) {
            return false;
        }

        @TruffleBoundary
        @Specialization
        protected boolean isInstanceVariableDefined(RubyDynamicObject object, String name) {
            final String ivar = SymbolTable.checkInstanceVariableName(getContext(), name, object, this);
            return object.getShape().hasProperty(ivar);
        }

    }

    @CoreMethod(names = "instance_variable_get", required = 1)
    @NodeChild(value = "object", type = RubyNode.class)
    @NodeChild(value = "name", type = RubyNode.class)
    public abstract static class InstanceVariableGetNode extends CoreMethodNode {

        @CreateCast("name")
        protected RubyNode coerceName(RubyNode name) {
            return NameToJavaStringNode.create(name);
        }

        @Specialization
        protected Object instanceVariableGetSymbol(RubyDynamicObject object, String name,
                @Cached CheckIVarNameNode checkIVarNameNode,
                @CachedLibrary(limit = "getDynamicObjectCacheLimit()") DynamicObjectLibrary objectLibrary) {
            checkIVarNameNode.execute(object, name);
            return objectLibrary.getOrDefault(object, name, nil);
        }
    }

    @CoreMethod(names = "instance_variable_set", raiseIfFrozenSelf = true, required = 2)
    @NodeChild(value = "object", type = RubyNode.class)
    @NodeChild(value = "name", type = RubyNode.class)
    @NodeChild(value = "value", type = RubyNode.class)
    public abstract static class InstanceVariableSetNode extends CoreMethodNode {

        @CreateCast("name")
        protected RubyNode coerceName(RubyNode name) {
            return NameToJavaStringNode.create(name);
        }

        @Specialization
        protected Object instanceVariableSet(RubyDynamicObject object, String name, Object value,
                @Cached CheckIVarNameNode checkIVarNameNode,
                @Cached WriteObjectFieldNode writeNode) {
            checkIVarNameNode.execute(object, name);
            writeNode.execute(object, name, value);
            return value;
        }
    }

    @CoreMethod(names = "remove_instance_variable", raiseIfFrozenSelf = true, required = 1)
    @NodeChild(value = "object", type = RubyNode.class)
    @NodeChild(value = "name", type = RubyNode.class)
    public abstract static class RemoveInstanceVariableNode extends CoreMethodNode {

        @CreateCast("name")
        protected RubyNode coerceToString(RubyNode name) {
            return NameToJavaStringNode.create(name);
        }

        @TruffleBoundary
        @Specialization
        protected Object removeInstanceVariable(RubyDynamicObject object, String name) {
            final String ivar = SymbolTable.checkInstanceVariableName(getContext(), name, object, this);
            final Object value = DynamicObjectLibrary.getUncached().getOrDefault(object, ivar, nil);

            if (SharedObjects.isShared(object)) {
                synchronized (object) {
                    removeField(object, name);
                }
            } else {
                removeField(object, name);
            }
            return value;
        }

        private void removeField(RubyDynamicObject object, String name) {
            if (!DynamicObjectLibrary.getUncached().removeKey(object, name)) {
                throw new RaiseException(
                        getContext(),
                        coreExceptions().nameErrorInstanceVariableNotDefined(name, object, this));
            }
        }
    }

    @CoreMethod(names = "instance_variables")
    public abstract static class InstanceVariablesNode extends CoreMethodArrayArgumentsNode {

        @Child private ObjectInstanceVariablesNode instanceVariablesNode = ObjectInstanceVariablesNodeFactory
                .create(null);

        @Specialization
        protected RubyArray instanceVariables(Object self) {
            return instanceVariablesNode.executeGetIVars(self);
        }

    }

    @CoreMethod(names = { "is_a?", "kind_of?" }, required = 1)
    public abstract static class KernelIsANode extends CoreMethodArrayArgumentsNode {

        @Specialization
        protected boolean isA(Object self, RubyModule module,
                @Cached IsANode isANode) {
            return isANode.executeIsA(self, module);
        }

        @Specialization(guards = "!isRubyModule(module)")
        protected boolean isATypeError(Object self, Object module) {
            throw new RaiseException(getContext(), coreExceptions().typeError("class or module required", this));
        }

    }

    @CoreMethod(names = "lambda", isModuleFunction = true, needsBlock = true)
    public abstract static class LambdaNode extends CoreMethodArrayArgumentsNode {

        @Child private WarnNode warnNode;

        @TruffleBoundary
        @Specialization
        protected RubyProc lambda(NotProvided block,
                @Cached("create(nil)") FindAndReadDeclarationVariableNode readNode) {
            final MaterializedFrame parentFrame = getContext()
                    .getCallStack()
                    .getCallerFrameIgnoringSend(FrameAccess.MATERIALIZE)
                    .materialize();
            Object parentBlock = readNode
                    .execute(parentFrame, TranslatorEnvironment.METHOD_BLOCK_NAME);

            if (parentBlock == nil) {
                throw new RaiseException(
                        getContext(),
                        coreExceptions().argumentError("tried to create Proc object without a block", this));
            } else {
                warnProcWithoutBlock();
            }

            Node callNode = getContext().getCallStack().getCallerNode(2, true);
            if (isLiteralBlock(callNode)) {
                return lambdaFromBlock((RubyProc) parentBlock);
            } else {
                return (RubyProc) parentBlock;
            }
        }

        @Specialization(guards = "isLiteralBlock(block)")
        protected RubyProc lambdaFromBlock(RubyProc block) {
            return ProcOperations.createLambdaFromBlock(getContext(), block);
        }

        @Specialization(guards = "!isLiteralBlock(block)")
        protected RubyProc lambdaFromExistingProc(RubyProc block) {
            return block;
        }

        @TruffleBoundary
        protected boolean isLiteralBlock(RubyProc block) {
            Node callNode = getContext().getCallStack().getCallerNodeIgnoringSend();
            return isLiteralBlock(callNode);
        }

        private boolean isLiteralBlock(Node callNode) {
            RubyCallNode rubyCallNode = NodeUtil.findParent(callNode, RubyCallNode.class);
            return rubyCallNode != null && rubyCallNode.hasLiteralBlock();
        }

        private void warnProcWithoutBlock() {
            if (warnNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                warnNode = insert(new WarnNode());
            }

            if (warnNode.shouldWarn()) {
                final SourceSection sourceSection = getContext().getCallStack().getTopMostUserSourceSection();
                warnNode.warningMessage(sourceSection, "tried to create Proc object without a block");
            }
        }

    }

    @CoreMethod(names = "__method__", isModuleFunction = true)
    public abstract static class MethodNameNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        protected RubySymbol methodName() {
            // the "original/definition name" of the method.
            return getSymbol(
                    getContext().getCallStack().getCallingMethodIgnoringSend().getSharedMethodInfo().getName());
        }

    }

    @CoreMethod(names = "method", required = 1)
    @NodeChild(value = "receiver", type = RubyNode.class)
    @NodeChild(value = "name", type = RubyNode.class)
    public abstract static class MethodNode extends CoreMethodNode {

        @Child private GetMethodObjectNode getMethodObjectNode = GetMethodObjectNode.create(true);

        @CreateCast("name")
        protected RubyNode coerceToString(RubyNode name) {
            return ToStringOrSymbolNodeGen.create(name);
        }

        @Specialization
        protected RubyMethod method(VirtualFrame frame, Object self, Object name) {
            return getMethodObjectNode.executeGetMethodObject(frame, self, name);
        }

    }

    public abstract static class GetMethodObjectNode extends RubyContextNode {

        public static GetMethodObjectNode create(boolean ignoreVisibility) {
            return GetMethodObjectNodeGen.create(ignoreVisibility);
        }

        private final DispatchConfiguration dispatchConfig;

        @Child private AllocateHelperNode allocateNode = AllocateHelperNode.create();
        @Child private NameToJavaStringNode nameToJavaStringNode = NameToJavaStringNode.create();
        @Child private LookupMethodOnSelfNode lookupMethodNode;
        @Child private DispatchNode respondToMissingNode = DispatchNode.create();
        @Child private BooleanCastNode booleanCastNode = BooleanCastNode.create();

        public GetMethodObjectNode(boolean ignoreVisibility) {
            this.dispatchConfig = ignoreVisibility ? PRIVATE : PUBLIC;
            lookupMethodNode = LookupMethodOnSelfNode.create();
        }

        public abstract RubyMethod executeGetMethodObject(VirtualFrame frame, Object self, Object name);

        @Specialization
        protected RubyMethod method(VirtualFrame frame, Object self, Object name,
                @Cached ConditionProfile notFoundProfile,
                @Cached ConditionProfile respondToMissingProfile) {
            final String normalizedName = nameToJavaStringNode.execute(name);
            InternalMethod method = lookupMethodNode
                    .lookup(frame, self, normalizedName, dispatchConfig);

            if (notFoundProfile.profile(method == null)) {
                final Object respondToMissing = respondToMissingNode
                        .call(self, "respond_to_missing?", name, dispatchConfig.ignoreVisibility);
                if (respondToMissingProfile.profile(booleanCastNode.executeToBoolean(respondToMissing))) {
                    final InternalMethod methodMissing = lookupMethodNode
                            .lookup(frame, self, "method_missing", dispatchConfig);
                    method = createMissingMethod(self, name, normalizedName, methodMissing);
                } else {
                    throw new RaiseException(
                            getContext(),
                            coreExceptions().nameErrorUndefinedMethod(
                                    normalizedName,
                                    coreLibrary().getLogicalClass(self),
                                    this));
                }
            }
            final RubyMethod instance = new RubyMethod(
                    coreLibrary().methodClass,
                    RubyLanguage.methodShape,
                    self,
                    method);
            allocateNode.trace(getContext().getLanguage(), getContext(), instance);
            return instance;
        }

        @TruffleBoundary
        private InternalMethod createMissingMethod(Object self, Object name, String normalizedName,
                InternalMethod methodMissing) {
            final SharedMethodInfo info = methodMissing
                    .getSharedMethodInfo()
                    .convertMethodMissingToMethod(normalizedName);

            final RubyNode newBody = new CallMethodMissingWithStaticName(name);
            final RubyRootNode newRootNode = new RubyRootNode(
                    getContext(),
                    info.getSourceSection(),
                    new FrameDescriptor(nil),
                    info,
                    newBody,
                    Split.HEURISTIC);
            final RootCallTarget newCallTarget = Truffle.getRuntime().createCallTarget(newRootNode);

            final RubyClass module = coreLibrary().getMetaClass(self);
            return new InternalMethod(
                    getContext(),
                    info,
                    methodMissing.getLexicalScope(),
                    DeclarationContext.NONE,
                    normalizedName,
                    module,
                    Visibility.PUBLIC,
                    newCallTarget);
        }

        private static class CallMethodMissingWithStaticName extends RubyContextSourceNode {

            private final Object methodName;
            @Child private DispatchNode methodMissing = DispatchNode.create();

            public CallMethodMissingWithStaticName(Object methodName) {
                this.methodName = methodName;
            }

            @Override
            public Object execute(VirtualFrame frame) {
                final Object[] originalUserArguments = RubyArguments.getArguments(frame);
                final Object[] newUserArguments = ArrayUtils.unshift(originalUserArguments, methodName);
                return methodMissing.callWithBlock(
                        RubyArguments.getSelf(frame),
                        "method_missing",
                        RubyArguments.getBlock(frame),
                        newUserArguments);
            }
        }

    }

    @CoreMethod(names = "methods", optional = 1)
    @NodeChild(value = "object", type = RubyNode.class)
    @NodeChild(value = "regular", type = RubyNode.class)
    public abstract static class MethodsNode extends CoreMethodNode {

        @CreateCast("regular")
        protected RubyNode coerceToBoolean(RubyNode regular) {
            return BooleanCastWithDefaultNodeGen.create(true, regular);
        }

        @TruffleBoundary
        @Specialization(guards = "regular")
        protected RubyArray methodsRegular(Object self, boolean regular,
                @Cached MetaClassNode metaClassNode) {
            final RubyModule metaClass = metaClassNode.execute(self);

            Object[] objects = metaClass.fields
                    .filterMethodsOnObject(getContext(), regular, MethodFilter.PUBLIC_PROTECTED)
                    .toArray();
            return createArray(objects);
        }

        @Specialization(guards = "!regular")
        protected RubyArray methodsSingleton(VirtualFrame frame, Object self, boolean regular,
                @Cached SingletonMethodsNode singletonMethodsNode) {
            return singletonMethodsNode.executeSingletonMethods(frame, self, false);
        }

    }

    @CoreMethod(names = "nil?", needsSelf = false)
    public abstract static class IsNilNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        protected boolean isNil() {
            return false;
        }
    }

    // A basic Kernel#p for debugging core, overridden later in kernel.rb
    @NonStandard
    @CoreMethod(names = "p", isModuleFunction = true, required = 1)
    public abstract static class DebugPrintNode extends CoreMethodArrayArgumentsNode {

        @Child private DispatchNode callInspectNode = DispatchNode.create();

        @Specialization
        protected Object p(VirtualFrame frame, Object value) {
            Object inspected = callInspectNode.call(value, "inspect");
            print(inspected);
            return value;
        }

        @SuppressFBWarnings("OS")
        @TruffleBoundary
        private void print(Object inspected) {
            final PrintStream stream = BacktraceFormatter.printStreamFor(getContext().getEnv().out());
            stream.println(inspected.toString());
        }
    }

    @CoreMethod(names = "private_methods", optional = 1)
    @NodeChild(value = "object", type = RubyNode.class)
    @NodeChild(value = "includeAncestors", type = RubyNode.class)
    public abstract static class PrivateMethodsNode extends CoreMethodNode {

        @Child private MetaClassNode metaClassNode = MetaClassNode.create();

        @CreateCast("includeAncestors")
        protected RubyNode coerceToBoolean(RubyNode includeAncestors) {
            return BooleanCastWithDefaultNodeGen.create(true, includeAncestors);
        }

        @TruffleBoundary
        @Specialization
        protected RubyArray privateMethods(Object self, boolean includeAncestors) {
            RubyClass metaClass = metaClassNode.execute(self);

            Object[] objects = metaClass.fields
                    .filterMethodsOnObject(getContext(), includeAncestors, MethodFilter.PRIVATE)
                    .toArray();
            return createArray(objects);
        }

    }

    @CoreMethod(names = "proc", isModuleFunction = true, needsBlock = true)
    public abstract static class ProcNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        protected RubyProc proc(VirtualFrame frame, Object maybeBlock,
                @Cached ProcNewNode procNewNode) {
            return procNewNode.executeProcNew(frame, coreLibrary().procClass, ArrayUtils.EMPTY_ARRAY, maybeBlock);
        }

    }

    @CoreMethod(names = "protected_methods", optional = 1)
    @NodeChild(value = "object", type = RubyNode.class)
    @NodeChild(value = "includeAncestors", type = RubyNode.class)
    public abstract static class ProtectedMethodsNode extends CoreMethodNode {

        @Child private MetaClassNode metaClassNode = MetaClassNode.create();

        @CreateCast("includeAncestors")
        protected RubyNode coerceToBoolean(RubyNode includeAncestors) {
            return BooleanCastWithDefaultNodeGen.create(true, includeAncestors);
        }

        @TruffleBoundary
        @Specialization
        protected RubyArray protectedMethods(Object self, boolean includeAncestors) {
            final RubyClass metaClass = metaClassNode.execute(self);

            Object[] objects = metaClass.fields
                    .filterMethodsOnObject(getContext(), includeAncestors, MethodFilter.PROTECTED)
                    .toArray();
            return createArray(objects);
        }

    }

    @CoreMethod(names = "public_method", required = 1)
    @NodeChild(value = "receiver", type = RubyNode.class)
    @NodeChild(value = "name", type = RubyNode.class)
    public abstract static class PublicMethodNode extends CoreMethodNode {

        @Child private GetMethodObjectNode getMethodObjectNode = GetMethodObjectNode.create(false);

        @CreateCast("name")
        protected RubyNode coerceToString(RubyNode name) {
            return ToStringOrSymbolNodeGen.create(name);
        }

        @Specialization
        protected RubyMethod publicMethod(VirtualFrame frame, Object self, Object name) {
            return getMethodObjectNode.executeGetMethodObject(frame, self, name);
        }

    }

    @CoreMethod(names = "public_methods", optional = 1)
    @NodeChild(value = "object", type = RubyNode.class)
    @NodeChild(value = "includeAncestors", type = RubyNode.class)
    public abstract static class PublicMethodsNode extends CoreMethodNode {

        @Child private MetaClassNode metaClassNode = MetaClassNode.create();

        @CreateCast("includeAncestors")
        protected RubyNode coerceToBoolean(RubyNode includeAncestors) {
            return BooleanCastWithDefaultNodeGen.create(true, includeAncestors);
        }

        @TruffleBoundary
        @Specialization
        protected RubyArray publicMethods(Object self, boolean includeAncestors) {
            final RubyModule metaClass = metaClassNode.execute(self);

            Object[] objects = metaClass.fields
                    .filterMethodsOnObject(getContext(), includeAncestors, MethodFilter.PUBLIC)
                    .toArray();
            return createArray(objects);
        }

    }

    @CoreMethod(names = "public_send", needsBlock = true, required = 1, rest = true)
    public abstract static class PublicSendNode extends CoreMethodArrayArgumentsNode {

        @Child private DispatchNode dispatchNode = DispatchNode.create(PUBLIC);
        @Child private ReadCallerFrameNode readCallerFrame = ReadCallerFrameNode.create();
        @Child private NameToJavaStringNode nameToJavaString = NameToJavaStringNode.create();

        @Specialization
        protected Object send(VirtualFrame frame, Object self, Object name, Object[] args, NotProvided block) {
            return send(frame, self, name, args, (RubyProc) null);
        }

        @Specialization
        protected Object send(VirtualFrame frame, Object self, Object name, Object[] args, RubyProc block) {
            DeclarationContext context = RubyArguments.getDeclarationContext(readCallerFrame.execute(frame));
            RubyArguments.setDeclarationContext(frame, context);

            return dispatchNode.dispatch(frame, self, nameToJavaString.execute(name), block, args);
        }

    }

    @CoreMethod(names = "respond_to?", required = 1, optional = 1)
    @NodeChild(value = "object", type = RubyNode.class)
    @NodeChild(value = "name", type = RubyNode.class)
    @NodeChild(value = "includeProtectedAndPrivate", type = RubyNode.class)
    public abstract static class RespondToNode extends CoreMethodNode {

        @Child private DispatchNode dispatch;
        @Child private DispatchNode dispatchIgnoreVisibility;
        @Child private DispatchNode dispatchRespondToMissing;
        @Child private DispatchNode respondToMissingNode;
        @Child private BooleanCastNode booleanCastNode;
        @Child private ReadCallerFrameNode readCallerFrame = ReadCallerFrameNode.create();
        private final ConditionProfile ignoreVisibilityProfile = ConditionProfile.create();
        private final ConditionProfile isTrueProfile = ConditionProfile.create();
        private final ConditionProfile respondToMissingProfile = ConditionProfile.create();

        public RespondToNode() {
            dispatch = DispatchNode.create(PUBLIC_DOES_RESPOND);
            dispatchIgnoreVisibility = DispatchNode.create(PRIVATE_DOES_RESPOND);
            dispatchRespondToMissing = DispatchNode.create(PRIVATE_DOES_RESPOND);
        }

        public abstract boolean executeDoesRespondTo(VirtualFrame frame, Object object, Object name,
                boolean includeProtectedAndPrivate);

        @CreateCast("includeProtectedAndPrivate")
        protected RubyNode coerceToBoolean(RubyNode includeProtectedAndPrivate) {
            return BooleanCastWithDefaultNodeGen.create(false, includeProtectedAndPrivate);
        }

        @Specialization
        protected boolean doesRespondToString(
                VirtualFrame frame,
                Object object,
                RubyString name,
                boolean includeProtectedAndPrivate,
                @Cached ToJavaStringNode toJavaString) {
            final boolean ret;

            if (ignoreVisibilityProfile.profile(includeProtectedAndPrivate)) {
                ret = dispatchIgnoreVisibility.doesRespondTo(frame, toJavaString.executeToJavaString(name), object);
            } else {
                if (toJavaString.executeToJavaString(name).equals("foo")) {
                    final DeclarationContext declarationContext = RubyArguments.tryGetDeclarationContext(frame);
                }
                ret = dispatch.doesRespondTo(frame, toJavaString.executeToJavaString(name), object);
            }

            if (isTrueProfile.profile(ret)) {
                return true;
            } else if (respondToMissingProfile
                    .profile(dispatchRespondToMissing.doesRespondTo(frame, "respond_to_missing?", object))) {
                return respondToMissing(
                        frame,
                        object,
                        getSymbol(name.rope),
                        includeProtectedAndPrivate);
            } else {
                return false;
            }
        }

        @Specialization
        protected boolean doesRespondToSymbol(
                VirtualFrame frame,
                Object object,
                RubySymbol name,
                boolean includeProtectedAndPrivate,
                @Cached ToJavaStringNode toJavaString) {
            final boolean ret;

            if (ignoreVisibilityProfile.profile(includeProtectedAndPrivate)) {
                ret = dispatchIgnoreVisibility.doesRespondTo(frame, toJavaString.executeToJavaString(name), object);
            } else {
                if (toJavaString.executeToJavaString(name).equals("foo")) {
                    DeclarationContext context = RubyArguments.getDeclarationContext(readCallerFrame.execute(frame));
                    final DeclarationContext declarationContext = RubyArguments.tryGetDeclarationContext(frame);
                    System.out.println("FFF");
                }
                ret = dispatch.doesRespondTo(frame, toJavaString.executeToJavaString(name), object);
            }

            if (isTrueProfile.profile(ret)) {
                return true;
            } else if (respondToMissingProfile
                    .profile(dispatchRespondToMissing.doesRespondTo(frame, "respond_to_missing?", object))) {
                return respondToMissing(frame, object, name, includeProtectedAndPrivate);
            } else {
                return false;
            }
        }

        private boolean respondToMissing(VirtualFrame frame, Object object, RubySymbol name,
                boolean includeProtectedAndPrivate) {
            if (respondToMissingNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                respondToMissingNode = insert(DispatchNode.create());
            }

            if (booleanCastNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                booleanCastNode = insert(BooleanCastNode.create());
            }

            return booleanCastNode.executeToBoolean(
                    respondToMissingNode.call(object, "respond_to_missing?", name, includeProtectedAndPrivate));
        }
    }

    @CoreMethod(names = "respond_to_missing?", required = 2)
    public abstract static class RespondToMissingNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        protected boolean doesRespondToMissingString(Object object, RubyString name, Object unusedIncludeAll) {
            return false;
        }

        @Specialization
        protected boolean doesRespondToMissingSymbol(Object object, RubySymbol name, Object unusedIncludeAll) {
            return false;
        }

    }

    @CoreMethod(names = "set_trace_func", isModuleFunction = true, required = 1)
    public abstract static class SetTraceFuncNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        protected Object setTraceFunc(Nil traceFunc) {
            getContext().getTraceManager().setTraceFunc(null);
            return nil;
        }

        @Specialization
        protected RubyProc setTraceFunc(RubyProc traceFunc) {
            getContext().getTraceManager().setTraceFunc(traceFunc);
            return traceFunc;
        }
    }

    @CoreMethod(names = "singleton_class")
    public abstract static class SingletonClassMethodNode extends CoreMethodArrayArgumentsNode {

        @Child private SingletonClassNode singletonClassNode = SingletonClassNode.create();

        public abstract RubyClass executeSingletonClass(Object self);

        @Specialization
        protected RubyClass singletonClass(Object self) {
            return singletonClassNode.executeSingletonClass(self);
        }

    }

    @CoreMethod(names = "singleton_method", required = 1)
    @NodeChild(value = "object", type = RubyNode.class)
    @NodeChild(value = "name", type = RubyNode.class)
    public abstract static class SingletonMethodNode extends CoreMethodNode {

        @Child private AllocateHelperNode allocateNode = AllocateHelperNode.create();
        @Child private MetaClassNode metaClassNode = MetaClassNode.create();

        @CreateCast("name")
        protected RubyNode coerceToString(RubyNode name) {
            return NameToJavaStringNode.create(name);
        }

        @Specialization
        protected RubyMethod singletonMethod(Object self, String name,
                @Cached BranchProfile errorProfile,
                @Cached ConditionProfile singletonProfile,
                @Cached ConditionProfile methodProfile) {
            final RubyClass metaClass = metaClassNode.execute(self);

            if (singletonProfile.profile(metaClass.isSingleton)) {
                final InternalMethod method = metaClass.fields.getMethod(name);
                if (methodProfile.profile(method != null && !method.isUndefined())) {
                    final RubyMethod instance = new RubyMethod(
                            coreLibrary().methodClass,
                            RubyLanguage.methodShape,
                            self,
                            method);
                    allocateNode.trace(instance, this);
                    return instance;
                }
            }

            errorProfile.enter();
            throw new RaiseException(
                    getContext(),
                    coreExceptions().nameErrorUndefinedSingletonMethod(name, self, this));
        }

    }

    @CoreMethod(names = "singleton_methods", optional = 1)
    @NodeChild(value = "object", type = RubyNode.class)
    @NodeChild(value = "includeAncestors", type = RubyNode.class)
    public abstract static class SingletonMethodsNode extends CoreMethodNode {

        public static SingletonMethodsNode create() {
            return SingletonMethodsNodeFactory.create(null, null);
        }

        public abstract RubyArray executeSingletonMethods(VirtualFrame frame, Object self, boolean includeAncestors);

        @CreateCast("includeAncestors")
        protected RubyNode coerceToBoolean(RubyNode includeAncestors) {
            return BooleanCastWithDefaultNodeGen.create(true, includeAncestors);
        }

        @TruffleBoundary
        @Specialization
        protected RubyArray singletonMethods(Object self, boolean includeAncestors,
                @Cached MetaClassNode metaClassNode) {
            final RubyClass metaClass = metaClassNode.execute(self);

            if (!metaClass.isSingleton) {
                return ArrayHelpers.createEmptyArray(getContext());
            }

            Object[] objects = metaClass.fields
                    .filterSingletonMethods(getContext(), includeAncestors, MethodFilter.PUBLIC_PROTECTED)
                    .toArray();
            return createArray(objects);
        }

    }

    @NodeChild(value = "duration", type = RubyNode.class)
    @CoreMethod(names = "sleep", isModuleFunction = true, optional = 1)
    public abstract static class SleepNode extends CoreMethodNode {

        @CreateCast("duration")
        protected RubyNode coerceDuration(RubyNode duration) {
            return DurationToMillisecondsNodeGen.create(false, duration);
        }

        @Specialization
        protected long sleep(long durationInMillis,
                @Cached GetCurrentRubyThreadNode getCurrentRubyThreadNode,
                @Cached BranchProfile errorProfile) {
            if (durationInMillis < 0) {
                errorProfile.enter();
                throw new RaiseException(
                        getContext(),
                        coreExceptions().argumentError("time interval must be positive", this));
            }

            final RubyThread thread = getCurrentRubyThreadNode.execute();

            // Clear the wakeUp flag, following Ruby semantics:
            // it should only be considered if we are inside the sleep when Thread#{run,wakeup} is called.
            thread.wakeUp.set(false);

            return sleepFor(getContext(), thread, durationInMillis, this);
        }

        @TruffleBoundary
        public static long sleepFor(RubyContext context, RubyThread thread, long durationInMillis,
                Node currentNode) {
            assert durationInMillis >= 0;

            // We want a monotonic clock to measure sleep duration
            final long startInNanos = System.nanoTime();

            context.getThreadManager().runUntilResult(currentNode, () -> {
                final long nowInNanos = System.nanoTime();
                final long sleptInNanos = nowInNanos - startInNanos;
                final long sleptInMillis = TimeUnit.NANOSECONDS.toMillis(sleptInNanos);

                if (sleptInMillis >= durationInMillis || thread.wakeUp.getAndSet(false)) {
                    return BlockingAction.SUCCESS;
                }

                Thread.sleep(durationInMillis - sleptInMillis);
                return BlockingAction.SUCCESS;
            });

            return TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - startInNanos);
        }

    }

    @CoreMethod(names = { "format", "sprintf" }, isModuleFunction = true, rest = true, required = 1, taintFrom = 1)
    @ImportStatic({ StringCachingGuards.class, StringOperations.class })
    @ReportPolymorphism
    public abstract static class SprintfNode extends CoreMethodArrayArgumentsNode {

        @Child private MakeStringNode makeStringNode;
        @Child private RubyLibrary rubyLibrary;
        @Child private BooleanCastNode readDebugGlobalNode = BooleanCastNodeGen
                .create(ReadGlobalVariableNodeGen.create("$DEBUG"));

        private final BranchProfile exceptionProfile = BranchProfile.create();
        private final ConditionProfile resizeProfile = ConditionProfile.create();

        @Specialization(
                guards = {
                        "equalNode.execute(format.rope, cachedFormat)",
                        "isDebug(frame) == cachedIsDebug" },
                limit = "getRubyLibraryCacheLimit()")
        protected RubyString formatCached(VirtualFrame frame, RubyString format, Object[] arguments,
                @Cached("isDebug(frame)") boolean cachedIsDebug,
                @Cached("privatizeRope(format)") Rope cachedFormat,
                @Cached("cachedFormat.byteLength()") int cachedFormatLength,
                @Cached("create(compileFormat(format, arguments, isDebug(frame)))") DirectCallNode callPackNode,
                @Cached RopeNodes.EqualNode equalNode,
                @CachedLibrary("format") RubyLibrary rubyLibrary) {
            final BytesResult result;
            try {
                result = (BytesResult) callPackNode.call(
                        new Object[]{ arguments, arguments.length, rubyLibrary.isTainted(format), null });
            } catch (FormatException e) {
                exceptionProfile.enter();
                throw FormatExceptionTranslator.translate(getContext(), this, e);
            }

            return finishFormat(cachedFormatLength, result);
        }

        @Specialization(replaces = "formatCached", limit = "getRubyLibraryCacheLimit()")
        protected RubyString formatUncached(VirtualFrame frame, RubyString format, Object[] arguments,
                @Cached IndirectCallNode callPackNode,
                @CachedLibrary("format") RubyLibrary rubyLibrary) {
            final BytesResult result;
            final boolean isDebug = readDebugGlobalNode.executeBoolean(frame);
            try {
                result = (BytesResult) callPackNode.call(
                        compileFormat(format, arguments, isDebug),
                        new Object[]{ arguments, arguments.length, rubyLibrary.isTainted(format), null });
            } catch (FormatException e) {
                exceptionProfile.enter();
                throw FormatExceptionTranslator.translate(getContext(), this, e);
            }

            return finishFormat(format.rope.byteLength(), result);
        }

        private RubyString finishFormat(int formatLength, BytesResult result) {
            byte[] bytes = result.getOutput();

            if (resizeProfile.profile(bytes.length != result.getOutputLength())) {
                bytes = Arrays.copyOf(bytes, result.getOutputLength());
            }

            if (makeStringNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                makeStringNode = insert(MakeStringNode.create());
            }

            final RubyString string = makeStringNode.executeMake(
                    bytes,
                    result.getEncoding().getEncodingForLength(formatLength),
                    result.getStringCodeRange());

            if (result.isTainted()) {
                if (rubyLibrary == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    rubyLibrary = insert(RubyLibrary.getFactory().createDispatched(getRubyLibraryCacheLimit()));
                }

                rubyLibrary.taint(string);
            }

            return string;
        }

        @TruffleBoundary
        protected RootCallTarget compileFormat(RubyString format, Object[] arguments, boolean isDebug) {
            try {
                return new PrintfCompiler(getContext(), this)
                        .compile(format.rope, arguments, isDebug);
            } catch (InvalidFormatException e) {
                throw new RaiseException(getContext(), coreExceptions().argumentError(e.getMessage(), this));
            }
        }

        protected boolean isDebug(VirtualFrame frame) {
            return readDebugGlobalNode.executeBoolean(frame);
        }

    }

    @CoreMethod(names = "global_variables", isModuleFunction = true)
    public abstract static class KernelGlobalVariablesNode extends CoreMethodArrayArgumentsNode {

        @TruffleBoundary
        @Specialization
        protected RubyArray globalVariables() {
            final String[] keys = coreLibrary().globalVariables.keys();
            final Object[] store = new Object[keys.length];
            for (int i = 0; i < keys.length; i++) {
                store[i] = getSymbol(keys[i]);
            }
            return createArray(store);
        }

    }

    @CoreMethod(names = "taint")
    public abstract static class KernelTaintNode extends CoreMethodArrayArgumentsNode {

        @Specialization(limit = "getRubyLibraryCacheLimit()")
        protected Object taint(Object object,
                @CachedLibrary("object") RubyLibrary rubyLibrary) {
            rubyLibrary.taint(object);
            return object;
        }

    }

    @CoreMethod(names = "tainted?")
    public abstract static class KernelIsTaintedNode extends CoreMethodArrayArgumentsNode {

        @Specialization(limit = "getRubyLibraryCacheLimit()")
        protected boolean isTainted(Object object,
                @CachedLibrary("object") RubyLibrary rubyLibrary) {
            return rubyLibrary.isTainted(object);
        }

    }

    @GenerateUncached
    @GenerateNodeFactory
    @NodeChild(value = "value", type = RubyNode.class)
    @Primitive(name = "kernel_to_hex")
    public abstract static class ToHexStringNode extends RubySourceNode {

        public static ToHexStringNode create() {
            return KernelNodesFactory.ToHexStringNodeFactory.create(null);
        }

        public abstract String executeToHexString(Object value);

        @Specialization
        protected String toHexString(int value) {
            return toHexString((long) value);
        }

        @TruffleBoundary
        @Specialization
        protected String toHexString(long value) {
            return Long.toHexString(value);
        }

        @Specialization
        protected String toHexString(RubyBignum value) {
            return BigIntegerOps.toString(value.value, 16);
        }

    }

    @GenerateUncached
    @GenerateNodeFactory
    @NodeChild(value = "self", type = RubyNode.class)
    @CoreMethod(names = { "to_s", "inspect" }) // Basic #inspect, refined later in core
    public abstract static class ToSNode extends RubySourceNode {

        public static ToSNode create() {
            return KernelNodesFactory.ToSNodeFactory.create(null);
        }

        public abstract RubyString executeToS(Object self);

        @Specialization
        protected RubyString toS(Object self,
                @Cached LogicalClassNode classNode,
                @Cached MakeStringNode makeStringNode,
                @Cached ObjectIDNode objectIDNode,
                @Cached ToHexStringNode toHexStringNode,
                @Cached PropagateTaintNode propagateTaintNode) {
            String className = classNode.executeLogicalClass(self).fields.getName();
            Object id = objectIDNode.execute(self);
            String hexID = toHexStringNode.executeToHexString(id);

            final RubyString string = makeStringNode.executeMake(
                    Utils.concat("#<", className, ":0x", hexID, ">"),
                    UTF8Encoding.INSTANCE,
                    CodeRange.CR_UNKNOWN);
            propagateTaintNode.executePropagate(self, string);
            return string;
        }

    }

    @CoreMethod(names = "untaint")
    public abstract static class UntaintNode extends CoreMethodArrayArgumentsNode {

        @Specialization(limit = "getRubyLibraryCacheLimit()")
        protected Object untaint(Object object,
                @CachedLibrary("object") RubyLibrary rubyLibrary) {
            rubyLibrary.untaint(object);
            return object;
        }

    }

}
