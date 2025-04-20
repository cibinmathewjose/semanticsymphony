package org.symphonykernel;

public class KnowledgeDto  {

	private String name;
	
	private QueryType type;
	

	private String data;

	private String params;
		

	private String resolver;


	private String card;
	
	private String url;
	
	public String getUrl() {
		return url;
	}

	public void setUrl(String desc) {
		this.url = desc;
	}


	public String getParams() {
		return params;
	}

	public void setParams(String params) {
		this.params = params;
	}
	
	public String getResolver() {
		return resolver;
	}

	public void setResolver(String resolver) {
		this.resolver = resolver;
	}
	
	public QueryType getType() {
		return type;
	}

	public void setType(QueryType val) {
		this.type = val;
	}

	public String getName() {
		return name;
	}

	public void setName(String val) {
		this.name = val;
	}

	public String getData() {
		return data;
	}

	public void setData(String val) {
		this.data = val;
	}
	public String getCard() {
		return card;
	}

	public void setCard(String card) {
		this.card = card;
	}





}
