package org.symphonykernel.core;

import java.util.Collection;
import java.util.Date;

import org.symphonykernel.Indexer;

public interface IIndexTraker {
	void start();
	<T>void executeIndexing(Indexer<T> indexer);
	<T>Indexer<T> getIndexer(String indexName);
	Collection<Indexer<?>> getAllIndexers();
	
	<T>void registerIndexer(Indexer<T> indexer);
	
	Date getDocumentIndexDate(String indexName);

	void saveDocumentIndexDate(String indexName, Date indexDate);

}