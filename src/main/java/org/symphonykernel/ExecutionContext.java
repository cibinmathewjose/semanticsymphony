
package org.symphonykernel;

import com.fasterxml.jackson.databind.JsonNode;

public class ExecutionContext {
	private String userToken;	
	JsonNode variables;
	KnowledgeDto kb;
	String name; 
	String usersQuery; 
	boolean convert;
	public void setName(String name) {
		this.name = name;
	}
	
	public String getName() {
		return name==null&&kb!=null?kb.getName():name;
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
	
	public void setUserToken(String userToken) {
		this.userToken = userToken;
	}
	
	public String getUserToken() {
		return userToken;
	}
	public void setVariables(JsonNode variables) {
		this.variables = variables;
	}
	
	public JsonNode getVariables() {
		return variables;
	}
	public void setKnowledge(KnowledgeDto kb) {
		this.kb = kb;
	}
	
	public KnowledgeDto getKnowledge() {
		return kb;
	}
}