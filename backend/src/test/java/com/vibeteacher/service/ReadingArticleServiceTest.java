package com.vibeteacher.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import java.util.function.Supplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpServer;
import com.vibeteacher.api.dto.ReadingArticleResponse;

class ReadingArticleServiceTest {
    private static final String EASY_SENTENCE = "The cat sat on the mat. ";
    private static final String HARD_SENTENCE =
        "Comprehensive epidemiological investigations demonstrated statistically significant "
            + "associations between environmental contamination and cardiovascular complications. ";
    private static final String RIGHTS =
        "\"rightsinfo\":{\"url\":\"//creativecommons.org/licenses/by-sa/4.0/\",\"text\":\"CC BY-SA 4.0\"}";

    private HttpServer server;
    private ReadingArticleService service;
    private Function<String, String> simpleWikipedia;
    private Function<String, String> wikinews;
    private Function<String, String> plos;
    private Function<String, String> rss;
    private final List<String> rssQueries = new CopyOnWriteArrayList<>();

    @BeforeEach
    void setUp() throws IOException {
        simpleWikipedia = query -> null;
        wikinews = query -> null;
        plos = query -> null;
        rss = query -> null;

        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        route("/simple", () -> simpleWikipedia);
        route("/wikinews", () -> wikinews);
        route("/plos", () -> plos);
        route("/rss", () -> rss);
        server.start();

        service = newService(1);
    }

    private ReadingArticleService newService(int rssMaxPages) {
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        return new ReadingArticleService(base + "/simple", base + "/wikinews", base + "/plos",
            new String[] {base + "/rss?feed=1"}, rssMaxPages, 500);
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void picksOpenLicensedArticleMatchingLowerGrade() {
        String extract = "== History ==\\n" + EASY_SENTENCE.repeat(40).trim()
            + "\\n\\n== References ==\\nSmith, J. (2001). A very long bibliography entry.";
        simpleWikipedia = query -> query.contains("generator=random")
            ? "{\"query\":{\"pages\":[{\"pageid\":1,\"title\":\"Stub\",\"length\":200},"
                + "{\"pageid\":2,\"title\":\"Cat\",\"length\":9000}]}}"
            : wikiPage(query, extract, RIGHTS);

        ReadingArticleResponse article = service.fetchArticle("中学7年级");

        assertEquals("Cat", article.getTitle());
        assertEquals("Simple English Wikipedia", article.getSource());
        assertEquals("https://example.org/wiki/2", article.getUrl());
        assertEquals("CC BY-SA 4.0", article.getLicense());
        assertEquals("https://creativecommons.org/licenses/by-sa/4.0/", article.getLicenseUrl());
        assertTrue(article.isLevelMatched());
        assertTrue(article.isExcerpt());
        assertTrue(article.getText().length() > 400 && article.getText().length() <= 500);
        assertTrue(article.getText().endsWith("mat."), "excerpt must end on a full sentence");
        assertFalse(article.getText().contains("=="));
    }

    @Test
    void reportsMismatchWhenOnlyHarderTextIsAvailable() {
        simpleWikipedia = query -> query.contains("generator=random")
            ? "{\"query\":{\"pages\":[{\"pageid\":2,\"title\":\"Hard\",\"length\":9000}]}}"
            : wikiPage(query, HARD_SENTENCE.repeat(3).trim(), RIGHTS);

        ReadingArticleResponse article = service.fetchArticle("中学7年级");

        assertFalse(article.isLevelMatched());
        assertTrue(article.getEstimatedGrade() > 12);
    }

    @Test
    void skipsWikiPagesThatCannotBeAttributed() {
        simpleWikipedia = query -> query.contains("generator=random")
            ? "{\"query\":{\"pages\":[{\"pageid\":2,\"title\":\"Cat\",\"length\":9000}]}}"
            : wikiPage(query, EASY_SENTENCE.repeat(40).trim(), "\"rightsinfo\":{}");

        assertThrows(IllegalStateException.class, () -> service.fetchArticle("中学7年级"));
    }

    @Test
    void usesJournalAbstractWhenOtherSourceIsDown() {
        plos = query -> "{\"response\":{\"docs\":["
            + "{\"id\":\"10.1371/journal.pone.0000001\",\"title_display\":\"No abstract\",\"abstract\":[\"\"]},"
            + "{\"id\":\"10.1371/journal.pone.0000002\",\"title_display\":\"Risk in <i>Homo sapiens</i>\","
            + "\"journal\":\"PLOS ONE\",\"author_display\":[\"A One\",\"B Two\",\"C Three\",\"D Four\"],"
            + "\"abstract\":[\"\\n  " + HARD_SENTENCE.repeat(3) + "\"]}]}}";

        ReadingArticleResponse article = service.fetchArticle("TOEFL写作");

        assertEquals("Risk in Homo sapiens", article.getTitle());
        assertEquals("PLOS ONE", article.getSource());
        assertEquals("A One et al.", article.getAuthor());
        assertEquals("https://doi.org/10.1371/journal.pone.0000002", article.getUrl());
        assertEquals("CC BY 4.0", article.getLicense());
        assertEquals(HARD_SENTENCE.repeat(3).trim(), article.getText());
    }

    @Test
    void readsArticleTextFromRssFeedItems() {
        String html = "<p>" + EASY_SENTENCE.repeat(10).trim() + "</p><p>Tom &amp; Ann like the cat. "
            + EASY_SENTENCE.repeat(6).trim() + "</p><p>The post Cat news appeared first on Easy News.</p>";
        rss = query -> "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
            + "<rss version=\"2.0\" xmlns:content=\"http://purl.org/rss/1.0/modules/content/\""
            + " xmlns:dc=\"http://purl.org/dc/elements/1.1/\"><channel><title>Easy News</title>"
            + "<item><title>Too short</title><link>https://example.org/short</link>"
            + "<description>Just a teaser.</description></item>"
            + "<item><title>Cat news &#8211; level 1</title><link>https://example.org/cat</link>"
            + "<dc:creator>Ann</dc:creator><description>Just a teaser.</description>"
            + "<content:encoded><![CDATA[" + html + "]]></content:encoded></item>"
            + "</channel></rss>";

        ReadingArticleResponse article = service.fetchArticle("中学7年级");

        assertEquals("Cat news – level 1", article.getTitle());
        assertEquals("Easy News", article.getSource());
        assertEquals("Ann", article.getAuthor());
        assertEquals("https://example.org/cat", article.getUrl());
        assertTrue(article.isLevelMatched());
        assertFalse(article.isExcerpt());
        assertEquals(EASY_SENTENCE.repeat(10).trim() + "\n\nTom & Ann like the cat. "
            + EASY_SENTENCE.repeat(6).trim(), article.getText());
    }

    @Test
    void rejectsUnknownLevelAndReportsUnavailableSources() {
        assertThrows(IllegalArgumentException.class, () -> service.fetchArticle("小学1年级"));
        assertThrows(IllegalStateException.class, () -> service.fetchArticle("GRE写作"));
    }

    @Test
    void excludesCurrentArticleEvenWhenItIsTheOnlyDifficultyMatch() {
        rss = query -> feed(
            feedItem("Current", "https://example.org/current", EASY_SENTENCE.repeat(20)),
            feedItem("Another", "https://example.org/another", HARD_SENTENCE.repeat(3)));

        ReadingArticleResponse current = service.fetchArticle("中学7年级");
        ReadingArticleResponse next = service.fetchArticle("中学7年级", List.of(current.getUrl()));

        assertEquals("https://example.org/current", current.getUrl());
        assertTrue(current.isLevelMatched());
        assertEquals("https://example.org/another", next.getUrl());
        assertFalse(next.isLevelMatched());
    }

    @Test
    void visitsEveryArticleBeforeRepeatingWhenNoneMatchTheLevel() {
        rss = query -> feed(easyItems(4));

        assertVisitsAllDistinct("GRE写作", 4, false);
    }

    @Test
    void visitsEveryMatchingArticleBeforeRepeating() {
        rss = query -> feed(easyItems(5));

        assertVisitsAllDistinct("中学7年级", 5, true);
    }

    @Test
    void fallsBackToEarlierArticlesOnceEveryArticleHasBeenSeen() {
        rss = query -> feed(easyItems(2));

        ReadingArticleResponse next = service.fetchArticle("中学7年级",
            List.of("https://example.org/1", "https://example.org/0"));

        assertEquals("https://example.org/0", next.getUrl());
    }

    @Test
    void picksAmongSeveralNearestArticlesInsteadOfAlwaysTheSameOne() {
        rss = query -> feed(easyItems(5));

        Set<String> urls = new HashSet<>();
        for (int i = 0; i < 40; i++) {
            urls.add(service.fetchArticle("GRE写作").getUrl());
        }

        assertTrue(urls.size() > 1, "a level mismatch must not always return the single nearest article");
    }

    @Test
    void reportsWhenOnlyTheCurrentArticleIsAvailable() {
        rss = query -> feed(feedItem("Only", "https://example.org/only", EASY_SENTENCE.repeat(20)));

        ReadingArticleResponse current = service.fetchArticle("中学7年级");
        IllegalStateException error = assertThrows(IllegalStateException.class,
            () -> service.fetchArticle("中学7年级", List.of(current.getUrl())));

        assertEquals("暂时没有其他可用文章，请稍后重试或切换学习级别。", error.getMessage());
        assertEquals(current.getUrl(), service.fetchArticle("中学7年级", List.of()).getUrl());
    }

    @Test
    void mergesFeedItemsThatRepeatTheSameText() {
        String text = EASY_SENTENCE.repeat(20);
        rss = query -> feed(
            feedItem("Ad", "https://example.org/ad-level-1", text, false),
            feedItem("Ad", "https://example.org/ad-level-2", text, false));

        assertThrows(IllegalStateException.class,
            () -> service.fetchArticle("中学7年级", List.of("https://example.org/ad-level-1")));
    }

    @Test
    void readsRandomOlderFeedPagesWithinTheConfiguredLimit() {
        Set<String> pages = ConcurrentHashMap.newKeySet();
        rss = query -> {
            String page = query.contains("paged=") ? query.replaceAll(".*paged=(\\d+).*", "$1") : "1";
            pages.add(page);
            String title = page.equals("1") ? "Test News" : "Page " + page + " – Test News";
            return "<rss version=\"2.0\"><channel><title>" + title + "</title>"
                + feedItem("Page " + page, "https://example.org/page-" + page, EASY_SENTENCE.repeat(20))
                + "</channel></rss>";
        };
        ReadingArticleService paged = newService(4);

        for (int i = 0; i < 40; i++) {
            ReadingArticleResponse article = paged.fetchArticle("中学7年级");
            assertTrue(article.getUrl().matches("https://example\\.org/page-[1-4]"));
            assertEquals("Test News", article.getSource());
        }

        assertTrue(pages.stream().anyMatch(page -> !page.equals("1")), "older pages must be requested");
        assertTrue(pages.stream().allMatch(page -> Integer.parseInt(page) <= 4));
        assertTrue(rssQueries.stream().allMatch(query -> query.startsWith("feed=1")),
            "the paging parameter must be appended to the configured feed query");
    }

    @Test
    void fallsBackToFirstFeedPageWhenOlderPageIsUnavailable() {
        rss = query -> query.contains("paged=")
            ? null
            : feed(feedItem("Latest", "https://example.org/latest", EASY_SENTENCE.repeat(20)));
        ReadingArticleService paged = newService(4);

        for (int i = 0; i < 10; i++) {
            assertEquals("https://example.org/latest", paged.fetchArticle("中学7年级").getUrl());
        }
    }

    private void assertVisitsAllDistinct(String level, int count, boolean expectMatched) {
        List<String> history = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            ReadingArticleResponse article = service.fetchArticle(level, history);
            assertFalse(history.contains(article.getUrl()), "returned an article that was already shown");
            assertEquals(expectMatched, article.isLevelMatched());
            history.add(0, article.getUrl());
        }
    }

    private String[] easyItems(int count) {
        String[] items = new String[count];
        for (int i = 0; i < count; i++) {
            items[i] = feedItem("Story " + i, "https://example.org/" + i, EASY_SENTENCE.repeat(20));
        }
        return items;
    }

    private String feed(String... items) {
        return "<rss version=\"2.0\"><channel><title>Test News</title>"
            + String.join("", items) + "</channel></rss>";
    }

    private String feedItem(String title, String url, String text) {
        return feedItem(title, url, text, true);
    }

    /** 默认在正文里带上标题，让各条正文互不相同，避免被按正文去重。 */
    private String feedItem(String title, String url, String text, boolean distinctText) {
        String body = distinctText ? "This is " + title + ". " + text : text;
        return "<item><title>" + title + "</title><link>" + url
            + "</link><description>" + body + "</description></item>";
    }

    private String wikiPage(String query, String extract, String rights) {
        String pageId = query.replaceAll(".*pageids=(\\d+).*", "$1");
        return "{\"query\":{" + rights + ",\"pages\":[{\"pageid\":" + pageId + ",\"title\":\"Cat\","
            + "\"fullurl\":\"https://example.org/wiki/" + pageId + "\",\"extract\":\"" + extract + "\"}]}}";
    }

    private void route(String path, Supplier<Function<String, String>> handler) {
        server.createContext(path, exchange -> {
            String query = URLDecoder.decode(exchange.getRequestURI().getRawQuery(), StandardCharsets.UTF_8);
            if (path.equals("/rss")) {
                rssQueries.add(query);
            }
            String body = handler.get().apply(query);
            byte[] bytes = (body == null ? "unavailable" : body).getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(body == null ? 503 : 200, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
    }
}
