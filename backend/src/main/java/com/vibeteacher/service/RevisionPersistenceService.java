package com.vibeteacher.service;

import java.util.Collections;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.vibeteacher.api.dto.RevisionHistoryItem;
import com.vibeteacher.api.dto.RevisionRequest;
import com.vibeteacher.api.dto.RevisionResponse;
import com.vibeteacher.persistence.entity.ErrorOccurrenceEntity;
import com.vibeteacher.persistence.entity.RevisionEntity;
import com.vibeteacher.persistence.repo.ErrorOccurrenceRepository;
import com.vibeteacher.persistence.repo.RevisionRepository;

@Service
public class RevisionPersistenceService {

    private final RevisionRepository revisionRepository;
    private final ErrorOccurrenceRepository errorOccurrenceRepository;

    public RevisionPersistenceService(RevisionRepository revisionRepository,
                                      ErrorOccurrenceRepository errorOccurrenceRepository) {
        this.revisionRepository = revisionRepository;
        this.errorOccurrenceRepository = errorOccurrenceRepository;
    }

    @Transactional
    public Long saveRevisionWithIssues(String userId, RevisionRequest request, RevisionResponse response, String level) {
        RevisionEntity revision = new RevisionEntity();
        revision.setUserId(userId);
        revision.setLevel(level);
        revision.setModel(request.getModel());
        revision.setRequirements(request.getRequirements());
        revision.setExpand(request.isExpand());
        revision.setSourceText(request.getText());
        revision.setCorrectedText(response.getCorrectedText());
        revision.setSummary(response.getSummary());

        RevisionEntity saved = revisionRepository.save(revision);

        List<RevisionResponse.Issue> issues = response.getIssues() == null
            ? Collections.emptyList()
            : response.getIssues();

        for (RevisionResponse.Issue issue : issues) {
            ErrorOccurrenceEntity entity = new ErrorOccurrenceEntity();
            entity.setRevisionId(saved.getId());
            entity.setUserId(userId);
            entity.setErrorType(issue.getType());
            entity.setOriginalText(issue.getOriginal());
            entity.setSuggestedText(issue.getSuggested());
            entity.setReason(issue.getReason());
            errorOccurrenceRepository.save(entity);
        }

        return saved.getId();
    }

    @Transactional(readOnly = true)
    public Page<RevisionHistoryItem> getRevisionHistory(String userId, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 20);
        Pageable pageable = PageRequest.of(safePage, safeSize);
        return revisionRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
            .map(this::toHistoryItem);
    }

    private RevisionHistoryItem toHistoryItem(RevisionEntity entity) {
        RevisionHistoryItem item = new RevisionHistoryItem();
        item.setId(entity.getId());
        item.setLevel(entity.getLevel());
        item.setSourceText(entity.getSourceText());
        item.setCorrectedText(entity.getCorrectedText());
        item.setSummary(entity.getSummary());
        item.setCreatedAt(entity.getCreatedAt());
        return item;
    }
}
