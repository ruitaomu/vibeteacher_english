package com.vibeteacher.api;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.vibeteacher.api.dto.ReadingArticleResponse;
import com.vibeteacher.security.TokenService;
import com.vibeteacher.service.ReadingArticleService;
import com.vibeteacher.service.ReadingAssistantService;

class ReadingAssistantControllerTest {
    private ReadingArticleService articleService;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        articleService = mock(ReadingArticleService.class);
        TokenService tokens = mock(TokenService.class);
        when(tokens.validateToken("test-session")).thenReturn(true);
        mvc = MockMvcBuilders.standaloneSetup(new ReadingAssistantController(
            mock(ReadingAssistantService.class), articleService, tokens))
            .setMessageConverters(new StringHttpMessageConverter(StandardCharsets.UTF_8),
                new MappingJackson2HttpMessageConverter())
            .build();
    }

    @Test
    void passesExcludedUrlToServiceAndDisablesResponseCaching() throws Exception {
        String currentUrl = "https://example.org/article?id=12&level=1";
        String earlierUrl = "https://example.org/a,b";
        ReadingArticleResponse article = new ReadingArticleResponse();
        article.setUrl("https://example.org/next");
        when(articleService.fetchArticle("中学7年级", List.of(currentUrl, earlierUrl))).thenReturn(article);

        mvc.perform(get("/api/reading/article")
                .header("Authorization", "Bearer test-session")
                .param("level", "中学7年级")
                .param("excludeUrl", currentUrl, earlierUrl))
            .andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$.url").value("https://example.org/next"));

        verify(articleService).fetchArticle("中学7年级", List.of(currentUrl, earlierUrl));
    }

    @Test
    void keepsRequestsWithoutExcludedUrlCompatible() throws Exception {
        when(articleService.fetchArticle("中学7年级", List.of())).thenReturn(new ReadingArticleResponse());

        mvc.perform(get("/api/reading/article")
                .header("Authorization", "Bearer test-session")
                .param("level", "中学7年级"))
            .andExpect(status().isOk());

        verify(articleService).fetchArticle("中学7年级", List.of());
    }

    @Test
    void reportsWhenThereIsNoDifferentArticle() throws Exception {
        String message = "暂时没有其他可用文章，请稍后重试或切换学习级别。";
        when(articleService.fetchArticle("中学7年级", List.of("https://example.org/current")))
            .thenThrow(new IllegalStateException(message));

        mvc.perform(get("/api/reading/article")
                .header("Authorization", "Bearer test-session")
                .param("level", "中学7年级")
                .param("excludeUrl", "https://example.org/current"))
            .andExpect(status().isBadGateway())
            .andExpect(content().string(message));
    }

    @Test
    void stillRequiresAuthorization() throws Exception {
        mvc.perform(get("/api/reading/article").param("level", "中学7年级"))
            .andExpect(status().isUnauthorized());
        verifyNoInteractions(articleService);
    }
}
