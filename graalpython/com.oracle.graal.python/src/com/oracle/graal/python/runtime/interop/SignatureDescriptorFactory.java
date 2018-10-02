package com.oracle.graal.python.runtime.interop;

import com.oracle.truffle.api.interop.KeyInfo;
import com.oracle.truffle.api.interop.MessageResolution;
import com.oracle.truffle.api.interop.Resolve;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.nodes.Node;

@MessageResolution(receiverType = SignatureDescriptor.class)
public class SignatureDescriptorFactory {

    @Resolve(message = "HAS_SIZE")
    abstract static class PForeignHasSizeNode extends Node {
        public Object access(@SuppressWarnings("unused") SignatureDescriptor object) {
            return true;
        }
    }

    @Resolve(message = "GET_SIZE")
    abstract static class PForeignGetSizeNode extends Node {
        public Object access(SignatureDescriptor object) {
            return object.parameters.size();
        }
    }

    @Resolve(message = "READ")
    abstract static class PForeignReadNode extends Node {

        public Object access(SignatureDescriptor object, Integer key) {
            if (key < 0 || object.parameters.size() <= key) {
                throw UnknownIdentifierException.raise(key.toString());
            }
            return object.parameters.get(key);
        }
    }

    @Resolve(message = "KEY_INFO")
    abstract static class PKeyInfoNode extends Node {

        public int access(SignatureDescriptor object, Integer key) {
            if (key < 0 || object.parameters.size() <= key) {
                return KeyInfo.NONE;
            }
            return KeyInfo.READABLE;
        }
    }

    @Resolve(message = "INVOKE")
    abstract static class PForeignInvokeNode extends Node {

        public Object access(SignatureDescriptor receiver, String name, @SuppressWarnings("unused") Object[] arguments) {
            if (name.equals("format")) {
                return receiver.format();
            }
            throw UnknownIdentifierException.raise(name);
        }
    }

// @Resolve(message = "HAS_KEYS")
// abstract static class HasKeysNode extends Node {
// public Object access(@SuppressWarnings("unused") SignatureDescriptor obj) {
// return true;
// }
// }
//
// @Resolve(message = "KEYS")
// abstract static class PForeignKeysNode extends Node {
// @Child private PythonObjectFactory factory = PythonObjectFactory.create();
//
// public Object access(SignatureDescriptor object) {
// return factory.createTuple(new String[]{object.identifier});
// }
// }
}
