package io.github.open55.mockupstream.upstream;

import io.github.open55.mockupstream.config.SimulatorProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

/**
 * OTX REST API 客户端。
 * <p>
 * 基于 Spring RestClient 封装模拟器调用 OTX 的全部端点（充值入账、提现冻结/广播/
 * 确认结算/取消、账户查询）。每次调用打印"端点 + 响应体（Result JSON）"，形成
 * 可读的业务日志；OTX 业务错误统一 HTTP 200 + 业务码（Result.code），RestClient
 * 默认不抛异常，直接解析展示；连接失败（OTX 未启动）捕获后返回失败结果，
 * 由故事线决定继续或终止，保证演示驱动不因单点故障崩溃。
 */
@Slf4j
@Component
public class OtxClient {

    /**
     * 模拟器配置（含 OTX 基础地址）
     */
    private final SimulatorProperties properties;

    /**
     * RestClient 实例（baseUrl 指向 OTX）
     */
    private final RestClient restClient;

    /**
     * JSON 解析器，用于提取 Result 响应中的 code/message/data
     */
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 构造客户端：以配置的 OTX 基础地址创建 RestClient。
     * <p>
     * 不依赖 Spring 自动配置的 RestClient.Builder（Boot 4 未提供），直接静态工厂创建。
     *
     * @param properties 模拟器配置
     */
    public OtxClient(SimulatorProperties properties) {
        this.properties = properties;
        this.restClient = RestClient.builder().baseUrl(properties.getBaseUrl()).build();
    }

    /**
     * 充值入账：链上确认达标后通知 OTX 入账（POST /deposit）。
     *
     * @param body 充值请求体（uid/amount/bizNo/currency/chainId/chainTxHash）
     * @return 调用结果（含业务码与响应数据）
     */
    public ApiResult deposit(Map<String, Object> body) {
        return post("/deposit", body);
    }

    /**
     * 提现冻结：申请提现时锁定资金（POST /withdraw/freeze）。
     *
     * @param body 冻结请求体（uid/bizNo/amount/currency）
     * @return 调用结果（含业务码与响应数据）
     */
    public ApiResult freeze(Map<String, Object> body) {
        return post("/withdraw/freeze", body);
    }

    /**
     * 提现广播：向链上广播提现交易（POST /withdraw/broadcast）。
     *
     * @param body 广播请求体（uid/bizNo/amount/currency/chainId/toAddress）
     * @return 调用结果（含业务码与响应数据）
     */
    public ApiResult broadcast(Map<String, Object> body) {
        return post("/withdraw/broadcast", body);
    }

    /**
     * 提现确认结算：链上确认达标后完成扣款与凭证过账（POST /withdraw/{bizNo}/confirm-settle）。
     *
     * @param bizNo 提现业务号（幂等键）
     * @return 调用结果（含业务码与响应数据）
     */
    public ApiResult confirmSettle(String bizNo) {
        return post("/withdraw/" + bizNo + "/confirm-settle", null);
    }

    /**
     * 提现取消：解冻已冻结资金并置请求为已取消（POST /withdraw/{bizNo}/cancel）。
     *
     * @param bizNo 提现业务号（幂等键）
     * @return 调用结果（含业务码与响应数据）
     */
    public ApiResult cancel(String bizNo) {
        return post("/withdraw/" + bizNo + "/cancel", null);
    }

    /**
     * 创建用户账户：用户注册时调用（POST /accounts/create/{uid}）。
     * <p>
     * 真实上游场景中账户在注册时已创建，充值/提现故事线以开户为前置，
     * 避免 OTX 侧因账户缺失产生异常路径。
     *
     * @param uid 用户唯一标识
     * @return 调用结果（含业务码与响应数据）
     */
    public ApiResult createAccount(Long uid) {
        return post("/accounts/create/" + uid, null);
    }

    /**
     * 账户查询：按用户标识查询可用/冻结余额（GET /accounts/{uid}）。
     *
     * @param uid 用户唯一标识
     * @return 调用结果（含业务码与响应数据）
     */
    public ApiResult getAccount(Long uid) {
        return get("/accounts/" + uid);
    }

    /**
     * 执行 POST 请求并打印"端点 + 响应体"。
     * <p>
     * 连接失败（OTX 未启动/端口不通）捕获 ResourceAccessException 打印错误，
     * 返回 httpStatus=-1 的失败结果，由故事线统计失败步骤。
     *
     * @param path 端点路径（不含 baseUrl）
     * @param body 请求体 JSON（可为空，如 confirm-settle/cancel 无请求体）
     * @return 解析后的调用结果
     */
    private ApiResult post(String path, Map<String, Object> body) {
        String url = properties.getBaseUrl() + path;
        log.info("POST {}", url);
        try {
            RestClient.RequestBodySpec spec = restClient.post().uri(url);
            ResponseEntity<String> response;
            if (body == null) {
                // 无请求体端点（确认结算/取消），直接发送
                response = spec.retrieve()
                        .onStatus(HttpStatusCode::isError, (request, res) -> {
                        })
                        .toEntity(String.class);
            } else {
                // 有请求体端点，以 JSON 发送
                response = spec.contentType(MediaType.APPLICATION_JSON).body(body)
                        .retrieve()
                        .onStatus(HttpStatusCode::isError, (request, res) -> {
                        })
                        .toEntity(String.class);
            }
            return parseAndPrint(response);
        } catch (ResourceAccessException e) {
            // OTX 未启动或网络不通：打印错误，返回失败结果让故事线继续
            log.error("连接 OTX 失败（请确认 OTX 已在 {} 启动）：{}", properties.getBaseUrl(), e.getMessage());
            return ApiResult.connectionFailure(e.getMessage());
        } catch (RestClientException e) {
            // 其它客户端异常（如响应解析失败）
            log.error("调用 OTX 失败：{}", e.getMessage());
            return ApiResult.connectionFailure(e.getMessage());
        }
    }

    /**
     * 执行 GET 请求并打印"端点 + 响应体"。
     *
     * @param path 端点路径（不含 baseUrl）
     * @return 解析后的调用结果
     */
    private ApiResult get(String path) {
        String url = properties.getBaseUrl() + path;
        log.info("GET {}", url);
        try {
            ResponseEntity<String> response = restClient.get().uri(url)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, res) -> {
                    })
                    .toEntity(String.class);
            return parseAndPrint(response);
        } catch (ResourceAccessException e) {
            log.error("连接 OTX 失败（请确认 OTX 已在 {} 启动）：{}", properties.getBaseUrl(), e.getMessage());
            return ApiResult.connectionFailure(e.getMessage());
        } catch (RestClientException e) {
            log.error("调用 OTX 失败：{}", e.getMessage());
            return ApiResult.connectionFailure(e.getMessage());
        }
    }

    /**
     * 解析 Result 响应并打印"→ HTTP 状态 + 响应体"。
     *
     * @param response HTTP 响应（实体为响应体原始字符串）
     * @return 解析后的调用结果（code/message/data/rawBody）
     */
    private ApiResult parseAndPrint(ResponseEntity<String> response) {
        String rawBody = response.getBody() == null ? "" : response.getBody();
        log.info("→ HTTP {} {}", response.getStatusCode().value(), rawBody);
        try {
            JsonNode root = objectMapper.readTree(rawBody);
            return new ApiResult(response.getStatusCode().value(),
                    root.path("code").asText(""),
                    root.path("message").asText(""),
                    root.path("data"),
                    rawBody);
        } catch (Exception e) {
            // 响应非 JSON（如网关错误页），保留原始响应体
            log.warn("响应体解析失败（非标准 Result JSON）：{}", e.getMessage());
            return new ApiResult(response.getStatusCode().value(), "", "", null, rawBody);
        }
    }
}
