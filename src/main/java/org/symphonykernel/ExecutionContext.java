package org.symphonykernel;

import org.symphonykernel.core.IHttpHeaderProvider;

import com.fasterxml.jackson.databind.JsonNode;

public class ExecutionContext {

    private IHttpHeaderProvider header;
    JsonNode variables;
    Knowledge kb;
    String name;
    String usersQuery;
    boolean convert;

    public void setName(String name) {
        this.name = name;
    }

    public String getName() {
        return name == null && kb != null ? kb.getName() : name;
    }

    public void setConvert(boolean convert) {
        this.convert = convert;
    }

    public boolean getConvert() {
        return convert;
    }

    public void setUsersQuery(String usersQuery) {
        this.usersQuery = usersQuery;
    }

    public String getUsersQuery() {
        return usersQuery;
    }

    public void setHttpHeaderProvider(IHttpHeaderProvider header) {
        this.header = header;
    }

    public IHttpHeaderProvider getHttpHeaderProvider() {
        return header;
    }

    public void setVariables(JsonNode variables) {
        this.variables = variables;
    }

    public JsonNode getVariables() {
        return variables;
    }

    public void setKnowledge(Knowledge kb) {
        this.kb = kb;
    }

    public Knowledge getKnowledge() {
        return kb;
    }
}
