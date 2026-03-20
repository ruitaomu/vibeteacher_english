package com.vibeteacher.model;

import com.vibeteacher.api.dto.VocabularyItem;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class UserProgress {
    private final String userId;
    private int totalSubmissions;
    private LocalDate lastSubmissionDate;
    private int currentStreak;
    private int longestStreak;
    private final Set<String> earnedAchievements;
    private final List<VocabularyItem> vocabulary;

    public UserProgress(String userId) {
        this.userId = userId;
        this.totalSubmissions = 0;
        this.currentStreak = 0;
        this.longestStreak = 0;
        this.earnedAchievements = new HashSet<>();
        this.vocabulary = new ArrayList<>();
    }

    public String getUserId() {
        return userId;
    }

    public int getTotalSubmissions() {
        return totalSubmissions;
    }

    public void setTotalSubmissions(int totalSubmissions) {
        this.totalSubmissions = totalSubmissions;
    }

    public LocalDate getLastSubmissionDate() {
        return lastSubmissionDate;
    }

    public void setLastSubmissionDate(LocalDate lastSubmissionDate) {
        this.lastSubmissionDate = lastSubmissionDate;
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

    public Set<String> getEarnedAchievements() {
        return earnedAchievements;
    }

    public List<VocabularyItem> getVocabulary() {
        return vocabulary;
    }

    public static class Stats {
        private final int totalSubmissions;
        private final int currentStreak;
        private final int longestStreak;
        private final int vocabularyCount;
        private final int achievementsCount;
        private final LocalDate lastSubmissionDate;

        public Stats(int totalSubmissions, int currentStreak, int longestStreak,
                     int vocabularyCount, int achievementsCount, LocalDate lastSubmissionDate) {
            this.totalSubmissions = totalSubmissions;
            this.currentStreak = currentStreak;
            this.longestStreak = longestStreak;
            this.vocabularyCount = vocabularyCount;
            this.achievementsCount = achievementsCount;
            this.lastSubmissionDate = lastSubmissionDate;
        }

        public int getTotalSubmissions() { return totalSubmissions; }
        public int getCurrentStreak() { return currentStreak; }
        public int getLongestStreak() { return longestStreak; }
        public int getVocabularyCount() { return vocabularyCount; }
        public int getAchievementsCount() { return achievementsCount; }
        public LocalDate getLastSubmissionDate() { return lastSubmissionDate; }
    }
}
