package com.vibeteacher.service;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 学习级别与文章难度的对应关系。难度用 Flesch-Kincaid 年级指数估算；
 * 区间针对以英语为外语的学习者做了下调，属于经验值而非精确分级。
 */
public enum ReadingLevel {
    GRADE_7("中学7年级", 0, 5, List.of(ArticleSource.SIMPLE_WIKIPEDIA)),
    GRADE_8("中学8年级", 3.5, 6, List.of(ArticleSource.SIMPLE_WIKIPEDIA)),
    GRADE_9("中学9年级", 4.5, 7, List.of(ArticleSource.SIMPLE_WIKIPEDIA)),
    GRADE_10("中学10年级", 5.5, 8, List.of(ArticleSource.SIMPLE_WIKIPEDIA)),
    GRADE_11("中学11年级", 6.5, 9, List.of(ArticleSource.SIMPLE_WIKIPEDIA, ArticleSource.WIKINEWS)),
    GRADE_12("中学12年级", 7.5, 10, List.of(ArticleSource.SIMPLE_WIKIPEDIA, ArticleSource.WIKINEWS)),
    CET_4("大学英语四级", 9, 12, List.of(ArticleSource.WIKINEWS, ArticleSource.SIMPLE_WIKIPEDIA)),
    CET_6("大学英语六级", 11, 14, List.of(ArticleSource.WIKINEWS, ArticleSource.PLOS)),
    TOEFL("TOEFL写作", 12, 16, List.of(ArticleSource.PLOS, ArticleSource.WIKINEWS)),
    GRE("GRE写作", 14, 30, List.of(ArticleSource.PLOS, ArticleSource.WIKINEWS));

    public enum ArticleSource {
        SIMPLE_WIKIPEDIA, WIKINEWS, PLOS, RSS
    }

    private static final Pattern SENTENCE_END = Pattern.compile("[.!?]+(?=\\s|$)");
    private static final Pattern WORD = Pattern.compile("[A-Za-z]+(?:['’-][A-Za-z]+)*");
    private static final Pattern VOWEL_GROUP = Pattern.compile("[aeiouy]+");

    private final String label;
    private final double minGrade;
    private final double maxGrade;
    private final List<ArticleSource> sources;

    ReadingLevel(String label, double minGrade, double maxGrade, List<ArticleSource> sources) {
        this.label = label;
        this.minGrade = minGrade;
        this.maxGrade = maxGrade;
        this.sources = sources;
    }

    public String label() {
        return label;
    }

    public List<ArticleSource> sources() {
        return sources;
    }

    public static ReadingLevel fromLabel(String label) {
        if (label != null) {
            for (ReadingLevel level : values()) {
                if (level.label.equals(label.trim())) {
                    return level;
                }
            }
        }
        return null;
    }

    public boolean matches(double grade) {
        return grade >= minGrade && grade <= maxGrade;
    }

    /** 与目标区间的距离，区间内为 0。 */
    public double distance(double grade) {
        if (grade < minGrade) {
            return minGrade - grade;
        }
        return grade > maxGrade ? grade - maxGrade : 0;
    }

    public static double estimateGrade(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        int words = 0;
        int syllables = 0;
        Matcher wordMatcher = WORD.matcher(text);
        while (wordMatcher.find()) {
            words++;
            syllables += countSyllables(wordMatcher.group());
        }
        if (words == 0) {
            return 0;
        }
        int sentences = 0;
        Matcher sentenceMatcher = SENTENCE_END.matcher(text);
        while (sentenceMatcher.find()) {
            sentences++;
        }
        sentences = Math.max(sentences, 1);
        double grade = 0.39 * words / sentences + 11.8 * syllables / words - 15.59;
        return Math.max(0, Math.round(grade * 10) / 10.0);
    }

    private static int countSyllables(String word) {
        String lower = word.toLowerCase();
        int count = 0;
        Matcher matcher = VOWEL_GROUP.matcher(lower);
        while (matcher.find()) {
            count++;
        }
        if (count > 1 && lower.endsWith("e") && !lower.endsWith("le") && !lower.endsWith("ee")) {
            count--;
        }
        return Math.max(count, 1);
    }
}
