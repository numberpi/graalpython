# Copyright (c) 2018, Oracle and/or its affiliates.
# Copyright (C) 1996-2017 Python Software Foundation
#
# Licensed under the PYTHON SOFTWARE FOUNDATION LICENSE VERSION 2


import unittest
import zlib
import binascii
import re
import sys

pintNumber = 98765432109876543210
longNumber = 9876543210

class MyIntObject:
    def __init__(self, value):
        self.value = value
    def __int__(self):
        return self.value

class MyIndexObject:
    def __init__(self, value):
        self.value = value
    def __index__(self):
        return self.value

class CustomInt:
    def __int__(self):
        return 100

class ChecksumTests(unittest.TestCase):

    # checksum test cases
    def test_crc32start(self):
        self.assertEqual(zlib.crc32(b""), zlib.crc32(b"", 0))
        self.assertTrue(zlib.crc32(b"abc", 0xffffffff))

    def test_crc32empty(self):
        self.assertEqual(zlib.crc32(b"", 0), 0)
        self.assertEqual(zlib.crc32(b"", 1), 1)
        self.assertEqual(zlib.crc32(b"", 432), 432)
        self.assertEqual(zlib.crc32(b"", -1), 4294967295)
        self.assertEqual(zlib.crc32(b"", longNumber), 1286608618)
        self.assertEqual(zlib.crc32(b"", pintNumber), 3844505322)
        self.assertEqual(zlib.crc32(b"", MyIntObject(10)), 10)

    def test_adler32start(self):
        self.assertEqual(zlib.adler32(b""), zlib.adler32(b"", 1))
        self.assertTrue(zlib.adler32(b"abc", 0xffffffff))

    def test_adler32empty(self):
        self.assertEqual(zlib.adler32(b"", 0), 0)
        self.assertEqual(zlib.adler32(b"", 1), 1)
        self.assertEqual(zlib.adler32(b"", 432), 432)
        self.assertEqual(zlib.adler32(b"", longNumber), 1286608618)
        self.assertEqual(zlib.adler32(b"", pintNumber), 3844505322)
        self.assertEqual(zlib.adler32(b"", MyIntObject(10)), 10)

    def test_penguins(self):
        self.assertEqual(zlib.crc32(b"penguin", 0), 0x0e5c1a120)
        self.assertEqual(zlib.crc32(b"penguin", 1), 0x43b6aa94)
        self.assertEqual(zlib.adler32(b"penguin", 0), 0x0bcf02f6)
        self.assertEqual(zlib.adler32(b"penguin", 1), 0x0bd602f7)
        self.assertEqual(zlib.crc32(b"penguin"), zlib.crc32(b"penguin", 0))
        self.assertEqual(zlib.adler32(b"penguin"),zlib.adler32(b"penguin",1))

    def test_crc32_adler32_unsigned(self):
        foo = b'abcdefghijklmnop'
        # explicitly test signed behavior
        self.assertEqual(zlib.crc32(foo), 2486878355)
        self.assertEqual(zlib.crc32(b'spam'), 1138425661)
        self.assertEqual(zlib.adler32(foo+foo), 3573550353)
        self.assertEqual(zlib.adler32(b'spam'), 72286642)

    def test_same_as_binascii_crc32(self):
        foo = b'abcdefghijklmnop'
        crc = 2486878355
        self.assertEqual(binascii.crc32(foo), crc)
        self.assertEqual(zlib.crc32(foo), crc)
        self.assertEqual(binascii.crc32(b'spam'), zlib.crc32(b'spam'))

    def test_wrong_inputs(self):
        self.assertRaises(TypeError, zlib.crc32, 10)
        self.assertRaises(TypeError, zlib.crc32, 'ahoj')
        self.assertRaises(TypeError, zlib.crc32, b'ahoj', MyIndexObject(10))
        self.assertRaises(TypeError, zlib.adler32, 10)
        self.assertRaises(TypeError, zlib.adler32, 'ahoj')
        self.assertRaises(TypeError, zlib.adler32, b'ahoj', MyIndexObject(10))

class BaseCompressTestCase(object):
    def check_big_compress_buffer(self, size, compress_func):
        _1M = 1024 * 1024
        # Generate 10 MiB worth of random, and expand it by repeating it.
        # The assumption is that zlib's memory is not big enough to exploit
        # such spread out redundancy.
        data = b''.join([random.getrandbits(8 * _1M).to_bytes(_1M, 'little')
                        for i in range(10)])
        data = data * (size // len(data) + 1)
        try:
            compress_func(data)
        finally:
            # Release memory
            data = None

    def check_big_decompress_buffer(self, size, decompress_func):
        data = b'x' * size
        try:
            compressed = zlib.compress(data, 1)
        finally:
            # Release memory
            data = None
        data = decompress_func(compressed)
        # Sanity check
        try:
            self.assertEqual(len(data), size)
            self.assertEqual(len(data.strip(b'x')), 0)
        finally:
            data = None

    def assertRaisesRegex(self, expected_exception, expected_regex, function,
                          *args, **kwargs):
        """Asserts that the message in a raised exception matches a regex.

        Args:
            expected_exception: Exception class expected to be raised.
            expected_regex: Regex (re.Pattern object or string) expected
                    to be found in error message.
            args: Function to be called and extra positional args.
            kwargs: Extra kwargs.
            msg: Optional message used in case of failure. Can only be used
                    when assertRaisesRegex is used as a context manager.
        """
        try: 
            function(*args, **kwargs)
        except expected_exception as e:
            if not re.compile(expected_regex).search(str(e)):
                assert False, "exception message '%r' does not match '%r'" % (e, expected_regex)
        else:
            assert False, "expected '%r' to raise '%r'" % (self.function, exc_type)    

class CompressTests(BaseCompressTestCase, unittest.TestCase):
    # Test compression in one go (whole message compression)
    def test_speech(self):
        x = zlib.compress(HAMLET_SCENE)
        self.assertEqual(zlib.decompress(x), HAMLET_SCENE)

    def test_keywords(self):
        if (sys.version_info.major >= 3 and sys.version_info.minor >= 6):
            # the keywords were added later
            x = zlib.compress(HAMLET_SCENE, level=3)
            self.assertEqual(zlib.decompress(x), HAMLET_SCENE)
            with self.assertRaises(TypeError):
                zlib.compress(data=HAMLET_SCENE, level=3)
            self.assertEqual(zlib.decompress(x,
                                         wbits=zlib.MAX_WBITS,
                                         bufsize=zlib.DEF_BUF_SIZE),
                        HAMLET_SCENE)

    def test_speech128(self):
        # compress more data
        data = HAMLET_SCENE * 128
        x = zlib.compress(data)
        self.assertEqual(zlib.compress(bytearray(data)), x)
        for ob in x, bytearray(x):
            self.assertEqual(zlib.decompress(ob), data)

    def test_incomplete_stream(self):
        
        # A useful error message is given
        x = zlib.compress(HAMLET_SCENE)
        self.assertRaisesRegex(zlib.error,
            "Error -5 while decompressing data: incomplete or truncated stream",
            zlib.decompress, x[:-1])

    def test_custom_bufsize(self):
        data = HAMLET_SCENE * 10
        compressed = zlib.compress(data, 1)
        self.assertEqual(zlib.decompress(compressed, 15, CustomInt()), data)

HAMLET_SCENE = b"""
LAERTES

       O, fear me not.
       I stay too long: but here my father comes.

       Enter POLONIUS

       A double blessing is a double grace,
       Occasion smiles upon a second leave.

LORD POLONIUS

       Yet here, Laertes! aboard, aboard, for shame!
       The wind sits in the shoulder of your sail,
       And you are stay'd for. There; my blessing with thee!
       And these few precepts in thy memory
       See thou character. Give thy thoughts no tongue,
       Nor any unproportioned thought his act.
       Be thou familiar, but by no means vulgar.
       Those friends thou hast, and their adoption tried,
       Grapple them to thy soul with hoops of steel;
       But do not dull thy palm with entertainment
       Of each new-hatch'd, unfledged comrade. Beware
       Of entrance to a quarrel, but being in,
       Bear't that the opposed may beware of thee.
       Give every man thy ear, but few thy voice;
       Take each man's censure, but reserve thy judgment.
       Costly thy habit as thy purse can buy,
       But not express'd in fancy; rich, not gaudy;
       For the apparel oft proclaims the man,
       And they in France of the best rank and station
       Are of a most select and generous chief in that.
       Neither a borrower nor a lender be;
       For loan oft loses both itself and friend,
       And borrowing dulls the edge of husbandry.
       This above all: to thine ownself be true,
       And it must follow, as the night the day,
       Thou canst not then be false to any man.
       Farewell: my blessing season this in thee!

LAERTES

       Most humbly do I take my leave, my lord.

LORD POLONIUS

       The time invites you; go; your servants tend.

LAERTES

       Farewell, Ophelia; and remember well
       What I have said to you.

OPHELIA

       'Tis in my memory lock'd,
       And you yourself shall keep the key of it.

LAERTES

       Farewell.
"""
