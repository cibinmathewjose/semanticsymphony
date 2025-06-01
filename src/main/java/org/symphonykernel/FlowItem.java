/**
 * Represents an item in a flow with attributes such as name, payload, and prompts.
 * 
 * This class is used to define individual steps or items in a flow.
 * 
 * @author Cibin Jose
 */
package org.symphonykernel;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents a flow item with prompts and payload information.
 */
public class FlowItem {
   
    @JsonProperty("Name")
    String name;
    @JsonProperty("Key")
    String key;
    @JsonProperty("Paylod")
    String paylod;
    @JsonProperty("LoopKey")
    String loop;
    @JsonProperty("Array")
    boolean array;

    @JsonProperty("Required")
    
    boolean required;
    
    /**
     * System-level prompt for the flow item.
     */
    @JsonProperty("SystemPrompt")
    public String SystemPrompt;
    
    /**
     * Gets the name of the flow item.
     * 
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the name of the flow item.
     * 
     * @param name the name to set
     */
    public void setName(String name) {
        this.name = name;
    }
    public String getKey() {
        return (key==null||key.isEmpty())?name:key;
    }

    /**
     * Sets the name of the flow item.
     * 
     * @param name the name to set
     */
    public void setKey(String key) {
        this.key = key;
    }
    

    /**
     * Retrieves the payload of the flow item.
     *
     * @return the payload as a string
     */
    public String getPaylod() {
        return paylod;
    }

    /**
     * Sets the payload of the flow item.
     *
     * @param payload the payload to set
     */
    public void setPaylod(String payload) {
        this.paylod = payload;
    }

    /**
     * Checks if the flow item is an array.
     *
     * @return true if the flow item is an array, false otherwise
     */
    public boolean isArray() {
        return array;
    }
   
    public void setArray(boolean a) {
        this.array = a;
    }

    public boolean isRequired() {
        return required;
    }
   
    public void setRequired(boolean a) {
        this.required = a;
    }
  
    public String getLoopKey() {
        return loop;
    }

    /**
     * Sets the array status of the flow item.
     *
     * @param a true to set as array, false otherwise
     */
    public void setLoopKey(String a) {
        this.loop = a;
    }

    @Override
    public String toString() {
        return "FlowItem{" + "Name='" + name + '\'' + ", Paylod='" + paylod + '\'' + '}';
    }
}
