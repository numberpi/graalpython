package com.oracle.graal.python.runtime.interop;

import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.TruffleObject;

public class SignatureParameterDescriptor implements TruffleObject {

    enum ParameterType {
        ID,
        VARARGS,
        KEYWORD,
        KEYWORDARGS
    }

    private final String identifier;
    private final ParameterType type;

    public SignatureParameterDescriptor(String identifier, ParameterType type) {
        this.identifier = identifier;
        this.type = type;
    }

    public ForeignAccess getForeignAccess() {
        return SignatureParameterDescriptorFactoryForeign.ACCESS;
    }

    public String format() {
        switch (type) {
            case ID:
                return identifier;
            case VARARGS:
                return "*varargs";
            case KEYWORD:
                return identifier + "=";
            case KEYWORDARGS:
                return "**kwargs";
        }

        throw new IllegalStateException(type.toString());
    }

    static boolean isInstance(TruffleObject object) {
        return object instanceof SignatureParameterDescriptor;
    }
}
