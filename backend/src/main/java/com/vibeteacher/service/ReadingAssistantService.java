package com.vibeteacher.service;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.ResponsesModel;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.ResponseOutputItem;
import com.vibeteacher.api.dto.ReadingAnalysisResponse;
import com.vibeteacher.api.dto.SentenceStructureResponse;

@Service
public class ReadingAssistantService {
    private static final Logger logger = LoggerFactory.getLogger(ReadingAssistantService.class);

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final KeyVaultService keyVaultService;
    private final boolean promptDebug;

    public ReadingAssistantService(KeyVaultService keyVaultService,
                                   @Value("${prompt.debug:false}") boolean promptDebug) {
        this.keyVaultService = keyVaultService;
        this.promptDebug = promptDebug;
    }

    public ReadingAnalysisResponse analyze(String model, String text) {
        String systemPrompt = "你是英语阅读助手。你的任务是对用户输入的英文内容做阅读分析。\n"
            + "输出要求：\n"
            + "1. 只输出 JSON 对象，不要输出任何额外解释。\n"
            + "2. JSON 顶层字段固定为 overallTranslation, sentences。\n"
            + "3. overallTranslation 是对全文的自然中文翻译。\n"
            + "4. sentences 是数组，按原文顺序逐句输出。每项字段固定为 original, translation, keyWords, keyPhrases, typicalPatterns。\n"
            + "5. translation 是该句的中文翻译。\n"
            + "6. keyWords 只保留对中学生英语学习不常见、但值得重点掌握的单词；若没有则返回空数组。\n"
            + "7. keyPhrases 只保留该句中值得学习的习惯短语、固定搭配或表达；若没有则返回空数组。\n"
            + "8. typicalPatterns 只保留该句中有教学价值的典型句式或结构，且这种典型句式或结构必须在中学英语教学中具有代表性（比如常见于教学示例中），在日常英语写作中有普遍应用，同时在英语考试中具有一定的应用价值；若没有则返回空数组。\n"
            + "9. keyWords、keyPhrases、typicalPatterns 的每个元素字段固定为 term, explanation, exampleSentence。\n"
            + "10. explanation 必须使用中文，说明要结合当前句子，不要空泛，不要捏造原文没有的知识点。\n"
            + "11. exampleSentence 优先使用原句中的相关片段；若不适合，可填写原句本身。\n"
            + "12. 每句每类最多列出3项，避免过度分析。";

        String userPrompt = "请分析以下英文内容，并严格输出 JSON：\n" + text;
        String textOutput = requestText(model, systemPrompt, userPrompt);

        ReadingAnalysisResponse response = new ReadingAnalysisResponse();
        response.setRawOutput(textOutput);
        if (promptDebug) {
            logger.info("analyze raw output:\n{}", textOutput);
        }

        try {
            JsonNode root = objectMapper.readTree(textOutput);
            JsonNode overallTranslation = root.get("overallTranslation");
            if (overallTranslation != null && !overallTranslation.isNull()) {
                response.setOverallTranslation(overallTranslation.asText());
            }

            JsonNode sentencesNode = root.get("sentences");
            if (sentencesNode != null && sentencesNode.isArray()) {
                List<ReadingAnalysisResponse.SentenceAnalysis> sentences = new ArrayList<>();
                for (JsonNode sentenceNode : sentencesNode) {
                    ReadingAnalysisResponse.SentenceAnalysis sentence = new ReadingAnalysisResponse.SentenceAnalysis();
                    if (sentenceNode.has("original")) {
                        sentence.setOriginal(sentenceNode.get("original").asText());
                    }
                    if (sentenceNode.has("translation")) {
                        sentence.setTranslation(sentenceNode.get("translation").asText());
                    }
                    sentence.setKeyWords(parseLearningPoints(sentenceNode.get("keyWords")));
                    sentence.setKeyPhrases(parseLearningPoints(sentenceNode.get("keyPhrases")));
                    sentence.setTypicalPatterns(parseLearningPoints(sentenceNode.get("typicalPatterns")));
                    sentences.add(sentence);
                }
                response.setSentences(sentences);
            }
        } catch (Exception ignored) {

        }

        return response;
    }

    public SentenceStructureResponse explainSentenceStructure(String model, String sentence) {
        String systemPrompt = "你是英语语法讲解助手。请对单个英文句子进行句子结构拆解讲解。\n"
            + "输出要求：\n"
            + "1. 只输出 JSON 对象，不要输出任何额外文字。\n"
            + "2. JSON 字段固定为 original, structureExplanation。\n"
            + "3. structureExplanation 必须使用中文，结合原句按成分或层次解释句子结构，帮助学生理解主干、从句、非谓语、修饰成分等。\n"
            + "4. 讲解要清楚但不过度冗长，避免项目符号过多。";

        String userPrompt = "请分析这个句子的结构，并严格输出 JSON：\n" + sentence;
        String textOutput = requestText(model, systemPrompt, userPrompt);

        SentenceStructureResponse response = new SentenceStructureResponse();
        response.setRawOutput(textOutput);
        if (promptDebug) {
            logger.info("explainSentenceStructure raw output:\n{}", textOutput);
        }
        response.setOriginal(sentence);

        try {
            JsonNode root = objectMapper.readTree(textOutput);
            JsonNode original = root.get("original");
            JsonNode structureExplanation = root.get("structureExplanation");
            if (original != null && !original.isNull() && !original.asText().isBlank()) {
                response.setOriginal(original.asText());
            }
            if (structureExplanation != null && !structureExplanation.isNull()) {
                response.setStructureExplanation(structureExplanation.asText());
            }
        } catch (Exception ignored) {
        }

        return response;
    }

    private List<ReadingAnalysisResponse.LearningPoint> parseLearningPoints(JsonNode node) {
        List<ReadingAnalysisResponse.LearningPoint> points = new ArrayList<>();
        if (node == null || !node.isArray()) {
            return points;
        }

        for (JsonNode itemNode : node) {
            if (itemNode == null || !itemNode.has("term")) {
                continue;
            }
            String term = itemNode.get("term").asText();
            if (term == null || term.isBlank()) {
                continue;
            }
            ReadingAnalysisResponse.LearningPoint point = new ReadingAnalysisResponse.LearningPoint();
            point.setTerm(term);
            if (itemNode.has("explanation")) {
                point.setExplanation(itemNode.get("explanation").asText());
            }
            if (itemNode.has("exampleSentence")) {
                point.setExampleSentence(itemNode.get("exampleSentence").asText());
            }
            points.add(point);
        }
        return points;
    }

    private String requestText(String model, String systemPrompt, String userPrompt) {
        String apiKey = keyVaultService.getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("未配置 Key Vault API KEY");
        }
        String endpoint = keyVaultService.getEndpoint();
        if (endpoint == null || endpoint.isBlank()) {
            throw new IllegalStateException("未配置 Key Vault ENDPOINT");
        }

        String fullPrompt = systemPrompt + "\n\n" + userPrompt;
        if (promptDebug) {
            logger.info("Prompt debug enabled. Full prompt:\n{}", fullPrompt);
        }

        OpenAIClient client = OpenAIOkHttpClient.builder()
            .apiKey(apiKey)
            .baseUrl(endpoint)
            .build();

        ResponseCreateParams params = ResponseCreateParams.builder()
            .model(ResponsesModel.ofString(model))
            .input(fullPrompt)
            .build();

        if (promptDebug) {
            logger.info("OpenAI request start. model={}, endpoint={}", model, endpoint);
        }

        Response response;
        try {
            response = client.responses().create(params);
        } catch (Exception ex) {
            logger.error("OpenAI request failed. model={}, endpoint={}", model, endpoint, ex);
            throw ex;
        }

        String textOutput = extractText(response);
        if (promptDebug) {
            logger.info("OpenAI response received. chars={}", textOutput == null ? 0 : textOutput.length());
        }
        return textOutput;
    }

    private String extractText(Response response) {
        StringBuilder builder = new StringBuilder();
        for (ResponseOutputItem item : response.output()) {
            item.message().ifPresent(message -> {
                for (com.openai.models.responses.ResponseOutputMessage.Content content : message.content()) {
                    content.outputText().ifPresent(outputText -> builder.append(outputText.text()));
                }
            });
        }
        return builder.toString().trim();
    }
}
