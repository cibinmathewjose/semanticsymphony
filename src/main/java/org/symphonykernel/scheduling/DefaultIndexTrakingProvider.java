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
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

import org.springframework.stereotype.Component;
import org.symphonykernel.Index;
import org.symphonykernel.Indexer;
import org.symphonykernel.KnowledgeDescription;
import org.symphonykernel.ai.Agent;
import org.symphonykernel.ai.VectorSearchHelper;
import org.symphonykernel.core.IIndexTraker;
import org.symphonykernel.core.IknowledgeBase;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.scheduling.support.CronTrigger;

@Component
@Configuration
@EnableScheduling
/**
 * Provides default implementation for index tracking and scheduling.
 * 
 * <p>This class implements {@link IIndexTraker} and {@link SchedulingConfigurer}.
 * 
 * @version 1.0
 * @since 1.0
 */
/**
 * Default implementation for index tracking and scheduling.
 * Implements {@link IIndexTraker} and {@link SchedulingConfigurer}.
 */
public class DefaultIndexTrakingProvider implements IIndexTraker, SchedulingConfigurer {
	
	private static final Logger logger = LoggerFactory.getLogger(DefaultIndexTrakingProvider.class);
	private final static String indexTrakerIndex = "cs_auto_indexes";
    /**
     * Knowledge index constant used for tracking.
     */
    public final static String csKnowledgeIndex = "cs_copilot_knowledge";
	IknowledgeBase knowledgeBaserepo;	
	VectorSearchHelper knowledgeVector;
	ScheduledTaskRegistrar taskRegistrar ;

    /**
     * Validates a cron expression.
     *
     * @param cronExpression the cron expression to validate
     * @return true if the cron expression is valid, false otherwise
     */
	private boolean isValidCronExpression(String cronExpression) {
		 try {
		        CronExpression.parse(cronExpression);
		        return true;
		    } catch (IllegalArgumentException ex) {
		        logger.error("Invalid cron expression: " + cronExpression, ex);
		        return false;
		    }
	}
	@Autowired
	private ThreadPoolTaskScheduler taskScheduler;

	private static final ConcurrentMap<String, Indexer<?>> indexerMap = new ConcurrentHashMap<>();

	/**
     * Constructs a DefaultIndexTrakingProvider with the specified knowledge base and vector search helper.
     * 
     * @param knowledgeBaserepo the knowledge base repository
     * @param knowledgeVector the vector search helper
     */
	@Autowired
	public DefaultIndexTrakingProvider(IknowledgeBase knowledgeBaserepo,VectorSearchHelper knowledgeVector) {
		this.knowledgeBaserepo=knowledgeBaserepo;
		this.knowledgeVector=knowledgeVector;
	}

	public void start()
	{
		knowledgeVector.createIndex(indexTrakerIndex, Index.class);
		Indexer<KnowledgeDescription> indexer =new Indexer<KnowledgeDescription>(csKnowledgeIndex, KnowledgeDescription.class, this.knowledgeBaserepo);
		
		registerIndexer(indexer);		
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
    	this.taskRegistrar=taskRegistrar;
		taskRegistrar.setTaskScheduler(taskScheduler);
	}
   /* @Scheduled(cron = "0 0 * * * ?") // Runs every hour at the 0th minute
    public void runEveryhour() {
    	logger.info("Scheduled run" );
    	Collection<Indexer<?>> indexers = getAllIndexers();
        for (Indexer<?> idx : indexers) {
        	executeIndexing(idx);
        }
    }*/
	private void registerSchedule(Indexer<?> idx) {
    String cronExpression = idx.getCronExpression();
    if (cronExpression != null && isValidCronExpression(cronExpression)) {        
        if(idx.getFuture()!=null)
        {
        	if(idx.getFuture().cancel(false))
        	{
        		logger.info("scheduler for: " + idx.getIndexName() + " canceled");
        	}
        }
        idx.setFuture(taskScheduler.schedule(() -> executeIndexing(idx), new CronTrigger(cronExpression)));
        logger.info("Indexer for: " + idx.getIndexName() + " scheduled for " + idx.getCronExpression());
    } else {
        logger.error("Invalid or missing cron expression for task: " + idx);
    }
	}

	@Override
	public <T> void executeIndexing(Indexer<T> indexer) {
		try
		{
			logger.info("Start indexing for: " + indexer.getIndexName());
			String indexName =indexer.getIndexName();
			Index idx= knowledgeVector.getDocument(indexTrakerIndex, indexName, Index.class);
			if(idx!=null &&!"running".equals(idx.indexingStatus))
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
			};
			registerSchedule(indexer);
			executeIndexing(indexer);
		}
	}

	public Collection<Indexer<?>> getAllIndexers() {
		return indexerMap.values();
	}

}