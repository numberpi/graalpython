package com.oracle.graal.python.runtime.interop;

import com.oracle.truffle.api.interop.MessageResolution;
import com.oracle.truffle.api.interop.Resolve;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.nodes.Node;

@MessageResolution(receiverType = SignatureParameterDescriptor.class)
public class SignatureParameterDescriptorFactory {

    @Resolve(message = "INVOKE")
    abstract static class PForeignInvokeNode extends Node {

        public Object access(SignatureParameterDescriptor receiver, String name, @SuppressWarnings("unused") Object[] arguments) {
            if (name.equals("format")) {
                return receiver.format();
            }
            throw UnknownIdentifierException.raise(name);
        }
    }
}
