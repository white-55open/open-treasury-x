package io.github.open55.mockupstream;

import io.github.open55.mockupstream.config.SimulatorProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 模拟上游（mock-upstream）启动类。
 * <p>
 * 定位：OTX 外部调用方模拟器——模拟"链上事件源 + 业务系统调用方"两个角色，
 * 仅通过 HTTP 调用 OTX REST API（默认 http://localhost:8003）演绎完整业务故事线
 * （充值确认达标入账、提现冻结→广播→确认结算、提现冻结→取消解冻）。
 * 本应用为独立目录，不加入 OTX 主工程 Maven reactor，不 import 任何 otx-* 模块类。
 * <p>
 * {@link EnableScheduling} 开启定时调度：驱动模拟链区块推进（@Scheduled 见
 * BlockchainSimulator）；{@link EnableConfigurationProperties} 注册 otx 配置段。
 */
@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(SimulatorProperties.class)
public class MockUpstreamApplication {

    /**
     * 应用入口：启动 Spring Boot 上下文，随后由 DemoRunner（ApplicationRunner）
     * 自动顺序演绎充值 → 提现结算 → 提现取消三条故事线。
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(MockUpstreamApplication.class, args);
    }
}
