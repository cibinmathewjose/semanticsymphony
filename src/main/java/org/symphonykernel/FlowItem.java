package org.symphonykernel;

import com.fasterxml.jackson.annotation.JsonProperty;

public class FlowItem {
   
    @JsonProperty("Name")
    String name;
    @JsonProperty("Paylod")
    String paylod;
    @JsonProperty("Array")
    boolean array;
    
    @JsonProperty("SystemPrompt")
    public String SystemPrompt;
    
    @JsonProperty("UserPrompt")
    public String UserPrompt;

    public String getName() {
        return name;
    }

    public void set(String name) {
        this.name = name;
    }

    public String getPaylod() {
        return paylod;
    }

    public void setPaylod(String payload) {
        this.paylod = payload;
    }

    public boolean isArray() {
        return array;
    }

    public void setArray(boolean a) {
        this.array = a;
    }

    @Override
    public String toString() {
        return "FlowItem{" + "Name='" + name + '\'' + ", Paylod='" + paylod + '\'' + '}';
    }
}
