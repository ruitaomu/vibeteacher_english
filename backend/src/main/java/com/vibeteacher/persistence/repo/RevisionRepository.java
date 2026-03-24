package com.vibeteacher.persistence.repo;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.vibeteacher.persistence.entity.RevisionEntity;

public interface RevisionRepository extends JpaRepository<RevisionEntity, Long> {
    List<RevisionEntity> findTop50ByUserIdOrderByCreatedAtDesc(String userId);
}
