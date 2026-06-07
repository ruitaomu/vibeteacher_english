package com.vibeteacher.api.dto;

public class SentenceStructureResponse {
    private String original;
    private String structureExplanation;
    private String rawOutput;

    public String getOriginal() {
        return original;
    }

    public void setOriginal(String original) {
        this.original = original;
    }

    public String getStructureExplanation() {
        return structureExplanation;
    }

    public void setStructureExplanation(String structureExplanation) {
        this.structureExplanation = structureExplanation;
    }

    public String getRawOutput() {
        return rawOutput;
    }

    public void setRawOutput(String rawOutput) {
        this.rawOutput = rawOutput;
    }
}
