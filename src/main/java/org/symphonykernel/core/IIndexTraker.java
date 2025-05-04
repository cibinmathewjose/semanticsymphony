package org.symphonykernel.core;

import java.util.Collection;
import java.util.Date;

import org.symphonykernel.Indexer;

/**
 * Interface for tracking and managing indexers.
 * 
 * Provides methods to execute, register, and retrieve indexers, as well as manage document index dates.
 */
public interface IIndexTraker {
	/**
	 * Starts the indexing process.
	 */
	void start();
	
	/**
	 * Executes the indexing process for a given indexer.
	 * 
	 * @param <T> the type of the indexer
	 * @param indexer the indexer to execute
	 */
	<T>void executeIndexing(Indexer<T> indexer);
	
	/**
	 * Retrieves the indexer for a specific index name.
	 * 
	 * @param <T> the type of the indexer
	 * @param indexName the name of the index
	 * @return the indexer associated with the index name
	 */
	<T>Indexer<T> getIndexer(String indexName);
	
	/**
	 * Retrieves all registered indexers.
	 * 
	 * @return a collection of all indexers
	 */
	Collection<Indexer<?>> getAllIndexers();
	
	/**
	 * Registers a new indexer.
	 * 
	 * @param <T> the type of the indexer
	 * @param indexer the indexer to register
	 */
	<T>void registerIndexer(Indexer<T> indexer);
	
	/**
	 * Retrieves the document index date for a specific index.
	 * 
	 * @param indexName the name of the index
	 * @return the date of the document index
	 */
	Date getDocumentIndexDate(String indexName);

	/**
	 * Saves the document index date for a specific index.
	 * 
	 * @param indexName the name of the index
	 * @param indexDate the date to save
	 */
	void saveDocumentIndexDate(String indexName, Date indexDate);

}