/**
 * Event is used to hold the data regarding events received from IBEX.
 * Author: Preeti Ankam
 * Date: November 30, 2024
 */

package com.eh.digitalpathology.email.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class IbexEvent {
    private String id;

    @JsonProperty("event_type")
    private String eventType;

    private String priority;

    @JsonProperty("subject_id")
    private String subjectId;

    @JsonProperty("subject_type")
    private String subjectType;

    @JsonProperty("subject_url")
    private String subjectUrl;

    private String description;

    @JsonProperty("created_at")
    private String createdAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getPriority() {
        return priority;
    }

    public void setPriority(String priority) {
        this.priority = priority;
    }

    public String getSubjectId() {
        return subjectId;
    }

    public void setSubjectId(String subjectId) {
        this.subjectId = subjectId;
    }

    public String getSubjectType() {
        return subjectType;
    }

    public void setSubjectType(String subjectType) {
        this.subjectType = subjectType;
    }

    public String getSubjectUrl() {
        return subjectUrl;
    }

    public void setSubjectUrl(String subjectUrl) {
        this.subjectUrl = subjectUrl;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }
}
