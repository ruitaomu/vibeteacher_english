package com.vibeteacher.service;

import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vibeteacher.api.dto.ReadingArticleResponse;
import com.vibeteacher.service.ReadingLevel.ArticleSource;

/**
 * 从开放许可（Creative Commons）的英文来源以及配置的 RSS 源拉取文章，并按学习级别挑选难度合适的一篇。
 */
@Service
public class ReadingArticleService {
    private static final Logger logger = LoggerFactory.getLogger(ReadingArticleService.class);

    private static final String USER_AGENT =
        "VibeTeacherEnglish/1.0 (https://github.com/ruitaomu/vibeteacher_english)";
    private static final int CANDIDATES_PER_SOURCE = 3;
    private static final int NEAREST_CANDIDATES = 3;
    private static final int MIN_WIKI_PAGE_BYTES = 2500;
    private static final int MIN_TEXT_CHARS = 300;
    private static final int PLOS_MAX_START = 200;
    private static final String PLOS_LICENSE = "CC BY 4.0";
    private static final String PLOS_LICENSE_URL = "https://creativecommons.org/licenses/by/4.0/";

    private static final Pattern HEADING = Pattern.compile("^=+\\s*(.*?)\\s*=+$");
    private static final Pattern TRAILING_SECTION = Pattern.compile(
        "(?i)references|related pages|other websites|sources|external links|see also|notes|"
            + "sister links|related news|further reading|citations|bibliography");
    private static final Pattern SENTENCE_BREAK = Pattern.compile("(?<=[.!?][\"”’)]{0,2})\\s+");
    private static final Pattern HTML_TAG = Pattern.compile("<[^>]+>");
    private static final Pattern HTML_BLOCK_END = Pattern.compile("(?i)<br\\s*/?>|</(p|div|li|h\\d|blockquote)>");
    private static final Pattern FEED_FOOTER = Pattern.compile("(?i)the post .* appeared first on .*");
    private static final Pattern FEED_PAGE_PREFIX = Pattern.compile("^Page \\d+\\s*[–—-]\\s*");
    private static final String RSS_RIGHTS_NOTICE = "版权归原网站所有";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();
    private final Random random = new Random();

    private final String simpleWikipediaApi;
    private final String wikinewsApi;
    private final String plosApi;
    private final List<String> rssFeeds;
    private final int rssMaxPages;
    private final int maxChars;

    public ReadingArticleService(
        @Value("${reading.articles.simple-wikipedia-api:https://simple.wikipedia.org/w/api.php}") String simpleWikipediaApi,
        @Value("${reading.articles.wikinews-api:https://en.wikinews.org/w/api.php}") String wikinewsApi,
        @Value("${reading.articles.plos-api:https://api.plos.org/search}") String plosApi,
        @Value("${reading.articles.rss-feeds:}") String[] rssFeeds,
        @Value("${reading.articles.rss-max-pages:1}") int rssMaxPages,
        @Value("${reading.articles.max-chars:2000}") int maxChars) {
        this.simpleWikipediaApi = simpleWikipediaApi;
        this.wikinewsApi = wikinewsApi;
        this.plosApi = plosApi;
        this.rssFeeds = Arrays.stream(rssFeeds)
            .map(String::trim)
            .filter(feed -> !feed.isEmpty())
            .collect(Collectors.toList());
        this.rssMaxPages = Math.max(1, rssMaxPages);
        this.maxChars = maxChars;
    }

    /**
     * @throws IllegalArgumentException 学习级别无法识别
     * @throws IllegalStateException    所有来源都没有拿到可用文章
     */
    public ReadingArticleResponse fetchArticle(String levelLabel) {
        return fetchArticle(levelLabel, List.of());
    }

    /**
     * @param excludeUrls 最近看过的文章链接，第一项为当前文章。优先返回没看过的文章；
     *                    只剩看过的文章时退而返回其中一篇，但绝不返回当前文章。
     */
    public ReadingArticleResponse fetchArticle(String levelLabel, List<String> excludeUrls) {
        ReadingLevel level = ReadingLevel.fromLabel(levelLabel);
        if (level == null) {
            throw new IllegalArgumentException("不支持的学习级别: " + levelLabel);
        }
        List<String> seen = excludeUrls == null ? List.of() : excludeUrls.stream()
            .filter(url -> url != null && !url.isBlank())
            .map(String::trim)
            .collect(Collectors.toList());
        String current = seen.isEmpty() ? "" : seen.get(0);

        List<ArticleSource> sources = new ArrayList<>(level.sources());
        if (!rssFeeds.isEmpty()) {
            sources.add(ArticleSource.RSS);
        }
        Collections.shuffle(sources, random);

        List<ReadingArticleResponse> unseen = new ArrayList<>();
        List<ReadingArticleResponse> seenBefore = new ArrayList<>();
        boolean currentArticleFound = false;
        for (ArticleSource source : sources) {
            List<ReadingArticleResponse> candidates;
            try {
                candidates = fetchCandidates(source);
            } catch (IOException | RuntimeException ex) {
                logger.warn("Fetching reading articles failed. source={}", source, ex);
                continue;
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                break;
            }

            List<ReadingArticleResponse> fresh = new ArrayList<>();
            for (ReadingArticleResponse candidate : candidates) {
                if (candidate.getUrl().equals(current)) {
                    currentArticleFound = true;
                } else if (seen.contains(candidate.getUrl())) {
                    seenBefore.add(candidate);
                } else {
                    fresh.add(candidate);
                }
            }
            List<ReadingArticleResponse> matched = fresh.stream()
                .filter(candidate -> level.matches(candidate.getEstimatedGrade()))
                .collect(Collectors.toList());
            if (!matched.isEmpty()) {
                return finish(matched.get(random.nextInt(matched.size())), level, true);
            }
            unseen.addAll(fresh);
        }

        List<ReadingArticleResponse> pool = unseen.isEmpty() ? seenBefore : unseen;
        if (pool.isEmpty()) {
            if (currentArticleFound) {
                throw new IllegalStateException("暂时没有其他可用文章，请稍后重试或切换学习级别。");
            }
            throw new IllegalStateException("暂时无法从文章来源获取内容，请稍后重试。");
        }
        List<ReadingArticleResponse> matched = pool.stream()
            .filter(candidate -> level.matches(candidate.getEstimatedGrade()))
            .collect(Collectors.toList());
        if (!matched.isEmpty()) {
            return finish(matched.get(random.nextInt(matched.size())), level, true);
        }
        return finish(pickNearest(pool, level), level, false);
    }

    /** 在难度最接近的几篇里随机挑一篇，避免每次都落到同一篇“最接近”的文章上。 */
    private ReadingArticleResponse pickNearest(List<ReadingArticleResponse> pool, ReadingLevel level) {
        List<ReadingArticleResponse> sorted = new ArrayList<>(pool);
        sorted.sort((a, b) -> Double.compare(
            level.distance(a.getEstimatedGrade()), level.distance(b.getEstimatedGrade())));
        return sorted.get(random.nextInt(Math.min(NEAREST_CANDIDATES, sorted.size())));
    }

    private ReadingArticleResponse finish(ReadingArticleResponse article, ReadingLevel level, boolean matched) {
        article.setLevel(level.label());
        article.setLevelMatched(matched);
        return article;
    }

    private List<ReadingArticleResponse> fetchCandidates(ArticleSource source)
        throws IOException, InterruptedException {
        switch (source) {
            case SIMPLE_WIKIPEDIA:
                return fetchWikiArticles(simpleWikipediaApi, "Simple English Wikipedia", randomWikiPageIds());
            case WIKINEWS:
                return fetchWikiArticles(wikinewsApi, "Wikinews", recentWikinewsPageIds());
            case RSS:
                return fetchRssArticles();
            default:
                return fetchPlosArticles();
        }
    }

    private List<Long> randomWikiPageIds() throws IOException, InterruptedException {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("action", "query");
        params.put("format", "json");
        params.put("formatversion", "2");
        params.put("generator", "random");
        params.put("grnnamespace", "0");
        params.put("grnfilterredir", "nonredirects");
        params.put("grnlimit", "20");
        params.put("prop", "info");

        List<Long> ids = new ArrayList<>();
        for (JsonNode page : getJson(simpleWikipediaApi, params).path("query").path("pages")) {
            // 过滤掉内容过短的小条目
            if (page.path("length").asInt() >= MIN_WIKI_PAGE_BYTES && page.hasNonNull("pageid")) {
                ids.add(page.get("pageid").asLong());
            }
        }
        return pickCandidates(ids);
    }

    private List<Long> recentWikinewsPageIds() throws IOException, InterruptedException {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("action", "query");
        params.put("format", "json");
        params.put("formatversion", "2");
        params.put("list", "categorymembers");
        params.put("cmtitle", "Category:Published");
        params.put("cmnamespace", "0");
        params.put("cmsort", "timestamp");
        params.put("cmdir", "desc");
        params.put("cmlimit", "50");

        List<Long> ids = new ArrayList<>();
        for (JsonNode member : getJson(wikinewsApi, params).path("query").path("categorymembers")) {
            if (member.hasNonNull("pageid")) {
                ids.add(member.get("pageid").asLong());
            }
        }
        return pickCandidates(ids);
    }

    private List<Long> pickCandidates(List<Long> ids) {
        Collections.shuffle(ids, random);
        return ids.subList(0, Math.min(CANDIDATES_PER_SOURCE, ids.size()));
    }

    private List<ReadingArticleResponse> fetchWikiArticles(String api, String sourceName, List<Long> pageIds)
        throws IOException, InterruptedException {
        List<ReadingArticleResponse> articles = new ArrayList<>();
        for (Long pageId : pageIds) {
            // 全文 extract 每次请求只能取一页；同时带回站点声明的许可信息用于署名
            Map<String, String> params = new LinkedHashMap<>();
            params.put("action", "query");
            params.put("format", "json");
            params.put("formatversion", "2");
            params.put("pageids", String.valueOf(pageId));
            params.put("prop", "extracts|info");
            params.put("explaintext", "1");
            params.put("exsectionformat", "wiki");
            params.put("inprop", "url");
            params.put("meta", "siteinfo");
            params.put("siprop", "rightsinfo");

            JsonNode query = getJson(api, params).path("query");
            JsonNode page = query.path("pages").path(0);
            JsonNode rights = query.path("rightsinfo");
            String license = rights.path("text").asText("").trim();
            String url = page.path("fullurl").asText("").trim();
            // 没有许可信息或原文链接就无法按许可要求署名，直接跳过
            if (license.isEmpty() || url.isEmpty()) {
                continue;
            }

            String fullText = cleanWikiExtract(page.path("extract").asText(""));
            String text = excerpt(fullText);
            if (text.length() < MIN_TEXT_CHARS) {
                continue;
            }

            ReadingArticleResponse article = new ReadingArticleResponse();
            article.setTitle(page.path("title").asText("").trim());
            article.setText(text);
            article.setAuthor(sourceName + " contributors");
            article.setSource(sourceName);
            article.setUrl(url);
            article.setLicense(license);
            article.setLicenseUrl(normalizeUrl(rights.path("url").asText("")));
            article.setEstimatedGrade(ReadingLevel.estimateGrade(text));
            article.setExcerpt(text.length() < fullText.length());
            articles.add(article);
        }
        return articles;
    }

    private List<ReadingArticleResponse> fetchPlosArticles() throws IOException, InterruptedException {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("q", "doc_type:full");
        params.put("fq", "article_type:\"Research Article\"");
        params.put("fl", "id,title_display,abstract,journal,author_display");
        params.put("sort", "publication_date desc");
        params.put("rows", "10");
        params.put("start", String.valueOf(random.nextInt(PLOS_MAX_START)));
        params.put("wt", "json");

        List<ReadingArticleResponse> articles = new ArrayList<>();
        for (JsonNode doc : getJson(plosApi, params).path("response").path("docs")) {
            String doi = doc.path("id").asText("").trim();
            String fullText = normalizeWhitespace(stripTags(firstText(doc.path("abstract"))));
            String text = excerpt(fullText);
            if (doi.isEmpty() || text.length() < MIN_TEXT_CHARS) {
                continue;
            }

            List<String> authors = new ArrayList<>();
            for (JsonNode author : doc.path("author_display")) {
                authors.add(author.asText());
            }
            String journal = doc.path("journal").asText("").trim();

            ReadingArticleResponse article = new ReadingArticleResponse();
            article.setTitle(normalizeWhitespace(stripTags(doc.path("title_display").asText(""))));
            article.setText(text);
            article.setAuthor(formatAuthors(authors));
            article.setSource(journal.isEmpty() ? "PLOS" : journal);
            article.setUrl("https://doi.org/" + doi);
            article.setLicense(PLOS_LICENSE);
            article.setLicenseUrl(PLOS_LICENSE_URL);
            article.setEstimatedGrade(ReadingLevel.estimateGrade(text));
            // 只取摘要，对整篇论文而言始终是节选
            article.setExcerpt(true);
            articles.add(article);
        }
        return articles;
    }

    /** 只使用 feed 自身提供的标题、正文和链接，不抓取文章网页。 */
    private List<ReadingArticleResponse> fetchRssArticles() throws IOException, InterruptedException {
        List<ReadingArticleResponse> articles = new ArrayList<>();
        IOException lastFailure = null;
        for (String feedUrl : rssFeeds) {
            try {
                articles.addAll(fetchRssFeed(feedUrl));
            } catch (IOException ex) {
                logger.warn("Fetching RSS feed failed. feed={}", feedUrl, ex);
                lastFailure = ex;
            }
        }
        if (articles.isEmpty() && lastFailure != null) {
            throw lastFailure;
        }
        // 同一篇赞助文章会以不同级别的链接重复出现，按正文去重
        Map<String, ReadingArticleResponse> distinct = new LinkedHashMap<>();
        for (ReadingArticleResponse article : articles) {
            distinct.putIfAbsent(article.getText(), article);
        }
        return new ArrayList<>(distinct.values());
    }

    /**
     * feed 第一页只有最新的十几条，所以随机读取 WordPress 风格的 {@code ?paged=N} 历史分页；
     * 不支持分页的 feed 会忽略该参数。随机页失败或为空时回退到第一页。
     */
    private List<ReadingArticleResponse> fetchRssFeed(String feedUrl) throws IOException, InterruptedException {
        int page = 1 + random.nextInt(rssMaxPages);
        if (page > 1) {
            String pagedUrl = feedUrl + (feedUrl.contains("?") ? "&" : "?") + "paged=" + page;
            try {
                List<ReadingArticleResponse> articles = parseRssFeed(feedUrl, getRssBody(pagedUrl));
                if (!articles.isEmpty()) {
                    return articles;
                }
            } catch (IOException ex) {
                logger.info("RSS page unavailable, falling back to first page. feed={}, page={}", feedUrl, page);
            }
        }
        return parseRssFeed(feedUrl, getRssBody(feedUrl));
    }

    private String getRssBody(String url) throws IOException, InterruptedException {
        return getBody(url, "application/rss+xml, application/xml, text/xml");
    }

    private List<ReadingArticleResponse> parseRssFeed(String feedUrl, String xml) throws IOException {
        Document document;
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            // feed 是不可信的外部 XML，禁用 DOCTYPE 以防 XXE
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            document = factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
        } catch (ParserConfigurationException | SAXException ex) {
            throw new IOException("Invalid RSS feed " + feedUrl, ex);
        }

        String sourceName = "";
        NodeList channels = document.getElementsByTagNameNS("*", "channel");
        if (channels.getLength() > 0) {
            sourceName = childText((Element) channels.item(0), "title");
            // WordPress 历史分页的频道标题带有 "Page N – " 前缀
            sourceName = FEED_PAGE_PREFIX.matcher(sourceName).replaceFirst("");
        }
        if (sourceName.isEmpty()) {
            sourceName = URI.create(feedUrl).getHost();
        }

        List<ReadingArticleResponse> articles = new ArrayList<>();
        NodeList items = document.getElementsByTagNameNS("*", "item");
        for (int i = 0; i < items.getLength(); i++) {
            Element item = (Element) items.item(i);
            String url = childText(item, "link");
            String body = childText(item, "encoded");
            if (body.isEmpty()) {
                body = childText(item, "description");
            }
            String fullText = htmlToText(body);
            String text = excerpt(fullText);
            if (url.isEmpty() || text.length() < MIN_TEXT_CHARS) {
                continue;
            }

            ReadingArticleResponse article = new ReadingArticleResponse();
            article.setTitle(normalizeWhitespace(htmlToText(childText(item, "title"))));
            article.setText(text);
            article.setAuthor(childText(item, "creator"));
            article.setSource(sourceName);
            article.setUrl(url);
            article.setLicense(RSS_RIGHTS_NOTICE);
            article.setLicenseUrl("");
            article.setEstimatedGrade(ReadingLevel.estimateGrade(text));
            article.setExcerpt(text.length() < fullText.length());
            articles.add(article);
        }
        return articles;
    }

    private String childText(Element parent, String localName) {
        for (Node child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element && localName.equals(child.getLocalName())) {
                return child.getTextContent().trim();
            }
        }
        return "";
    }

    private String htmlToText(String html) {
        String withBreaks = HTML_BLOCK_END.matcher(html).replaceAll("\n");
        String plain = HtmlUtils.htmlUnescape(stripTags(withBreaks)).replace('\u00a0', ' ');
        List<String> paragraphs = new ArrayList<>();
        for (String line : plain.split("\\R")) {
            String paragraph = normalizeWhitespace(line);
            if (!paragraph.isEmpty() && !FEED_FOOTER.matcher(paragraph).matches()) {
                paragraphs.add(paragraph);
            }
        }
        return String.join("\n\n", paragraphs);
    }

    private String formatAuthors(List<String> authors) {
        if (authors.isEmpty()) {
            return "";
        }
        if (authors.size() <= 3) {
            return String.join(", ", authors);
        }
        return authors.get(0) + " et al.";
    }

    private String firstText(JsonNode node) {
        if (node.isArray()) {
            return node.path(0).asText("");
        }
        return node.asText("");
    }

    private String stripTags(String value) {
        return HTML_TAG.matcher(value).replaceAll("");
    }

    private String normalizeWhitespace(String value) {
        return value.replaceAll("\\s+", " ").trim();
    }

    private String normalizeUrl(String url) {
        String trimmed = url.trim();
        return trimmed.startsWith("//") ? "https:" + trimmed : trimmed;
    }

    /** 去掉小节标题，并在参考资料等尾部小节处截断。 */
    private String cleanWikiExtract(String extract) {
        List<String> paragraphs = new ArrayList<>();
        for (String line : extract.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            java.util.regex.Matcher heading = HEADING.matcher(trimmed);
            if (heading.matches()) {
                if (TRAILING_SECTION.matcher(heading.group(1)).matches()) {
                    break;
                }
                continue;
            }
            paragraphs.add(normalizeWhitespace(trimmed));
        }
        return String.join("\n\n", paragraphs);
    }

    /** 按整句截取，保证不超过 maxChars。 */
    private String excerpt(String text) {
        if (text.length() <= maxChars) {
            return text;
        }
        StringBuilder builder = new StringBuilder();
        for (String paragraph : text.split("\n\n")) {
            String separator = builder.length() == 0 ? "" : "\n\n";
            for (String sentence : SENTENCE_BREAK.split(paragraph)) {
                if (builder.length() + separator.length() + sentence.length() > maxChars) {
                    return builder.toString();
                }
                builder.append(separator).append(sentence);
                separator = " ";
            }
        }
        return builder.toString();
    }

    private JsonNode getJson(String baseUrl, Map<String, String> params) throws IOException, InterruptedException {
        String query = params.entrySet().stream()
            .map(entry -> entry.getKey() + "=" + URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8))
            .collect(Collectors.joining("&"));
        return objectMapper.readTree(getBody(baseUrl + "?" + query, "application/json"));
    }

    private String getBody(String url, String accept) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(6))
            .header("User-Agent", USER_AGENT)
            .header("Accept", accept)
            .GET()
            .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("Unexpected status " + response.statusCode() + " from " + url);
        }
        return response.body();
    }
}
