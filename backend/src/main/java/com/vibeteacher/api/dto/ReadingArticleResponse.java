package com.vibeteacher.api.dto;

public class ReadingArticleResponse {
    private String title;
    private String text;
    private String author;
    private String source;
    private String url;
    private String license;
    private String licenseUrl;
    private String level;
    private double estimatedGrade;
    private boolean levelMatched;
    private boolean excerpt;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getLicense() {
        return license;
    }

    public void setLicense(String license) {
        this.license = license;
    }

    public String getLicenseUrl() {
        return licenseUrl;
    }

    public void setLicenseUrl(String licenseUrl) {
        this.licenseUrl = licenseUrl;
    }

    public String getLevel() {
        return level;
    }

    public void setLevel(String level) {
        this.level = level;
    }

    public double getEstimatedGrade() {
        return estimatedGrade;
    }

    public void setEstimatedGrade(double estimatedGrade) {
        this.estimatedGrade = estimatedGrade;
    }

    public boolean isLevelMatched() {
        return levelMatched;
    }

    public void setLevelMatched(boolean levelMatched) {
        this.levelMatched = levelMatched;
    }

    public boolean isExcerpt() {
        return excerpt;
    }

    public void setExcerpt(boolean excerpt) {
        this.excerpt = excerpt;
    }
}
