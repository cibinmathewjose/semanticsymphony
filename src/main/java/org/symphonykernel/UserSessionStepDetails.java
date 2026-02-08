package org.symphonykernel;

import java.util.Date;

/**
 * Represents a user session with details such as creation date, modification date, and status.
 */
public class UserSessionStepDetails {

    private String requestId;


    private String stepName;

    private String data;
    
    private Date createDt;
    /**
     * Gets the request ID.
     *
     * @return the request ID.
     */
    public String getRequestId() {
        return requestId;
    }

    /**
     * Sets the request ID.
     *
     * @param requestId the request ID to set.
     */
    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    /**
     * Gets the step name.
     *
     * @return the step name.
     */
    public String getStepName() {
        return stepName;
    }

    /**
     * Sets the step name.
     *
     * @param stepName the step name to set.
     */
    public void setStepName(String stepName) {
        this.stepName = stepName;
    }

    /**
     * Gets the data.
     *
     * @return the data.
     */
    public String getData() {
        return data;
    }

    /**
     * Sets the data.
     *
     * @param data the data to set.
     */
    public void setData(String data) {
        this.data = data;
    }

    public void setCreateDt(Date createDt) {
        this.createDt = createDt;
    }

    public Date getCreateDt() {
        return createDt;
    }
}
