package io.github.open55.otx.infrastructure.blockchain;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * web3j 连接配置。
 * <p>
 * 从 application.yaml 的 web3j.* 读取，支持单链多 RPC 节点配置。
 * 当前仅支持单条链，后续可通过策略模式扩展多链支持。
 */
@Data
@ConfigurationProperties(prefix = "web3j")
public class Web3jProperties {

    /**
     * 以太坊链 ID，如 "11155111" 表示 Sepolia 测试网
     */
    private String chainId;

    /**
     * RPC 节点 URL 列表，按序故障切换
     */
    private List<String> rpcUrls = new ArrayList<>();

    /**
     * RPC 请求读取超时（毫秒），默认 5000
     */
    private long readTimeout = 5000;

    /**
     * 返回不可修改的 RPC URL 列表
     *
     * @return RPC URL 列表
     */
    public List<String> getRpcUrls() {
        return Collections.unmodifiableList(rpcUrls);
    }
}
