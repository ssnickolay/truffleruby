/*
 * Copyright (c) 2014, 2019 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.language;

import java.util.function.Function;
import java.util.function.Predicate;

import com.oracle.truffle.api.CallTarget;
import org.truffleruby.RubyContext;
import org.truffleruby.SuppressFBWarnings;
import org.truffleruby.collections.Memo;
import org.truffleruby.core.array.ArrayUtils;
import org.truffleruby.language.arguments.RubyArguments;
import org.truffleruby.language.backtrace.Backtrace;
import org.truffleruby.language.backtrace.BacktraceFormatter;
import org.truffleruby.language.backtrace.InternalRootNode;
import org.truffleruby.language.methods.InternalMethod;
import org.truffleruby.language.methods.SharedMethodInfo;
import org.truffleruby.shared.TruffleRuby;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameInstance;
import com.oracle.truffle.api.frame.FrameInstance.FrameAccess;
import com.oracle.truffle.api.frame.FrameInstanceVisitor;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.SourceSection;

public class CallStackManager {

    private final RubyContext context;

    public CallStackManager(RubyContext context) {
        this.context = context;
    }

    // Frame

    @TruffleBoundary
    public Frame getCurrentFrame(FrameAccess frameAccess) {
        return iterateFrames(0, f -> true, f -> f.getFrame(frameAccess));
    }

    @TruffleBoundary
    public Frame getCallerFrameIgnoringSend(FrameAccess frameAccess) {
        // System.err.printf("Getting a caller frame...\n");
        // new Error().printStackTrace();
        return getCallerFrameIgnoringSend(f -> isRubyFrameAndNotSend(f.getFrame(FrameAccess.READ_ONLY)), frameAccess);
    }

    @TruffleBoundary
    public Frame getCallerFrameNotInModules(FrameAccess frameAccess, Object[] modules) {
        final Memo<Boolean> skippedFirstFrameFound = new Memo<>(false);

        return getCallerFrameIgnoringSend(frameInstance -> {
            final InternalMethod method = getMethod(frameInstance.getFrame(FrameAccess.READ_ONLY));
            if (method != null && !ArrayUtils.contains(modules, method.getDeclaringModule())) {
                if (skippedFirstFrameFound.get()) {
                    return true;
                }
                skippedFirstFrameFound.set(true);
            }
            return false;
        }, frameAccess);
    }

    // Node

    @TruffleBoundary
    public Node getCallerNodeIgnoringSend() {
        return getCallerNode(1, true);
    }

    @TruffleBoundary
    public Frame getParentCallTargetFrame(CallTarget callTarget, FrameAccess frameAccess) {
        return iterateFrames(2, f -> f.getCallTarget().toString() == callTarget.toString(), f -> f.getFrame(frameAccess));
    }

    @TruffleBoundary
    public Node getCallerNode(int skip, boolean ignoreSend) {
        return iterateFrames(skip, frameInstance -> {
            if (ignoreSend) {
                return isRubyFrameAndNotSend(frameInstance.getFrame(FrameAccess.READ_ONLY));
            } else {
                return true;
            }
        }, f -> f.getCallNode());
    }

    // Method

    @TruffleBoundary
    public InternalMethod getCallingMethodIgnoringSend() {
        return getMethod(getCallerFrameIgnoringSend(FrameAccess.READ_ONLY));
    }

    @TruffleBoundary
    public boolean callerIsSend() {
        final Boolean isSend = iterateFrames(
                1,
                f -> true,
                frameInstance -> context
                        .getCoreLibrary()
                        .isSend(getMethod(frameInstance.getFrame(FrameAccess.READ_ONLY))));
        return isSend != null && isSend;
    }

    // SourceSection

    @TruffleBoundary
    public SourceSection getTopMostUserSourceSection(SourceSection encapsulatingSourceSection) {
        if (BacktraceFormatter.isUserSourceSection(context, encapsulatingSourceSection)) {
            return encapsulatingSourceSection;
        } else {
            return getTopMostUserSourceSection();
        }
    }

    @TruffleBoundary
    public SourceSection getTopMostUserSourceSection() {
        return Truffle.getRuntime().iterateFrames(frameInstance -> {
            final Node callNode = frameInstance.getCallNode();
            if (callNode == null) {
                return null; // Go to the next frame
            }

            final SourceSection sourceSection = callNode.getEncapsulatingSourceSection();
            if (BacktraceFormatter.isUserSourceSection(context, sourceSection)) {
                return sourceSection;
            } else {
                return null; // Go to the next frame
            }
        });
    }

    // Internals

    private Frame getCallerFrameIgnoringSend(Predicate<FrameInstance> filter, FrameAccess frameAccess) {
        return iterateFrames(1, filter, f -> f.getFrame(frameAccess));
    }

    /** Returns action() for the first frame matching the filter, and null if none matches.
     * <p>
     * skip=0 starts at the current frame and skip=1 starts at the caller frame. */
    private <R> R iterateFrames(int skip, Predicate<FrameInstance> filter, Function<FrameInstance, R> action) {
        return Truffle.getRuntime().iterateFrames(new FilterApplyVisitor<>(skip, filter, action));
    }

    private static class FilterApplyVisitor<R> implements FrameInstanceVisitor<R> {
        private final int skip;
        private final Predicate<FrameInstance> filter;
        private final Function<FrameInstance, R> action;
        private int skipped = 0;

        private FilterApplyVisitor(int skip, Predicate<FrameInstance> filter, Function<FrameInstance, R> action) {
            this.skip = skip;
            this.filter = filter;
            this.action = action;
        }

        @Override
        public R visitFrame(FrameInstance frameInstance) {
            if (skipped < skip) {
                skipped++;
                return null; // Go to the next frame
            }

            if (filter.test(frameInstance)) {
                return action.apply(frameInstance);
            } else {
                return null; // Go to the next frame
            }
        }
    }

    private boolean isRubyFrameAndNotSend(Frame frame) {
        final InternalMethod method = getMethod(frame);
        return method != null && !context.getCoreLibrary().isSend(method);
    }

    private static InternalMethod getMethod(Frame frame) {
        return RubyArguments.tryGetMethod(frame);
    }

    // Backtraces

    public Backtrace getBacktrace(Node currentNode) {
        return getBacktrace(currentNode, null, 0, null);
    }

    public Backtrace getBacktrace(Node currentNode, int omit) {
        return getBacktrace(currentNode, null, omit, null);
    }

    public Backtrace getBacktrace(Node currentNode, SourceSection sourceLocation, Throwable javaThrowable) {
        return getBacktrace(currentNode, sourceLocation, 0, javaThrowable);
    }

    public Backtrace getBacktrace(Node currentNode, SourceSection sourceLocation, int omit, Throwable javaThrowable) {
        if (context.getOptions().EXCEPTIONS_STORE_JAVA || context.getOptions().BACKTRACES_INTERLEAVE_JAVA) {
            if (javaThrowable == null) {
                javaThrowable = newException();
            }
        }

        return new Backtrace(currentNode, sourceLocation, omit, javaThrowable);
    }

    @SuppressFBWarnings("ES")
    public boolean ignoreFrame(Node callNode, RootCallTarget callTarget) {
        // Nodes with no call node are top-level or require, which *should* appear in the backtrace.
        if (callNode == null) {
            return false;
        }

        final RootNode rootNode = callNode.getRootNode();

        if (rootNode instanceof RubyRootNode) {
            final SharedMethodInfo sharedMethodInfo = ((RubyRootNode) rootNode).getSharedMethodInfo();

            // Ignore BasicObject#__send__, Kernel#send and Kernel#public_send like MRI
            if (context.getCoreLibrary().isSend(sharedMethodInfo)) {
                return true;
            }

            // Ignore Truffle::Boot.main and its caller
            if (context.getCoreLibrary().isTruffleBootMainMethod(sharedMethodInfo)) {
                return true;
            }
            final SourceSection sourceSection = sharedMethodInfo.getSourceSection();
            if (sourceSection != null && sourceSection.getSource().getName() == TruffleRuby.BOOT_SOURCE_NAME) {
                return true;
            }
        }

        return rootNode instanceof InternalRootNode || callNode.getEncapsulatingSourceSection() == null;

    }

    @TruffleBoundary
    private Exception newException() {
        return new Exception();
    }

}
