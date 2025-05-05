package org.symphonykernel.core;

import java.util.Date;
import java.util.List;

/**
 * Interface for managing delta operations on documents.
 *
 * @param <T> the type of document
 */
public interface IDelta<T> {

    /**
     * Retrieves a list of documents that have changed since the specified date.
     *
     * @param from the date from which to retrieve changed documents
     * @return a list of changed documents
     */
    List<T> getDocuments(Date from);
}
