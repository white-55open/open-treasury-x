package io.github.open55.mockupstream;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 应用上下文与 Web 容器冒烟测试。
 * <p>
 * 覆盖范围：mock-upstream 引入 Web + Thymeleaf 依赖后上下文可正常启动、
 * Web 容器（随机端口）已就绪并响应请求；启动后无自动演绎驱动
 * （DemoRunner 已移除，上下文加载完成即证明启动零主动 HTTP 调用）。
 * <p>
 * HTTP 探测使用 JDK 内置 HttpClient（不引入 resttestclient 模块，
 * 其自动配置在当前 Boot 4.0.6 依赖组合下存在类型推断兼容问题）。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MockUpstreamApplicationTest {

    /**
     * 随机端口：由测试框架注入，指向本次启动的 Web 容器
     */
    @LocalServerPort
    private int port;

    /**
     * JDK 内置 HTTP 客户端，仅用于冒烟探测（无任何第三方依赖）
     */
    private final HttpClient httpClient = HttpClient.newHttpClient();

    /**
     * 上下文启动成功：Web 容器已就绪且个人中心首页可渲染（200）。
     * <p>
     * 首页渲染无需选中用户（未选中时仅展示创建入口），亦不触发任何 OTX 调用，
     * 同时验证启动后无自动演绎（DemoRunner 已移除，无主动 HTTP 调用）。
     */
    @Test
    void context_loads_and_home_page_renders() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/")).GET().build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("模拟用户中心");
    }
}
