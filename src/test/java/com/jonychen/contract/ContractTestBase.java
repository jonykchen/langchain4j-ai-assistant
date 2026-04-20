package com.jonychen.contract;

import com.jonychen.controller.ChatController;
import com.jonychen.model.ChatResponse;
import com.jonychen.service.AiService;
import io.restassured.module.mockmvc.RestAssuredMockMvc;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * 契约测试基类
 * Spring Cloud Contract 会自动生成测试类继承此类
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("contract-test")
public abstract class ContractTestBase {

    @MockBean
    protected AiService aiService;

    @BeforeEach
    void setUp() {
        // 配置 Mock 行为
        when(aiService.chat(anyString()))
                .thenReturn("这是测试回复");

        // 配置流式响应
        when(aiService.chatFlux(anyString()))
                .thenReturn(reactor.core.publisher.Flux.just("测", "试", "回", "复"));

        // 设置 RestAssuredMockMvc
        RestAssuredMockMvc.standaloneSetup(new ChatController(aiService));
    }
}
