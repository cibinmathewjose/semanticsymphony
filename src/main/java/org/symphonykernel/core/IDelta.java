package org.symphonykernel.core;

import java.util.Date;
import java.util.List;

public interface IDelta<T> {
	List<T> getDocuments(Date from);
}
