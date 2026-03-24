package com.vibeteacher.api.dto;

import java.util.List;

import com.vibeteacher.model.Achievement;

public class RevisionResponse {
    private String correctedText;
    private String summary;
    private List<Issue> issues;
    private List<VocabularyItem> vocabularySuggestions;
    private List<Achievement> newAchievements;
    private String rawOutput;

    public String getCorrectedText() {
        return correctedText;
    }

    public void setCorrectedText(String correctedText) {
        this.correctedText = correctedText;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public List<Issue> getIssues() {
        return issues;
    }

    public void setIssues(List<Issue> issues) {
        this.issues = issues;
    }

    public List<VocabularyItem> getVocabularySuggestions() {
        return vocabularySuggestions;
    }

    public void setVocabularySuggestions(List<VocabularyItem> vocabularySuggestions) {
        this.vocabularySuggestions = vocabularySuggestions;
    }

    public List<Achievement> getNewAchievements() {
        return newAchievements;
    }

    public void setNewAchievements(List<Achievement> newAchievements) {
        this.newAchievements = newAchievements;
    }

    public String getRawOutput() {
        return rawOutput;
    }

    public void setRawOutput(String rawOutput) {
        this.rawOutput = rawOutput;
    }

    public static class Issue {
        private String type;
        private String original;
        private String suggested;
        private String reason;

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getOriginal() {
            return original;
        }

        public void setOriginal(String original) {
            this.original = original;
        }

        public String getSuggested() {
            return suggested;
        }

        public void setSuggested(String suggested) {
            this.suggested = suggested;
        }

        public String getReason() {
            return reason;
        }

        public void setReason(String reason) {
            this.reason = reason;
        }
    }
}
