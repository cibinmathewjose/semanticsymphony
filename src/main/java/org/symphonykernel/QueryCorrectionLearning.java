package org.symphonykernel;

import java.util.Date;

/**
 * Represents a learned correction from a failed SQL query attempt.
 * When a generated query fails and is subsequently corrected by the LLM,
 * the original error and correction are persisted for future reference,
 * improving accuracy of subsequent query generation.
 */
public class QueryCorrectionLearning {

    private String id;
    private String dbName;
    private String originalQuestion;
    private String failedSql;
    private String errorMessage;
    private String correctedSql;
    private String tablesTouched;
    private Date createdAt;

    public QueryCorrectionLearning() {
    }

    public QueryCorrectionLearning(String dbName, String originalQuestion,
                                    String failedSql, String errorMessage,
                                    String correctedSql, String tablesTouched) {
        this.dbName = dbName;
        this.originalQuestion = originalQuestion;
        this.failedSql = failedSql;
        this.errorMessage = errorMessage;
        this.correctedSql = correctedSql;
        this.tablesTouched = tablesTouched;
        this.createdAt = new Date();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getDbName() {
        return dbName;
    }

    public void setDbName(String dbName) {
        this.dbName = dbName;
    }

    public String getOriginalQuestion() {
        return originalQuestion;
    }

    public void setOriginalQuestion(String originalQuestion) {
        this.originalQuestion = originalQuestion;
    }

    public String getFailedSql() {
        return failedSql;
    }

    public void setFailedSql(String failedSql) {
        this.failedSql = failedSql;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getCorrectedSql() {
        return correctedSql;
    }

    public void setCorrectedSql(String correctedSql) {
        this.correctedSql = correctedSql;
    }

    public String getTablesTouched() {
        return tablesTouched;
    }

    public void setTablesTouched(String tablesTouched) {
        this.tablesTouched = tablesTouched;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }
}
