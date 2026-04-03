package com.vibeteacher.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.vibeteacher.api.dto.RevisionResponse;
import com.vibeteacher.api.dto.VocabularyItem;
import com.vibeteacher.model.Achievement;
import com.vibeteacher.model.UserProgress;
import com.vibeteacher.persistence.entity.UserAchievementEntity;
import com.vibeteacher.persistence.entity.UserProgressEntity;
import com.vibeteacher.persistence.entity.UserVocabularyEntity;
import com.vibeteacher.persistence.repo.UserAchievementRepository;
import com.vibeteacher.persistence.repo.UserProgressRepository;
import com.vibeteacher.persistence.repo.UserVocabularyRepository;

@Service
public class UserProgressService {

    private static final List<Achievement> ALL_ACHIEVEMENTS = List.of(
        new Achievement("FIRST_SUBMISSION", "初来乍到", "完成第一次写作提交", "🥇"),
        new Achievement("SUBMISSIONS_10", "十次达成", "累计完成10次写作提交", "📝"),
        new Achievement("STREAK_7", "连签7天", "连续7天提交写作", "🔥"),
        new Achievement("STREAK_15", "连签15天", "连续15天提交写作", "🔥"),
        new Achievement("STREAK_30", "连签30天", "连续30天提交写作", "🔥"),
        new Achievement("FIRST_SUGGESTED_VOCAB_USED", "学以致用", "首次使用建议过的词汇", "📘"),
        new Achievement("SUGGESTED_VOCAB_USED_10", "词汇达人", "累计使用10次建议过的词汇", "📗"),
        new Achievement("VOCABULARY_CORRECT_5", "词汇实践者", "累计5次正确使用词汇表生词", "⭐"),
        new Achievement("VOCABULARY_CORRECT_20", "词汇大师", "累计20次正确使用词汇表生词", "👑")
    );

    private final UserProgressRepository userProgressRepository;
    private final UserVocabularyRepository userVocabularyRepository;
    private final UserAchievementRepository userAchievementRepository;

    public UserProgressService(UserProgressRepository userProgressRepository,
                               UserVocabularyRepository userVocabularyRepository,
                               UserAchievementRepository userAchievementRepository) {
        this.userProgressRepository = userProgressRepository;
        this.userVocabularyRepository = userVocabularyRepository;
        this.userAchievementRepository = userAchievementRepository;
    }

    @Transactional
    public List<Achievement> recordSubmission(String userId) {
        UserProgressEntity progress = getOrCreateProgressEntity(userId);
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
        userProgressRepository.save(progress);

        return checkAndGrantAchievements(userId, progress);
    }

    @Transactional
    public List<VocabularyItem> addVocabulary(String userId, List<VocabularyItem> items) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }

        getOrCreateProgressEntity(userId);
        for (VocabularyItem item : items) {
            if (item == null || item.getWord() == null || item.getWord().isBlank()) {
                continue;
            }

            String word = item.getWord().trim();
            boolean exists = userVocabularyRepository.findByUserIdAndWordIgnoreCase(userId, word).isPresent();
            if (exists) {
                continue;
            }

            UserVocabularyEntity entity = new UserVocabularyEntity();
            entity.setUserId(userId);
            entity.setWord(word);
            entity.setMeaning(item.getTranslation());
            entity.setExample(item.getExampleSentence());
            try {
                userVocabularyRepository.save(entity);
            } catch (DataIntegrityViolationException ignored) {
                // Concurrent insert for the same (user_id, word) already succeeded.
            }
        }
        return getVocabulary(userId);
    }

    public List<VocabularyItem> getVocabulary(String userId) {
        getOrCreateProgressEntity(userId);
        return userVocabularyRepository.findByUserId(userId).stream()
            .map(this::toVocabularyItem)
            .collect(Collectors.toList());
    }

    @Transactional
    public List<Achievement> recordSuggestedVocabularyUsage(String userId, int usageCount) {
        if (usageCount <= 0) {
            return List.of();
        }
        UserProgressEntity progress = getOrCreateProgressEntity(userId);
        progress.setSuggestedVocabularyUsageCount(progress.getSuggestedVocabularyUsageCount() + usageCount);
        userProgressRepository.save(progress);
        return checkAndGrantAchievements(userId, progress);
    }

    @Transactional
    public List<Achievement> recordVocabularyUsageFromAI(String userId, List<RevisionResponse.VocabularyUsage> vocabularyUsage) {
        if (userId == null || vocabularyUsage == null || vocabularyUsage.isEmpty()) {
            return List.of();
        }

        // 统计正确使用的词汇数量
        int correctUsageCount = 0;
        for (RevisionResponse.VocabularyUsage usage : vocabularyUsage) {
            if (usage != null && "correct".equalsIgnoreCase(usage.getEvaluation())) {
                correctUsageCount++;
            }
        }

        if (correctUsageCount <= 0) {
            return List.of();
        }

        // 更新用户进度中的正确使用次数
        UserProgressEntity progress = getOrCreateProgressEntity(userId);
        // 使用 suggestedVocabularyUsageCount 字段来存储正确使用次数（复用字段）
        progress.setSuggestedVocabularyUsageCount(progress.getSuggestedVocabularyUsageCount() + correctUsageCount);
        userProgressRepository.save(progress);

        // 检查并授予成就
        return checkAndGrantAchievements(userId, progress);
    }

    public int countSuggestedVocabularyMatches(String userId, String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        int matches = 0;
        for (UserVocabularyEntity item : userVocabularyRepository.findByUserId(userId)) {
            if (item == null || item.getWord() == null || item.getWord().isBlank()) {
                continue;
            }
            String normalized = item.getWord().trim().toLowerCase(Locale.ROOT);
            if (lower.matches(".*\\b" + Pattern.quote(normalized) + "\\b.*")) {
                matches++;
            }
        }
        return matches;
    }

    public List<Achievement> getAchievements(String userId) {
        getOrCreateProgressEntity(userId);
        Set<String> earnedIds = userAchievementRepository.findByUserId(userId).stream()
            .map(UserAchievementEntity::getAchievementId)
            .collect(Collectors.toSet());

        List<Achievement> result = new ArrayList<>();
        for (Achievement template : ALL_ACHIEVEMENTS) {
            Achievement a = new Achievement(template.getId(), template.getName(), template.getDescription(), template.getIcon());
            a.setEarned(earnedIds.contains(template.getId()));
            result.add(a);
        }
        return result;
    }

    public UserProgress.Stats getStats(String userId) {
        UserProgressEntity progress = getOrCreateProgressEntity(userId);
        return new UserProgress.Stats(
            progress.getTotalSubmissions(),
            progress.getCurrentStreak(),
            progress.getLongestStreak(),
            userVocabularyRepository.countByUserId(userId),
            progress.getSuggestedVocabularyUsageCount(),
            userAchievementRepository.countByUserId(userId),
            progress.getLastSubmissionDate()
        );
    }

    @Transactional
    protected List<Achievement> checkAndGrantAchievements(String userId, UserProgressEntity progress) {
        List<Achievement> newlyEarned = new ArrayList<>();
        int total = progress.getTotalSubmissions();
        int streak = progress.getCurrentStreak();
        int suggestedVocabUsage = progress.getSuggestedVocabularyUsageCount();

        Set<String> earnedIds = new HashSet<>(userAchievementRepository.findByUserId(userId).stream()
            .map(UserAchievementEntity::getAchievementId)
            .collect(Collectors.toSet()));

        if (total >= 1) grantIfNew(userId, earnedIds, "FIRST_SUBMISSION", newlyEarned);
        if (total >= 10) grantIfNew(userId, earnedIds, "SUBMISSIONS_10", newlyEarned);
        if (streak >= 7) grantIfNew(userId, earnedIds, "STREAK_7", newlyEarned);
        if (streak >= 15) grantIfNew(userId, earnedIds, "STREAK_15", newlyEarned);
        if (streak >= 30) grantIfNew(userId, earnedIds, "STREAK_30", newlyEarned);
        if (suggestedVocabUsage >= 1) grantIfNew(userId, earnedIds, "FIRST_SUGGESTED_VOCAB_USED", newlyEarned);
        if (suggestedVocabUsage >= 10) grantIfNew(userId, earnedIds, "SUGGESTED_VOCAB_USED_10", newlyEarned);
        if (suggestedVocabUsage >= 5) grantIfNew(userId, earnedIds, "VOCABULARY_CORRECT_5", newlyEarned);
        if (suggestedVocabUsage >= 20) grantIfNew(userId, earnedIds, "VOCABULARY_CORRECT_20", newlyEarned);

        return newlyEarned;
    }

    private void grantIfNew(String userId,
                            Set<String> earnedIds,
                            String achievementId,
                            List<Achievement> newlyEarned) {
        if (earnedIds.contains(achievementId)) {
            return;
        }

        UserAchievementEntity entity = new UserAchievementEntity();
        entity.setUserId(userId);
        entity.setAchievementId(achievementId);
        try {
            userAchievementRepository.save(entity);
        } catch (DataIntegrityViolationException ignored) {
            // Concurrent insert for the same (user_id, achievement_id) already succeeded.
            earnedIds.add(achievementId);
            return;
        }
        earnedIds.add(achievementId);

        ALL_ACHIEVEMENTS.stream()
            .filter(a -> a.getId().equals(achievementId))
            .findFirst()
            .ifPresent(template -> {
                Achievement earned = new Achievement(template.getId(), template.getName(), template.getDescription(), template.getIcon());
                earned.setEarned(true);
                newlyEarned.add(earned);
            });
    }

    private UserProgressEntity getOrCreateProgressEntity(String userId) {
        UserProgressEntity existing = userProgressRepository.findById(userId).orElse(null);
        if (existing != null) {
            return existing;
        }

        UserProgressEntity created = new UserProgressEntity();
        created.setUserId(userId);
        created.setTotalSubmissions(0);
        created.setCurrentStreak(0);
        created.setLongestStreak(0);
        created.setSuggestedVocabularyUsageCount(0);

        try {
            return userProgressRepository.save(created);
        } catch (DataIntegrityViolationException ex) {
            // Concurrent first-write from another request may already have inserted this user.
            return userProgressRepository.findById(userId).orElseThrow(() -> ex);
        }
    }

    private VocabularyItem toVocabularyItem(UserVocabularyEntity entity) {
        VocabularyItem item = new VocabularyItem();
        item.setWord(entity.getWord());
        item.setTranslation(entity.getMeaning());
        item.setExampleSentence(entity.getExample());
        return item;
    }
}
