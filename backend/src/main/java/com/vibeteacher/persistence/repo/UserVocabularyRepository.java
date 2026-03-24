package com.vibeteacher.persistence.repo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.vibeteacher.persistence.entity.UserVocabularyEntity;

public interface UserVocabularyRepository extends JpaRepository<UserVocabularyEntity, Long> {
    List<UserVocabularyEntity> findByUserId(String userId);

    Optional<UserVocabularyEntity> findByUserIdAndWordIgnoreCase(String userId, String word);

    int countByUserId(String userId);
}
