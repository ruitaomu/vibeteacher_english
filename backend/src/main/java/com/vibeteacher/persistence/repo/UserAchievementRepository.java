package com.vibeteacher.persistence.repo;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.vibeteacher.persistence.entity.UserAchievementEntity;

public interface UserAchievementRepository extends JpaRepository<UserAchievementEntity, Long> {
    List<UserAchievementEntity> findByUserId(String userId);

    boolean existsByUserIdAndAchievementId(String userId, String achievementId);

    int countByUserId(String userId);
}
