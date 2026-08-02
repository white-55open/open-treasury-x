package io.github.open55.otx.infrastructure.blockchain;

import io.github.open55.otx.common.exception.BizErrorEnum;
import io.github.open55.otx.common.exception.BizException;
import io.github.open55.otx.common.exception.blockchain.Web3jRpcException;
import io.github.open55.otx.domain.chain.port.BroadcastResult;
import io.github.open55.otx.domain.chain.port.SignedTx;
import io.github.open55.otx.domain.chain.port.TxBroadcastPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.response.EthGetTransactionCount;
import org.web3j.protocol.core.methods.response.EthSendTransaction;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Web3j 链上广播适配器，实现 TxBroadcastPort 出站端口。
 * <p>
 * 支持单链多 RPC 节点故障切换：按配置顺序依次尝试，网络异常或节点拒绝时自动切换到下一节点，
 * 全部失败时抛出 Web3jRpcException。Web3j 客户端从 web3jPool 中按 URL 获取共享实例。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class Web3jTxBroadcastAdapter implements TxBroadcastPort {

    private final Map<String, Web3j> web3jPool;

    private final Web3jProperties properties;

    /**
     * 广播已签名交易到链上。
     * <p>
     * 依次尝试各 RPC 节点提交 eth_sendRawTransaction，首个成功节点返回交易哈希；
     * 全部节点失败时抛出 Web3jRpcException。
     *
     * @param signedTx 已签名交易
     * @return 广播结果，包含链上交易哈希
     */
    @Override
    public BroadcastResult broadcast(SignedTx signedTx) {
        assertChainIdMatches(signedTx.getChainId());

        List<String> failedUrls = new ArrayList<>();
        for (String url : properties.getRpcUrls()) {
            Web3j web3j = web3jPool.get(url);
            if (web3j == null) {
                // RPC URL 在连接池中不存在，跳过该节点
                log.warn("RPC URL not found in pool, skipping: {}", url);
                failedUrls.add(url);
                continue;
            }
            try {
                EthSendTransaction response = web3j.ethSendRawTransaction(signedTx.getRawTransaction()).send();
                String txHash = response.getTransactionHash();
                if (response.hasError() || txHash == null || txHash.isBlank()) {
                    // 节点拒绝广播（如 nonce 冲突），记录失败并尝试下一个节点
                    log.warn("RPC node rejected raw transaction, url={}, error={}", url, response.getError());
                    failedUrls.add(url);
                    continue;
                }
                // broadcast 调用成功，记录交易哈希和使用的 RPC URL
                log.debug("broadcast succeeded, txHash={}, url={}", txHash, url);
                return new BroadcastResult(signedTx.getChainId(), txHash,
                        signedTx.getFromAddress(), signedTx.getToAddress());
            } catch (Exception e) {
                // 当前 RPC 节点不可用，尝试切换到下一个节点
                log.warn("RPC node unavailable, switching to next, url={}, error={}", url, e.getMessage());
                failedUrls.add(url);
            }
        }
        throw new Web3jRpcException(signedTx.getChainId(), failedUrls, null);
    }

    /**
     * 查询发送方地址在指定链上的当前 nonce（含待确认交易的 PENDING 计数）。
     * <p>
     * 依次尝试各 RPC 节点，首个成功节点返回 nonce；全部失败时抛出 Web3jRpcException。
     *
     * @param chainId     区块链 ID
     * @param fromAddress 发送方地址
     * @return 当前 nonce
     */
    @Override
    public BigInteger currentNonce(String chainId, String fromAddress) {
        assertChainIdMatches(chainId);

        List<String> failedUrls = new ArrayList<>();
        for (String url : properties.getRpcUrls()) {
            Web3j web3j = web3jPool.get(url);
            if (web3j == null) {
                // RPC URL 在连接池中不存在，跳过该节点
                log.warn("RPC URL not found in pool, skipping: {}", url);
                failedUrls.add(url);
                continue;
            }
            try {
                EthGetTransactionCount response = web3j.ethGetTransactionCount(
                        fromAddress, DefaultBlockParameterName.PENDING).send();
                // currentNonce 调用成功，记录 nonce 和使用的 RPC URL
                log.debug("currentNonce succeeded, nonce={}, url={}", response.getTransactionCount(), url);
                return response.getTransactionCount();
            } catch (Exception e) {
                // 当前 RPC 节点不可用，尝试切换到下一个节点
                log.warn("RPC node unavailable, switching to next, url={}, error={}", url, e.getMessage());
                failedUrls.add(url);
            }
        }
        throw new Web3jRpcException(chainId, failedUrls, null);
    }

    /**
     * 校验请求的链 ID 是否已配置，未配置时抛 LEDGER_CHAIN_NOT_CONFIGURED。
     *
     * @param chainId 请求的链 ID
     */
    private void assertChainIdMatches(String chainId) {
        if (!properties.getChainId().equals(chainId)) {
            throw BizException.get(BizErrorEnum.LEDGER_CHAIN_NOT_CONFIGURED);
        }
    }
}
