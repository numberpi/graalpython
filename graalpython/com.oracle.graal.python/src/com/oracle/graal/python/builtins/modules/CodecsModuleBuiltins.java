/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.modules;

import static com.oracle.graal.python.runtime.exception.PythonErrorType.LookupError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.UnicodeDecodeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.UnicodeEncodeError;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.bytes.PIBytesLike;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.profiles.ValueProfile;

@CoreFunctions(defineModule = "_codecs")
public class CodecsModuleBuiltins extends PythonBuiltins {
    public static String DEFAULT_ENCODING = "utf-8";

    // python to java codecs mapping
    private static Map<String, String> PY_CODECS_ALIASES = new HashMap<>();
    static {
        // ascii
        PY_CODECS_ALIASES.put("us-ascii", "us-ascii");
        PY_CODECS_ALIASES.put("ascii", "us-ascii");
        PY_CODECS_ALIASES.put("646", "us-ascii");

        // latin 1
        PY_CODECS_ALIASES.put("iso-8859-1", "iso-8859-1");
        PY_CODECS_ALIASES.put("latin-1", "iso-8859-1");
        PY_CODECS_ALIASES.put("latin_1", "iso-8859-1");
        PY_CODECS_ALIASES.put("iso-8859-1", "iso-8859-1");
        PY_CODECS_ALIASES.put("iso8859-1", "iso-8859-1");
        PY_CODECS_ALIASES.put("8859", "iso-8859-1");
        PY_CODECS_ALIASES.put("cp819", "iso-8859-1");
        PY_CODECS_ALIASES.put("latin", "iso-8859-1");
        PY_CODECS_ALIASES.put("latin1", "iso-8859-1");
        PY_CODECS_ALIASES.put("L1", "iso-8859-1");

        // utf-8
        PY_CODECS_ALIASES.put("utf-8", "utf-8");
        PY_CODECS_ALIASES.put("utf_8", "utf-8");
        PY_CODECS_ALIASES.put("U8", "utf-8");
        PY_CODECS_ALIASES.put("UTF", "utf-8");
        PY_CODECS_ALIASES.put("utf8", "utf-8");

        // utf-16
        PY_CODECS_ALIASES.put("utf-16", "utf-16");
        PY_CODECS_ALIASES.put("utf_16", "utf-16");
        PY_CODECS_ALIASES.put("U16", "utf-16");
        PY_CODECS_ALIASES.put("utf16", "utf-16");
        // TODO BMP only
        PY_CODECS_ALIASES.put("utf_16_be", "utf-16be");
        PY_CODECS_ALIASES.put("utf_16_le", "utf-16le");

        // utf-32
        PY_CODECS_ALIASES.put("utf-32", "utf-32");
        PY_CODECS_ALIASES.put("utf_32", "utf-32");
        PY_CODECS_ALIASES.put("U32", "utf-32");
        PY_CODECS_ALIASES.put("utf_32_be", "utf-32be");
        PY_CODECS_ALIASES.put("utf_32_le", "utf-32le");
        PY_CODECS_ALIASES.put("utf32", "utf-32");
        // big5 big5-tw, csbig5 Traditional Chinese
        // big5hkscs big5-hkscs, hkscs Traditional Chinese
        // cp037 IBM037, IBM039 English
        // cp424 EBCDIC-CP-HE, IBM424 Hebrew
        // cp437 437, IBM437 English
        // cp500 EBCDIC-CP-BE, EBCDIC-CP-CH, IBM500 Western Europe
        // cp720 Arabic
        // cp737 Greek
        // cp775 IBM775 Baltic languages
        // cp850 850, IBM850 Western Europe
        // cp852 852, IBM852 Central and Eastern Europe
        // cp855 855, IBM855 Bulgarian, Byelorussian, Macedonian, Russian, Serbian
        // cp856 Hebrew
        // cp857 857, IBM857 Turkish
        // cp858 858, IBM858 Western Europe
        // cp860 860, IBM860 Portuguese
        // cp861 861, CP-IS, IBM861 Icelandic
        // cp862 862, IBM862 Hebrew
        // cp863 863, IBM863 Canadian
        // cp864 IBM864 Arabic
        // cp865 865, IBM865 Danish, Norwegian
        // cp866 866, IBM866 Russian
        // cp869 869, CP-GR, IBM869 Greek
        // cp874 Thai
        // cp875 Greek
        // cp932 932, ms932, mskanji, ms-kanji Japanese
        // cp949 949, ms949, uhc Korean
        // cp950 950, ms950 Traditional Chinese
        // cp1006 Urdu
        // cp1026 ibm1026 Turkish
        // cp1140 ibm1140 Western Europe
        // cp1250 windows-1250 Central and Eastern Europe
        // cp1251 windows-1251 Bulgarian, Byelorussian, Macedonian, Russian, Serbian
        // cp1252 windows-1252 Western Europe
        // cp1253 windows-1253 Greek
        // cp1254 windows-1254 Turkish
        // cp1255 windows-1255 Hebrew
        // cp1256 windows-1256 Arabic
        // cp1257 windows-1257 Baltic languages
        // cp1258 windows-1258 Vietnamese
        // euc_jp eucjp, ujis, u-jis Japanese
        // euc_jis_2004 jisx0213, eucjis2004 Japanese
        // euc_jisx0213 eucjisx0213 Japanese
        // euc_kr euckr, korean, ksc5601, ks_c-5601, ks_c-5601-1987, ksx1001, ks_x-1001 Korean
        // gb2312 chinese, csiso58gb231280, euc- cn, euccn, eucgb2312-cn, gb2312-1980, gb2312-80,
        // iso- ir-58 Simplified Chinese
        // gbk 936, cp936, ms936 Unified Chinese
        // gb18030 gb18030-2000 Unified Chinese
        // hz hzgb, hz-gb, hz-gb-2312 Simplified Chinese
        // iso2022_jp csiso2022jp, iso2022jp, iso-2022-jp Japanese
        // iso2022_jp_1 iso2022jp-1, iso-2022-jp-1 Japanese
        // iso2022_jp_2 iso2022jp-2, iso-2022-jp-2 Japanese, Korean, Simplified Chinese, Western
        // Europe, Greek
        // iso2022_jp_2004 iso2022jp-2004, iso-2022-jp-2004 Japanese
        // iso2022_jp_3 iso2022jp-3, iso-2022-jp-3 Japanese
        // iso2022_jp_ext iso2022jp-ext, iso-2022-jp-ext Japanese
        // iso2022_kr csiso2022kr, iso2022kr, iso-2022-kr Korean
        // iso8859_2 iso-8859-2, latin2, L2 Central and Eastern Europe
        // iso8859_3 iso-8859-3, latin3, L3 Esperanto, Maltese
        // iso8859_4 iso-8859-4, latin4, L4 Baltic languages
        // iso8859_5 iso-8859-5, cyrillic Bulgarian, Byelorussian, Macedonian, Russian, Serbian
        // iso8859_6 iso-8859-6, arabic Arabic
        // iso8859_7 iso-8859-7, greek, greek8 Greek
        // iso8859_8 iso-8859-8, hebrew Hebrew
        // iso8859_9 iso-8859-9, latin5, L5 Turkish
        // iso8859_10 iso-8859-10, latin6, L6 Nordic languages
        // iso8859_11 iso-8859-11, thai Thai languages
        // iso8859_13 iso-8859-13, latin7, L7 Baltic languages
        // iso8859_14 iso-8859-14, latin8, L8 Celtic languages
        // iso8859_15 iso-8859-15, latin9, L9 Western Europe
        // iso8859_16 iso-8859-16, latin10, L10 South-Eastern Europe
        // johab cp1361, ms1361 Korean
        // koi8_r Russian
        // koi8_u Ukrainian
        // mac_cyrillic maccyrillic Bulgarian, Byelorussian, Macedonian, Russian, Serbian
        // mac_greek macgreek Greek
        // mac_iceland maciceland Icelandic
        // mac_latin2 maclatin2, maccentraleurope Central and Eastern Europe
        // mac_roman macroman Western Europe
        // mac_turkish macturkish Turkish
        // ptcp154 csptcp154, pt154, cp154, cyrillic-asian Kazakh
        // shift_jis csshiftjis, shiftjis, sjis, s_jis Japanese
        // shift_jis_2004 shiftjis2004, sjis_2004, sjis2004 Japanese
        // shift_jisx0213 shiftjisx0213, sjisx0213, s_jisx0213 Japanese
        // utf_7 U7, unicode-1-1-utf-7 all languages
        // utf_8_sig
    }

    @TruffleBoundary
    static Charset getCharset(String encoding) {
        if (encoding == null) {
            return Charset.forName(DEFAULT_ENCODING);
        } else {
            String val = PY_CODECS_ALIASES.get(encoding);
            if (val != null) {
                return Charset.forName(val);
            }
            return Charset.forName(encoding);
        }
    }

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return CodecsModuleBuiltinsFactory.getFactories();
    }

    abstract static class EncodeBaseNode extends PythonBuiltinNode {

        protected static CodingErrorAction convertCodingErrorAction(String errors) {
            CodingErrorAction errorAction;
            switch (errors) {
                // TODO: see [GR-10256] to implement the correct handling mechanics
                case "ignore":
                case "surrogatepass":
                    errorAction = CodingErrorAction.IGNORE;
                    break;
                case "replace":
                case "surrogateescape":
                case "namereplace":
                case "backslashreplace":
                case "xmlcharrefreplace":
                    errorAction = CodingErrorAction.REPLACE;
                    break;
                default:
                    errorAction = CodingErrorAction.REPORT;
                    break;
            }
            return errorAction;
        }
    }

    // _codecs.encode(obj, encoding='utf-8', errors='strict')
    @Builtin(name = "__truffle_encode", fixedNumOfPositionalArgs = 1, keywordArguments = {"encoding", "errors"})
    @GenerateNodeFactory
    public abstract static class CodecsEncodeNode extends EncodeBaseNode {
        @Child private SequenceStorageNodes.LenNode lenNode;

        @Specialization(guards = "isString(str)")
        Object encode(Object str, @SuppressWarnings("unused") PNone encoding, @SuppressWarnings("unused") PNone errors,
                        @Cached("createClassProfile()") ValueProfile strTypeProfile) {
            Object profiledStr = strTypeProfile.profile(str);
            PBytes bytes = encodeString(profiledStr.toString(), "utf-8", "strict");
            return factory().createTuple(new Object[]{bytes, getLength(bytes)});
        }

        @Specialization(guards = {"isString(str)", "isString(encoding)"})
        Object encode(Object str, Object encoding, @SuppressWarnings("unused") PNone errors,
                        @Cached("createClassProfile()") ValueProfile strTypeProfile,
                        @Cached("createClassProfile()") ValueProfile encodingTypeProfile) {
            Object profiledStr = strTypeProfile.profile(str);
            Object profiledEncoding = encodingTypeProfile.profile(encoding);
            PBytes bytes = encodeString(profiledStr.toString(), profiledEncoding.toString(), "strict");
            return factory().createTuple(new Object[]{bytes, getLength(bytes)});
        }

        @Specialization(guards = {"isString(str)", "isString(errors)"})
        Object encode(Object str, @SuppressWarnings("unused") PNone encoding, Object errors,
                        @Cached("createClassProfile()") ValueProfile strTypeProfile,
                        @Cached("createClassProfile()") ValueProfile errorsTypeProfile) {
            Object profiledStr = strTypeProfile.profile(str);
            Object profiledErrors = errorsTypeProfile.profile(errors);
            PBytes bytes = encodeString(profiledStr.toString(), "utf-8", profiledErrors.toString());
            return factory().createTuple(new Object[]{bytes, getLength(bytes)});
        }

        @Specialization(guards = {"isString(str)", "isString(encoding)", "isString(errors)"})
        Object encode(Object str, Object encoding, Object errors,
                        @Cached("createClassProfile()") ValueProfile strTypeProfile,
                        @Cached("createClassProfile()") ValueProfile encodingTypeProfile,
                        @Cached("createClassProfile()") ValueProfile errorsTypeProfile) {
            Object profiledStr = strTypeProfile.profile(str);
            Object profiledEncoding = encodingTypeProfile.profile(encoding);
            Object profiledErrors = errorsTypeProfile.profile(errors);
            PBytes bytes = encodeString(profiledStr.toString(), profiledEncoding.toString(), profiledErrors.toString());
            return factory().createTuple(new Object[]{bytes, getLength(bytes)});
        }

        @Fallback
        Object encode(Object str, @SuppressWarnings("unused") Object encoding, @SuppressWarnings("unused") Object errors) {
            throw raise(TypeError, "Can't convert '%p' object to str implicitly", str);
        }

        @TruffleBoundary
        private PBytes encodeString(String self, String encoding, String errors) {
            CodingErrorAction errorAction = convertCodingErrorAction(errors);
            try {
                Charset charset = getCharset(encoding);
                ByteBuffer encoded = charset.newEncoder().onMalformedInput(errorAction).onUnmappableCharacter(errorAction).encode(CharBuffer.wrap(self));
                int n = encoded.remaining();
                byte[] data = new byte[n];
                encoded.get(data);
                return factory().createBytes(data);
            } catch (IllegalArgumentException e) {
                throw raise(LookupError, "unknown encoding: %s", encoding);
            } catch (CharacterCodingException e) {
                throw raise(UnicodeEncodeError, e);
            }
        }

        private int getLength(PBytes b) {
            if (lenNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                lenNode = insert(SequenceStorageNodes.LenNode.create());
            }
            return lenNode.execute(b.getSequenceStorage());
        }
    }

    @Builtin(name = "__truffle_raw_encode", fixedNumOfPositionalArgs = 1, keywordArguments = {"errors"})
    @GenerateNodeFactory
    public abstract static class RawEncodeNode extends EncodeBaseNode {

        @Specialization
        PTuple encode(String self, @SuppressWarnings("unused") PNone none) {
            return encode(self, "strict");
        }

        @Specialization
        PTuple encode(String self, String errors) {
            return factory().createTuple(encodeString(self, errors));
        }

        @TruffleBoundary
        private Object[] encodeString(String self, String errors) {
            CodingErrorAction errorAction = convertCodingErrorAction(errors);

            try {
                Charset charset = getCharset("utf-32");
                ByteBuffer encoded = charset.newEncoder().onMalformedInput(errorAction).onUnmappableCharacter(errorAction).encode(CharBuffer.wrap(self));
                int n = encoded.remaining();
                ByteBuffer buf = ByteBuffer.allocate(n);
                assert n % Integer.BYTES == 0;
                int codePoints = n / Integer.BYTES;

                while (encoded.hasRemaining()) {
                    int codePoint = encoded.getInt();
                    if (codePoint <= 0xFF) {
                        buf.put((byte) codePoint);
                    } else {
                        buf.put((byte) '\\');
                        buf.put((byte) 'u');
                        String hexString = Integer.toHexString(codePoint);
                        for (int i = 0; i < hexString.length(); i++) {
                            assert hexString.charAt(i) < 128;
                            buf.put((byte) hexString.charAt(i));
                        }
                    }
                }
                buf.flip();
                n = buf.remaining();
                byte[] data = new byte[n];
                buf.get(data);
                // TODO(fa): bytes object creation should not be behind a TruffleBoundary
                return new Object[]{factory().createBytes(data), codePoints};
            } catch (CharacterCodingException e) {
                throw raise(UnicodeEncodeError, e);
            }
        }

    }

    // _codecs.decode(obj, encoding='utf-8', errors='strict')
    @Builtin(name = "__truffle_decode", fixedNumOfPositionalArgs = 1, keywordArguments = {"encoding", "errors"})
    @GenerateNodeFactory
    abstract static class CodecsDecodeNode extends EncodeBaseNode {
        @Child private SequenceStorageNodes.ToByteArrayNode toByteArrayNode;

        @Specialization
        Object decode(PIBytesLike bytes, @SuppressWarnings("unused") PNone encoding, @SuppressWarnings("unused") PNone errors) {
            String string = decodeBytes(getBytesBuffer(bytes), "utf-8", "strict");
            return factory().createTuple(new Object[]{string, string.length()});
        }

        @Specialization(guards = {"isString(encoding)"})
        Object decode(PIBytesLike bytes, Object encoding, @SuppressWarnings("unused") PNone errors,
                        @Cached("createClassProfile()") ValueProfile encodingTypeProfile) {
            Object profiledEncoding = encodingTypeProfile.profile(encoding);
            String string = decodeBytes(getBytesBuffer(bytes), profiledEncoding.toString(), "strict");
            return factory().createTuple(new Object[]{string, string.length()});
        }

        @Specialization(guards = {"isString(errors)"})
        Object decode(PIBytesLike bytes, @SuppressWarnings("unused") PNone encoding, Object errors,
                        @Cached("createClassProfile()") ValueProfile errorsTypeProfile) {
            Object profiledErrors = errorsTypeProfile.profile(errors);
            String string = decodeBytes(getBytesBuffer(bytes), "utf-8", profiledErrors.toString());
            return factory().createTuple(new Object[]{string, string.length()});
        }

        @Specialization(guards = {"isString(encoding)", "isString(errors)"})
        Object decode(PIBytesLike bytes, Object encoding, Object errors,
                        @Cached("createClassProfile()") ValueProfile encodingTypeProfile,
                        @Cached("createClassProfile()") ValueProfile errorsTypeProfile) {
            Object profiledEncoding = encodingTypeProfile.profile(encoding);
            Object profiledErrors = errorsTypeProfile.profile(errors);
            String string = decodeBytes(getBytesBuffer(bytes), profiledEncoding.toString(), profiledErrors.toString());
            return factory().createTuple(new Object[]{string, string.length()});
        }

        @Fallback
        Object decode(Object bytes, @SuppressWarnings("unused") Object encoding, @SuppressWarnings("unused") Object errors) {
            throw raise(TypeError, "a bytes-like object is required, not '%p'", bytes);
        }

        private ByteBuffer getBytesBuffer(PIBytesLike bytesLike) {
            if (toByteArrayNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                toByteArrayNode = insert(SequenceStorageNodes.ToByteArrayNode.create(false));
            }
            byte[] barr = toByteArrayNode.execute(bytesLike.getSequenceStorage());
            return ByteBuffer.wrap(barr, 0, barr.length);
        }

        @TruffleBoundary
        String decodeBytes(ByteBuffer bytes, String encoding, String errors) {
            CodingErrorAction errorAction = convertCodingErrorAction(errors);
            try {
                Charset charset = getCharset(encoding);
                CharBuffer decoded = charset.newDecoder().onMalformedInput(errorAction).onUnmappableCharacter(errorAction).decode(bytes);
                return String.valueOf(decoded);
            } catch (IllegalArgumentException e) {
                throw raise(LookupError, "unknown encoding: %s", encoding);
            } catch (CharacterCodingException e) {
                throw raise(UnicodeDecodeError, e);
            }
        }
    }

    @Builtin(name = "__truffle_raw_decode", fixedNumOfPositionalArgs = 1, keywordArguments = {"errors"})
    @GenerateNodeFactory
    abstract static class RawDecodeNode extends EncodeBaseNode {
        @Child private SequenceStorageNodes.ToByteArrayNode toByteArrayNode;

        @Specialization
        Object decode(PIBytesLike bytes, @SuppressWarnings("unused") PNone errors) {
            String string = decodeBytes(getBytesBuffer(bytes), "strict");
            return factory().createTuple(new Object[]{string, string.length()});
        }

        @Specialization(guards = {"isString(errors)"})
        Object decode(PIBytesLike bytes, Object errors,
                        @Cached("createClassProfile()") ValueProfile errorsTypeProfile) {
            Object profiledErrors = errorsTypeProfile.profile(errors);
            String string = decodeBytes(getBytesBuffer(bytes), profiledErrors.toString());
            return factory().createTuple(new Object[]{string, string.length()});
        }

        private ByteBuffer getBytesBuffer(PIBytesLike bytesLike) {
            if (toByteArrayNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                toByteArrayNode = insert(SequenceStorageNodes.ToByteArrayNode.create(false));
            }
            byte[] barr = toByteArrayNode.execute(bytesLike.getSequenceStorage());
            return ByteBuffer.wrap(barr, 0, barr.length);
        }

        @TruffleBoundary
        String decodeBytes(ByteBuffer bytes, String errors) {
            CodingErrorAction errorAction = convertCodingErrorAction(errors);
            try {
                ByteBuffer buf = ByteBuffer.allocate(bytes.remaining() * Integer.BYTES);
                while (bytes.hasRemaining()) {
                    int val;
                    byte b = bytes.get();
                    if (b == (byte) '\\') {
                        byte b1 = bytes.get();
                        if (b1 == (byte) 'u') {
                            // read 2 bytes as integer
                            val = bytes.getShort();
                        } else if (b1 == (byte) 'U') {
                            val = bytes.getInt();
                        } else {
                            throw new CharacterCodingException();
                        }
                    } else {
                        val = b;
                    }
                    buf.putInt(val);
                }
                buf.flip();
                Charset charset = getCharset("utf-32");
                CharBuffer decoded = charset.newDecoder().onMalformedInput(errorAction).onUnmappableCharacter(errorAction).decode(buf);
                return String.valueOf(decoded);
            } catch (CharacterCodingException e) {
                throw raise(UnicodeDecodeError, e);
            }
        }
    }

    // _codecs.lookup(name)
    @Builtin(name = "__truffle_lookup", fixedNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class CodecsLookupNode extends PythonBuiltinNode {
        // This is replaced in the core _codecs.py with the full functionality
        @Specialization
        Object lookup(String encoding) {
            try {
                getCharset(encoding);
                return true;
            } catch (IllegalArgumentException e) {
                return PNone.NONE;
            }
        }
    }
}
