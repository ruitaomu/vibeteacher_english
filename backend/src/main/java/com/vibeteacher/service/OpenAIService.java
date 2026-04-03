package com.vibeteacher.service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

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
import com.openai.models.responses.ResponseOutputMessage;
import com.vibeteacher.api.dto.RevisionResponse;
import com.vibeteacher.api.dto.VocabularyItem;

@Service
public class OpenAIService {
    private static final Logger logger = LoggerFactory.getLogger(OpenAIService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final KeyVaultService keyVaultService;
    private final boolean promptDebug;

    public OpenAIService(KeyVaultService keyVaultService,
                         @Value("${prompt.debug:false}") boolean promptDebug) {
        this.keyVaultService = keyVaultService;
        this.promptDebug = promptDebug;
    }

    public RevisionResponse revise(String model, String level, String text, String requirements, boolean expand, List<VocabularyItem> userVocabulary) {
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

        String levelGuidance = buildLevelGuidance(level);
        String vocabularyGuidance = buildVocabularyGuidance(userVocabulary);
        String systemPrompt = "你是英语写作学习助手。你的任务是根据指定学习级别（中学7-12年级、大学英语四级、大学英语六级、TOFEL写作、GRE写作）对英文短文进行修改建议。\n" +
                "整体要求：\n" +
                "1. 不要改变原文意思。\n" +
                "2. 识别语法、时态、词汇、拼写、句子结构问题。\n" +
                "3. 检查是否存在句式过于简单、用词过于基础、明显低于该年级水平的情况，并给出升级建议。\n" +
                "4. 修改意见必须使用中文。\n" +
                "5. 无论在任何情况下，均不得输出除 JSON 对象以外的内容，且JSON对象的长度都不能超过10000字节。\n" +
                "6. 无论在任何情况下，修改后的文章都必须使用英文，且不能超过1500字。\n" +
                "7. 输出 JSON 对象，字段为: correctedText, summary, issues, vocabularySuggestions, vocabularyUsage。\n" +
                "8. issues 是数组，每项包含 type, original, suggested, reason。\n" +
                "9. vocabularySuggestions 是数组，从原文中挑选3-5个值得学习或改进的词汇，每项包含 word（英文单词或短语）, translation（中文翻译）, exampleSentence（英文示例句）。\n" +
                "10. vocabularyUsage 是数组，检查原文中是否使用了用户词汇表中的词汇。如果使用了，请将这些词加入数组，每项包含 word（使用的词汇）, context（在原文中的使用上下文，不超过20个词）, evaluation（使用评价：'correct'表示使用正确且恰当，'incorrect'表示使用有误）。仅收录原文中确实使用的词汇表单词，不要添加未出现的词。";

        String requirementsLine = (requirements == null || requirements.isBlank())
            ? ""
            : "附加要求: " + requirements.trim() + "\n";
        String expandLine = expand
            ? "若篇幅不足该级别要求，请扩写至该级别规定的写作篇幅。\n"
            : "";

        String userPrompt = "学习级别: " + level + "\n" +
        (levelGuidance.isBlank() ? "" : ("该学习级别强调的写作要求：\n" + levelGuidance + "\n")) +
        (vocabularyGuidance.isBlank() ? "" : (vocabularyGuidance + "\n")) +
            requirementsLine +
            expandLine +
            "原文: \n" + text + "\n\n" +
            "请仅输出 JSON 对象。";

        String fullPrompt = systemPrompt + "\n\n" + userPrompt;
        if (promptDebug) {
            logger.info("Prompt debug enabled. Full prompt:\n{}", fullPrompt);
        }

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

            JsonNode vocabNode = node.get("vocabularySuggestions");
            if (vocabNode != null && vocabNode.isArray()) {
                List<VocabularyItem> vocab = new ArrayList<>();
                for (JsonNode vNode : vocabNode) {
                    VocabularyItem item = new VocabularyItem();
                    if (vNode.has("word")) item.setWord(vNode.get("word").asText());
                    if (vNode.has("translation")) item.setTranslation(vNode.get("translation").asText());
                    if (vNode.has("exampleSentence")) item.setExampleSentence(vNode.get("exampleSentence").asText());
                    if (item.getWord() != null && !item.getWord().isBlank()) {
                        vocab.add(item);
                    }
                }
                revisionResponse.setVocabularySuggestions(vocab);
            }

            JsonNode vocabUsageNode = node.get("vocabularyUsage");
            if (vocabUsageNode != null && vocabUsageNode.isArray()) {
                List<RevisionResponse.VocabularyUsage> vocabUsage = new ArrayList<>();
                for (JsonNode usageNode : vocabUsageNode) {
                    RevisionResponse.VocabularyUsage usage = new RevisionResponse.VocabularyUsage();
                    if (usageNode.has("word")) usage.setWord(usageNode.get("word").asText());
                    if (usageNode.has("context")) usage.setContext(usageNode.get("context").asText());
                    if (usageNode.has("evaluation")) usage.setEvaluation(usageNode.get("evaluation").asText());
                    if (usage.getWord() != null && !usage.getWord().isBlank()) {
                        vocabUsage.add(usage);
                    }
                }
                revisionResponse.setVocabularyUsage(vocabUsage);
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

        private String buildLevelGuidance(String level) {
        if (level == null || level.isBlank()) {
            return "";
        }
        return switch (level.trim()) {
            case "中学7年级" -> "写作任务类型：简单记叙文（如 My Day, A Trip）、书信（给笔友）、看图写话。\n" +
                "内容与主题：日常生活、家庭、学校、兴趣爱好。\n" +
                "语言要求：词汇量约 800；掌握一般现在时、一般过去时；以简单句为主，少量并列句。\n" +
                "篇幅/结构：50–80 词；具备基本开头–主体–结尾结构。";
            case "中学8年级" -> "写作任务类型：记叙文（难忘经历）、说明文（如何做某事）、简单议论文（支持/反对）。\n" +
                "内容与主题：友谊、健康、节日、环保初步话题。\n" +
                "语言要求：词汇量约 1600；使用连接词（and, but, because）；能使用基础复合句（when, if 引导）。\n" +
                "篇幅/结构：80–100 词；初步形成三段式结构。";
            case "中学9年级" -> "写作任务类型：记叙文、应用文（邮件、通知、日记）、简单议论文（利弊分析）。\n" +
                "内容与主题：成长、梦想、社会责任、传统文化、科技影响。\n" +
                "语言要求：词汇量 1800–2000；正确使用 5 种基本时态；使用过渡词（first, however, in conclusion）。\n" +
                "篇幅/结构：100–120 词；结构清晰，逻辑连贯。";
            case "中学10年级" -> "写作任务类型：夹叙夹议、说明文（解释现象）、观点类议论文。\n" +
                "内容与主题：学习压力、网络使用、代际沟通、环境问题。\n" +
                "语言要求：词汇量约 2500；使用非谓语动词、定语从句；能表达个人观点并简单论证。\n" +
                "篇幅/结构：120–150 词；包含论点 + 1–2 个理由。";
            case "中学11年级" -> "写作任务类型：正式议论文、读后续写/概要写作（新高考题型）、书评/影评。\n" +
                "内容与主题：社会议题（如 AI、教育公平）、文化差异、心理健康。\n" +
                "语言要求：词汇量约 3000；熟练使用复杂句、强调句、虚拟语气；逻辑衔接自然。\n" +
                "篇幅/结构：150–180 词；引言–主体–结论完整。";
            case "中学12年级" -> "写作任务类型：高阶议论文、图表描述 + 观点表达、开放性话题写作。\n" +
                "内容与主题：全球化、青年责任、科技创新、传统与现代冲突。\n" +
                "语言要求：词汇量 3500+；语言准确、多样、得体；可使用修辞（比喻、排比）增强表现力。\n" +
                "篇幅/结构：150–200 词；思想深刻，结构严谨，语言地道。";
            case "大学英语四级" -> "写作任务类型：应用文（建议信、通知等）、议论文（给定观点或图表）。\n" +
                "核心要求与评分维度：切题；结构完整；语言基本正确；有简单论证。\n" +
                "篇幅：120–180 词。\n" +
                "语言与思维水平：相当于高二–高三水平；能清晰表达观点，但句式较单一。";
            case "大学英语六级" -> "写作任务类型：议论文（社会热点、抽象话题）、评论性写作。\n" +
                "核心要求与评分维度：思想深度；逻辑严密；词汇语法多样性；语言地道性。\n" +
                "篇幅：150–200 词。\n" +
                "语言与思维水平：接近大学低年级水平；能使用高级词汇、复杂句式、恰当衔接。";
            case "TOFEL写作" -> "写作任务类型：Integrated Writing（读+听+写）、Independent Writing（独立议论文）。\n" +
                "核心要求与评分维度：信息整合能力；观点明确 + 充分展开；语言学术化；连贯与衔接。\n" +
                "篇幅：综合 150–225 词；独立 300+ 词。\n" +
                "语言与思维水平：大学本科入学水平；需具备学术写作基础，逻辑严密，例证具体。";
            case "GRE写作" -> "写作任务类型：Analyze an Issue（观点分析）、Analyze an Argument（论证分析）。\n" +
                "核心要求与评分维度：批判性思维；多角度论证；语言精准、正式；识别逻辑漏洞（Argument）。\n" +
                "篇幅：每篇约 350–600 词。\n" +
                "语言与思维水平：研究生水平；强调思辨能力、逻辑严密性、学术语言风格。";
            default -> "";
        };
    }

    private String buildVocabularyGuidance(List<VocabularyItem> userVocabulary) {
        if (userVocabulary == null || userVocabulary.isEmpty()) {
            return "";
        }
        
        String vocabList = userVocabulary.stream()
            .filter(v -> v != null && v.getWord() != null && !v.getWord().isBlank())
            .map(v -> {
                String meaning = v.getTranslation() != null && !v.getTranslation().isBlank() 
                    ? "（" + v.getTranslation() + "）" 
                    : "";
                return "- " + v.getWord() + meaning;
            })
            .collect(Collectors.joining("\n"));
        
        if (vocabList.isBlank()) {
            return "";
        }
        
        return "用户词汇表（检查原文是否使用了这些词汇，如果使用到了请在 vocabularyUsage 中列出并评价）：\n" + vocabList;
    }
}
