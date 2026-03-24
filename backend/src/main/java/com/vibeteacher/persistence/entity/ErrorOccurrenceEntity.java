package com.vibeteacher.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

@Entity
@Table(name = "error_occurrence")
public class ErrorOccurrenceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "revision_id", nullable = false)
    private Long revisionId;

    @Column(name = "user_id", nullable = false, length = 128)
    private String userId;

    @Column(name = "error_type", length = 128)
    private String errorType;

    @Lob
    @Column(name = "original_text", columnDefinition = "LONGTEXT")
    private String originalText;

    @Lob
    @Column(name = "suggested_text", columnDefinition = "LONGTEXT")
    private String suggestedText;

    @Lob
    @Column(name = "reason", columnDefinition = "LONGTEXT")
    private String reason;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getRevisionId() {
        return revisionId;
    }

    public void setRevisionId(Long revisionId) {
        this.revisionId = revisionId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getErrorType() {
        return errorType;
    }

    public void setErrorType(String errorType) {
        this.errorType = errorType;
    }

    public String getOriginalText() {
        return originalText;
    }

    public void setOriginalText(String originalText) {
        this.originalText = originalText;
    }

    public String getSuggestedText() {
        return suggestedText;
    }

    public void setSuggestedText(String suggestedText) {
        this.suggestedText = suggestedText;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
