package com.vibeteacher.api;

import com.vibeteacher.api.dto.RevisionRequest;
import com.vibeteacher.api.dto.RevisionResponse;
import com.vibeteacher.service.OpenAIService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class RevisionController {
    private final OpenAIService openAIService;

    public RevisionController(OpenAIService openAIService) {
        this.openAIService = openAIService;
    }

    @PostMapping("/revise")
    public ResponseEntity<?> revise(@RequestBody RevisionRequest request) {
        if (request.getText() == null || request.getText().isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("缺少英文片段");
        }
        if (request.getApiKey() == null || request.getApiKey().isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("缺少 API KEY");
        }
        if (request.getModel() == null || request.getModel().isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("缺少模型名称");
        }
        String endpoint = request.getEndpoint();
        if (endpoint == null || endpoint.isBlank()) {
            endpoint = "https://api.openai.com/v1";
        }
        String level = request.getLevel() == null ? "中学7年级" : request.getLevel();

        RevisionResponse response = openAIService.revise(endpoint, request.getApiKey(), request.getModel(), level, request.getText());
        return ResponseEntity.ok(response);
    }
}
