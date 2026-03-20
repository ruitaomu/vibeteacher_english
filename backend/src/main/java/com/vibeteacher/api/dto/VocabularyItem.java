package com.vibeteacher.api.dto;

public class VocabularyItem {
    private String word;
    private String translation;
    private String exampleSentence;

    public VocabularyItem() {}

    public VocabularyItem(String word, String translation, String exampleSentence) {
        this.word = word;
        this.translation = translation;
        this.exampleSentence = exampleSentence;
    }

    public String getWord() {
        return word;
    }

    public void setWord(String word) {
        this.word = word;
    }

    public String getTranslation() {
        return translation;
    }

    public void setTranslation(String translation) {
        this.translation = translation;
    }

    public String getExampleSentence() {
        return exampleSentence;
    }

    public void setExampleSentence(String exampleSentence) {
        this.exampleSentence = exampleSentence;
    }
}
