package io.github.open55.mockupstream.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 模拟器配置属性（otx 段，由 application.yaml 或环境变量注入）。
 * <p>
 * 集中管理模拟器与 OTX 交互的全部可调参数：OTX 服务地址、
 * 链上确认阈值与区块推进间隔，支撑「一键处理」的确认数演化节奏调节。
 */
@Data
@ConfigurationProperties(prefix = "otx")
public class SimulatorProperties {

    /**
     * OTX 服务基础地址（REST API 根路径），默认本地 dev 端口 8003
     */
    private String baseUrl = "http://localhost:8003";

    /**
     * 充值入账所需的链上安全确认数，模拟链确认数达到该值才触发 POST /deposit
     */
    private int requiredConfirmations = 12;

    /**
     * 模拟链区块推进间隔（毫秒），每间隔推进一个区块、各交易确认数 +1
     */
    private long blockIntervalMs = 2000;
}
