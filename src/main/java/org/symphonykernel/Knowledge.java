package org.symphonykernel;

import jakarta.persistence.Column;
import jakarta.persistence.Lob;

/**
 * Represents a knowledge entity with a description and data.
 */
public class Knowledge extends KnowledgeDescription  {

	
 
	private QueryType type;
	
	
	private String params;
	
	private String data;
		
	private String card;
	
	private String url;

	private String tools;
	
    private String systemPrompt;
    
	
	/**
     * Gets the URL associated with the knowledge.
     *
     * @return the URL.
     */
	public String getUrl() {
		return url;
	}

	/**
     * Sets the URL associated with the knowledge.
     *
     * @param desc the URL to set.
     */
	public void setUrl(String desc) {
		this.url = desc;
	}
	

	
	/**
     * Gets the card associated with the knowledge.
     *
     * @return the card.
     */
	public String getCard() {
		return card;
	}

	/**
     * Sets the card associated with the knowledge.
     *
     * @param card the card to set.
     */
	public void setCard(String card) {
		this.card = card;
	}
	
	/**
     * Gets the description associated with the knowledge.
     *
     * @return the description.
     */
	public String getDesc() {
		return desc;
	}

	/**
     * Sets the description associated with the knowledge.
     *
     * @param desc the description to set.
     */
	public void setDesc(String desc) {
		this.desc = desc;
	}

	/**
     * Gets the parameters associated with the knowledge.
     *
     * @return the parameters.
     */
	public String getParams() {
		return params;
	}

	/**
     * Sets the parameters associated with the knowledge.
     *
     * @param params the parameters to set.
     */
	public void setParams(String params) {
		this.params = params;
	}
	
	/**
     * Gets the query type associated with the knowledge.
     *
     * @return the query type.
     */
	public QueryType getType() {
		return type;
	}

	/**
     * Sets the query type associated with the knowledge.
     *
     * @param val the query type to set.
     */
	public void setType(QueryType val) {
		this.type = val;
	}
	
	/**
     * Gets the name associated with the knowledge.
     *
     * @return the name.
     */
	public String getName() {
		return name;
	}

	/**
     * Sets the name associated with the knowledge.
     *
     * @param val the name to set.
     */
	public void setName(String val) {
		this.name = val;
	}

	/**
     * Gets the data associated with the knowledge.
     *
     * @return the data.
     */
	public String getData() {
		return data;
	}

	/**
     * Sets the data associated with the knowledge.
     *
     * @param val the data to set.
     */
	public void setData(String val) {
		this.data = val;
	}

	public String getTools() {
		return tools;
	}
	
	public void setTools(String tools) {
		this.tools = tools;
	}
	public String getSystemPrompt() {
        return systemPrompt;
    }

    public void setSystemPrompt(String systemPrompt) {
        this.systemPrompt = systemPrompt;
    }
}
