/***** BEGIN LICENSE BLOCK *****
 * Version: EPL 1.0/GPL 2.0/LGPL 2.1
 *
 * The contents of this file are subject to the Eclipse Public
 * License Version 1.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of
 * the License at http://www.eclipse.org/legal/epl-v10.html
 *
 * Software distributed under the License is distributed on an "AS
 * IS" basis, WITHOUT WARRANTY OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * rights and limitations under the License.
 *
 * Copyright (C) 2001 Alan Moore <alan_moore@gmx.net>
 * Copyright (C) 2001-2002 Benoit Cerrina <b.cerrina@wanadoo.fr>
 * Copyright (C) 2001-2004 Jan Arne Petersen <jpetersen@uni-bonn.de>
 * Copyright (C) 2002-2004 Anders Bengtsson <ndrsbngtssn@yahoo.se>
 * Copyright (C) 2004-2005 Thomas E Enebo <enebo@acm.org>
 * Copyright (C) 2004 Stefan Matthias Aust <sma@3plus4.de>
 * Copyright (C) 2005 David Corbin <dcorbin@users.sourceforge.net>
 * Copyright (C) 2006 Nick Sieger <nicksieger@gmail.com>
 * Copyright (C) 2006 Miguel Covarrubias <mlcovarrubias@gmail.com>
 *
 * Alternatively, the contents of this file may be used under the terms of
 * either of the GNU General Public License Version 2 or later (the "GPL"),
 * or the GNU Lesser General Public License Version 2.1 or later (the "LGPL"),
 * in which case the provisions of the GPL or the LGPL are applicable instead
 * of those above. If you wish to allow use of your version of this file only
 * under the terms of either the GPL or the LGPL, and not to allow others to
 * use your version of this file under the terms of the EPL, indicate your
 * decision by deleting the provisions above and replace them with the notice
 * and other provisions required by the GPL or the LGPL. If you do not delete
 * the provisions above, a recipient may use your version of this file under
 * the terms of any one of the EPL, the GPL or the LGPL.
 ***** END LICENSE BLOCK *****/
package org.jruby.truffle.core.regexp;

import org.jcodings.Encoding;
import org.jcodings.specific.ASCIIEncoding;
import org.jcodings.specific.USASCIIEncoding;
import org.jcodings.specific.UTF8Encoding;
import org.joni.Matcher;
import org.joni.NameEntry;
import org.joni.Option;
import org.joni.Regex;
import org.joni.Region;
import org.joni.Syntax;
import org.joni.exception.JOniException;
import org.jruby.Ruby;
import org.jruby.RubyArray;
import org.jruby.RubyClass;
import org.jruby.RubyFixnum;
import org.jruby.RubyHash;
import org.jruby.RubyMatchData;
import org.jruby.RubyModule;
import org.jruby.RubyNumeric;
import org.jruby.RubyObject;
import org.jruby.RubyString;
import org.jruby.RubySymbol;
import org.jruby.RubyThread;
import org.jruby.anno.JRubyClass;
import org.jruby.anno.JRubyMethod;
import org.jruby.common.IRubyWarnings.ID;
import org.jruby.exceptions.RaiseException;
import org.jruby.parser.ReOptions;
import org.jruby.runtime.Block;
import org.jruby.runtime.ClassIndex;
import org.jruby.runtime.ObjectAllocator;
import org.jruby.runtime.ThreadContext;
import org.jruby.runtime.Visibility;
import org.jruby.runtime.builtin.IRubyObject;
import org.jruby.runtime.encoding.EncodingCapable;
import org.jruby.runtime.encoding.MarshalEncoding;
import org.jruby.runtime.marshal.MarshalStream;
import org.jruby.runtime.marshal.UnmarshalStream;
import org.jruby.truffle.RubyContext;
import org.jruby.util.ByteList;
import org.jruby.util.KCode;
import org.jruby.util.Pack;
import org.jruby.util.RegexpOptions;
import org.jruby.util.RegexpSupport;
import org.jruby.util.StringSupport;
import org.jruby.util.TypeConverter;
import org.jruby.util.cli.Options;
import org.jruby.util.collections.WeakValuedMap;
import org.jruby.util.io.EncodingUtils;

import java.nio.charset.StandardCharsets;
import java.util.Iterator;

import static org.jruby.anno.FrameField.BACKREF;
import static org.jruby.anno.FrameField.LASTLINE;
import static org.jruby.util.StringSupport.CR_BROKEN;
import static org.jruby.util.StringSupport.CR_UNKNOWN;
import static org.jruby.util.StringSupport.EMPTY_STRING_ARRAY;
import static org.jruby.util.StringSupport.codeRangeScan;

public class ClassicRegexp extends RubyObject implements ReOptions, EncodingCapable, MarshalEncoding {
    private Regex pattern;
    private ByteList str = ByteList.EMPTY_BYTELIST;
    private RegexpOptions options;
    private int useCount;

    public static final int ARG_ENCODING_FIXED     =   ReOptions.RE_FIXED;
    public static final int ARG_ENCODING_NONE      =   ReOptions.RE_NONE;

    public void setLiteral() {
        options.setLiteral(true);
    }

    public void clearLiteral() {
        options.setLiteral(false);
    }

    public boolean isLiteral() {
        return options.isLiteral();
    }

    public boolean isKCodeDefault() {
        return options.isKcodeDefault();
    }

    public void setEncodingNone() {
        options.setEncodingNone(true);
    }

    public void clearEncodingNone() {
        options.setEncodingNone(false);
    }

    public boolean isEncodingNone() {
        return options.isEncodingNone();
    }

    public KCode getKCode() {
        return options.getKCode();
    }

    @Override
    public Encoding getEncoding() {
        return pattern.getEncoding();
    }

    @Override
    public void setEncoding(Encoding encoding) {
        // FIXME: Which encoding should be changed here?
        // FIXME: transcode?
    }

    @Override
    public boolean shouldMarshalEncoding() {
        return getEncoding() != ASCIIEncoding.INSTANCE;
    }

    @Override
    public Encoding getMarshalEncoding() {
        return getEncoding();
    }
    // FIXME: Maybe these should not be static?
    static final WeakValuedMap<ByteList, Regex> patternCache = new WeakValuedMap();
    static final WeakValuedMap<ByteList, Regex> quotedPatternCache = new WeakValuedMap();
    static final WeakValuedMap<ByteList, Regex> preprocessedPatternCache = new WeakValuedMap();

    private static Regex makeRegexp(Ruby runtime, ByteList bytes, RegexpOptions options, Encoding enc) {
        try {
            int p = bytes.getBegin();
            return new Regex(bytes.getUnsafeBytes(), p, p + bytes.getRealSize(), options.toJoniOptions(), enc, Syntax.DEFAULT, runtime.getWarnings());
        } catch (Exception e) {
            RegexpSupport.raiseRegexpError19(runtime, bytes, enc, options, e.getMessage());
            return null; // not reached
        }
    }

    static Regex getRegexpFromCache(Ruby runtime, ByteList bytes, Encoding enc, RegexpOptions options) {
        Regex regex = patternCache.get(bytes);
        if (regex != null && regex.getEncoding() == enc && regex.getOptions() == options.toJoniOptions()) return regex;
        regex = makeRegexp(runtime, bytes, options, enc);
        regex.setUserObject(bytes);
        patternCache.put(bytes, regex);
        return regex;
    }

    static Regex getQuotedRegexpFromCache(Ruby runtime, RubyString str, RegexpOptions options) {
        final ByteList bytes = str.getByteList();
        Regex regex = quotedPatternCache.get(bytes);
        Encoding enc = str.isAsciiOnly() ? USASCIIEncoding.INSTANCE : bytes.getEncoding();
        if (regex != null && regex.getEncoding() == enc && regex.getOptions() == options.toJoniOptions()) return regex;
        final ByteList quoted = quote19(str);
        regex = makeRegexp(runtime, quoted, options, quoted.getEncoding());
        regex.setUserObject(quoted);
        quotedPatternCache.put(bytes, regex);
        return regex;
    }

    //static Regex getQuotedRegexpFromCache(Ruby runtime, ByteList bytes, RegexpOptions options, boolean asciiOnly) {
    //    Regex regex = quotedPatternCache.get(bytes);
    //    Encoding enc = asciiOnly ? USASCIIEncoding.INSTANCE : bytes.getEncoding();
    //    if (regex != null && regex.getEncoding() == enc && regex.getOptions() == options.toJoniOptions()) return regex;
    //    final ByteList quoted = quote19(bytes, asciiOnly);
    //    regex = makeRegexp(runtime, quoted, options, quoted.getEncoding());
    //    regex.setUserObject(quoted);
    //    quotedPatternCache.put(bytes, regex);
    //    return regex;
    //}

    private static Regex getPreprocessedRegexpFromCache(Ruby runtime, ByteList bytes, Encoding enc, RegexpOptions options, RegexpSupport.ErrorMode mode) {
        Regex regex = preprocessedPatternCache.get(bytes);
        if (regex != null && regex.getEncoding() == enc && regex.getOptions() == options.toJoniOptions()) return regex;
        ByteList preprocessed = RegexpSupport.preprocess(runtime, bytes, enc, new Encoding[]{null}, RegexpSupport.ErrorMode.RAISE);
        regex = makeRegexp(runtime, preprocessed, options, enc);
        regex.setUserObject(preprocessed);
        preprocessedPatternCache.put(bytes, regex);
        return regex;
    }

    /*public static RubyClass createRegexpClass(Ruby runtime) {
        RubyClass regexpClass = runtime.defineClass("Regexp", runtime.getObject(), REGEXP_ALLOCATOR);
        runtime.setRegexp(regexpClass);

        regexpClass.setClassIndex(ClassIndex.REGEXP);
        regexpClass.setReifiedClass(ClassicRegexp.class);

        regexpClass.kindOf = new RubyModule.JavaClassKindOf(ClassicRegexp.class);

        regexpClass.defineConstant("IGNORECASE", runtime.newFixnum(RE_OPTION_IGNORECASE));
        regexpClass.defineConstant("EXTENDED", runtime.newFixnum(RE_OPTION_EXTENDED));
        regexpClass.defineConstant("MULTILINE", runtime.newFixnum(RE_OPTION_MULTILINE));

        regexpClass.defineConstant("FIXEDENCODING", runtime.newFixnum(RE_FIXED));
        regexpClass.defineConstant("NOENCODING", runtime.newFixnum(RE_NONE));

        regexpClass.defineAnnotatedMethods(ClassicRegexp.class);
        regexpClass.getSingletonClass().defineAlias("compile", "new");

        return regexpClass;
    }*/

    private static ObjectAllocator REGEXP_ALLOCATOR = new ObjectAllocator() {
        @Override
        public IRubyObject allocate(Ruby runtime, RubyClass klass) {
            return new ClassicRegexp(runtime, klass);
        }
    };

    public static int matcherSearch(Matcher matcher, int start, int range, int option) {
        try {
            SearchMatchTask task = new SearchMatchTask(null, start, range, option, false);
            return task.run(null, matcher);
        } catch (InterruptedException e) {
            throw new UnsupportedOperationException();
        }
    }

    public static int matcherMatch(Ruby runtime, Matcher matcher, int start, int range, int option) {
        try {
            ThreadContext context = runtime.getCurrentContext();
            RubyThread thread = context.getThread();
            SearchMatchTask task = new SearchMatchTask(thread, start, range, option, true);
            return thread.executeTask(context, matcher, task);
        } catch (InterruptedException e) {
            throw runtime.newInterruptedRegexpError("Regexp Interrupted");
        }
    }

    private static class SearchMatchTask implements RubyThread.Task<Matcher, Integer> {
        final RubyThread thread;
        final int start;
        final int range;
        final int option;
        final boolean match;

        SearchMatchTask(RubyThread thread, int start, int range, int option, boolean match) {
            this.thread = thread;
            this.start = start;
            this.range = range;
            this.option = option;
            this.match = match;
        }

        @Override
        public Integer run(ThreadContext context, Matcher matcher) throws InterruptedException {
            return match ?
                    matcher.matchInterruptible(start, range, option) :
                    matcher.searchInterruptible(start, range, option);
        }

        @Override
        public void wakeup(RubyThread thread, Matcher matcher) {
            thread.getNativeThread().interrupt();
        }
    }

    @Override
    public ClassIndex getNativeClassIndex() {
        return ClassIndex.REGEXP;
    }

    /** used by allocator
     */
    private ClassicRegexp(Ruby runtime, RubyClass klass) {
        super(runtime, klass);
        this.options = new RegexpOptions();
    }

    /** default constructor
     */
    ClassicRegexp(Ruby runtime) {
        super(runtime, runtime.getRegexp());
        this.options = new RegexpOptions();
    }

    private ClassicRegexp(Ruby runtime, ByteList str) {
        this(runtime);
        str.getClass();
        this.str = str;
        this.pattern = getRegexpFromCache(runtime, str, str.getEncoding(), RegexpOptions.NULL_OPTIONS);
    }

    private ClassicRegexp(Ruby runtime, ByteList str, RegexpOptions options) {
        this(runtime);
        str.getClass();

        regexpInitialize(str, str.getEncoding(), options);
    }

    // used only by the compiler/interpreter (will set the literal flag)
    public static ClassicRegexp newRegexp(Ruby runtime, String pattern, RegexpOptions options) {
        return newRegexp(runtime, ByteList.create(pattern), options);
    }

    // used only by the compiler/interpreter (will set the literal flag)
    public static ClassicRegexp newRegexp(Ruby runtime, ByteList pattern, int options) {
        return newRegexp(runtime, pattern, RegexpOptions.fromEmbeddedOptions(options));
    }

    // used only by the compiler/interpreter (will set the literal flag)
    public static ClassicRegexp newRegexp(Ruby runtime, ByteList pattern, RegexpOptions options) {
        try {
            return new ClassicRegexp(runtime, pattern, (RegexpOptions)options.clone());
        } catch (RaiseException re) {
            throw runtime.newSyntaxError(re.getMessage());
        }
    }

    /**
     * throws RaiseException on error so parser can pick this up and give proper line and line number
     * error as opposed to any non-literal regexp creation which may raise a syntax error but will not
     * have this extra source info in the error message
     */
    public static ClassicRegexp newRegexpParser(Ruby runtime, ByteList pattern, RegexpOptions options) {
        return new ClassicRegexp(runtime, pattern, (RegexpOptions)options.clone());
    }

    // used only by the compiler/interpreter (will set the literal flag)
    public static ClassicRegexp newDRegexp(Ruby runtime, RubyString pattern, RegexpOptions options) {
        try {
            return new ClassicRegexp(runtime, pattern.getByteList(), (RegexpOptions)options.clone());
        } catch (RaiseException re) {
            throw runtime.newRegexpError(re.getMessage());
        }
    }

    // used only by the compiler/interpreter (will set the literal flag)
    public static ClassicRegexp newDRegexp(Ruby runtime, RubyString pattern, int joniOptions) {
        try {
            RegexpOptions options = RegexpOptions.fromJoniOptions(joniOptions);
            return new ClassicRegexp(runtime, pattern.getByteList(), options);
        } catch (RaiseException re) {
            throw runtime.newRegexpError(re.getMessage());
        }
    }

    // used only by the compiler/interpreter (will set the literal flag)
    public static ClassicRegexp newDRegexpEmbedded(Ruby runtime, RubyString pattern, int embeddedOptions) {
        try {
            RegexpOptions options = RegexpOptions.fromEmbeddedOptions(embeddedOptions);
            // FIXME: Massive hack (fix in DRegexpNode too for interpreter)
            if (pattern.getEncoding() == USASCIIEncoding.INSTANCE) {
                pattern.setEncoding(ASCIIEncoding.INSTANCE);
            }
            return new ClassicRegexp(runtime, pattern.getByteList(), options);
        } catch (RaiseException re) {
            throw runtime.newRegexpError(re.getMessage());
        }
    }

    public static ClassicRegexp newDRegexpEmbedded19(Ruby runtime, IRubyObject[] strings, int embeddedOptions) {
        try {
            RegexpOptions options = RegexpOptions.fromEmbeddedOptions(embeddedOptions);
            RubyString pattern = preprocessDRegexp(runtime, strings, options);

            return new ClassicRegexp(runtime, pattern.getByteList(), options);
        } catch (RaiseException re) {
            throw runtime.newRegexpError(re.getMessage());
        }

    }

    public static ClassicRegexp newRegexp(Ruby runtime, ByteList pattern) {
        return new ClassicRegexp(runtime, pattern);
    }

    static ClassicRegexp newRegexp(Ruby runtime, ByteList str, Regex pattern) {
        ClassicRegexp regexp = new ClassicRegexp(runtime);
        str.getClass();
        regexp.str = str;
        regexp.options = RegexpOptions.fromJoniOptions(pattern.getOptions());
        regexp.pattern = pattern;
        return regexp;
    }

    // internal usage (Complex/Rational)
    static ClassicRegexp newDummyRegexp(Ruby runtime, Regex regex) {
        ClassicRegexp regexp = new ClassicRegexp(runtime);
        regexp.pattern = regex;
        regexp.str = ByteList.EMPTY_BYTELIST;
        regexp.options.setFixed(true);
        return regexp;
    }

    // MRI: rb_reg_new_str
    public static ClassicRegexp newRegexpFromStr(Ruby runtime, RubyString s, int options) {
        ClassicRegexp re = (ClassicRegexp)runtime.getRegexp().allocate();
        re.regexpInitializeString(s, RegexpOptions.fromJoniOptions(options));
        return re;
    }

    /** rb_reg_options
     */
    public RegexpOptions getOptions() {
        check();
        return options;
    }

    public final Regex getPattern() {
        check();
        return pattern;
    }

    private static void encodingMatchError(Ruby runtime, Regex pattern, Encoding strEnc) {
        throw runtime.newEncodingCompatibilityError("incompatible encoding regexp match (" +
                pattern.getEncoding() + " regexp with " + strEnc + " string)");
    }

    private Encoding checkEncoding(RubyString str, boolean warn) {
        if (str.scanForCodeRange() == StringSupport.CR_BROKEN) {
            throw getRuntime().newArgumentError("invalid byte sequence in " + str.getEncoding());
        }
        check();
        Encoding enc = str.getEncoding();
        if (!enc.isAsciiCompatible()) {
            if (enc != pattern.getEncoding()) encodingMatchError(getRuntime(), pattern, enc);
        } else if (options.isFixed()) {
            if (enc != pattern.getEncoding() &&
               (!pattern.getEncoding().isAsciiCompatible() ||
               str.scanForCodeRange() != StringSupport.CR_7BIT)) encodingMatchError(getRuntime(), pattern, enc);
            enc = pattern.getEncoding();
        }
        if (warn && isEncodingNone() && enc != ASCIIEncoding.INSTANCE && str.scanForCodeRange() != StringSupport.CR_7BIT) {
            getRuntime().getWarnings().warn(ID.REGEXP_MATCH_AGAINST_STRING, "regexp match /.../n against to " + enc + " string");
        }
        return enc;
    }

    public final Regex preparePattern(RubyString str) {
        // checkEncoding does `check();` no need to here
        Encoding enc = checkEncoding(str, true);
        if (enc == pattern.getEncoding()) return pattern;
        return getPreprocessedRegexpFromCache(getRuntime(), this.str, enc, options, RegexpSupport.ErrorMode.PREPROCESS);
    }


    /**
     * Preprocess the given string for use in regexp, raising errors for encoding
     * incompatibilities that arise.
     *
     * This version does not produce a new, unescaped version of the bytelist,
     * and simply does the string-walking portion of the logic.
     *
     * @param runtime current runtime
     * @param str string to preprocess
     * @param enc string's encoding
     * @param fixedEnc new encoding after fixing
     * @param mode mode of errors
     */
    private static void preprocessLight(Ruby runtime, ByteList str, Encoding enc, Encoding[]fixedEnc, RegexpSupport.ErrorMode mode) {
        if (enc.isAsciiCompatible()) {
            fixedEnc[0] = null;
        } else {
            fixedEnc[0] = enc;
        }

        boolean hasProperty = RegexpSupport.unescapeNonAscii(runtime, null, str.getUnsafeBytes(), str.getBegin(), str.getBegin() + str.getRealSize(), enc, fixedEnc, str, mode);
        if (hasProperty && fixedEnc[0] == null) fixedEnc[0] = enc;
    }

    private static void preprocessLight(RubyContext context, ByteList str, Encoding enc, Encoding[]fixedEnc, RegexpSupport.ErrorMode mode) {
        if (enc.isAsciiCompatible()) {
            fixedEnc[0] = null;
        } else {
            fixedEnc[0] = enc;
        }

        boolean hasProperty = unescapeNonAscii(context, null, str.getUnsafeBytes(), str.getBegin(), str.getBegin() + str.getRealSize(), enc, fixedEnc, str, mode);
        if (hasProperty && fixedEnc[0] == null) fixedEnc[0] = enc;
    }

    public static boolean unescapeNonAscii(RubyContext context, ByteList to, byte[] bytes, int p, int end, Encoding enc, Encoding[] encp, ByteList str, RegexpSupport.ErrorMode mode) {
        boolean hasProperty = false;
        byte[] buf = null;

        while (p < end) {
            int cl = StringSupport.preciseLength(enc, bytes, p, end);
            if (cl <= 0) raisePreprocessError(context, str, "invalid multibyte character", mode);
            if (cl > 1 || (bytes[p] & 0x80) != 0) {
                if (to != null) to.append(bytes, p, cl);
                p += cl;
                if (encp[0] == null) {
                    encp[0] = enc;
                } else if (encp[0] != enc) {
                    raisePreprocessError(context, str, "non ASCII character in UTF-8 regexp", mode);
                }
                continue;
            }
            int c;
            switch (c = bytes[p++] & 0xff) {
                case '\\':
                    if (p == end) raisePreprocessError(context, str, "too short escape sequence", mode);

                    switch (c = bytes[p++] & 0xff) {
                        case '1': case '2': case '3':
                        case '4': case '5': case '6': case '7': /* \O, \OO, \OOO or backref */
                            if (StringSupport.scanOct(bytes, p - 1, end - (p - 1)) <= 0177) {
                                if (to != null) to.append('\\').append(c);
                                break;
                            }

                        case '0': /* \0, \0O, \0OO */
                        case 'x': /* \xHH */
                        case 'c': /* \cX, \c\M-X */
                        case 'C': /* \C-X, \C-\M-X */
                        case 'M': /* \M-X, \M-\C-X, \M-\cX */
                            p -= 2;
                            if (enc == USASCIIEncoding.INSTANCE) {
                                if (buf == null) buf = new byte[1];
                                int pbeg = p;
                                p = readEscapedByte(context, buf, 0, bytes, p, end, str, mode);
                                c = buf[0];
                                if (c == (char)-1) return false;
                                if (to != null) {
                                    to.append(bytes, pbeg, p - pbeg);
                                }
                            }
                            else {
                                p = unescapeEscapedNonAscii(context, to, bytes, p, end, enc, encp, str, mode);
                            }
                            break;

                        case 'u':
                            if (p == end) raisePreprocessError(context, str, "too short escape sequence", mode);
                            if (bytes[p] == (byte)'{') { /* \\u{H HH HHH HHHH HHHHH HHHHHH ...} */
                                p++;
                                p = unescapeUnicodeList(context, to, bytes, p, end, encp, str, mode);
                                if (p == end || bytes[p++] != (byte)'}') raisePreprocessError(context, str, "invalid Unicode list", mode);
                            } else { /* \\uHHHH */
                                p = unescapeUnicodeBmp(context, to, bytes, p, end, encp, str, mode);
                            }
                            break;
                        case 'p': /* \p{Hiragana} */
                            if (encp[0] == null) hasProperty = true;
                            if (to != null) to.append('\\').append(c);
                            break;

                        default:
                            if (to != null) to.append('\\').append(c);
                            break;
                    } // inner switch
                    break;

                default:
                    if (to != null) to.append(c);
            } // switch
        } // while
        return hasProperty;
    }

    private static int unescapeUnicodeBmp(RubyContext context, ByteList to, byte[] bytes, int p, int end, Encoding[] encp, ByteList str, RegexpSupport.ErrorMode mode) {
        if (p + 4 > end) raisePreprocessError(context, str, "invalid Unicode escape", mode);
        int code = StringSupport.scanHex(bytes, p, 4);
        int len = StringSupport.hexLength(bytes, p, 4);
        if (len != 4) raisePreprocessError(context, str, "invalid Unicode escape", mode);
        appendUtf8(context, to, code, encp, str, mode);
        return p + 4;
    }

    private static int unescapeUnicodeList(RubyContext context, ByteList to, byte[]bytes, int p, int end, Encoding[]encp, ByteList str, RegexpSupport.ErrorMode mode) {
        while (p < end && ASCIIEncoding.INSTANCE.isSpace(bytes[p] & 0xff)) p++;

        boolean hasUnicode = false;
        while (true) {
            int code = StringSupport.scanHex(bytes, p, end - p);
            int len = StringSupport.hexLength(bytes, p, end - p);
            if (len == 0) break;
            if (len > 6) raisePreprocessError(context, str, "invalid Unicode range", mode);
            p += len;
            if (to != null) appendUtf8(context, to, code, encp, str, mode);
            hasUnicode = true;
            while (p < end && ASCIIEncoding.INSTANCE.isSpace(bytes[p] & 0xff)) p++;
        }

        if (!hasUnicode) raisePreprocessError(context, str, "invalid Unicode list", mode);
        return p;
    }

    private static void appendUtf8(RubyContext context, ByteList to, int code, Encoding[] enc, ByteList str, RegexpSupport.ErrorMode mode) {
        checkUnicodeRange(context, code, str, mode);

        if (code < 0x80) {
            if (to != null) to.append(String.format("\\x%02X", code).getBytes(StandardCharsets.US_ASCII));
        } else {
            if (to != null) {
                to.ensure(to.getRealSize() + 6);
                to.setRealSize(to.getRealSize() + utf8Decode(context, to.getUnsafeBytes(), to.getBegin() + to.getRealSize(), code));
            }
            if (enc[0] == null) {
                enc[0] = UTF8Encoding.INSTANCE;
            } else if (!(enc[0].isUTF8())) {
                raisePreprocessError(context, str, "UTF-8 character in non UTF-8 regexp", mode);
            }
        }
    }

    public static int utf8Decode(RubyContext context, byte[]to, int p, int code) {
        if (code <= 0x7f) {
            to[p] = (byte)code;
            return 1;
        }
        if (code <= 0x7ff) {
            to[p + 0] = (byte)(((code >>> 6) & 0xff) | 0xc0);
            to[p + 1] = (byte)((code & 0x3f) | 0x80);
            return 2;
        }
        if (code <= 0xffff) {
            to[p + 0] = (byte)(((code >>> 12) & 0xff) | 0xe0);
            to[p + 1] = (byte)(((code >>> 6) & 0x3f) | 0x80);
            to[p + 2] = (byte)((code & 0x3f) | 0x80);
            return 3;
        }
        if (code <= 0x1fffff) {
            to[p + 0] = (byte)(((code >>> 18) & 0xff) | 0xf0);
            to[p + 1] = (byte)(((code >>> 12) & 0x3f) | 0x80);
            to[p + 2] = (byte)(((code >>> 6) & 0x3f) | 0x80);
            to[p + 3] = (byte)((code & 0x3f) | 0x80);
            return 4;
        }
        if (code <= 0x3ffffff) {
            to[p + 0] = (byte)(((code >>> 24) & 0xff) | 0xf8);
            to[p + 1] = (byte)(((code >>> 18) & 0x3f) | 0x80);
            to[p + 2] = (byte)(((code >>> 12) & 0x3f) | 0x80);
            to[p + 3] = (byte)(((code >>> 6) & 0x3f) | 0x80);
            to[p + 4] = (byte)((code & 0x3f) | 0x80);
            return 5;
        }
        if (code <= 0x7fffffff) {
            to[p + 0] = (byte)(((code >>> 30) & 0xff) | 0xfc);
            to[p + 1] = (byte)(((code >>> 24) & 0x3f) | 0x80);
            to[p + 2] = (byte)(((code >>> 18) & 0x3f) | 0x80);
            to[p + 3] = (byte)(((code >>> 12) & 0x3f) | 0x80);
            to[p + 4] = (byte)(((code >>> 6) & 0x3f) | 0x80);
            to[p + 5] = (byte)((code & 0x3f) | 0x80);
            return 6;
        }
        throw new org.jruby.truffle.language.control.RaiseException(context.getCoreExceptions().rangeError("pack(U): value out of range", null));
    }

    private static void checkUnicodeRange(RubyContext context, int code, ByteList str, RegexpSupport.ErrorMode mode) {
        // Unicode is can be only 21 bits long, int is enough
        if ((0xd800 <= code && code <= 0xdfff) /* Surrogates */ || 0x10ffff < code) {
            raisePreprocessError(context, str, "invalid Unicode range", mode);
        }
    }

    private static int unescapeEscapedNonAscii(RubyContext context, ByteList to, byte[]bytes, int p, int end, Encoding enc, Encoding[]encp, ByteList str, RegexpSupport.ErrorMode mode) {
        byte[]chBuf = new byte[enc.maxLength()];
        int chLen = 0;

        p = readEscapedByte(context, chBuf, chLen++, bytes, p, end, str, mode);
        while (chLen < enc.maxLength() && StringSupport.MBCLEN_NEEDMORE_P(StringSupport.preciseLength(enc, chBuf, 0, chLen))) {
            p = readEscapedByte(context, chBuf, chLen++, bytes, p, end, str, mode);
        }

        int cl = StringSupport.preciseLength(enc, chBuf, 0, chLen);
        if (cl == -1) {
            raisePreprocessError(context, str, "invalid multibyte escape", mode); // MBCLEN_INVALID_P
        }

        if (chLen > 1 || (chBuf[0] & 0x80) != 0) {
            if (to != null) to.append(chBuf, 0, chLen);

            if (encp[0] == null) {
                encp[0] = enc;
            } else if (encp[0] != enc) {
                raisePreprocessError(context, str, "escaped non ASCII character in UTF-8 regexp", mode);
            }
        } else {
            if (to != null) to.append(String.format("\\x%02X", chBuf[0] & 0xff).getBytes(StandardCharsets.US_ASCII));
        }
        return p;
    }

    public static int raisePreprocessError(RubyContext context, ByteList str, String err, RegexpSupport.ErrorMode mode) {
        switch (mode) {
            case RAISE:
                throw new org.jruby.truffle.language.control.RaiseException(context.getCoreExceptions().regexpError(err, null));
            case PREPROCESS:
                throw new org.jruby.truffle.language.control.RaiseException(context.getCoreExceptions().argumentError("regexp preprocess failed: " + err, null));
            case DESC:
                // silent ?
        }
        return 0;
    }

    public static int readEscapedByte(RubyContext context, byte[] to, int toP, byte[] bytes, int p, int end, ByteList str, RegexpSupport.ErrorMode mode) {
        if (p == end || bytes[p++] != (byte)'\\') raisePreprocessError(context, str, "too short escaped multibyte character", mode);

        boolean metaPrefix = false, ctrlPrefix = false;
        int code = 0;
        while (true) {
            if (p == end) raisePreprocessError(context, str, "too short escape sequence", mode);

            switch (bytes[p++]) {
                case '\\': code = '\\'; break;
                case 'n': code = '\n'; break;
                case 't': code = '\t'; break;
                case 'r': code = '\r'; break;
                case 'f': code = '\f'; break;
                case 'v': code = '\013'; break;
                case 'a': code = '\007'; break;
                case 'e': code = '\033'; break;

            /* \OOO */
                case '0': case '1': case '2': case '3':
                case '4': case '5': case '6': case '7':
                    p--;
                    int olen = end < p + 3 ? end - p : 3;
                    code = StringSupport.scanOct(bytes, p, olen);
                    p += StringSupport.octLength(bytes, p, olen);
                    break;

                case 'x': /* \xHH */
                    int hlen = end < p + 2 ? end - p : 2;
                    code = StringSupport.scanHex(bytes, p, hlen);
                    int len = StringSupport.hexLength(bytes, p, hlen);
                    if (len < 1) raisePreprocessError(context, str, "invalid hex escape", mode);
                    p += len;
                    break;

                case 'M': /* \M-X, \M-\C-X, \M-\cX */
                    if (metaPrefix) raisePreprocessError(context, str, "duplicate meta escape", mode);
                    metaPrefix = true;
                    if (p + 1 < end && bytes[p++] == (byte)'-' && (bytes[p] & 0x80) == 0) {
                        if (bytes[p] == (byte)'\\') {
                            p++;
                            continue;
                        } else {
                            code = bytes[p++] & 0xff;
                            break;
                        }
                    }
                    raisePreprocessError(context, str, "too short meta escape", mode);

                case 'C': /* \C-X, \C-\M-X */
                    if (p == end || bytes[p++] != (byte)'-') raisePreprocessError(context, str, "too short control escape", mode);

                case 'c': /* \cX, \c\M-X */
                    if (ctrlPrefix) raisePreprocessError(context, str, "duplicate control escape", mode);
                    ctrlPrefix = true;
                    if (p < end && (bytes[p] & 0x80) == 0) {
                        if (bytes[p] == (byte)'\\') {
                            p++;
                            continue;
                        } else {
                            code = bytes[p++] & 0xff;
                            break;
                        }
                    }
                    raisePreprocessError(context, str, "too short control escape", mode);
                default:
                    raisePreprocessError(context, str, "unexpected escape sequence", mode);
            } // switch

            if (code < 0 || code > 0xff) raisePreprocessError(context, str, "invalid escape code", mode);

            if (ctrlPrefix) code &= 0x1f;
            if (metaPrefix) code |= 0x80;

            to[toP] = (byte)code;
            return p;
        } // while
    }

    public static void preprocessCheck(Ruby runtime, ByteList bytes) {
        RegexpSupport.preprocess(runtime, bytes, bytes.getEncoding(), new Encoding[]{null}, RegexpSupport.ErrorMode.RAISE);
    }

    public static RubyString preprocessDRegexp(Ruby runtime, RubyString[] strings, int embeddedOptions) {
        return preprocessDRegexp(runtime, strings, RegexpOptions.fromEmbeddedOptions(embeddedOptions));
    }

    // rb_reg_preprocess_dregexp
    public static RubyString preprocessDRegexp(Ruby runtime, IRubyObject[] strings, RegexpOptions options) {
        RubyString string = null;
        Encoding regexpEnc = null;

        for (int i = 0; i < strings.length; i++) {
            RubyString str = strings[i].convertToString();
            regexpEnc = processDRegexpElement(runtime, options, regexpEnc, runtime.getCurrentContext().encodingHolder(), str);
            string = string == null ? string = (RubyString)str.dup() : string.append19(str);
        }

        if (regexpEnc != null) string.setEncoding(regexpEnc);

        return string;
    }

    public static ByteList preprocessDRegexp(RubyContext context, ByteList[] strings, RegexpOptions options) {
        ByteList string = null;
        Encoding regexpEnc = null;

        for (int i = 0; i < strings.length; i++) {
            ByteList str = strings[i];
            final Encoding[] encodingHolder = new Encoding[]{null};
            regexpEnc = processDRegexpElement(context, options, regexpEnc, encodingHolder, str);
            if (string == null) {
                string = str.dup();
            } else {
                string.append(str);
            }
        }

        if (regexpEnc != null) string.setEncoding(regexpEnc);

        return string;
    }

    public static RubyString preprocessDRegexp(Ruby runtime, IRubyObject arg0, RegexpOptions options) {
        return processElementIntoResult(runtime, null, arg0, options, null, runtime.getCurrentContext().encodingHolder());
    }

    public static RubyString preprocessDRegexp(Ruby runtime, IRubyObject arg0, IRubyObject arg1, RegexpOptions options) {
        return processElementIntoResult(runtime, null, arg0, arg1, options, null, runtime.getCurrentContext().encodingHolder());
    }

    public static RubyString preprocessDRegexp(Ruby runtime, IRubyObject arg0, IRubyObject arg1, IRubyObject arg2, RegexpOptions options) {
        return processElementIntoResult(runtime, null, arg0, arg1, arg2, options, null, runtime.getCurrentContext().encodingHolder());
    }

    public static RubyString preprocessDRegexp(Ruby runtime, IRubyObject arg0, IRubyObject arg1, IRubyObject arg2, IRubyObject arg3, RegexpOptions options) {
        return processElementIntoResult(runtime, null, arg0, arg1, arg2, arg3, options, null, runtime.getCurrentContext().encodingHolder());
    }

    public static RubyString preprocessDRegexp(Ruby runtime, IRubyObject arg0, IRubyObject arg1, IRubyObject arg2, IRubyObject arg3, IRubyObject arg4, RegexpOptions options) {
        return processElementIntoResult(runtime, null, arg0, arg1, arg2, arg3, arg4, options, null, runtime.getCurrentContext().encodingHolder());
    }

    private static RubyString processElementIntoResult(
            Ruby runtime,
            RubyString result,
            IRubyObject arg0,
            IRubyObject arg1,
            IRubyObject arg2,
            IRubyObject arg3,
            IRubyObject arg4,
            RegexpOptions options,
            Encoding regexpEnc,
            Encoding[] fixedEnc) {
        RubyString str = arg0.convertToString();
        regexpEnc = processDRegexpElement(runtime, options, regexpEnc, fixedEnc, str);
        return processElementIntoResult(runtime, result == null ? str.strDup(runtime) : result.append19(str), arg1, arg2, arg3, arg4, options, regexpEnc, fixedEnc);
    }

    private static RubyString processElementIntoResult(
            Ruby runtime,
            RubyString result,
            IRubyObject arg0,
            IRubyObject arg1,
            IRubyObject arg2,
            IRubyObject arg3,
            RegexpOptions options,
            Encoding regexpEnc,
            Encoding[] fixedEnc) {
        RubyString str = arg0.convertToString();
        regexpEnc = processDRegexpElement(runtime, options, regexpEnc, fixedEnc, str);
        return processElementIntoResult(runtime, result == null ? str.strDup(runtime) : result.append19(str), arg1, arg2, arg3, options, regexpEnc, fixedEnc);
    }

    private static RubyString processElementIntoResult(
            Ruby runtime,
            RubyString result,
            IRubyObject arg0,
            IRubyObject arg1,
            IRubyObject arg2,
            RegexpOptions options,
            Encoding regexpEnc,
            Encoding[] fixedEnc) {
        RubyString str = arg0.convertToString();
        regexpEnc = processDRegexpElement(runtime, options, regexpEnc, fixedEnc, str);
        return processElementIntoResult(runtime, result == null ? str.strDup(runtime) : result.append19(str), arg1, arg2, options, regexpEnc, fixedEnc);
    }

    private static RubyString processElementIntoResult(
            Ruby runtime,
            RubyString result,
            IRubyObject arg0,
            IRubyObject arg1,
            RegexpOptions options,
            Encoding regexpEnc,
            Encoding[] fixedEnc) {
        RubyString str = arg0.convertToString();
        regexpEnc = processDRegexpElement(runtime, options, regexpEnc, fixedEnc, str);
        return processElementIntoResult(runtime, result == null ? str.strDup(runtime) : result.append19(str), arg1, options, regexpEnc, fixedEnc);
    }

    private static RubyString processElementIntoResult(
            Ruby runtime,
            RubyString result,
            IRubyObject arg0,
            RegexpOptions options,
            Encoding regexpEnc,
            Encoding[] fixedEnc) {
        RubyString str = arg0.convertToString();
        regexpEnc = processDRegexpElement(runtime, options, regexpEnc, fixedEnc, str);
        result = result == null ? str.strDup(runtime) : result.append19(str);
        if (regexpEnc != null) result.setEncoding(regexpEnc);
        return result;
    }

    private static Encoding processDRegexpElement(Ruby runtime, RegexpOptions options, Encoding regexpEnc, Encoding[] fixedEnc, RubyString str) {
        Encoding strEnc = str.getEncoding();

        if (options.isEncodingNone() && strEnc != ASCIIEncoding.INSTANCE) {
            if (str.scanForCodeRange() != StringSupport.CR_7BIT) {
                throw runtime.newRegexpError("/.../n has a non escaped non ASCII character in non ASCII-8BIT script");
            }
            strEnc = ASCIIEncoding.INSTANCE;
        }

        // This used to call preprocess, but the resulting bytelist was not
        // used. Since the preprocessing error-checking can be done without
        // creating a new bytelist, I added a "light" path.
        ClassicRegexp.preprocessLight(runtime, str.getByteList(), strEnc, fixedEnc, RegexpSupport.ErrorMode.PREPROCESS);

        if (fixedEnc[0] != null) {
            if (regexpEnc != null && regexpEnc != fixedEnc[0]) {
                throw runtime.newRegexpError("encoding mismatch in dynamic regexp: " + new String(regexpEnc.getName()) + " and " + new String(fixedEnc[0].getName()));
            }
            regexpEnc = fixedEnc[0];
        }
        return regexpEnc;
    }

    private static Encoding processDRegexpElement(RubyContext context, RegexpOptions options, Encoding regexpEnc, Encoding[] fixedEnc, ByteList str) {
        Encoding strEnc = str.getEncoding();

        if (options.isEncodingNone() && strEnc != ASCIIEncoding.INSTANCE) {
            if (scanForCodeRange(str) != StringSupport.CR_7BIT) {
                throw new org.jruby.truffle.language.control.RaiseException(context.getCoreExceptions().regexpError("/.../n has a non escaped non ASCII character in non ASCII-8BIT script", null));
            }
            strEnc = ASCIIEncoding.INSTANCE;
        }

        // This used to call preprocess, but the resulting bytelist was not
        // used. Since the preprocessing error-checking can be done without
        // creating a new bytelist, I added a "light" path.
        ClassicRegexp.preprocessLight(context, str, strEnc, fixedEnc, RegexpSupport.ErrorMode.PREPROCESS);

        if (fixedEnc[0] != null) {
            if (regexpEnc != null && regexpEnc != fixedEnc[0]) {
                throw new org.jruby.truffle.language.control.RaiseException(context.getCoreExceptions().regexpError("encoding mismatch in dynamic regexp: " + new String(regexpEnc.getName()) + " and " + new String(fixedEnc[0].getName()), null));
            }
            regexpEnc = fixedEnc[0];
        }
        return regexpEnc;
    }

    private static int scanForCodeRange(ByteList str) {
        int cr;
        Encoding enc = str.getEncoding();
        if (enc.minLength() > 1 && enc.isDummy()) {
            cr = CR_BROKEN;
        } else {
            cr = codeRangeScan(EncodingUtils.getActualEncoding(enc, str), str);
        }
        return cr;
    }

    private void check() {
        if (pattern == null) throw getRuntime().newTypeError("uninitialized Regexp");
    }

    @JRubyMethod(meta = true)
    public static IRubyObject try_convert(ThreadContext context, IRubyObject recv, IRubyObject args) {
        return TypeConverter.convertToTypeWithCheck(args, context.runtime.getRegexp(), "to_regexp");
    }

    /** rb_reg_s_quote
     *
     */
    @JRubyMethod(name = {"quote", "escape"}, meta = true)
    public static IRubyObject quote19(ThreadContext context, IRubyObject recv, IRubyObject arg) {
        final RubyString str = operandCheck(arg);
        return RubyString.newStringShared(context.runtime, quote19(str));
    }

    static ByteList quote19(final RubyString str) {
        final ByteList bytes = str.getByteList();
        final ByteList qBytes = quote19(bytes, str.isAsciiOnly());
        if (qBytes == bytes) str.setByteListShared();
        return qBytes;
    }

    /** rb_reg_quote
     *
     */
    private static final int QUOTED_V = 11;
    public static ByteList quote19(ByteList bs, boolean asciiOnly) {
        int p = bs.getBegin();
        int end = p + bs.getRealSize();
        byte[] bytes = bs.getUnsafeBytes();
        Encoding enc = bs.getEncoding();

        metaFound: do {
            while (p < end) {
                final int c;
                final int cl;
                if (enc.isAsciiCompatible()) {
                    cl = 1;
                    c = bytes[p] & 0xff;
                } else {
                    cl = StringSupport.preciseLength(enc, bytes, p, end);
                    c = enc.mbcToCode(bytes, p, end);
                }

                if (!Encoding.isAscii(c)) {
                    p += StringSupport.length(enc, bytes, p, end);
                    continue;
                }

                switch (c) {
                case '[': case ']': case '{': case '}':
                case '(': case ')': case '|': case '-':
                case '*': case '.': case '\\':
                case '?': case '+': case '^': case '$':
                case ' ': case '#':
                case '\t': case '\f': case QUOTED_V: case '\n': case '\r':
                    break metaFound;
                }
                p += cl;
            }
            if (asciiOnly) {
                ByteList tmp = bs.shallowDup();
                tmp.setEncoding(USASCIIEncoding.INSTANCE);
                return tmp;
            }
            return bs;
        } while (false);

        ByteList result = new ByteList(end * 2);
        result.setEncoding(asciiOnly ? USASCIIEncoding.INSTANCE : bs.getEncoding());
        byte[]obytes = result.getUnsafeBytes();
        int op = p - bs.getBegin();
        System.arraycopy(bytes, bs.getBegin(), obytes, 0, op);

        while (p < end) {
            final int c;
            final int cl;
            if (enc.isAsciiCompatible()) {
                cl = 1;
                c = bytes[p] & 0xff;
            } else {
                cl = StringSupport.preciseLength(enc, bytes, p, end);
                c = enc.mbcToCode(bytes, p, end);
            }

            if (!Encoding.isAscii(c)) {
                int n = StringSupport.length(enc, bytes, p, end);
                while (n-- > 0) obytes[op++] = bytes[p++];
                continue;
            }
            p += cl;
            switch (c) {
            case '[': case ']': case '{': case '}':
            case '(': case ')': case '|': case '-':
            case '*': case '.': case '\\':
            case '?': case '+': case '^': case '$':
            case '#':
                op += enc.codeToMbc('\\', obytes, op);
                break;
            case ' ':
                op += enc.codeToMbc('\\', obytes, op);
                op += enc.codeToMbc(' ', obytes, op);
                continue;
            case '\t':
                op += enc.codeToMbc('\\', obytes, op);
                op += enc.codeToMbc('t', obytes, op);
                continue;
            case '\n':
                op += enc.codeToMbc('\\', obytes, op);
                op += enc.codeToMbc('n', obytes, op);
                continue;
            case '\r':
                op += enc.codeToMbc('\\', obytes, op);
                op += enc.codeToMbc('r', obytes, op);
                continue;
            case '\f':
                op += enc.codeToMbc('\\', obytes, op);
                op += enc.codeToMbc('f', obytes, op);
                continue;
            case QUOTED_V:
                op += enc.codeToMbc('\\', obytes, op);
                op += enc.codeToMbc('v', obytes, op);
                continue;
            }
            op += enc.codeToMbc(c, obytes, op);
        }

        result.setRealSize(op);
        return result;
    }

    /** rb_reg_s_last_match / match_getter
    *
    */
    @JRubyMethod(name = "last_match", meta = true, reads = BACKREF)
    public static IRubyObject last_match_s(ThreadContext context, IRubyObject recv) {
        return getBackRef(context);
    }

    public static IRubyObject getBackRef(ThreadContext context) {
        IRubyObject backref = context.getBackRef();
        if (backref instanceof RubyMatchData) ((RubyMatchData) backref).use();
        return backref;
    }

    /** rb_reg_s_last_match
    *
    */
    @JRubyMethod(name = "last_match", meta = true, reads = BACKREF)
    public static IRubyObject last_match_s(ThreadContext context, IRubyObject recv, IRubyObject nth) {
        IRubyObject match = context.getBackRef();
        if (match.isNil()) return match;
        return nth_match(((RubyMatchData)match).backrefNumber(nth), match);
    }

    /** rb_reg_s_union
    *
    */
    public static IRubyObject union(ThreadContext context, IRubyObject recv, IRubyObject[] args) {
        return union19(context, recv, args);
    }

    @JRubyMethod(name = "union", rest = true, meta = true)
    public static IRubyObject union19(ThreadContext context, IRubyObject recv, IRubyObject[] args) {
        IRubyObject obj;
        if (args.length == 1 && !(obj = args[0].checkArrayType()).isNil()) {
            RubyArray ary = (RubyArray)obj;
            IRubyObject[]tmp = new IRubyObject[ary.size()];
            ary.copyInto(tmp, 0);
            args = tmp;
        }

        Ruby runtime = context.runtime;
        if (args.length == 0) {
            return runtime.getRegexp().newInstance(context, runtime.newString("(?!)"), Block.NULL_BLOCK);
        } else if (args.length == 1) {
            IRubyObject re = TypeConverter.convertToTypeWithCheck(args[0], runtime.getRegexp(), "to_regexp");
            return !re.isNil() ? re : newRegexpFromStr(runtime, (RubyString)quote19(context, recv, args[0]), 0);
        } else {
            boolean hasAsciiOnly = false;
            RubyString source = runtime.newString();
            Encoding hasAsciiCompatFixed = null;
            Encoding hasAsciiIncompat = null;

            for(int i = 0; i < args.length; i++) {
                IRubyObject e = args[i];
                if (i > 0) source.cat((byte)'|');
                IRubyObject v = TypeConverter.convertToTypeWithCheck(e, runtime.getRegexp(), "to_regexp");
                Encoding enc;
                if (!v.isNil()) {
                    ClassicRegexp regex = (ClassicRegexp) v;
                    enc = regex.getEncoding();
                    if (!enc.isAsciiCompatible()) {
                        if (hasAsciiIncompat == null) { // First regexp of union sets kcode.
                            hasAsciiIncompat = enc;
                        } else if (hasAsciiIncompat != enc) { // n kcode doesn't match first one
                            throw runtime.newArgumentError("incompatible encodings: " + hasAsciiIncompat + " and " + enc);
                        }
                    } else if (regex.getOptions().isFixed()) {
                        if (hasAsciiCompatFixed == null) { // First regexp of union sets kcode.
                            hasAsciiCompatFixed = enc;
                        } else if (hasAsciiCompatFixed != enc) { // n kcode doesn't match first one
                            throw runtime.newArgumentError("incompatible encodings: " + hasAsciiCompatFixed + " and " + enc);
                        }
                    } else {
                        hasAsciiOnly = true;
                    }
                    v = regex.to_s();
                } else {
                    RubyString str = e.convertToString();
                    enc = str.getEncoding();

                    if (!enc.isAsciiCompatible()) {
                        if (hasAsciiIncompat == null) { // First regexp of union sets kcode.
                            hasAsciiIncompat = enc;
                        } else if (hasAsciiIncompat != enc) { // n kcode doesn't match first one
                            throw runtime.newArgumentError("incompatible encodings: " + hasAsciiIncompat + " and " + enc);
                        }
                    } else if (str.isAsciiOnly()) {
                        hasAsciiOnly = true;
                    } else {
                        if (hasAsciiCompatFixed == null) { // First regexp of union sets kcode.
                            hasAsciiCompatFixed = enc;
                        } else if (hasAsciiCompatFixed != enc) { // n kcode doesn't match first one
                            throw runtime.newArgumentError("incompatible encodings: " + hasAsciiCompatFixed + " and " + enc);
                        }
                    }
                    v = quote19(context, recv, str);
                }

                if (hasAsciiIncompat != null) {
                    if (hasAsciiOnly) {
                        throw runtime.newArgumentError("ASCII incompatible encoding: " + hasAsciiIncompat);
                    }
                    if (hasAsciiCompatFixed != null) {
                        throw runtime.newArgumentError("incompatible encodings: " + hasAsciiIncompat + " and " + hasAsciiCompatFixed);
                    }
                }

                // set encoding for first append
                if (i == 0) source.setEncoding(enc);
                source.append(v);
            }
            if (hasAsciiIncompat != null) {
                source.setEncoding(hasAsciiIncompat);
            } else if (hasAsciiCompatFixed != null) {
                source.setEncoding(hasAsciiCompatFixed);
            } else {
                source.setEncoding(ASCIIEncoding.INSTANCE);
            }
            return runtime.getRegexp().newInstance(context, source, Block.NULL_BLOCK);
        }
    }

    /** rb_reg_init_copy
     */
    @JRubyMethod(required = 1, visibility = Visibility.PRIVATE)
    @Override
    public IRubyObject initialize_copy(IRubyObject re) {
        if (this == re) return this;
        checkFrozen();

        if (getMetaClass().getRealClass() != re.getMetaClass().getRealClass()) {
            throw getRuntime().newTypeError("wrong argument type");
        }

        ClassicRegexp regexp = (ClassicRegexp)re;
        regexp.check();

        return regexpInitialize(regexp.str, regexp.str.getEncoding(), regexp.getOptions());
    }

    private int objectAsJoniOptions(IRubyObject arg) {
        if (arg instanceof RubyFixnum) return RubyNumeric.fix2int(arg);
        if (arg.isTrue()) return RE_OPTION_IGNORECASE;

        return 0;
    }

    public IRubyObject initialize_m(IRubyObject arg) {
        return initialize_m19(arg);
    }

    public IRubyObject initialize_m(IRubyObject arg0, IRubyObject arg1) {
        return initialize_m19(arg0, arg1);
    }

    public IRubyObject initialize_m(IRubyObject arg0, IRubyObject arg1, IRubyObject arg2) {
        return initialize_m19(arg0, arg1, arg2);
    }

    @JRubyMethod(name = "initialize", visibility = Visibility.PRIVATE)
    public IRubyObject initialize_m19(IRubyObject arg) {
        if (arg instanceof ClassicRegexp) return initializeByRegexp19((ClassicRegexp)arg);
        return regexpInitializeString(arg.convertToString(), new RegexpOptions());
    }

    @JRubyMethod(name = "initialize", visibility = Visibility.PRIVATE)
    public IRubyObject initialize_m19(IRubyObject arg0, IRubyObject arg1) {
        if (arg0 instanceof ClassicRegexp && Options.PARSER_WARN_FLAGS_IGNORED.load()) {
            getRuntime().getWarnings().warn(ID.REGEXP_IGNORED_FLAGS, "flags ignored");
            return initializeByRegexp19((ClassicRegexp)arg0);
        }

        return regexpInitializeString(arg0.convertToString(),
                RegexpOptions.fromJoniOptions(objectAsJoniOptions(arg1)));
    }

    @JRubyMethod(name = "initialize", visibility = Visibility.PRIVATE)
    public IRubyObject initialize_m19(IRubyObject arg0, IRubyObject arg1, IRubyObject arg2) {
        if (arg0 instanceof ClassicRegexp && Options.PARSER_WARN_FLAGS_IGNORED.load()) {
            getRuntime().getWarnings().warn(ID.REGEXP_IGNORED_FLAGS, "flags ignored");
            return initializeByRegexp19((ClassicRegexp)arg0);
        }

        RegexpOptions newOptions = RegexpOptions.fromJoniOptions(objectAsJoniOptions(arg1));

        if (!arg2.isNil()) {
            ByteList kcodeBytes = arg2.convertToString().getByteList();
            if ((kcodeBytes.getRealSize() > 0 && kcodeBytes.getUnsafeBytes()[kcodeBytes.getBegin()] == 'n') ||
                (kcodeBytes.getRealSize() > 1 && kcodeBytes.getUnsafeBytes()[kcodeBytes.getBegin() + 1] == 'N')) {
                return regexpInitialize(arg0.convertToString().getByteList(), ASCIIEncoding.INSTANCE, newOptions);
            } else {
                getRuntime().getWarnings().warn("encoding option is ignored - " + kcodeBytes);
            }
        }
        return regexpInitializeString(arg0.convertToString(), newOptions);
    }

    private IRubyObject initializeByRegexp19(ClassicRegexp regexp) {
        regexp.check();
        // Clone and toggle flags since this is no longer a literal regular expression
        // but it did come from one.
        RegexpOptions newOptions = (RegexpOptions) regexp.getOptions().clone();
        newOptions.setLiteral(false);
        return regexpInitialize(regexp.str, regexp.getEncoding(), newOptions);
    }

    // rb_reg_initialize_str
    private ClassicRegexp regexpInitializeString(RubyString str, RegexpOptions options) {
        if (isLiteral()) throw getRuntime().newSecurityError("can't modify literal regexp");
        ByteList bytes = str.getByteList();
        Encoding enc = bytes.getEncoding();
        if (options.isEncodingNone()) {
            if (enc != ASCIIEncoding.INSTANCE) {
                if (str.scanForCodeRange() != StringSupport.CR_7BIT) {
                    RegexpSupport.raiseRegexpError19(getRuntime(), bytes, enc, options, "/.../n has a non escaped non ASCII character in non ASCII-8BIT script");
                }
                enc = ASCIIEncoding.INSTANCE;
            }
        }
        return regexpInitialize(bytes, enc, options);
    }

    // rb_reg_initialize
    public ClassicRegexp regexpInitialize(ByteList bytes, Encoding enc, RegexpOptions options) {
        Ruby runtime = getRuntime();
        this.options = options;

        checkFrozen();
        // FIXME: Something unsets this bit, but we aren't...be more permissive until we figure this out
        //if (isLiteral()) throw runtime.newSecurityError("can't modify literal regexp");
        if (pattern != null) throw runtime.newTypeError("already initialized regexp");
        if (enc.isDummy()) RegexpSupport.raiseRegexpError19(runtime, bytes, enc, options, "can't make regexp with dummy encoding");

        Encoding[]fixedEnc = new Encoding[]{null};
        ByteList unescaped = RegexpSupport.preprocess(runtime, bytes, enc, fixedEnc, RegexpSupport.ErrorMode.RAISE);
        if (fixedEnc[0] != null) {
            if ((fixedEnc[0] != enc && options.isFixed()) ||
               (fixedEnc[0] != ASCIIEncoding.INSTANCE && options.isEncodingNone())) {
                   RegexpSupport.raiseRegexpError19(runtime, bytes, enc, options, "incompatible character encoding");
            }
            if (fixedEnc[0] != ASCIIEncoding.INSTANCE) {
                options.setFixed(true);
                enc = fixedEnc[0];
            }
        } else if (!options.isFixed()) {
            enc = USASCIIEncoding.INSTANCE;
        }

        if (fixedEnc[0] != null) options.setFixed(true);
        if (options.isEncodingNone()) setEncodingNone();

        pattern = getRegexpFromCache(runtime, unescaped, enc, options);
        bytes.getClass();
        str = bytes;
        return this;
    }

    @JRubyMethod
    @Override
    public RubyFixnum hash() {
        check();
        int hash = pattern.getOptions();
        int len = str.getRealSize();
        int p = str.getBegin();
        byte[]bytes = str.getUnsafeBytes();
        while (len-- > 0) {
            hash = hash * 33 + bytes[p++];
        }
        return getRuntime().newFixnum(hash + (hash >> 5));
    }

    @JRubyMethod(name = {"==", "eql?"}, required = 1)
    @Override
    public IRubyObject op_equal(ThreadContext context, IRubyObject other) {
        if (this == other) {
            return context.runtime.getTrue();
        }
        if (!(other instanceof ClassicRegexp)) {
            return context.runtime.getFalse();
        }
        ClassicRegexp otherRegex = (ClassicRegexp)other;

        check();
        otherRegex.check();

        return context.runtime.newBoolean(str.equal(otherRegex.str) &&
                getOptions().equals(otherRegex.options));
    }

    public IRubyObject op_match2(ThreadContext context) {
        return op_match2_19(context);
    }

    @JRubyMethod(name = "~", reads = {LASTLINE, BACKREF}, writes = BACKREF)
    public IRubyObject op_match2_19(ThreadContext context) {
        Ruby runtime = context.runtime;
        IRubyObject line = context.getLastLine();
        if (line instanceof RubyString) {
            int start = search19(context, (RubyString)line, 0, false);
            if (start < 0) return runtime.getNil();
            return runtime.newFixnum(start);
        }
        context.setBackRef(runtime.getNil());
        return runtime.getNil();
    }

    /** rb_reg_eqq
     *
     */

    public IRubyObject eqq(ThreadContext context, IRubyObject arg) {
        return eqq19(context, arg);
    }

    @JRubyMethod(name = "===", required = 1, writes = BACKREF)
    public IRubyObject eqq19(ThreadContext context, IRubyObject arg) {
        Ruby runtime = context.runtime;
        arg = operandNoCheck(arg);
        if (arg.isNil()) {
            context.setBackRef(arg);
            return runtime.getFalse();
        }
        int start = search19(context, (RubyString)arg, 0, false);
        return (start < 0) ? runtime.getFalse() : runtime.getTrue();
    }

    @Override
    public IRubyObject op_match(ThreadContext context, IRubyObject arg) {
        return op_match19(context, arg);
    }

    // MRI: rb_reg_match
    /*@JRubyMethod(name = "=~", required = 1, writes = BACKREF, reads = BACKREF)
    @Override
    public IRubyObject op_match19(ThreadContext context, IRubyObject str) {
        final IRubyObject[] strp = { str };
        int pos = matchPos(context, str, strp, null, 0);
        if (pos < 0) return context.nil;
        pos = ((RubyString) strp[0]).subLength(pos);
        return RubyFixnum.newFixnum(context.runtime, pos);
    }*/

    /** rb_reg_match_m
     *
     */
    public IRubyObject match_m(ThreadContext context, IRubyObject str) {
        return match_m19(context, str, Block.NULL_BLOCK);
    }

    @JRubyMethod(name = "match", reads = BACKREF)
    public IRubyObject match_m19(ThreadContext context, IRubyObject str, Block block) {
        return match19Common(context, str, 0, true, block);
    }

    public IRubyObject match_m19(ThreadContext context, IRubyObject str, boolean useBackref, Block block) {
        return match19Common(context, str, 0, useBackref, block);
    }

    @JRubyMethod(name = "match", reads = BACKREF)
    public IRubyObject match_m19(ThreadContext context, IRubyObject str, IRubyObject pos, Block block) {
        return match19Common(context, str, RubyNumeric.num2int(pos), true, block);
    }

    private IRubyObject match19Common(ThreadContext context, IRubyObject str, int pos, boolean setBackref, Block block) {
        IRubyObject[] holder = setBackref ? null : new IRubyObject[] { context.nil };
        if (matchPos(context, str, null, holder, pos) < 0) {
            return context.nil;
        }

        IRubyObject backref = getBackRefInternal(context, holder);
        if (block.isGiven()) return block.yield(context, backref);
        return backref;
    }

    /**
     * MRI: reg_match_pos
     *
     * @param context thread context
     * @param arg the stringlike to match
     * @param strp an out param to hold the coerced string; ignored if null
     * @param holder an out param to hold the resulting match object; ignored if null
     * @param pos the position from which to start matching
     */
    private int matchPos(ThreadContext context, IRubyObject arg, IRubyObject[] strp, IRubyObject[] holder, int pos) {
        if (arg.isNil()) {
            setBackRefInternal(context, holder, context.nil);
            return -1;
        }
        final RubyString str = operandCheck(arg);
        if (strp != null) strp[0] = str;
        if (pos != 0) {
            if (pos < 0) {
                pos += str.strLength();
                if (pos < 0) return pos;
            }
            pos = str.rbStrOffset(pos);
        }
        return search19(context, str, pos, false, holder);
    }

    /** rb_reg_search
     */
    public final int search(ThreadContext context, RubyString str, int pos, boolean reverse, IRubyObject[] holder) {
        return search19(context, str, pos, reverse, holder);
    }

    /**
     * MRI: rb_reg_search
     *
     * This version uses current thread context to hold the resulting match data.
     */
    public final int search19(ThreadContext context, RubyString str, int pos, boolean reverse) {
        return search19(context, str, pos, reverse, true, null);
    }

    /**
     * MRI: rb_reg_search
     *
     * Holder, if non-null, will receive the backref result rather than setting it into context.
     */
    public final int search19(ThreadContext context, RubyString str, int pos, boolean reverse, IRubyObject[] holder) {
        return search19(context, str, pos, reverse, true, holder);
    }

    /**
     * MRI: rb_reg_search0
     *
     * Holder, if non-null, will receive the backref result rather than setting it into context.
     */
    public final int search19(ThreadContext context, RubyString str, int pos, boolean reverse, boolean setBackrefStr, IRubyObject[] holder) {
/*        Ruby runtime = context.runtime;

        int result = -1;
        IRubyObject match;
//        Region regs = null;
        ByteList strBL = str.getByteList();
        int range = strBL.begin();
        boolean tmpreg;

        if (pos > str.size() || pos < 0) {
            setBackRefInternal(context, holder, context.nil);
            return -1;
        }

        final Regex reg = preparePattern(str);
        tmpreg = reg != this.pattern;
        if (!tmpreg) this.useCount++;

        match = getBackRefInternal(context, holder);
        if ( match instanceof RubyMatchData ) { // ! match.isNil()
            if ( ((RubyMatchData) match).used() ) {
                match = context.nil;
            }
//            else {
//                regs = ((RubyMatchData)match).regs;
//            }
        }
//        if (match.isNil()) {
//            regs = null;
//        }
        if (!reverse) {
            range += str.size();
        }
        Matcher matcher = reg.matcher(strBL.unsafeBytes(), strBL.begin(), strBL.begin() + strBL.realSize());
        JOniException exception = null;
        try {
            result = matcherSearch(runtime, matcher, strBL.begin() + pos, range, RE_OPTION_NONE);
        } catch (JOniException je) {
            exception = je;
        }

        if (tmpreg) {
            if (this.useCount > 0) {
//                onig_free(reg);
            }
            else {
//                onig_free(RREGEXP(re)->ptr);
                this.pattern = reg;
            }
        }
        else {
            this.useCount--;
        }

        if (result < 0) {
            if (result == -1) {
                setBackRefInternal(context, holder, context.nil);
                return result;
            }
            else {
                throw runtime.newRegexpError(exception == null ? "FIXME: missing message" : exception.getMessage());
            }
        }

        final RubyMatchData matchData;
        if (match.isNil()) {
            matchData = createMatchData19(context, str, matcher, reg);
        }
        else {
            // FIXME: This could be reusing the MatchData object
            matchData = createMatchData19(context, str, matcher, reg);
            //if (setBackrefStr) {
            //    matchData.str = str.newFrozen();
            //    matchData.infectBy(str);
            //}
        }

        matchData.regexp = this;
        matchData.infectBy(this);
        setBackRefInternal(context, holder, matchData);

        return result;
 */
        throw new UnsupportedOperationException();
    }

    private static IRubyObject getBackRefInternal(ThreadContext context, IRubyObject[] holder) {
        return holder != null ? holder[0] : context.getBackRef();
    }

    private static void setBackRefInternal(ThreadContext context, IRubyObject[] holder, IRubyObject match) {
        if (holder != null) {
            holder[0] = match;
        } else {
            context.setBackRef(match);
        }
    }

    @JRubyMethod
    public IRubyObject options() {
        return getRuntime().newFixnum(getOptions().toOptions());
    }

    @JRubyMethod(name = "casefold?")
    public IRubyObject casefold_p(ThreadContext context) {
        check();
        return context.runtime.newBoolean(getOptions().isIgnorecase());
    }

    /** rb_reg_source
     *
     */
    @JRubyMethod
    public IRubyObject source() {
        check();
        return RubyString.newStringShared(getRuntime(), str).infectBy(this);
    }

    final int length() {
        return str.getRealSize();
    }

    /** rb_reg_inspect
     *
     */
    @Override
    public IRubyObject inspect() {
        return inspect19();
    }

    @JRubyMethod(name = "inspect")
    public IRubyObject inspect19() {
        if (pattern == null) return anyToString();
        return RubyString.newString(getRuntime(), RegexpSupport.regexpDescription19(getRuntime(), str, options, str.getEncoding()));
    }

    private final static int EMBEDDABLE = RE_OPTION_MULTILINE|RE_OPTION_IGNORECASE|RE_OPTION_EXTENDED;

    @JRubyMethod
    @Override
    public IRubyObject to_s() {
        check();

        Ruby runtime = getRuntime();
        RegexpOptions newOptions = (RegexpOptions)options.clone();
        int p = str.getBegin();
        int len = str.getRealSize();
        byte[] bytes = str.getUnsafeBytes();

        ByteList result = new ByteList(len);
        result.append((byte)'(').append((byte)'?');

        again: do {
            if (len >= 4 && bytes[p] == '(' && bytes[p + 1] == '?') {
                boolean err = true;
                p += 2;
                if ((len -= 2) > 0) {
                    do {
                        if (bytes[p] == 'm') {
                            newOptions.setMultiline(true);
                        } else if (bytes[p] == 'i') {
                            newOptions.setIgnorecase(true);
                        } else if (bytes[p] == 'x') {
                            newOptions.setExtended(true);
                        } else {
                            break;
                        }
                        p++;
                    } while (--len > 0);
                }
                if (len > 1 && bytes[p] == '-') {
                    ++p;
                    --len;
                    do {
                        if (bytes[p] == 'm') {
                            newOptions.setMultiline(false);
                        } else if (bytes[p] == 'i') {
                            newOptions.setIgnorecase(false);
                        } else if (bytes[p] == 'x') {
                            newOptions.setExtended(false);
                        } else {
                            break;
                        }
                        p++;
                    } while (--len > 0);
                }

                if (bytes[p] == ')') {
                    --len;
                    ++p;
                    continue again;
                }

                if (bytes[p] == ':' && bytes[p + len - 1] == ')') {
                    try {
                        new Regex(bytes, ++p, p + (len -= 2), Option.DEFAULT, str.getEncoding(), Syntax.DEFAULT);
                        err = false;
                    } catch (JOniException e) {
                        err = true;
                    }
                }

                if (err) {
                    newOptions = options;
                    p = str.getBegin();
                    len = str.getRealSize();
                }
            }

            RegexpSupport.appendOptions(result, newOptions);

            if (!newOptions.isEmbeddable()) {
                result.append((byte)'-');
                if (!newOptions.isMultiline()) result.append((byte)'m');
                if (!newOptions.isIgnorecase()) result.append((byte)'i');
                if (!newOptions.isExtended()) result.append((byte)'x');
            }
            result.append((byte)':');
            RegexpSupport.appendRegexpString19(runtime, result, bytes, p, len, str.getEncoding(), null);

            result.append((byte)')');
            return RubyString.newString(getRuntime(), result, getEncoding()).infectBy(this);
        } while (true);
    }

    public ByteList toByteList() {
        check();

        Ruby runtime = getRuntime();
        RegexpOptions newOptions = (RegexpOptions)options.clone();
        int p = str.getBegin();
        int len = str.getRealSize();
        byte[] bytes = str.getUnsafeBytes();

        ByteList result = new ByteList(len);
        result.append((byte)'(').append((byte)'?');

        again: do {
            if (len >= 4 && bytes[p] == '(' && bytes[p + 1] == '?') {
                boolean err = true;
                p += 2;
                if ((len -= 2) > 0) {
                    do {
                        if (bytes[p] == 'm') {
                            newOptions.setMultiline(true);
                        } else if (bytes[p] == 'i') {
                            newOptions.setIgnorecase(true);
                        } else if (bytes[p] == 'x') {
                            newOptions.setExtended(true);
                        } else {
                            break;
                        }
                        p++;
                    } while (--len > 0);
                }
                if (len > 1 && bytes[p] == '-') {
                    ++p;
                    --len;
                    do {
                        if (bytes[p] == 'm') {
                            newOptions.setMultiline(false);
                        } else if (bytes[p] == 'i') {
                            newOptions.setIgnorecase(false);
                        } else if (bytes[p] == 'x') {
                            newOptions.setExtended(false);
                        } else {
                            break;
                        }
                        p++;
                    } while (--len > 0);
                }

                if (bytes[p] == ')') {
                    --len;
                    ++p;
                    continue again;
                }

                if (bytes[p] == ':' && bytes[p + len - 1] == ')') {
                    try {
                        new Regex(bytes, ++p, p + (len -= 2), Option.DEFAULT, str.getEncoding(), Syntax.DEFAULT);
                        err = false;
                    } catch (JOniException e) {
                        err = true;
                    }
                }

                if (err) {
                    newOptions = options;
                    p = str.getBegin();
                    len = str.getRealSize();
                }
            }

            RegexpSupport.appendOptions(result, newOptions);

            if (!newOptions.isEmbeddable()) {
                result.append((byte)'-');
                if (!newOptions.isMultiline()) result.append((byte)'m');
                if (!newOptions.isIgnorecase()) result.append((byte)'i');
                if (!newOptions.isExtended()) result.append((byte)'x');
            }
            result.append((byte)':');
            RegexpSupport.appendRegexpString19(runtime, result, bytes, p, len, str.getEncoding(), null);

            result.append((byte)')');
            result.setEncoding(getEncoding());
            return result;
            //return RubyString.newString(getRuntime(), result, getEncoding()).infectBy(this);
        } while (true);
    }

    public String[] getNames() {
        int nameLength = pattern.numberOfNames();
        if (nameLength == 0) return EMPTY_STRING_ARRAY;

        String[] names = new String[nameLength];
        int j = 0;
        for (Iterator<NameEntry> i = pattern.namedBackrefIterator(); i.hasNext();) {
            NameEntry e = i.next();
            names[j++] = new String(e.name, e.nameP, e.nameEnd - e.nameP).intern();
        }

        return names;
    }

    /** rb_reg_names
     *
     */
    @JRubyMethod
    public IRubyObject names(ThreadContext context) {
        check();
        final Ruby runtime = context.runtime;
        if (pattern.numberOfNames() == 0) return runtime.newEmptyArray();

        RubyArray ary = RubyArray.newBlankArray(runtime, pattern.numberOfNames());
        int index = 0;
        for (Iterator<NameEntry> i = pattern.namedBackrefIterator(); i.hasNext();) {
            NameEntry e = i.next();
            RubyString name = RubyString.newStringShared(runtime, e.name, e.nameP, e.nameEnd - e.nameP, pattern.getEncoding());
            ary.store(index++, name);
        }
        return ary;
    }

    /** rb_reg_named_captures
     *
     */
    @JRubyMethod
    public IRubyObject named_captures(ThreadContext context) {
        check();
        final Ruby runtime = context.runtime;
        RubyHash hash = RubyHash.newHash(runtime);
        if (pattern.numberOfNames() == 0) return hash;

        for (Iterator<NameEntry> i = pattern.namedBackrefIterator(); i.hasNext();) {
            NameEntry e = i.next();
            int[] backrefs = e.getBackRefs();
            RubyArray ary = RubyArray.newBlankArray(runtime, backrefs.length);

            int index = 0;
            for (int backref : backrefs) {
                ary.store(index++, RubyFixnum.newFixnum(runtime, backref));
            }
            RubyString name = RubyString.newStringShared(runtime, e.name, e.nameP, e.nameEnd - e.nameP);
            hash.fastASet(name.freeze(context), ary);
        }
        return hash;
    }

    @JRubyMethod
    public IRubyObject encoding(ThreadContext context) {
        Encoding enc = (pattern == null) ? str.getEncoding() : pattern.getEncoding();
        return context.runtime.getEncodingService().getEncoding(enc);
    }

    @JRubyMethod(name = "fixed_encoding?")
    public IRubyObject fixed_encoding_p(ThreadContext context) {
        return context.runtime.newBoolean(options.isFixed());
    }

    /** rb_reg_nth_match
    *
    */
    public static IRubyObject nth_match(int nth, IRubyObject match) {
/*        if (match.isNil()) return match;
        RubyMatchData m = (RubyMatchData)match;
        m.check();

        Ruby runtime = m.getRuntime();

        final int start, end;
        if (m.regs == null) {
            if (nth >= 1 || (nth < 0 && ++nth <= 0)) return runtime.getNil();
            start = m.begin;
            end = m.end;
        } else {
            if (nth >= m.regs.numRegs || (nth < 0 && (nth+=m.regs.numRegs) <= 0)) return runtime.getNil();
            start = m.regs.beg[nth];
            end = m.regs.end[nth];
        }

        if (start == -1) return runtime.getNil();

        RubyString str = m.str.makeShared19(runtime, m.str.getType(), start, end - start);
        str.infectBy(m);
        return str;
*/
        throw new UnsupportedOperationException();
    }

    /** rb_reg_last_match
     *
     */
    public static IRubyObject last_match(IRubyObject match) {
        return nth_match(0, match);
    }
/*
    public static IRubyObject match_pre(IRubyObject match) {
        if (match.isNil()) return match;
        RubyMatchData m = (RubyMatchData)match;
        m.check();

        Ruby runtime = m.getRuntime();
        if (m.begin == -1) runtime.getNil();
        return m.str.makeShared19(runtime, m.str.getType(), 0,  m.begin).infectBy(m);
    }

    public static IRubyObject match_post(IRubyObject match) {
        if (match.isNil()) return match;
        RubyMatchData m = (RubyMatchData)match;
        m.check();

        Ruby runtime = m.getRuntime();
        if (m.begin == -1) return runtime.getNil();
        return m.str.makeShared19(runtime, m.str.getType(), m.end, m.str.getByteList().getRealSize() - m.end).infectBy(m);
    }

    public static IRubyObject match_last(IRubyObject match) {
        if (match.isNil()) return match;
        RubyMatchData m = (RubyMatchData)match;
        m.check();

        if (m.regs == null || m.regs.beg[0] == -1) return match.getRuntime().getNil();

        int i;
        for (i = m.regs.numRegs - 1; m.regs.beg[i] == -1 && i > 0; i--);
        if (i == 0) return match.getRuntime().getNil();

        return nth_match(i, match);
    }
*/
    // MRI: ASCGET macro from rb_reg_regsub
    private static final int ASCGET(boolean acompat, byte[] sBytes, int s, int e, int[] cl, Encoding strEnc) {
        if (acompat) {
            cl[0] = 1;
            return Encoding.isAscii(sBytes[s]) ? sBytes[s] & 0xFF : -1;
        } else {
            return EncodingUtils.encAscget(sBytes, s, e, cl, strEnc);
        }
    }

    // rb_reg_regsub
    static RubyString regsub19(ThreadContext context, RubyString str, RubyString src, Matcher matcher, Regex pattern) {
        Ruby runtime = str.getRuntime();

        RubyString val = null;
        int p, s, e;
        int no = 0, clen[] = {0};
        Encoding strEnc = EncodingUtils.encGet(context, str);
        Encoding srcEnc = EncodingUtils.encGet(context, src);
        boolean acompat = EncodingUtils.encAsciicompat(strEnc);

        Region regs = matcher.getRegion();
        ByteList bs = str.getByteList();
        ByteList srcbs = src.getByteList();
        byte[] sBytes = bs.getUnsafeBytes();

        p = s = bs.getBegin();
        e = p + bs.getRealSize();

        while (s < e) {
            int c = ASCGET(acompat, sBytes, s, e, clen, strEnc);

            if (c == -1) {
                s += StringSupport.length(strEnc, sBytes, s, e);
                continue;
            }
            int ss = s;
            s += clen[0];

            if (c != '\\' || s == e) continue;

            if (val == null) {
                val = RubyString.newString(str.getRuntime(), new ByteList(ss - p));
            }
            EncodingUtils.encStrBufCat(runtime, val, sBytes, p, ss - p, strEnc);

            c = ASCGET(acompat, sBytes, s, e, clen, strEnc);

            if (c == -1) {
                s += StringSupport.length(strEnc, sBytes, s, e);
                EncodingUtils.encStrBufCat(runtime, val, sBytes, ss, s - ss, strEnc);
                p = s;
                continue;
            }
            s += clen[0];

            p = s;
            switch (c) {
            case '1': case '2': case '3': case '4':
            case '5': case '6': case '7': case '8': case '9':
                if (pattern.noNameGroupIsActive(Syntax.RUBY)) {
                    no = c - '0';
                    break;
                } else {
                    continue;
                }
            case 'k':
                if (s < e && ASCGET(acompat, sBytes, s, e, clen, strEnc) == '<') {
                    int name = s + clen[0];
                    int nameEnd = name;
                    while (nameEnd < e) {
                        c = ASCGET(acompat, sBytes, nameEnd, e, clen, strEnc);
                        if (c == '>') break;
                        nameEnd += c == -1 ? StringSupport.length(strEnc, sBytes, nameEnd, e) : clen[0];
                    }
                    if (nameEnd < e) {
                        try {
                            no = pattern.nameToBackrefNumber(sBytes, name, nameEnd, regs);
                        } catch (JOniException je) {
                            throw str.getRuntime().newIndexError(je.getMessage());
                        }
                        p = s = nameEnd + clen[0];
                        break;
                    } else {
                        throw str.getRuntime().newRuntimeError("invalid group name reference format");
                    }
                }

                EncodingUtils.encStrBufCat(runtime, val, sBytes, ss, s - ss, strEnc);
                continue;
            case '0': case '&':
                no = 0;
                break;
            case '`':
                EncodingUtils.encStrBufCat(runtime, val, srcbs.getUnsafeBytes(), srcbs.getBegin(), matcher.getBegin(), srcEnc);
                continue;
            case '\'':
                EncodingUtils.encStrBufCat(runtime, val, srcbs.getUnsafeBytes(), srcbs.getBegin() + matcher.getEnd(), srcbs.getRealSize() - matcher.getEnd(), srcEnc);
                continue;
            case '+':
                if (regs != null) {
                    no = regs.numRegs - 1;
                    while (regs.beg[no] == -1 && no > 0) no--;
                }
                if (no == 0) continue;
                break;
            case '\\':
                EncodingUtils.encStrBufCat(runtime, val, sBytes, s - clen[0], clen[0], strEnc);
                continue;
            default:
                EncodingUtils.encStrBufCat(runtime, val, sBytes, ss, s - ss, strEnc);
                continue;
            }

            if (regs != null) {
                if (no >= 0) {
                    if (no >= regs.numRegs) continue;
                    if (regs.beg[no] == -1) continue;
                    EncodingUtils.encStrBufCat(runtime, val, srcbs.getUnsafeBytes(), srcbs.getBegin() + regs.beg[no], regs.end[no] - regs.beg[no], srcEnc);
                }
            } else {
                if (no != 0 || matcher.getBegin() == -1) continue;
                EncodingUtils.encStrBufCat(runtime, val, srcbs.getUnsafeBytes(), srcbs.getBegin() + matcher.getBegin(), matcher.getEnd() - matcher.getBegin(), srcEnc);
            }
        }

        if (val == null) return str;
        if (p < e) EncodingUtils.encStrBufCat(runtime, val, sBytes, p, e - p, strEnc);
        return val;
    }

    final int adjustStartPos19(RubyString str, int pos, boolean reverse) {
        return adjustStartPosInternal(str, checkEncoding(str, false), pos, reverse);
    }

    final int adjustStartPos(RubyString str, int pos, boolean reverse) {
        return adjustStartPosInternal(str, pattern.getEncoding(), pos, reverse);
    }

    private final int adjustStartPosInternal(RubyString str, Encoding enc, int pos, boolean reverse) {
        check();

        ByteList value = str.getByteList();
        int len = value.getRealSize();
        if (pos > 0 && enc.maxLength() != 1 && pos < len) {
            int start = value.getBegin();
            if ((reverse ? -pos : len - pos) > 0) {
                return enc.rightAdjustCharHead(value.getUnsafeBytes(), start, start + pos, start + len) - start;
            } else {
                return enc.leftAdjustCharHead(value.getUnsafeBytes(), start, start + pos, start + len) - start;
            }
        }

        return pos;
    }

    private static IRubyObject operandNoCheck(IRubyObject str) {
        return regOperand(str, false);
    }

    private static RubyString operandCheck(IRubyObject str) {
        return (RubyString) regOperand(str, true);
    }

    // MRI: reg_operand
    private static IRubyObject regOperand(IRubyObject str, boolean check) {
        if (str instanceof RubySymbol) return ((RubySymbol) str).to_s();
        return check ? str.convertToString() : str.checkStringType();
    }

    @Deprecated
    public static ClassicRegexp unmarshalFrom(UnmarshalStream input) throws java.io.IOException {
        ClassicRegexp result = newRegexp(input.getRuntime(), input.unmarshalString(), RegexpOptions.fromJoniOptions(input.readSignedByte()));
        input.registerLinkTarget(result);
        return result;
    }

    public static void marshalTo(ClassicRegexp regexp, MarshalStream output) throws java.io.IOException {
        output.registerLinkTarget(regexp);
        output.writeString(regexp.str);

        int options = regexp.pattern.getOptions() & EMBEDDABLE;

        if (regexp.getOptions().isFixed()) options |= RE_FIXED;

        output.writeByte(options);
    }
}
