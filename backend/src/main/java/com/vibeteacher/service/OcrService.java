package com.vibeteacher.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class OcrService {
    private final KeyVaultService keyVaultService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();

    public OcrService(KeyVaultService keyVaultService) {
        this.keyVaultService = keyVaultService;
    }

    public String recognizeText(byte[] imageBytes, String contentType) {
        String ocrKey = keyVaultService.getOcrKey();
        if (ocrKey == null || ocrKey.isBlank()) {
            throw new IllegalStateException("未配置 OCR API KEY");
        }
        String ocrEndpoint = keyVaultService.getOcrEndpoint();
        if (ocrEndpoint == null || ocrEndpoint.isBlank()) {
            throw new IllegalStateException("未配置 OCR ENDPOINT");
        }
        String base = ocrEndpoint.endsWith("/") ? ocrEndpoint : ocrEndpoint + "/";
        String analyzeUrl = base + "vision/v3.2/read/analyze?language=en";

        HttpHeaders headers = new HttpHeaders();
        headers.set("Ocp-Apim-Subscription-Key", ocrKey);
        MediaType mediaType = (contentType == null || contentType.isBlank())
                ? MediaType.APPLICATION_OCTET_STREAM
                : MediaType.parseMediaType(contentType);
        headers.setContentType(mediaType);

        HttpEntity<byte[]> request = new HttpEntity<>(imageBytes, headers);
        ResponseEntity<String> submit = restTemplate.postForEntity(analyzeUrl, request, String.class);
        String operationLocation = submit.getHeaders().getFirst("Operation-Location");
        if (operationLocation == null || operationLocation.isBlank()) {
            throw new IllegalStateException("OCR 请求失败：缺少 Operation-Location");
        }

        HttpHeaders pollHeaders = new HttpHeaders();
        pollHeaders.set("Ocp-Apim-Subscription-Key", ocrKey);
        HttpEntity<Void> pollRequest = new HttpEntity<>(pollHeaders);

        for (int i = 0; i < 20; i++) {
            try {
                Thread.sleep(500);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            ResponseEntity<String> poll = restTemplate.exchange(operationLocation, HttpMethod.GET, pollRequest, String.class);
            if (!poll.getStatusCode().is2xxSuccessful() || poll.getBody() == null) {
                continue;
            }
            try {
                JsonNode root = objectMapper.readTree(poll.getBody());
                String status = root.path("status").asText("");
                if ("succeeded".equalsIgnoreCase(status)) {
                    StringBuilder text = new StringBuilder();
                    JsonNode readResults = root.path("analyzeResult").path("readResults");
                    if (readResults.isArray()) {
                        for (JsonNode page : readResults) {
                            JsonNode lines = page.path("lines");
                            if (lines.isArray()) {
                                for (JsonNode line : lines) {
                                    String lineText = line.path("text").asText("");
                                    if (!lineText.isBlank()) {
                                        if (text.length() > 0) {
                                            text.append('\n');
                                        }
                                        text.append(lineText);
                                    }
                                }
                            }
                        }
                    }
                    return text.toString().trim();
                }
                if ("failed".equalsIgnoreCase(status)) {
                    throw new IllegalStateException("OCR 识别失败");
                }
            } catch (Exception ignored) {
                // ignore parse errors and continue polling
            }
        }
        throw new IllegalStateException("OCR 超时，请重试");
    }
}
