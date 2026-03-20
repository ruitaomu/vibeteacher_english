package com.vibeteacher.api;

import com.vibeteacher.api.dto.VocabularyItem;
import com.vibeteacher.model.Achievement;
import com.vibeteacher.model.UserProgress;
import com.vibeteacher.service.UserProgressService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

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

    @GetMapping("/stats")
    public ResponseEntity<?> getStats(@RequestParam String userId) {
        if (userId == null || userId.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("缺少用户ID");
        }
        UserProgress.Stats stats = userProgressService.getStats(userId.trim());
        return ResponseEntity.ok(stats);
    }
}
