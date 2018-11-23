# Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
# The Universal Permissive License (UPL), Version 1.0
#
# Subject to the condition set forth below, permission is hereby granted to any
# person obtaining a copy of this software, associated documentation and/or
# data (collectively the "Software"), free of charge and under any and all
# copyright rights in the Software, and any and all patent rights owned or
# freely licensable by each licensor hereunder covering either (i) the
# unmodified Software as contributed to or provided by such licensor, or (ii)
# the Larger Works (as defined below), to deal in both
#
# (a) the Software, and
#
# (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
# one is included with the Software each a "Larger Work" to which the Software
# is contributed by such licensors),
#
# without restriction, including without limitation the rights to copy, create
# derivative works of, display, perform, and distribute the Software and make,
# use, sell, offer for sale, import, export, have made, and have sold the
# Software and the Larger Work(s), and to sublicense the foregoing rights on
# either these or other terms.
#
# This license is subject to the following condition:
#
# The above copyright notice and either this complete permission notice or at a
# minimum a reference to the UPL must be included in all copies or substantial
# portions of the Software.
#
# THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
# IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
# FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
# AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
# LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
# OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
# SOFTWARE.


def assert_raises(err, fn, *args, **kwargs):
    raised = False
    try:
        fn(*args, **kwargs)
    except err:
        raised = True
    assert raised


def test_import():
    imported = True
    try:
        import array
    except ImportError:
        imported = False
    assert imported


def test_create():
    from array import array
    a = array('b', b'x' * 10)
    assert str(a) == "array('b', [120, 120, 120, 120, 120, 120, 120, 120, 120, 120])"

def test_wrong_create():
    from array import array
    raised = False
    try :
        a = array([1,2,3])
    except TypeError:
        raised = True
    assert raised

def test_add():
    from array import array
    a0 = array("b", b"hello")
    a1 = array("b", b"world")
    assert a0 + a1 == array("b", b"helloworld")

    a0 = array("b", b"hello")
    a1 = array("l", b"abcdabcd")
    try:
        res = a0 + a1
    except TypeError:
        assert True
    else:
        assert False


def test_add_int_to_long_storage():
    x = [2147483648, 1]
    x[0] = 42 # should not raise
    assert x[0] == 42

def test_add_int_to_long_array():
    from array import array
    y = array('l', [1, 2])
    y[0] = 42 # should not raise
    assert y[0] == 42
