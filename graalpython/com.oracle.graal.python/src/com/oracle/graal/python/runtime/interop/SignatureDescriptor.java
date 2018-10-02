package com.oracle.graal.python.runtime.interop;

import java.util.ArrayList;
import java.util.List;

import com.oracle.graal.python.runtime.interop.SignatureParameterDescriptor.ParameterType;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.TruffleObject;

public class SignatureDescriptor implements TruffleObject {

    protected final String identifier;
    protected final List<SignatureParameterDescriptor> parameters;

    public SignatureDescriptor(String name) {
        this.identifier = name;
        this.parameters = new ArrayList<>();
    }

    public ForeignAccess getForeignAccess() {
        return SignatureDescriptorFactoryForeign.ACCESS;
    }

    public String format() {
        StringBuilder sb = new StringBuilder();
        sb.append(identifier);
        sb.append('(');
        boolean first = true;
        for (SignatureParameterDescriptor parameter : parameters) {
            if (!first) {
                sb.append(", ");
            }
            sb.append(parameter.format());
            first = false;
        }
        sb.append(')');
        return sb.toString();
    }

    static boolean isInstance(TruffleObject object) {
        return object instanceof SignatureDescriptor;
    }

    public void addParameter(String paramId) {
        parameters.add(new SignatureParameterDescriptor(paramId, ParameterType.ID));
    }

    public void addVarArgs() {
        parameters.add(new SignatureParameterDescriptor("", ParameterType.VARARGS));
    }

    public void addKeyword(String paramId) {
        parameters.add(new SignatureParameterDescriptor(paramId, ParameterType.KEYWORD));
    }

    public void addKeywordArgs() {
        parameters.add(new SignatureParameterDescriptor("", ParameterType.KEYWORDARGS));
    }
}
