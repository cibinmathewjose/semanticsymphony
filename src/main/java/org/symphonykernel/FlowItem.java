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
    /**
     * Gets the key of the flow item.
     * If the key is null or empty, the name is returned instead.
     *
     * @return the key or name
     */
    public String getKey() {
        return (key == null || key.isEmpty()) ? name : key;
    }

    /**
     * Sets the key of the flow item.
     *
     * @param key the key to set
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
   
    /**
     * Sets whether the flow item is an array.
     *
     * @param a true if the flow item is an array, false otherwise
     */
    public void setArray(boolean a) {
        this.array = a;
    }

    /**
     * Checks if the flow item is required.
     *
     * @return true if required, false otherwise
     */
    public boolean isRequired() {
        return required;
    }
   
    /**
     * Sets whether the flow item is required.
     *
     * @param a true if required, false otherwise
     */
    public void setRequired(boolean a) {
        this.required = a;
    }
  
    /**
     * Gets the loop key of the flow item.
     *
     * @return the loop key
     */
    public String getLoopKey() {
        return loop;
    }

    /**
     * Sets the loop key of the flow item.
     *
     * @param a the loop key to set
     */
    public void setLoopKey(String a) {
        this.loop = a;
    }

    @Override
    public String toString() {
        return "FlowItem{" + "Name='" + name + '\'' + ", Paylod='" + paylod + '\'' + '}';
    }
}
