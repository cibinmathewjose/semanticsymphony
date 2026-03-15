package org.symphonykernel.core;

import java.util.List;

import org.springframework.stereotype.Component;
import org.symphonykernel.QueryCorrectionLearning;

/**
 * Repository interface for persisting and retrieving SQL query correction learnings.
 * Implementations should store learnings in a database table so they can be used
 * to improve future query generation accuracy.
 *
 * <p>This is an optional dependency — if no implementation is provided,
 * the DatabaseStep will still function but won't benefit from past corrections.
 *
 * <p>Recommended table schema:
 * <pre>{@code
 * CREATE TABLE query_correction_learning (
 *     id              VARCHAR(64)  PRIMARY KEY,
 *     db_name         VARCHAR(128),
 *     original_question TEXT,
 *     failed_sql      TEXT,
 *     error_message   TEXT,
 *     corrected_sql   TEXT,
 *     tables_touched  VARCHAR(1024),
 *     created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
 * );
 * }</pre>
 */
@Component
public interface IQueryCorrectionRepository {

    /**
     * Saves a correction learning to the data store.
     *
     * @param learning the correction learning to persist
     */
    void save(QueryCorrectionLearning learning);

    /**
     * Finds past correction learnings relevant to the given database and tables.
     * Implementations should return learnings where the tables overlap with
     * the provided table list, ordered by most recent first.
     *
     * @param dbName the database name
     * @param tables the list of table/view names involved in the current query
     * @param maxResults the maximum number of learnings to return
     * @return a list of relevant correction learnings
     */
    List<QueryCorrectionLearning> findRelevantLearnings(String dbName, List<String> tables, int maxResults);
}
