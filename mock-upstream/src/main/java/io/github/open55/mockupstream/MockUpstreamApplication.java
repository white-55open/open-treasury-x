package io.github.open55.mockupstream;

import io.github.open55.mockupstream.config.SimulatorProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * 模拟上游（mock-upstream）启动类。
 * <p>
 * 定位：OTX 外部调用方模拟器——提供"模拟用户个人中心"页面（:8004），
 * 用户手动触发业务动作（创建用户/充值/提现/取消），内部处理（确认数推进、
 * 入账、广播、结算、解冻）由「一键处理」统一驱动；仅通过 HTTP 调用
 * OTX REST API（默认 http://localhost:8003），不 import 任何 otx-* 模块类。
 * 本应用为独立目录，不加入 OTX 主工程 Maven reactor。
 * <p>
 * 启动后不自动演绎任何故事线（零主动 HTTP 调用），全部业务由页面操作触发。
 */
@SpringBootApplication
@EnableConfigurationProperties(SimulatorProperties.class)
public class MockUpstreamApplication {

    /**
     * 应用入口：启动 Spring Boot 上下文，随后由用户在个人中心页面手动操作。
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(MockUpstreamApplication.class, args);
    }
}
