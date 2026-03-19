package com.vibeteacher.service;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class OcrService {
    private static final String OCR_API_VERSION = "2024-02-01";

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

        String analyzeUrl = buildAnalyzeUrl(ocrEndpoint);

        HttpHeaders headers = new HttpHeaders();
        headers.set("Ocp-Apim-Subscription-Key", ocrKey);
        MediaType mediaType = (contentType == null || contentType.isBlank())
                ? MediaType.APPLICATION_OCTET_STREAM
                : MediaType.parseMediaType(contentType);
        headers.setContentType(mediaType);

        HttpEntity<byte[]> request = new HttpEntity<>(imageBytes, headers);
        ResponseEntity<String> response;
        try {
            response = restTemplate.postForEntity(analyzeUrl, request, String.class);
        } catch (HttpStatusCodeException ex) {
            throw new IllegalStateException(buildHttpErrorMessage(ex));
        }

        String responseBody = response.getBody();
        if (!response.getStatusCode().is2xxSuccessful() || responseBody == null || responseBody.isBlank()) {
            throw new IllegalStateException("OCR 请求失败：服务未返回有效结果");
        }

        try {
            JsonNode root = objectMapper.readTree(responseBody);
            String content = root.path("readResult").path("content").asText("").trim();
            if (!content.isBlank()) {
                return content;
            }

            String linesText = extractLines(root.path("readResult"));
            if (!linesText.isBlank()) {
                return linesText;
            }
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("OCR 响应解析失败");
        }

        throw new IllegalStateException("未识别到文本，请换一张更清晰的图片");
    }

    private String buildAnalyzeUrl(String ocrEndpoint) {
        String endpoint = normalizeEndpoint(ocrEndpoint);
        return endpoint + "/computervision/imageanalysis:analyze?api-version=" + OCR_API_VERSION + "&features=read&language=en";
    }

    private String normalizeEndpoint(String ocrEndpoint) {
        String endpoint = ocrEndpoint == null ? "" : ocrEndpoint.trim();
        if (endpoint.endsWith("/")) {
            endpoint = endpoint.substring(0, endpoint.length() - 1);
        }
        if (endpoint.endsWith("/computervision")) {
            endpoint = endpoint.substring(0, endpoint.length() - "/computervision".length());
        }
        int apiPathIndex = endpoint.indexOf("/computervision/");
        if (apiPathIndex >= 0) {
            endpoint = endpoint.substring(0, apiPathIndex);
        }
        return endpoint;
    }

    private String extractLines(JsonNode readResult) {
        StringBuilder text = new StringBuilder();

        JsonNode pages = readResult.path("pages");
        if (pages.isArray()) {
            appendPageLines(text, pages, "content");
        }

        JsonNode blocks = readResult.path("blocks");
        if (text.length() == 0 && blocks.isArray()) {
            appendPageLines(text, blocks, "text");
        }

        return text.toString().trim();
    }

    private void appendPageLines(StringBuilder text, JsonNode containers, String lineField) {
        for (JsonNode container : containers) {
            JsonNode lines = container.path("lines");
            if (!lines.isArray()) {
                continue;
            }
            for (JsonNode line : lines) {
                String lineText = line.path(lineField).asText("").trim();
                if (lineText.isBlank()) {
                    continue;
                }
                if (text.length() > 0) {
                    text.append('\n');
                }
                text.append(lineText);
            }
        }
    }

    private String buildHttpErrorMessage(HttpStatusCodeException ex) {
        String body = ex.getResponseBodyAsString();
        if (body != null) {
            body = body.trim();
        }
        if (body == null || body.isBlank()) {
            return "OCR 请求失败：HTTP " + ex.getStatusCode().value();
        }
        return "OCR 请求失败：HTTP " + ex.getStatusCode().value() + "，" + body;
    }
}
