package com.vibeteacher.api;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.vibeteacher.api.dto.VocabularyItem;
import com.vibeteacher.model.Achievement;
import com.vibeteacher.model.UserProgress;
import com.vibeteacher.service.UserProgressService;

@RestController
@RequestMapping("/api/user")
public class UserProgressController {

    private final UserProgressService userProgressService;

    public UserProgressController(UserProgressService userProgressService) {
        this.userProgressService = userProgressService;
    }

    @PostMapping("/submission")
    public ResponseEntity<?> recordSubmission(@RequestParam String userId) {
        if (userId == null || userId.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("缺少用户ID");
        }
        List<Achievement> newAchievements = userProgressService.recordSubmission(userId.trim());
        return ResponseEntity.ok(Map.of("newAchievements", newAchievements));
    }

    @GetMapping("/achievements")
    public ResponseEntity<?> getAchievements(@RequestParam String userId) {
        if (userId == null || userId.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("缺少用户ID");
        }
        List<Achievement> achievements = userProgressService.getAchievements(userId.trim());
        return ResponseEntity.ok(achievements);
    }

    @PostMapping("/vocabulary")
    public ResponseEntity<?> addVocabulary(@RequestParam String userId,
                                           @RequestBody List<VocabularyItem> items) {
        if (userId == null || userId.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("缺少用户ID");
        }
        List<VocabularyItem> updated = userProgressService.addVocabulary(userId.trim(), items);
        return ResponseEntity.ok(updated);
    }

    @GetMapping("/vocabulary")
    public ResponseEntity<?> getVocabulary(@RequestParam String userId) {
        if (userId == null || userId.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("缺少用户ID");
        }
        List<VocabularyItem> vocabulary = userProgressService.getVocabulary(userId.trim());
        return ResponseEntity.ok(vocabulary);
    }

    @GetMapping("/vocabulary/library")
    public ResponseEntity<?> getVocabularyLibrary(@RequestParam String userId) {
        return getVocabulary(userId);
    }

    @PostMapping("/vocabulary/usage")
    public ResponseEntity<?> recordVocabularyUsage(@RequestParam String userId,
                                                   @RequestBody Map<String, String> payload) {
        if (userId == null || userId.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("缺少用户ID");
        }
        String text = payload == null ? null : payload.get("text");
        int usageCount = userProgressService.countSuggestedVocabularyMatches(userId.trim(), text);
        List<Achievement> newAchievements = userProgressService.recordSuggestedVocabularyUsage(userId.trim(), usageCount);
        return ResponseEntity.ok(Map.of(
            "usageCount", usageCount,
            "newAchievements", newAchievements
        ));
    }

    @GetMapping("/stats")
    public ResponseEntity<?> getStats(@RequestParam String userId) {
        if (userId == null || userId.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("缺少用户ID");
        }
        UserProgress.Stats stats = userProgressService.getStats(userId.trim());
        return ResponseEntity.ok(stats);
    }
}
