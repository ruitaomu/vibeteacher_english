package com.vibeteacher.persistence.repo;

import org.springframework.data.jpa.repository.JpaRepository;

import com.vibeteacher.persistence.entity.UserProgressEntity;

public interface UserProgressRepository extends JpaRepository<UserProgressEntity, String> {
}
