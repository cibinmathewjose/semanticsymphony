package org.symphonykernel;

import java.util.concurrent.ScheduledFuture;

import org.symphonykernel.core.IDelta;

/**
 * Represents an indexer that manages indexing operations for a specific model class.
 * Includes scheduling and provider information for indexing tasks.
 *
 * @param <T> the type of the model class being indexed
 */
public class Indexer<T> {
	String indexName;
	Class<T> modelClass;
	IDelta<T> provider;
	ScheduledFuture<?> future;
	String cronExpression;

    /**
     * Constructs an Indexer instance with the specified index name, model class, and provider.
     *
     * @param indexName the name of the index
     * @param modelClass the class of the model being indexed
     * @param provider the provider for delta operations
     */
	public Indexer(String indexName, Class<T> modelClass, IDelta<T> provider) {
		this.indexName = indexName;
		this.modelClass = modelClass;
		this.provider = provider;
		this.cronExpression="0 0 * * * ?";//Default to every hour at the beginning of the hour (0 minutes, 0 seconds).
	}

    /**
     * Gets the name of the index.
     *
     * @return the index name
     */
	public String getIndexName() {
		return indexName;
	}

    /**
     * Gets the cron expression for scheduling indexing tasks.
     *
     * @return the cron expression
     */
	public String getCronExpression() {
		return cronExpression;
	}

    /**
     * Sets the cron expression for scheduling indexing tasks.
     *
     * @param cronExpression the cron expression to set
     */
	public void setCronExpression(String cronExpression) {
		this.cronExpression=cronExpression;
	}

    /**
     * Gets the model class being indexed.
     *
     * @return the model class
     */
	public Class<T> getModelClass() {
		return modelClass;
	}

    /**
     * Gets the provider for delta operations.
     *
     * @return the delta provider
     */
	public IDelta<T> getProvider() {
		return provider;
	}
	
    /**
     * Gets the scheduled future for the indexing task.
     *
     * @return the scheduled future
     */
	public ScheduledFuture<?> getFuture() {
		return future;
	}

    /**
     * Sets the scheduled future for the indexing task.
     *
     * @param future the scheduled future to set
     */
	public void setFuture(ScheduledFuture<?> future) {
		 this.future=future;
	}
}
