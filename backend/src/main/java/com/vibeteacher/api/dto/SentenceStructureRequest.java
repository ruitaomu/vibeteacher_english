package com.vibeteacher.api.dto;

public class SentenceStructureRequest {
    private String model;
    private String sentence;

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getSentence() {
        return sentence;
    }

    public void setSentence(String sentence) {
        this.sentence = sentence;
    }
}
