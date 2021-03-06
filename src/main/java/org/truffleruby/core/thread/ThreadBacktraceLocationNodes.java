/*
 * Copyright (c) 2015, 2019 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.core.thread;

import java.io.File;

import com.oracle.truffle.api.TruffleStackTraceElement;
import org.jcodings.specific.UTF8Encoding;
import org.truffleruby.Layouts;
import org.truffleruby.RubyContext;
import org.truffleruby.builtins.CoreMethod;
import org.truffleruby.builtins.CoreModule;
import org.truffleruby.builtins.UnaryCoreMethodNode;
import org.truffleruby.core.rope.CodeRange;
import org.truffleruby.core.string.StringNodes;
import org.truffleruby.language.backtrace.Backtrace;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.source.SourceSection;

@CoreModule(value = "Thread::Backtrace::Location", isClass = true)
public class ThreadBacktraceLocationNodes {

    @TruffleBoundary
    private static SourceSection getUserSourceSection(RubyContext context, DynamicObject threadBacktraceLocation) {
        final Backtrace backtrace = Layouts.THREAD_BACKTRACE_LOCATION.getBacktrace(threadBacktraceLocation);
        final int activationIndex = Layouts.THREAD_BACKTRACE_LOCATION.getActivationIndex(threadBacktraceLocation);

        return context.getUserBacktraceFormatter().nextUserSourceSection(backtrace.getStackTrace(), activationIndex);
    }

    @CoreMethod(names = "absolute_path")
    public abstract static class AbsolutePathNode extends UnaryCoreMethodNode {

        @TruffleBoundary
        @Specialization
        protected DynamicObject absolutePath(DynamicObject threadBacktraceLocation,
                @Cached StringNodes.MakeStringNode makeStringNode) {
            final SourceSection sourceSection = getUserSourceSection(getContext(), threadBacktraceLocation);
            final String path = getContext().getAbsolutePath(sourceSection.getSource());

            if (path == null) {
                return coreStrings().UNKNOWN.createInstance();
            } else {
                final String canonicalPath;
                if (new File(path).isAbsolute()) { // A normal file
                    canonicalPath = getContext().getFeatureLoader().canonicalize(path);
                } else { // eval()
                    canonicalPath = path;
                }

                return makeStringNode.fromRope(getContext().getPathToRopeCache().getCachedPath(canonicalPath));
            }
        }

    }

    @CoreMethod(names = "path")
    public abstract static class PathNode extends UnaryCoreMethodNode {

        @TruffleBoundary
        @Specialization
        protected DynamicObject path(DynamicObject threadBacktraceLocation,
                @Cached StringNodes.MakeStringNode makeStringNode) {
            final SourceSection sourceSection = getUserSourceSection(getContext(), threadBacktraceLocation);
            final String path = getContext().getPath(sourceSection.getSource());

            if (path == null) {
                return coreStrings().UNKNOWN.createInstance();
            } else {
                return makeStringNode.fromRope(getContext().getPathToRopeCache().getCachedPath(path));
            }
        }

    }

    @CoreMethod(names = "label")
    public abstract static class LabelNode extends UnaryCoreMethodNode {

        @Specialization
        protected DynamicObject label(DynamicObject threadBacktraceLocation,
                @Cached StringNodes.MakeStringNode makeStringNode) {
            final Backtrace backtrace = Layouts.THREAD_BACKTRACE_LOCATION.getBacktrace(threadBacktraceLocation);
            final int index = Layouts.THREAD_BACKTRACE_LOCATION.getActivationIndex(threadBacktraceLocation);
            final TruffleStackTraceElement element = backtrace.getStackTrace()[index];

            final String label = Backtrace.labelFor(element);
            return makeStringNode.executeMake(label, UTF8Encoding.INSTANCE, CodeRange.CR_UNKNOWN);
        }
    }

    @CoreMethod(names = "base_label")
    public abstract static class BaseLabelNode extends UnaryCoreMethodNode {
        @Specialization
        protected DynamicObject label(DynamicObject threadBacktraceLocation,
                @Cached StringNodes.MakeStringNode makeStringNode) {
            final Backtrace backtrace = Layouts.THREAD_BACKTRACE_LOCATION.getBacktrace(threadBacktraceLocation);
            final int index = Layouts.THREAD_BACKTRACE_LOCATION.getActivationIndex(threadBacktraceLocation);
            final TruffleStackTraceElement element = backtrace.getStackTrace()[index];

            final String baseLabel = Backtrace.baseLabelFor(element);
            return makeStringNode.executeMake(baseLabel, UTF8Encoding.INSTANCE, CodeRange.CR_UNKNOWN);
        }
    }

    @CoreMethod(names = "lineno")
    public abstract static class LinenoNode extends UnaryCoreMethodNode {

        @TruffleBoundary
        @Specialization
        protected int lineno(DynamicObject threadBacktraceLocation) {
            final SourceSection sourceSection = getUserSourceSection(getContext(), threadBacktraceLocation);

            return sourceSection.getStartLine();
        }

    }

    @CoreMethod(names = "to_s")
    public abstract static class ToSNode extends UnaryCoreMethodNode {

        @Child private StringNodes.MakeStringNode makeStringNode = StringNodes.MakeStringNode.create();

        @Specialization
        protected DynamicObject toS(DynamicObject threadBacktraceLocation) {
            final Backtrace backtrace = Layouts.THREAD_BACKTRACE_LOCATION.getBacktrace(threadBacktraceLocation);
            final int index = Layouts.THREAD_BACKTRACE_LOCATION.getActivationIndex(threadBacktraceLocation);

            final String description = getContext()
                    .getUserBacktraceFormatter()
                    .formatLine(backtrace.getStackTrace(), index, null);
            return makeStringNode.executeMake(description, UTF8Encoding.INSTANCE, CodeRange.CR_UNKNOWN);
        }

    }

}
