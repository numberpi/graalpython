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

import sys

if sys.implementation.name == "graalpython":
    import polyglot

    def test_import():
        imported_cext = polyglot.import_value("python_cext")
        import python_cext
        assert imported_cext is python_cext

    class GetterOnly():
        def __get__(self, instance, owner):
            pass

    class CustomObject():
        field = 42

        def __getitem__(self, item):
            return item * 2

        def __len__(self):
            return 21

        getter = GetterOnly()

        @property
        def setter(self):
            pass

        @setter.setter
        def setter_setter(self):
            pass

    class CustomMutable(CustomObject):
        _items = {}

        def keys(self):
            return self._items.keys()

        def items(self):
            return self._items.items()

        def values(self):
            return self._items.values()

        def __getitem__(self, key):
            return self._items[key]

        def __setitem__(self, key, item):
            self._items[key] = item

        def __delitem__(self, key):
            del self._items[key]

    def test_read():
        o = CustomObject()
        assert polyglot.__read__(o, "field") == o.field
        assert polyglot.__read__(o, 10) == o[10]
        assert polyglot.__read__(o, "@field") == o.field
        assert polyglot.__read__(o, "[field") == o["field"]

    def test_write():
        o = CustomMutable()
        o2 = CustomObject()

        polyglot.__write__(o, "field", 32)
        assert o.field == 42
        assert o["field"] == 32
        polyglot.__write__(o, "[field", 42)
        assert o.field == 42
        assert o["field"] == 42
        polyglot.__write__(o, "@field", 32)
        assert o.field == 32
        assert o["field"] == 42

        polyglot.__write__(o, "__getattribute__", 321)
        assert o["__getattribute__"] == 321
        assert o.__getattribute__ != 321

        polyglot.__write__(o, "grrrr", 42)
        assert not hasattr(o, "grrrr")
        assert o["grrrr"] == 42
        polyglot.__write__(o, "@grrrr", 42)
        assert o.grrrr == 42
        polyglot.__write__(o2, "grrrr", 42)
        assert o2.grrrr == 42

        non_string = bytearray(b"a fine non-string object we have here")
        polyglot.__write__(o, non_string, 12)
        assert not hasattr(o, non_string)
        assert o[non_string] == 12
        polyglot.__write__(o2, non_string, 12)
        assert getattr(o2, non_string) == 12

    def test_remove():
        o = CustomMutable()
        o.direct_field = 111

        polyglot.__remove__(o, "direct_field")
        assert not hasattr(o, "direct_field")

        o.direct_field = 12
        o["direct_field"] = 32
        assert "direct_field" in list(o.keys())
        polyglot.__remove__(o, "[direct_field")
        assert hasattr(o, "direct_field")
        assert "direct_field" not in list(o.keys())
        polyglot.__remove__(o, "@direct_field")
        assert not hasattr(o, "direct_field")

        o["grrrr"] = 12
        polyglot.__remove__(o, "grrrr")
        assert "grrrr" not in list(o.keys())

    def test_execute():
        assert polyglot.__execute__(abs, -10) == 10
        o = CustomMutable()
        assert polyglot.__execute__(o.__getattribute__, "field") == o.field

    def test_invoke():
        o = CustomMutable()
        assert polyglot.__invoke__(o, "__getattribute__", "field") == o.field

    def test_new():
        assert isinstance(polyglot.__new__(CustomMutable), CustomMutable)

    def test_is_null():
        assert polyglot.__is_null__(None)

    def test_has_size():
        import array

        assert polyglot.__has_size__((0,))
        assert polyglot.__has_size__([])
        assert polyglot.__has_size__(array.array('b'))
        assert polyglot.__has_size__(bytearray(b""))
        assert polyglot.__has_size__(b"")
        assert polyglot.__has_size__("")
        assert polyglot.__has_size__(range(10))
        assert polyglot.__has_size__(CustomObject())

        assert not polyglot.__has_size__({})
        assert not polyglot.__has_size__(object())

    def test_get_size():
        called = False

        class LenObject():

            def __getitem__(self, k):
                if k == 0:
                    return 1
                else:
                    raise IndexError

            def __len__(self):
                nonlocal called
                called = True
                return 1

        assert polyglot.__get_size__(LenObject()) == 1
        assert called

    def test_has_keys():
        assert not polyglot.__has_keys__(True)
        assert polyglot.__has_keys__(None)
        assert polyglot.__has_keys__(NotImplemented)
        assert not polyglot.__has_keys__(False)
        assert polyglot.__has_keys__(object())

    def test_keys():
        o = CustomObject()
        inherited_keys = len(polyglot.__keys__(o))
        o.my_field = 1
        assert len(polyglot.__keys__(o)) == 1 + inherited_keys
        assert "my_field" in polyglot.__keys__(o)

    def test_key_info():
        o = CustomObject()
        o.my_field = 1
        o.test_exec = lambda: False
        readable_invokable_insertable = polyglot.__key_info__(o, "__init__")

        readable_invokable_modifiable_insertable = polyglot.__key_info__(o, "__len__")
        assert readable_invokable_modifiable_insertable == polyglot.__key_info__(o, "test_exec")

        readable_modifiable_insertable = polyglot.__key_info__(o, "field")
        assert readable_modifiable_insertable == polyglot.__key_info__(o, "my_field")

        assert readable_invokable_insertable != readable_modifiable_insertable
        assert readable_invokable_insertable != readable_invokable_modifiable_insertable
        assert readable_modifiable_insertable != readable_invokable_modifiable_insertable

        sideeffects_get = polyglot.__key_info__(o, "getter")
        sideeffects_get_set = polyglot.__key_info__(o, "setter")
        assert sideeffects_get != sideeffects_get_set

    def test_host_lookup():
        import java
        try:
            strClass = java.type("java.lang.String")
            assert strClass.valueOf(True) == "true"
        except NotImplementedError as e:
            assert "host lookup is not allowed" in str(e)

        try:
            java.type("this.type.does.not.exist")
        except NotImplementedError as e:
            assert "host lookup is not allowed" in str(e)
        except KeyError as e:
            assert True
        else:
            assert False, "requesting a non-existing host symbol should raise KeyError"
