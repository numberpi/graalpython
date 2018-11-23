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
#include "capi.h"

PyTypeObject PyUnicode_Type = PY_TRUFFLE_TYPE("str", &PyType_Type, Py_TPFLAGS_DEFAULT | Py_TPFLAGS_BASETYPE | Py_TPFLAGS_UNICODE_SUBCLASS, sizeof(PyUnicodeObject));

// partially taken from CPython "Objects/unicodeobject.c"
const unsigned char _Py_ascii_whitespace[] = {
    0, 0, 0, 0, 0, 0, 0, 0,
/*     case 0x0009: * CHARACTER TABULATION */
/*     case 0x000A: * LINE FEED */
/*     case 0x000B: * LINE TABULATION */
/*     case 0x000C: * FORM FEED */
/*     case 0x000D: * CARRIAGE RETURN */
    0, 1, 1, 1, 1, 1, 0, 0,
    0, 0, 0, 0, 0, 0, 0, 0,
/*     case 0x001C: * FILE SEPARATOR */
/*     case 0x001D: * GROUP SEPARATOR */
/*     case 0x001E: * RECORD SEPARATOR */
/*     case 0x001F: * UNIT SEPARATOR */
    0, 0, 0, 0, 1, 1, 1, 1,
/*     case 0x0020: * SPACE */
    1, 0, 0, 0, 0, 0, 0, 0,
    0, 0, 0, 0, 0, 0, 0, 0,
    0, 0, 0, 0, 0, 0, 0, 0,
    0, 0, 0, 0, 0, 0, 0, 0,

    0, 0, 0, 0, 0, 0, 0, 0,
    0, 0, 0, 0, 0, 0, 0, 0,
    0, 0, 0, 0, 0, 0, 0, 0,
    0, 0, 0, 0, 0, 0, 0, 0,
    0, 0, 0, 0, 0, 0, 0, 0,
    0, 0, 0, 0, 0, 0, 0, 0,
    0, 0, 0, 0, 0, 0, 0, 0,
    0, 0, 0, 0, 0, 0, 0, 0
};

// partially taken from CPython "Objects/unicodeobject.c"
static Py_ssize_t unicode_aswidechar(PyObject *unicode, wchar_t *w, Py_ssize_t size) {
    Py_ssize_t res;
    const wchar_t *wstr;

    wstr = PyUnicode_AsUnicodeAndSize(unicode, &res);
    if (wstr == NULL) {
        return -1;
    }

    if (w != NULL) {
        if (size > res)
            size = res + 1;
        else
            res = size;
        bytes_copy2mem((char*)w, (char*)wstr, size * SIZEOF_WCHAR_T);
        return res;
    }
    else {
        return res + 1;
    }
}

PyObject* PyUnicode_FromString(const char* o) {
    return to_sulong(polyglot_from_string(o, SRC_CS));
}

static PyObject* _PyUnicode_FromUTF8(const char* o) {
    return to_sulong(polyglot_from_string(o, "utf-8"));
}

PyObject * PyUnicode_FromStringAndSize(const char *u, Py_ssize_t size) {
    if (size < 0) {
        PyErr_SetString(PyExc_SystemError, "Negative size passed to PyUnicode_FromStringAndSize");
        return NULL;
    }
    return to_sulong(polyglot_from_string_n(u, size, SRC_CS));
}

PyObject* PyTruffle_Unicode_FromFormat(const char* fmt, int s, void* v0, void* v1, void* v2, void* v3, void* v4, void* v5, void* v6, void* v7, void* v8, void* v9, void* v10, void* v11, void* v12, void* v13, void* v14, void* v15, void* v16, void* v17, void* v18, void* v19) {
    char** allocated_strings = calloc(sizeof(char*), s);
#   define ASSIGN(n, value)                     \
    switch(n) {                                 \
    case 0: v0 = value; break;                  \
    case 1: v1 = value; break;                  \
    case 2: v2 = value; break;                  \
    case 3: v3 = value; break;                  \
    case 4: v4 = value; break;                  \
    case 5: v5 = value; break;                  \
    case 6: v6 = value; break;                  \
    case 7: v7 = value; break;                  \
    case 8: v8 = value; break;                  \
    case 9: v9 = value; break;                  \
    case 10: v10 = value; break;                \
    case 11: v11 = value; break;                \
    case 12: v12 = value; break;                \
    case 13: v13 = value; break;                \
    case 14: v14 = value; break;                \
    case 15: v15 = value; break;                \
    case 16: v16 = value; break;                \
    case 17: v17 = value; break;                \
    case 18: v18 = value; break;                \
    case 19: v19 = value; break;                \
    }

    char* fmtcpy = strdup(fmt);
    char* c = fmtcpy;
    char* allocated;
    int cnt = 0;

    while (c[0] && cnt < s) {
        if (c[0] == '%') {
            switch (c[1]) {
            case 'c':
                c[1] = 'd';
                break;
            case 'A':
                c[1] = 's';
                ;
                allocated_strings[cnt] = allocated = as_char_pointer(UPCALL_O(PY_BUILTIN, polyglot_from_string("ascii", SRC_CS), native_to_java(polyglot_get_arg(cnt + 2))));
                ASSIGN(cnt, allocated);
                break;
            case 'U':
                c[1] = 's';
                allocated_strings[cnt] = allocated = as_char_pointer(PyObject_Str(polyglot_get_arg(cnt + 2)));
                ASSIGN(cnt, allocated);
                break;
            case 'S':
                c[1] = 's';
                allocated_strings[cnt] = allocated = as_char_pointer(PyObject_Str(polyglot_get_arg(cnt + 2)));
                ASSIGN(cnt, allocated);
                break;
            case 'R':
                c[1] = 's';
                allocated_strings[cnt] = allocated = as_char_pointer(PyObject_Repr(polyglot_get_arg(cnt + 2)));
                ASSIGN(cnt, allocated);
                break;
            }
            cnt++;
            c += 1;
        }
        c += 1;
    }

    char buffer[2048] = {'\0'};
    snprintf(buffer, 2047, fmtcpy, v0, v1, v2, v3, v4, v5, v6, v7, v8, v9, v10, v11, v12, v13, v14, v15, v16, v17, v18, v19);

    for (int i = 0; i < s; i++) {
        if (allocated_strings[i] != NULL) {
            free(allocated_strings[i]);
        }
    }

    return PyUnicode_FromString(buffer);

#   undef ASSIGN
}


PyObject * PyUnicode_FromUnicode(const Py_UNICODE *u, Py_ssize_t size) {
    if (u == NULL) {
        return to_sulong(polyglot_from_string_n("", 0, "utf-16le"));
    }

    switch(Py_UNICODE_SIZE) {
    case 2:
        return to_sulong(polyglot_from_string_n((const char*)u, size*2, "utf-16le"));
    case 4:
        return to_sulong(polyglot_from_string_n((const char*)u, size*4, "utf-32le"));
    }
    return NULL;
}

UPCALL_ID(PyUnicode_FromObject);
PyObject* PyUnicode_FromObject(PyObject* o) {
    return UPCALL_CEXT_O(_jls_PyUnicode_FromObject, native_to_java(o));
}

UPCALL_ID(PyUnicode_GetLength);
Py_ssize_t PyUnicode_GetLength(PyObject *unicode) {
    return UPCALL_CEXT_L(_jls_PyUnicode_GetLength, native_to_java(unicode));
}

UPCALL_ID(PyUnicode_Concat);
PyObject * PyUnicode_Concat(PyObject *left, PyObject *right) {
    return UPCALL_CEXT_O(_jls_PyUnicode_Concat, native_to_java(left), native_to_java(right));
}

UPCALL_ID(PyUnicode_FromEncodedObject);
PyObject * PyUnicode_FromEncodedObject(PyObject *obj, const char *encoding, const char *errors) {
    // TODO buffer treatment
    return UPCALL_CEXT_O(_jls_PyUnicode_FromEncodedObject, native_to_java(obj), polyglot_from_string(encoding, SRC_CS), polyglot_from_string(errors, SRC_CS));
}

UPCALL_ID(PyUnicode_InternInPlace);
void PyUnicode_InternInPlace(PyObject **s) {
    *s = UPCALL_CEXT_O(_jls_PyUnicode_InternInPlace, native_to_java(*s));
}

// taken from CPython "Python/Objects/unicodeobject.c"
PyObject * PyUnicode_InternFromString(const char *cp) {
    PyObject *s = _PyUnicode_FromUTF8(cp);
    if (s == NULL) {
        return NULL;
    }
    PyUnicode_InternInPlace(&s);
    return s;
}

// taken from CPython "Python/Objects/unicodeobject.c"
char* PyUnicode_AsUTF8(PyObject *unicode) {
    return PyUnicode_AsUTF8AndSize(unicode, NULL);
}

char* PyUnicode_AsUTF8AndSize(PyObject *unicode, Py_ssize_t *psize) {
    PyObject *result;
    result = _PyUnicode_AsUTF8String(unicode, NULL);
    if (psize) {
        *psize = PyObject_Length(result);
    }
    return PyBytes_AsString(result);
}

UPCALL_ID(_PyUnicode_AsUTF8String);
PyObject* _PyUnicode_AsUTF8String(PyObject *unicode, const char *errors) {
    void *jerrors = errors != NULL ? polyglot_from_string(errors, SRC_CS) : NULL;
    return UPCALL_CEXT_O(_jls__PyUnicode_AsUTF8String, native_to_java(unicode), native_to_java(jerrors), NULL);
}

// taken from CPython "Python/Objects/unicodeobject.c"
PyObject * PyUnicode_AsUTF8String(PyObject *unicode) {
    return _PyUnicode_AsUTF8String(unicode, NULL);
}

PyObject * PyUnicode_DecodeUTF32(const char *s, Py_ssize_t size, const char *errors, int *byteorder) {
    PyObject *result;
    void *jerrors = errors != NULL ? polyglot_from_string(errors, SRC_CS) : NULL;
    int bo = byteorder != NULL ? *byteorder : 0;
    return polyglot_invoke(PY_TRUFFLE_CEXT, "PyTruffle_Unicode_DecodeUTF32", s, size, native_to_java(jerrors), bo, NULL);
}

Py_ssize_t PyUnicode_AsWideChar(PyObject *unicode, wchar_t *w, Py_ssize_t size) {
    Py_ssize_t n;
    char* data;
    int i;
    if (w == NULL) {
        return PyObject_Size(unicode)+1;
    }
    if (unicode == NULL) {
        PyErr_BadInternalCall();
        return -1;
    }
    return unicode_aswidechar(unicode, w, size);
}

UPCALL_ID(_PyTruffle_Unicode_AsLatin1String);
PyObject* _PyUnicode_AsLatin1String(PyObject *unicode, const char *errors) {
    void *jerrors = errors != NULL ? polyglot_from_string(errors, SRC_CS) : NULL;
    return UPCALL_CEXT_O(_jls__PyTruffle_Unicode_AsLatin1String, native_to_java(unicode), native_to_java(jerrors), ERROR_MARKER);
}

// taken from CPython "Python/Objects/unicodeobject.c"
PyObject* PyUnicode_AsLatin1String(PyObject *unicode) {
    return _PyUnicode_AsLatin1String(unicode, NULL);
}

UPCALL_ID(_PyTruffle_Unicode_AsASCIIString);
PyObject* _PyUnicode_AsASCIIString(PyObject *unicode, const char *errors) {
    void *jerrors = errors != NULL ? polyglot_from_string(errors, SRC_CS) : NULL;
    return UPCALL_CEXT_O(_jls__PyTruffle_Unicode_AsASCIIString, native_to_java(unicode), native_to_java(jerrors), ERROR_MARKER);
}

// taken from CPython "Python/Objects/unicodeobject.c"
PyObject* PyUnicode_AsASCIIString(PyObject *unicode) {
    return _PyUnicode_AsASCIIString(unicode, NULL);
}

UPCALL_ID(PyUnicode_Format);
PyObject* PyUnicode_Format(PyObject *format, PyObject *args) {
    if (format == NULL || args == NULL) {
        PyErr_BadInternalCall();
        return NULL;
    }
    return UPCALL_CEXT_O(_jls_PyUnicode_Format, native_to_java(format), native_to_java(args));
}

Py_UNICODE* PyUnicode_AsUnicode(PyObject *unicode) {
    Py_ssize_t size = 0;
    return PyUnicode_AsUnicodeAndSize(unicode, &size);
}

UPCALL_ID(PyTruffle_Unicode_AsWideChar);
Py_UNICODE* PyUnicode_AsUnicodeAndSize(PyObject *unicode, Py_ssize_t *size) {
    PyObject* bytes = UPCALL_CEXT_O(_jls_PyTruffle_Unicode_AsWideChar, native_to_java(unicode), Py_UNICODE_SIZE, native_to_java(Py_None), ERROR_MARKER);
    if (bytes != NULL) {
        // exclude null terminator at the end
        *size = PyBytes_Size(bytes) / Py_UNICODE_SIZE;
        return (Py_UNICODE*) PyBytes_AsString(bytes);
    }
    return NULL;
}

int _PyUnicode_Ready(PyObject *unicode) {
    // TODO(fa) anything we need to initialize here?
    return 0;
}

UPCALL_ID(PyUnicode_FindChar);
Py_ssize_t PyUnicode_FindChar(PyObject *str, Py_UCS4 ch, Py_ssize_t start, Py_ssize_t end, int direction) {
    return UPCALL_CEXT_L(_jls_PyUnicode_FindChar, native_to_java(str), ch, start, end, direction);
}

UPCALL_ID(PyUnicode_Substring);
PyObject* PyUnicode_Substring(PyObject *self, Py_ssize_t start, Py_ssize_t end) {
    return UPCALL_CEXT_O(_jls_PyUnicode_Substring, native_to_java(self), start, end);
}

UPCALL_ID(PyUnicode_Join);
PyObject* PyUnicode_Join(PyObject *separator, PyObject *seq) {
    return UPCALL_CEXT_O(_jls_PyUnicode_Join, native_to_java(separator), native_to_java(seq));
}

PyObject* PyUnicode_New(Py_ssize_t size, Py_UCS4 maxchar) {
    return to_sulong(polyglot_from_string("", "ascii"));
}
