package com.vibeteacher.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.ResponseOutputItem;
import com.openai.models.responses.ResponseOutputMessage;
import com.openai.models.responses.Response;
import com.openai.models.ResponsesModel;
import com.vibeteacher.api.dto.RevisionResponse;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class OpenAIService {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final KeyVaultService keyVaultService;

    public OpenAIService(KeyVaultService keyVaultService) {
        this.keyVaultService = keyVaultService;
    }

    public RevisionResponse revise(String model, String level, String text, String requirements) {
        String apiKey = keyVaultService.getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("未配置 Key Vault API KEY");
        }
        String endpoint = keyVaultService.getEndpoint();
        if (endpoint == null || endpoint.isBlank()) {
            throw new IllegalStateException("未配置 Key Vault ENDPOINT");
        }
        OpenAIClient client = OpenAIOkHttpClient.builder()
                .apiKey(apiKey)
                .baseUrl(endpoint)
                .build();

        String systemPrompt = "你是英语写作学习助手。你的任务是根据指定学习级别（中学7-12年级、大学英语四级、大学英语六级、TOFEL写作、GRE写作）对英文短文进行修改建议。\n" +
                "要求：\n" +
                "1. 不改变原文意思。\n" +
                "2. 识别语法、时态、词汇、拼写、句子结构问题。\n" +
                "3. 检查是否存在句式过于简单、用词过于基础、明显低于该年级水平的情况，并给出升级建议。\n" +
                "4. 修改意见必须使用中文。\n" +
                "5. 无论在任何情况下，均不得输出除 JSON 对象以外的内容，且JSON对象的长度都不能超过10000字节。\n" +
                "6. 无论在任何情况下，修改后的文章都必须使用英文，且不能超过1500字。\n" +
                "7. 输出 JSON 对象，字段为: correctedText, summary, issues。\n" +
                "8. issues 是数组，每项包含 type, original, suggested, reason。";

        String requirementsLine = (requirements == null || requirements.isBlank())
            ? ""
            : "附加要求: " + requirements.trim() + "\n";

        String userPrompt = "学习级别: " + level + "\n" +
            requirementsLine +
            "原文: \n" + text + "\n\n" +
            "请仅输出 JSON 对象。";

        String fullPrompt = systemPrompt + "\n\n" + userPrompt;

        ResponseCreateParams params = ResponseCreateParams.builder()
            .model(ResponsesModel.ofString(model))
            .input(fullPrompt)
            .build();

        Response response = client.responses().create(params);
        String textOutput = extractText(response);

        RevisionResponse revisionResponse = new RevisionResponse();
        revisionResponse.setRawOutput(textOutput);

        try {
            JsonNode node = objectMapper.readTree(textOutput);
            JsonNode corrected = node.get("correctedText");
            JsonNode summary = node.get("summary");
            JsonNode issuesNode = node.get("issues");

            if (corrected != null) {
                revisionResponse.setCorrectedText(corrected.asText());
            }
            if (summary != null) {
                revisionResponse.setSummary(summary.asText());
            }

            if (issuesNode != null && issuesNode.isArray()) {
                List<RevisionResponse.Issue> issues = new ArrayList<>();
                for (JsonNode issueNode : issuesNode) {
                    RevisionResponse.Issue issue = new RevisionResponse.Issue();
                    if (issueNode.has("type")) {
                        issue.setType(issueNode.get("type").asText());
                    }
                    if (issueNode.has("original")) {
                        issue.setOriginal(issueNode.get("original").asText());
                    }
                    if (issueNode.has("suggested")) {
                        issue.setSuggested(issueNode.get("suggested").asText());
                    }
                    if (issueNode.has("reason")) {
                        issue.setReason(issueNode.get("reason").asText());
                    }
                    issues.add(issue);
                }
                revisionResponse.setIssues(issues);
            }
        } catch (Exception ignored) {
        }

        return revisionResponse;
    }

    private String extractText(Response response) {
        StringBuilder builder = new StringBuilder();
        for (ResponseOutputItem item : response.output()) {
            item.message().ifPresent(message -> {
                for (ResponseOutputMessage.Content content : message.content()) {
                    content.outputText().ifPresent(outputText -> builder.append(outputText.text()));
                }
            });
        }
        return builder.toString().trim();
    }
}
