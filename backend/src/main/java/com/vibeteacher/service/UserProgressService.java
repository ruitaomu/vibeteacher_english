package com.vibeteacher.service;

import com.vibeteacher.api.dto.VocabularyItem;
import com.vibeteacher.model.Achievement;
import com.vibeteacher.model.UserProgress;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class UserProgressService {

    private static final List<Achievement> ALL_ACHIEVEMENTS = List.of(
        new Achievement("FIRST_SUBMISSION", "初来乍到", "完成第一次写作提交", "🎉"),
        new Achievement("SUBMISSIONS_7", "坚持学习", "累计完成7次写作提交", "📚"),
        new Achievement("SUBMISSIONS_30", "学习达人", "累计完成30次写作提交", "🏆"),
        new Achievement("STREAK_3", "三天连签", "连续3天提交写作", "🔥"),
        new Achievement("STREAK_7", "周连签", "连续7天提交写作", "🌟"),
        new Achievement("VOCABULARY_10", "词汇积累", "积累10个词汇", "📖"),
        new Achievement("VOCABULARY_50", "词汇达人", "积累50个词汇", "💎")
    );

    private final ConcurrentHashMap<String, UserProgress> userProgressMap = new ConcurrentHashMap<>();

    public UserProgress getOrCreateProgress(String userId) {
        return userProgressMap.computeIfAbsent(userId, UserProgress::new);
    }

    public List<Achievement> recordSubmission(String userId) {
        UserProgress progress = getOrCreateProgress(userId);
        synchronized (progress) {
            progress.setTotalSubmissions(progress.getTotalSubmissions() + 1);

            LocalDate today = LocalDate.now();
            LocalDate lastDate = progress.getLastSubmissionDate();
            if (lastDate == null) {
                progress.setCurrentStreak(1);
            } else if (lastDate.plusDays(1).equals(today)) {
                progress.setCurrentStreak(progress.getCurrentStreak() + 1);
            } else if (!lastDate.equals(today)) {
                progress.setCurrentStreak(1);
            }
            progress.setLastSubmissionDate(today);
            if (progress.getCurrentStreak() > progress.getLongestStreak()) {
                progress.setLongestStreak(progress.getCurrentStreak());
            }

            return checkAndGrantAchievements(progress);
        }
    }

    public List<VocabularyItem> addVocabulary(String userId, List<VocabularyItem> items) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        UserProgress progress = getOrCreateProgress(userId);
        synchronized (progress) {
            for (VocabularyItem item : items) {
                if (item.getWord() != null && !item.getWord().isBlank()) {
                    boolean exists = progress.getVocabulary().stream()
                        .anyMatch(v -> v.getWord().equalsIgnoreCase(item.getWord()));
                    if (!exists) {
                        progress.getVocabulary().add(item);
                    }
                }
            }
            checkAndGrantAchievements(progress);
            return List.copyOf(progress.getVocabulary());
        }
    }

    public List<VocabularyItem> getVocabulary(String userId) {
        UserProgress progress = getOrCreateProgress(userId);
        synchronized (progress) {
            return List.copyOf(progress.getVocabulary());
        }
    }

    public List<Achievement> getAchievements(String userId) {
        UserProgress progress = getOrCreateProgress(userId);
        synchronized (progress) {
            List<Achievement> result = new ArrayList<>();
            for (Achievement template : ALL_ACHIEVEMENTS) {
                Achievement a = new Achievement(template.getId(), template.getName(), template.getDescription(), template.getIcon());
                a.setEarned(progress.getEarnedAchievements().contains(template.getId()));
                result.add(a);
            }
            return result;
        }
    }

    public UserProgress.Stats getStats(String userId) {
        UserProgress progress = getOrCreateProgress(userId);
        synchronized (progress) {
            return new UserProgress.Stats(
                progress.getTotalSubmissions(),
                progress.getCurrentStreak(),
                progress.getLongestStreak(),
                progress.getVocabulary().size(),
                progress.getEarnedAchievements().size(),
                progress.getLastSubmissionDate()
            );
        }
    }

    private List<Achievement> checkAndGrantAchievements(UserProgress progress) {
        List<Achievement> newlyEarned = new ArrayList<>();
        int total = progress.getTotalSubmissions();
        int streak = progress.getCurrentStreak();
        int vocabSize = progress.getVocabulary().size();

        if (total >= 1) grantIfNew(progress, "FIRST_SUBMISSION", newlyEarned);
        if (total >= 7) grantIfNew(progress, "SUBMISSIONS_7", newlyEarned);
        if (total >= 30) grantIfNew(progress, "SUBMISSIONS_30", newlyEarned);
        if (streak >= 3) grantIfNew(progress, "STREAK_3", newlyEarned);
        if (streak >= 7) grantIfNew(progress, "STREAK_7", newlyEarned);
        if (vocabSize >= 10) grantIfNew(progress, "VOCABULARY_10", newlyEarned);
        if (vocabSize >= 50) grantIfNew(progress, "VOCABULARY_50", newlyEarned);

        return newlyEarned;
    }

    private void grantIfNew(UserProgress progress, String achievementId, List<Achievement> newlyEarned) {
        if (progress.getEarnedAchievements().add(achievementId)) {
            ALL_ACHIEVEMENTS.stream()
                .filter(a -> a.getId().equals(achievementId))
                .findFirst()
                .ifPresent(template -> {
                    Achievement earned = new Achievement(template.getId(), template.getName(), template.getDescription(), template.getIcon());
                    earned.setEarned(true);
                    newlyEarned.add(earned);
                });
        }
    }
}
