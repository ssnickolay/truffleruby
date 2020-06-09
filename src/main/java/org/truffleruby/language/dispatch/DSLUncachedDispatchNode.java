/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.language.dispatch;

import org.truffleruby.RubyContext;
import org.truffleruby.RubyLanguage;
import org.truffleruby.core.array.ArrayUtils;
import org.truffleruby.core.cast.NameToJavaStringNode;
import org.truffleruby.core.cast.ToSymbolNode;
import org.truffleruby.core.exception.ExceptionOperations;
import org.truffleruby.core.symbol.RubySymbol;
import org.truffleruby.interop.OutgoingForeignCallNodeGen;
import org.truffleruby.language.RubyBaseNode;
import org.truffleruby.language.NotOptimizedWarningNode;
import org.truffleruby.language.RubyGuards;
import org.truffleruby.language.arguments.RubyArguments;
import org.truffleruby.language.control.RaiseException;
import org.truffleruby.language.methods.InternalMethod;
import org.truffleruby.language.methods.LookupMethodNode;
import org.truffleruby.language.methods.TranslateExceptionNode;
import org.truffleruby.language.methods.UnsupportedOperationBehavior;
import org.truffleruby.language.objects.MetaClassNode;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.CachedContext;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.profiles.BranchProfile;

@GenerateUncached
public abstract class DSLUncachedDispatchNode extends RubyBaseNode {

    public static DSLUncachedDispatchNode create() {
        return DSLUncachedDispatchNodeGen.create();
    }

    public Object dispatch(
            VirtualFrame frame,
            Object receiver,
            Object name,
            MaterializedFrame callerFrame,
            DynamicObject block,
            Object[] arguments,
            DispatchAction dispatchAction,
            MissingBehavior missingBehavior,
            boolean ignoreVisibility,
            boolean onlyCallPublic) {
        return executeDispatch(
                frame,
                receiver,
                name,
                callerFrame,
                block,
                arguments,
                dispatchAction,
                missingBehavior,
                ignoreVisibility,
                onlyCallPublic);
    }

    protected abstract Object executeDispatch(
            Frame frame,
            Object receiver,
            Object name,
            MaterializedFrame callerFrame,
            DynamicObject block,
            Object[] arguments,
            DispatchAction dispatchAction,
            MissingBehavior missingBehavior,
            boolean ignoreVisibility,
            boolean onlyCallPublic);

    @Specialization(
            guards = {
                    "dispatchAction == cachedDispatchAction",
                    "missingBehavior == cachedMissingBehaviour",
                    "ignoreVisibility == cachedIgnoreVisibility",
                    "onlyCallPublic == cachedOnlyCallPublic" })
    protected Object dispatch(
            Frame frame,
            Object receiver,
            Object name,
            MaterializedFrame callerFrame,
            DynamicObject block,
            Object[] arguments,
            DispatchAction dispatchAction,
            MissingBehavior missingBehavior,
            boolean ignoreVisibility,
            boolean onlyCallPublic,
            @Cached("dispatchAction") DispatchAction cachedDispatchAction,
            @Cached("missingBehavior") MissingBehavior cachedMissingBehaviour,
            @Cached("ignoreVisibility") boolean cachedIgnoreVisibility,
            @Cached("onlyCallPublic") boolean cachedOnlyCallPublic,
            @CachedContext(RubyLanguage.class) RubyContext context,
            @Cached MetaClassNode metaClassNode,
            @Cached NotOptimizedWarningNode notOptimizedWarningNode,
            @Cached LookupMethodNode lookupMethodNode,
            @Cached LookupMethodNode lookupMethodMissingNode,
            @Cached NameToJavaStringNode nameToJavaStringNode,
            @Cached IndirectCallNode indirectCallNode,
            @Cached ToSymbolNode toSymbolNode,
            @Cached BranchProfile foreignProfile,
            @Cached BranchProfile foreignErrorProfile,
            @Cached BranchProfile methodNotFoundProfile,
            @Cached BranchProfile methodMissingProfile,
            @Cached BranchProfile methodMissingNotFoundProfile,
            @Cached TranslateExceptionNode translateExceptionNode) {

        assert this != DSLUncachedDispatchNodeGen.getUncached() || frame == null;

        final String methodName = nameToJavaStringNode.executeToJavaString(name);

        if (cachedDispatchAction == DispatchAction.CALL_METHOD) {
            if (metaClassNode.executeMetaClass(receiver) == context.getCoreLibrary().truffleInteropForeignClass) {
                foreignProfile.enter();
                try {
                    return OutgoingForeignCallNodeGen.getUncached().executeCall(receiver, methodName, arguments);
                } catch (Throwable t) {
                    foreignErrorProfile.enter();
                    throw translateExceptionNode.executeTranslation(t, UnsupportedOperationBehavior.TYPE_ERROR);
                }
            }
        } else {
            assert !RubyGuards.isForeignObject(receiver) : "RESPOND_TO_METHOD not supported on foreign objects";
        }


        final InternalMethod method = lookupMethodNode.lookup(
                (VirtualFrame) frame,
                receiver,
                methodName,
                cachedIgnoreVisibility,
                cachedOnlyCallPublic);
//
//        if (methodName.equals(new String("foo")) || methodName.equals(new String("bar"))) {
//            System.out.println("======== DSLUncahed node: " + lookupMethodNode);
//            System.out.println("======== DSLUncahed name: " + methodName);
//            System.out.println("======== DSLUncahed method: " + method);
//        }

        if (method != null) {
            if (cachedDispatchAction == DispatchAction.CALL_METHOD) {
                return call(indirectCallNode, callerFrame, method, receiver, block, arguments);
            } else if (cachedDispatchAction == DispatchAction.RESPOND_TO_METHOD) {
                return !method.isUnimplemented();
            } else {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw new UnsupportedOperationException();
            }
        }

        methodNotFoundProfile.enter();

        if (cachedDispatchAction == DispatchAction.CALL_METHOD &&
                cachedMissingBehaviour == MissingBehavior.RETURN_MISSING) {
            return DispatchNode.MISSING;
        }

        methodMissingProfile.enter();

        final InternalMethod methodMissing = lookupMethodMissingNode.lookupIgnoringVisibility(
                (VirtualFrame) frame,
                receiver,
                "method_missing");

        if (methodMissing == null) {
            if (cachedDispatchAction == DispatchAction.RESPOND_TO_METHOD) {
                return false;
            } else {
                methodMissingNotFoundProfile.enter();
                final DynamicObject formatter = ExceptionOperations.getFormatter(
                        ExceptionOperations.NO_METHOD_ERROR,
                        context);
                throw new RaiseException(
                        context,
                        context.getCoreExceptions().noMethodErrorFromMethodMissing(
                                formatter,
                                receiver,
                                methodName,
                                arguments,
                                this));
            }
        }

        if (cachedDispatchAction == DispatchAction.CALL_METHOD) {
            final RubySymbol nameSymbol = toSymbolNode.executeToSymbol(name);
            final Object[] modifiedArgumentsObjects = ArrayUtils.unshift(arguments, nameSymbol);

            return call(indirectCallNode, callerFrame, methodMissing, receiver, block, modifiedArgumentsObjects);
        } else if (cachedDispatchAction == DispatchAction.RESPOND_TO_METHOD) {
            return false;
        } else {
            throw new UnsupportedOperationException();
        }
    }

    private Object call(
            IndirectCallNode indirectCallNode,
            MaterializedFrame callerFrame,
            InternalMethod method,
            Object receiverObject,
            DynamicObject blockObject,
            Object[] argumentsObjects) {
        return indirectCallNode.call(
                method.getCallTarget(),
                RubyArguments.pack(null, callerFrame, method, null, receiverObject, blockObject, argumentsObjects));
    }

}

