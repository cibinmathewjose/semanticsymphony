package org.symphonykernel;

import java.util.Date;

/**
 * Represents a user session with details such as creation date, modification date, and status.
 */
public class UserSession {

    private String requestId;

    private String userId;

    private String sessionID;

    private String userInput;

    private String botResponse;

    private String data;

    private Date createDt;

    private Date modifyDt;

    private String status;

    /**
     * Gets the session ID.
     *
     * @return the session ID.
     */
    public String getSessionID() {
        return sessionID;
    }

    /**
     * Sets the session ID.
     *
     * @param sessionID the session ID to set.
     */
    public void setSessionID(String sessionID) {
        this.sessionID = sessionID;
    }

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
     * Gets the user ID.
     *
     * @return the user ID.
     */
    public String getUserId() {
        return userId;
    }

    /**
     * Sets the user ID.
     *
     * @param userId the user ID to set.
     */
    public void setUserId(String userId) {
        this.userId = userId;
    }

    /**
     * Gets the user input.
     *
     * @return the user input.
     */
    public String getUserInput() {
        return userInput;
    }

    /**
     * Sets the user input.
     *
     * @param userInput the user input to set.
     */
    public void setUserInput(String userInput) {
        this.userInput = userInput;
    }

    /**
     * Gets the bot response.
     *
     * @return the bot response.
     */
    public String getBotResponse() {
        return botResponse;
    }

    /**
     * Sets the bot response.
     *
     * @param botResponse the bot response to set.
     */
    public void setBotResponse(String botResponse) {
        this.botResponse = botResponse;
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

    /**
     * Gets the creation date of the session.
     *
     * @return the creation date.
     */
    public Date getCreateDt() {
        return createDt;
    }

    /**
     * Sets the creation date of the session.
     *
     * @param createDt the creation date to set.
     */
    public void setCreateDt(Date createDt) {
        this.createDt = createDt;
    }

    /**
     * Gets the status of the session.
     *
     * @return the session status.
     */
    public String getStatus() {
        return status;
    }

    /**
     * Sets the status of the session.
     *
     * @param status the session status to set.
     */
    public void setStatus(String status) {
        this.status = status;
    }

    /**
     * Gets the modification date of the session.
     *
     * @return the modification date.
     */
    public Date getModifyDt() {
        return modifyDt;
    }

    /**
     * Sets the modification date of the session.
     *
     * @param modifyDt the modification date to set.
     */
    public void setModifyDt(Date modifyDt) {
        this.modifyDt = modifyDt;
    }

}
