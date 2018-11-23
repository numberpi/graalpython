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

typedef enum e_binop {
    ADD=0, SUB, MUL, TRUEDIV, LSHIFT, RSHIFT, OR, AND, XOR, FLOORDIV, MOD,
    INPLACE_OFFSET,
} BinOp;

typedef enum e_unaryop {
	POS=0, NEG, INVERT
} UnaryOp;

static PyObject* null_error(void) {
    if (!PyErr_Occurred()) {
        PyErr_SetString(PyExc_SystemError, "null argument to internal routine");
    }
    return NULL;
}

UPCALL_ID(PyNumber_Check);
int PyNumber_Check(PyObject *o) {
    PyObject *result = UPCALL_CEXT_O(_jls_PyNumber_Check, native_to_java(o));
    if(result == Py_True) {
    	return 1;
    }
    return 0;
}

UPCALL_ID(PyNumber_UnaryOp);
static PyObject * do_unaryop(PyObject *v, UnaryOp unaryop) {
    return UPCALL_CEXT_O(_jls_PyNumber_UnaryOp, native_to_java(v), unaryop);
}

UPCALL_ID(PyNumber_BinOp);
static PyObject * do_binop(PyObject *v, PyObject *w, BinOp binop) {
    return UPCALL_CEXT_O(_jls_PyNumber_BinOp, native_to_java(v), native_to_java(w), binop);
}

UPCALL_ID(PyNumber_InPlaceBinOp);
static PyObject * do_inplace_binop(PyObject *v, PyObject *w, BinOp binop) {
    return UPCALL_CEXT_O(_jls_PyNumber_InPlaceBinOp, native_to_java(v), native_to_java(w), binop);
}

PyObject * PyNumber_Add(PyObject *o1, PyObject *o2) {
	return do_binop(o1, o2, ADD);
}

PyObject * PyNumber_Subtract(PyObject *o1, PyObject *o2) {
	return do_binop(o1, o2, SUB);
}

PyObject * PyNumber_Multiply(PyObject *o1, PyObject *o2) {
	return do_binop(o1, o2, MUL);
}

PyObject * PyNumber_TrueDivide(PyObject *o1, PyObject *o2) {
	return do_binop(o1, o2, TRUEDIV);
}

PyObject * PyNumber_FloorDivide(PyObject *o1, PyObject *o2) {
	return do_binop(o1, o2, FLOORDIV);
}

PyObject * PyNumber_Remainder(PyObject *o1, PyObject *o2) {
	return do_binop(o1, o2, MOD);
}

PyObject * PyNumber_Lshift(PyObject *o1, PyObject *o2) {
	return do_binop(o1, o2, LSHIFT);
}

PyObject * PyNumber_Rshift(PyObject *o1, PyObject *o2) {
	return do_binop(o1, o2, RSHIFT);
}

PyObject * PyNumber_Or(PyObject *o1, PyObject *o2) {
	return do_binop(o1, o2, OR);
}

PyObject * PyNumber_And(PyObject *o1, PyObject *o2) {
	return do_binop(o1, o2, AND);
}

PyObject * PyNumber_Xor(PyObject *o1, PyObject *o2) {
	return do_binop(o1, o2, XOR);
}

PyObject * PyNumber_Positive(PyObject *o) {
	return do_unaryop(o, POS);
}

PyObject * PyNumber_Negative(PyObject *o) {
	return do_unaryop(o, NEG);
}

PyObject * PyNumber_Invert(PyObject *o) {
	return do_unaryop(o, INVERT);
}

UPCALL_ID(PyNumber_Index);
PyObject * PyNumber_Index(PyObject *o) {
    if (o == NULL) {
        return null_error();
    }
    return UPCALL_CEXT_O(_jls_PyNumber_Index, native_to_java(o));
}

PyObject * PyNumber_InPlaceTrueDivide(PyObject *o1, PyObject *o2) {
    return do_inplace_binop(o1, o2, TRUEDIV);
}

Py_ssize_t PyNumber_AsSsize_t(PyObject *item, PyObject *err) {
    Py_ssize_t result;
    PyObject *runerr;
    PyObject *value = PyNumber_Index(item);
    if (value == NULL) {
        return -1;
    }

    /* We're done if PyLong_AsSsize_t() returns without error. */
    result = PyLong_AsSsize_t(value);
    if (result != -1 || !(runerr = PyErr_Occurred())) {
    	return result;
    }

    /* Error handling code -- only manage OverflowError differently */
    if (!PyErr_GivenExceptionMatches(runerr, PyExc_OverflowError)) {
    	return result;
    }

    PyErr_Clear();
    /* If no error-handling desired then the default clipping
       is sufficient.
     */
    if (!err) {
        /* Whether or not it is less than or equal to
           zero is determined by the sign of ob_size
        */
        if (_PyLong_Sign(value) < 0)
            result = PY_SSIZE_T_MIN;
        else
            result = PY_SSIZE_T_MAX;
        return result;
    }
    else {
        /* Otherwise replace the error with caller's error object. */
    	PyErr_Format(err, "cannot fit '%s' into an index-sized integer", PyObject_Type(item));
    }

    Py_DECREF(value);
    return -1;
}

UPCALL_ID(PyNumber_Long);
PyObject * PyNumber_Long(PyObject *o) {
    return UPCALL_CEXT_O(_jls_PyNumber_Long, native_to_java(o));
}

UPCALL_ID(PyNumber_Float);
PyObject * PyNumber_Float(PyObject *o) {
    return ((PyObject* (*)(void*))_jls_PyNumber_Float)(native_to_java(o));
}

UPCALL_ID(PyNumber_Absolute);
PyObject * PyNumber_Absolute(PyObject *o) {
    return UPCALL_CEXT_O(_jls_PyNumber_Absolute, native_to_java(o));
}

UPCALL_ID(PyNumber_Divmod);
PyObject * PyNumber_Divmod(PyObject *a, PyObject *b) {
    return UPCALL_CEXT_O(_jls_PyNumber_Divmod, native_to_java(a), native_to_java(b));
}


UPCALL_ID(PyIter_Next);
PyObject * PyIter_Next(PyObject *iter) {
    return UPCALL_CEXT_O(_jls_PyIter_Next, native_to_java(iter));
}

UPCALL_ID(PySequence_Check);
int PySequence_Check(PyObject *s) {
    if (s == NULL) {
        return 0;
    }
    return UPCALL_CEXT_I(_jls_PySequence_Check, native_to_java(s));
}

UPCALL_ID(PyObject_Size);
Py_ssize_t PySequence_Size(PyObject *s) {
    return UPCALL_CEXT_L(_jls_PyObject_Size, native_to_java(s));
}

UPCALL_ID(PySequence_Contains);
int PySequence_Contains(PyObject *seq, PyObject *obj) {
    return UPCALL_CEXT_I(_jls_PySequence_Contains, native_to_java(seq), native_to_java(obj));
}

// taken from CPython "Objects/abstract.c"
#undef PySequence_Length
Py_ssize_t PySequence_Length(PyObject *s) {
    return PySequence_Size(s);
}
#define PySequence_Length PySequence_Size

UPCALL_ID(PySequence_GetItem);
PyObject* PySequence_GetItem(PyObject *s, Py_ssize_t i) {
    return UPCALL_CEXT_O(_jls_PySequence_GetItem, native_to_java(s), i);
}

UPCALL_ID(PySequence_SetItem);
int PySequence_SetItem(PyObject *s, Py_ssize_t i, PyObject *o) {
    return UPCALL_CEXT_I(_jls_PySequence_SetItem, native_to_java(s), i, native_to_java(o));
}

UPCALL_ID(PySequence_Tuple);
PyObject* PySequence_Tuple(PyObject *v) {
    return UPCALL_CEXT_O(_jls_PySequence_Tuple, native_to_java(v));
}

UPCALL_ID(PySequence_List);
PyObject* PySequence_List(PyObject *v) {
    return UPCALL_CEXT_O(_jls_PySequence_List, native_to_java(v));
}

PyObject * PySequence_Fast(PyObject *v, const char *m) {
    if (v == NULL) {
        return null_error();
    }

    if (PyList_CheckExact(v) || PyTuple_CheckExact(v)) {
        Py_INCREF(v);
        return v;
    }

	PyObject* result = UPCALL_CEXT_O(_jls_PySequence_List, native_to_java(v));
	if (result == NULL) {
		PyErr_SetString(PyExc_TypeError, m);
	}
	return result;
}

UPCALL_ID(PyObject_GetItem);
PyObject * PyMapping_GetItemString(PyObject *o, const char *key) {
    return UPCALL_CEXT_O(_jls_PyObject_GetItem, native_to_java(o), polyglot_from_string(key, SRC_CS));
}

UPCALL_ID(PyMapping_Keys);
PyObject * PyMapping_Keys(PyObject *o) {
    return UPCALL_CEXT_O(_jls_PyMapping_Keys, native_to_java(o));
}

// taken from CPython "Objects/abstract.c"
int PyObject_GetBuffer(PyObject *obj, Py_buffer *view, int flags) {
    PyBufferProcs *pb = obj->ob_type->tp_as_buffer;

    if (pb == NULL || pb->bf_getbuffer == NULL) {
        PyErr_Format(PyExc_TypeError,
                     "a bytes-like object is required, not '%.100s'",
                     Py_TYPE(obj)->tp_name);
        return -1;
    }
    return (*pb->bf_getbuffer)(obj, view, flags);
}

// taken from CPython "Objects/abstract.c"
void PyBuffer_Release(Py_buffer *view) {
    PyObject *obj = view->obj;
    PyBufferProcs *pb;
    if (obj == NULL)
        return;
    pb = Py_TYPE(obj)->tp_as_buffer;
    if (pb && pb->bf_releasebuffer)
        pb->bf_releasebuffer(obj, view);
    view->obj = NULL;
    Py_DECREF(obj);
}

// taken from CPython "Objects/abstract.c"
/* we do this in native code since we need to fill in the values in a given 'Py_buffer' struct */
int PyBuffer_FillInfo(Py_buffer *view, PyObject *obj, void *buf, Py_ssize_t len, int readonly, int flags) {
    if (view == NULL) {
        PyErr_SetString(PyExc_BufferError,
            "PyBuffer_FillInfo: view==NULL argument is obsolete");
        return -1;
    }

    if (((flags & PyBUF_WRITABLE) == PyBUF_WRITABLE) &&
        (readonly == 1)) {
        PyErr_SetString(PyExc_BufferError,
                        "Object is not writable.");
        return -1;
    }

    view->obj = obj;
    if (obj)
        Py_INCREF(obj);
    view->buf = buf;
    view->len = len;
    view->readonly = readonly;
    view->itemsize = 1;
    view->format = NULL;
    if ((flags & PyBUF_FORMAT) == PyBUF_FORMAT)
        view->format = "B";
    view->ndim = 1;
    view->shape = NULL;
    if ((flags & PyBUF_ND) == PyBUF_ND)
        view->shape = &(view->len);
    view->strides = NULL;
    if ((flags & PyBUF_STRIDES) == PyBUF_STRIDES)
        view->strides = &(view->itemsize);
    view->suboffsets = NULL;
    view->internal = NULL;
    return 0;
}
