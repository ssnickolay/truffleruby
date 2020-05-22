/*
 * Copyright (c) 2020 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.core.symbol;

import org.jcodings.specific.USASCIIEncoding;
import org.truffleruby.core.rope.RopeOperations;

import java.util.Arrays;
import java.util.List;

public class CoreSymbols {

    public static final RubySymbol AMPERSAND = createRubySymbol("&");
    public static final RubySymbol CIRCUMFLEX = createRubySymbol("^");
    public static final RubySymbol CLASS = createRubySymbol("class");
    public static final RubySymbol DIVIDE = createRubySymbol("/");
    public static final RubySymbol DIVMOD = createRubySymbol("divmod");
    public static final RubySymbol GREATER_OR_EQUAL = createRubySymbol(">=");
    public static final RubySymbol GREATER_THAN = createRubySymbol(">");
    public static final RubySymbol IMMEDIATE = createRubySymbol("immediate");
    public static final RubySymbol LESS_OR_EQUAL = createRubySymbol("<=");
    public static final RubySymbol LESS_THAN = createRubySymbol("<");
    public static final RubySymbol LINE = createRubySymbol("line");
    public static final RubySymbol MINUS = createRubySymbol("-");
    public static final RubySymbol MODULO = createRubySymbol("%");
    public static final RubySymbol MULTIPLY = createRubySymbol("*");
    public static final RubySymbol NEVER = createRubySymbol("never");
    public static final RubySymbol ON_BLOCKING = createRubySymbol("on_blocking");
    public static final RubySymbol PIPE = createRubySymbol("|");
    public static final RubySymbol POWER = createRubySymbol("**");
    public static final RubySymbol PLUS = createRubySymbol("+");
    public static final RubySymbol TO_A = createRubySymbol("to_a");
    public static final RubySymbol TO_ARY = createRubySymbol("to_ary");
    public static final RubySymbol TO_HASH = createRubySymbol("to_hash");

    public static List<RubySymbol> CORE_SYMBOLS = Arrays.asList(
            AMPERSAND,
            CIRCUMFLEX,
            CLASS,
            DIVIDE,
            DIVMOD,
            GREATER_OR_EQUAL,
            GREATER_THAN,
            IMMEDIATE,
            LESS_OR_EQUAL,
            LESS_THAN,
            LINE,
            MINUS,
            MODULO,
            MULTIPLY,
            NEVER,
            ON_BLOCKING,
            PIPE,
            POWER,
            PLUS,
            TO_A,
            TO_ARY,
            TO_HASH);

    private static RubySymbol createRubySymbol(String string) {
        return new RubySymbol(string, RopeOperations.encodeAscii(string, USASCIIEncoding.INSTANCE));
    }

}