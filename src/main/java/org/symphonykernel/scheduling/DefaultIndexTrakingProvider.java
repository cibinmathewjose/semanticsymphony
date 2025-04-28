package org.symphonykernel.scheduling;

import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

import org.springframework.stereotype.Component;
import org.symphonykernel.Index;
import org.symphonykernel.Indexer;
import org.symphonykernel.KnowledgeDescription;
import org.symphonykernel.ai.Agent;
import org.symphonykernel.ai.KnowledgeVector;
import org.symphonykernel.core.IIndexTraker;
import org.symphonykernel.core.IknowledgeBase;

@Component
@Configuration
@EnableScheduling
public class DefaultIndexTrakingProvider implements IIndexTraker,SchedulingConfigurer {
	
	private static final Logger logger = LoggerFactory.getLogger(DefaultIndexTrakingProvider.class);
	private final static String indexTrakerIndex = "cs_auto_indexes";
	private final static String csKnowledgeIndex = "cs_copilot_knowledge";
	IknowledgeBase knowledgeBaserepo;	
	KnowledgeVector knowledgeVector;
	ScheduledTaskRegistrar taskRegistrar ;

	private static final ConcurrentMap<String, Indexer<?>> indexerMap = new ConcurrentHashMap<>();

	@Autowired
	public DefaultIndexTrakingProvider(IknowledgeBase knowledgeBaserepo,KnowledgeVector knowledgeVector) {
		this.knowledgeBaserepo=knowledgeBaserepo;
		this.knowledgeVector=knowledgeVector;
	}

	public void start()
	{
		knowledgeVector.createIndex(indexTrakerIndex, Index.class);
		Indexer<KnowledgeDescription> indexer =new Indexer<KnowledgeDescription>(csKnowledgeIndex, KnowledgeDescription.class, this.knowledgeBaserepo);
		indexer.setCronExpression("0 * * * * ?");
		registerIndexer(indexer);		
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
    	this.taskRegistrar=taskRegistrar;
	}
    @Scheduled(cron = "0 0 * * * ?") // Runs every hour at the 0th minute
    public void runEveryhour() {
    	logger.info("Scheduled run" );
    	Collection<Indexer<?>> indexers = getAllIndexers();
        for (Indexer<?> idx : indexers) {
        	executeIndexing(idx);
        }
    }

	private void registerSchedule(Indexer<?> idx) {
		String cronExpression = idx.getCronExpression(); // Fetch the cron expression from your task service
		if (cronExpression != null&&this.taskRegistrar!=null) {
			logger.info("indexer for: " + idx.getIndexName()+" scheduled for "+idx.getCronExpression());
			this.taskRegistrar.addCronTask(() -> {
		        // Your task logic here
		        executeIndexing(idx); // The task to execute (your service method)
		    }, cronExpression);
		} else {
		    // Handle the case where the cron expression is not found (e.g., log a warning)
			logger.error("Cron expression not found for task: " + idx);
		}
	}

	@Override
	public <T> void executeIndexing(Indexer<T> indexer) {
		try
		{
			logger.info("Start indexing for: " + indexer.getIndexName());
			String indexName =indexer.getIndexName();
			Index idx= knowledgeVector.getDocument(indexTrakerIndex, indexName, Index.class);
			if(idx!=null &&indexName!=null&&indexName.equals(idx.indexName) && !"running".equals(idx.indexingStatus))
			{
				Date lastrun=idx.lastRefreshDate;
		    	Calendar calendar = Calendar.getInstance();
				idx.lastRefreshDate =calendar.getTime();
				idx.indexingStatus = "running";
				knowledgeVector.indexDocument(indexTrakerIndex, idx);
				knowledgeVector.createIndex(indexer.getIndexName(),indexer.getModelClass(),indexer.getProvider().getDocuments(lastrun));
				idx.indexingStatus = "ready";
				knowledgeVector.indexDocument(indexTrakerIndex, idx);
				logger.info("indexing completed for: " + indexer.getIndexName());
			}
			
		}
		catch(Exception ex)
		{
			logger.error("Scheduled indexing Failed",ex);
		}
	}
	
	@Override
	public Date getDocumentIndexDate(String indexName) {
		Index idx = knowledgeVector.getDocument(indexTrakerIndex, indexName, Index.class);
		if(idx!=null)
			return idx.lastRefreshDate;
		else
			return null;
	}

	@Override
	public void saveDocumentIndexDate(String indexName, Date indexDate){
		Index idx= knowledgeVector.getDocument(indexTrakerIndex, indexName, Index.class);
		if(idx!=null &&indexName!=null&&indexName.equals(idx.indexName))
		{
			idx.lastRefreshDate =indexDate;
			knowledgeVector.indexDocument(indexName, idx);
		}
		//else
		//	throw new Exception("index not found : "+indexName);		
		
	}

	@SuppressWarnings("unchecked")
	@Override
	public <T> Indexer<T> getIndexer(String indexName) {
	
		
		var obj =indexerMap.get(indexName);
		if(obj!=null)
		{				
			return (Indexer<T>) obj;
		}
		return null;
	}

	@Override
	public <T> void registerIndexer(Indexer<T> indexer) {
		if (indexer != null && indexer.getIndexName() != null) {
			indexerMap.put(indexer.getIndexName(), indexer);		
			knowledgeVector.createIndex(indexer.getIndexName(), indexer.getModelClass());
			Index idx= knowledgeVector.getDocument(indexTrakerIndex, indexer.getIndexName(), Index.class);
			if(idx==null)
			{
				idx=new Index();
				idx.indexName=indexer.getIndexName();
				idx.indexingStatus = "pending";
				knowledgeVector.indexDocument(indexTrakerIndex, idx);
			}
		}
	}

	public Collection<Indexer<?>> getAllIndexers() {
		return indexerMap.values();
	}

}