package com.vibeteacher.api.dto;

public class ReadingAnalysisRequest {
    private String model;
    private String text;

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }
}
