package com.vibeteacher.persistence.repo;

import org.springframework.data.jpa.repository.JpaRepository;

import com.vibeteacher.persistence.entity.ErrorOccurrenceEntity;

public interface ErrorOccurrenceRepository extends JpaRepository<ErrorOccurrenceEntity, Long> {
}
