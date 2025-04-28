package org.symphonykernel;

import java.util.concurrent.ScheduledFuture;

import org.symphonykernel.core.IDelta;

public class Indexer<T> {
	String indexName;
	Class<T> modelClass;
	IDelta<T> provider;
	ScheduledFuture<?> future;
	String cronExpression;

	public Indexer(String indexName, Class<T> modelClass, IDelta<T> provider) {
		this.indexName = indexName;
		this.modelClass = modelClass;
		this.provider = provider;
		this.cronExpression="0 0 * * * ?";//Default to every hour at the beginning of the hour (0 minutes, 0 seconds).
	}

	public String getIndexName() {
		return indexName;
	}
	public String getCronExpression() {
		return cronExpression;
	}
	public void setCronExpression(String cronExpression) {
		this.cronExpression=cronExpression;
	}

	public Class<T> getModelClass() {
		return modelClass;
	}

	public IDelta<T> getProvider() {
		return provider;
	}
	
	public ScheduledFuture<?> getFuture() {
		return future;
	}
	public void setFuture(ScheduledFuture<?> future) {
		 this.future=future;
	}
}
