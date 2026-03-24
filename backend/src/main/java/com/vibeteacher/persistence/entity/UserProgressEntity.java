package com.vibeteacher.persistence.entity;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "user_progress")
public class UserProgressEntity {

    @Id
    @Column(name = "user_id", nullable = false, length = 128)
    private String userId;

    @Column(name = "total_submissions", nullable = false)
    private int totalSubmissions;

    @Column(name = "current_streak", nullable = false)
    private int currentStreak;

    @Column(name = "longest_streak", nullable = false)
    private int longestStreak;

    @Column(name = "suggested_vocabulary_usage_count", nullable = false)
    private int suggestedVocabularyUsageCount;

    @Column(name = "last_submission_date")
    private LocalDate lastSubmissionDate;

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public int getTotalSubmissions() {
        return totalSubmissions;
    }

    public void setTotalSubmissions(int totalSubmissions) {
        this.totalSubmissions = totalSubmissions;
    }

    public int getCurrentStreak() {
        return currentStreak;
    }

    public void setCurrentStreak(int currentStreak) {
        this.currentStreak = currentStreak;
    }

    public int getLongestStreak() {
        return longestStreak;
    }

    public void setLongestStreak(int longestStreak) {
        this.longestStreak = longestStreak;
    }

    public int getSuggestedVocabularyUsageCount() {
        return suggestedVocabularyUsageCount;
    }

    public void setSuggestedVocabularyUsageCount(int suggestedVocabularyUsageCount) {
        this.suggestedVocabularyUsageCount = suggestedVocabularyUsageCount;
    }

    public LocalDate getLastSubmissionDate() {
        return lastSubmissionDate;
    }

    public void setLastSubmissionDate(LocalDate lastSubmissionDate) {
        this.lastSubmissionDate = lastSubmissionDate;
    }
}
