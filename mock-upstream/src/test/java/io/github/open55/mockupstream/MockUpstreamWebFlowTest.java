package io.github.open55.mockupstream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 个人中心 Web 流程冒烟测试。
 * <p>
 * 覆盖范围：创建用户/切换用户（session 视角切换）、非法金额与未选中用户
 * 的错误提示（302 + flash 消息而非 500）、个人中心首页渲染用户上下文。
 * <p>
 * 使用 JDK HttpClient + CookieManager 维持会话；OTX 未启动时业务动作
 * 的连接失败被容错（页面照常渲染），不影响端点行为验证。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MockUpstreamWebFlowTest {

    /**
     * 随机端口：由测试框架注入
     */
    @LocalServerPort
    private int port;

    /**
     * 带 Cookie 管理的 HTTP 客户端（维持会话中的当前用户）
     */
    private HttpClient httpClient;

    /**
     * 应用根地址
     */
    private String baseUrl;

    /**
     * 每次测试前重建带会话管理的客户端。
     */
    @BeforeEach
    void setUp() {
        CookieHandler cookieHandler = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        httpClient = HttpClient.newBuilder().cookieHandler(cookieHandler).build();
        baseUrl = "http://localhost:" + port;
    }

    /**
     * 创建用户后首页渲染当前用户上下文（302 重定向回主页）。
     */
    @Test
    void createUser_redirectsHome_andRendersCurrentUser() throws Exception {
        HttpRequest create = HttpRequest.newBuilder(URI.create(baseUrl + "/users"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString("uid=100001"))
                .build();
        HttpResponse<String> createResponse = httpClient.send(create, HttpResponse.BodyHandlers.ofString());

        assertThat(createResponse.statusCode()).isEqualTo(302);
        // 首次请求无 cookie 时 servlet 容器 URL 重写会附加 jsessionid，断言重定向回根路径
        String location = createResponse.headers().firstValue("Location").orElse("");
        assertThat(location).matches("^http://localhost:\\d+/\\S*$");

        // 携带会话再访主页：渲染当前用户
        HttpResponse<String> home = httpClient.send(
                HttpRequest.newBuilder(URI.create(baseUrl + "/")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(home.statusCode()).isEqualTo(200);
        assertThat(home.body()).contains("100001");
    }

    /**
     * 切换用户：会话视角切换后主页渲染目标用户。
     */
    @Test
    void selectUser_switchesSessionContext() throws Exception {
        postForm("/users", "uid=100001");
        postForm("/users", "uid=100002");
        postForm("/users/100001/select", null);

        HttpResponse<String> home = httpClient.send(
                HttpRequest.newBuilder(URI.create(baseUrl + "/")).GET().build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(home.body()).contains("当前用户");
        assertThat(home.body()).contains("100001");
    }

    /**
     * 未选中用户时发起充值：302 回主页并展示错误提示（而非 500）。
     */
    @Test
    void depositWithoutUser_showsErrorNot500() throws Exception {
        HttpResponse<String> response = postForm("/deposits", "amount=100");

        assertThat(response.statusCode()).isEqualTo(302);

        HttpResponse<String> home = httpClient.send(
                HttpRequest.newBuilder(URI.create(baseUrl + "/")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(home.statusCode()).isEqualTo(200);
        assertThat(home.body()).contains("请先创建或选择用户");
    }

    /**
     * 非法金额（负数）发起充值：302 回主页并展示金额错误提示（而非 500）。
     */
    @Test
    void depositWithNegativeAmount_showsErrorNot500() throws Exception {
        postForm("/users", "uid=100001");

        HttpResponse<String> response = postForm("/deposits", "amount=-5");

        assertThat(response.statusCode()).isEqualTo(302);
        HttpResponse<String> home = httpClient.send(
                HttpRequest.newBuilder(URI.create(baseUrl + "/")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(home.body()).contains("充值金额必须为正数");
    }

    /**
     * 一键处理无挂起事项：302 回主页且页面正常渲染（零动作）。
     */
    @Test
    void processWithoutPending_redirectsHome() throws Exception {
        postForm("/users", "uid=100001");

        HttpResponse<String> response = postForm("/internal/process", null);

        assertThat(response.statusCode()).isEqualTo(302);
        HttpResponse<String> home = httpClient.send(
                HttpRequest.newBuilder(URI.create(baseUrl + "/")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(home.statusCode()).isEqualTo(200);
    }

    /**
     * 提交表单并跟随重定向验证（POST application/x-www-form-urlencoded）。
     *
     * @param path 端点路径
     * @param body 表单体（可为 null）
     * @return 表单提交响应
     */
    private HttpResponse<String> postForm(String path, String body) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + path));
        if (body != null) {
            builder.header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body));
        } else {
            builder.POST(HttpRequest.BodyPublishers.noBody());
        }
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
}
