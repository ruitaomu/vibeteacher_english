package com.vibeteacher.api.dto;

import java.util.List;

public class ReadingAnalysisResponse {
    private String overallTranslation;
    private List<SentenceAnalysis> sentences;
    private String rawOutput;

    public String getOverallTranslation() {
        return overallTranslation;
    }

    public void setOverallTranslation(String overallTranslation) {
        this.overallTranslation = overallTranslation;
    }

    public List<SentenceAnalysis> getSentences() {
        return sentences;
    }

    public void setSentences(List<SentenceAnalysis> sentences) {
        this.sentences = sentences;
    }

    public String getRawOutput() {
        return rawOutput;
    }

    public void setRawOutput(String rawOutput) {
        this.rawOutput = rawOutput;
    }

    public static class SentenceAnalysis {
        private String original;
        private String translation;
        private List<LearningPoint> keyWords;
        private List<LearningPoint> keyPhrases;
        private List<LearningPoint> typicalPatterns;

        public String getOriginal() {
            return original;
        }

        public void setOriginal(String original) {
            this.original = original;
        }

        public String getTranslation() {
            return translation;
        }

        public void setTranslation(String translation) {
            this.translation = translation;
        }

        public List<LearningPoint> getKeyWords() {
            return keyWords;
        }

        public void setKeyWords(List<LearningPoint> keyWords) {
            this.keyWords = keyWords;
        }

        public List<LearningPoint> getKeyPhrases() {
            return keyPhrases;
        }

        public void setKeyPhrases(List<LearningPoint> keyPhrases) {
            this.keyPhrases = keyPhrases;
        }

        public List<LearningPoint> getTypicalPatterns() {
            return typicalPatterns;
        }

        public void setTypicalPatterns(List<LearningPoint> typicalPatterns) {
            this.typicalPatterns = typicalPatterns;
        }
    }

    public static class LearningPoint {
        private String term;
        private String explanation;
        private String exampleSentence;

        public String getTerm() {
            return term;
        }

        public void setTerm(String term) {
            this.term = term;
        }

        public String getExplanation() {
            return explanation;
        }

        public void setExplanation(String explanation) {
            this.explanation = explanation;
        }

        public String getExampleSentence() {
            return exampleSentence;
        }

        public void setExampleSentence(String exampleSentence) {
            this.exampleSentence = exampleSentence;
        }
    }
}
