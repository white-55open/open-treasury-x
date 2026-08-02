package io.github.open55.otx.infrastructure.blockchain;

import io.github.open55.otx.common.exception.BizErrorEnum;
import io.github.open55.otx.common.exception.BizException;
import io.github.open55.otx.common.exception.blockchain.Web3jRpcException;
import io.github.open55.otx.domain.ledger.port.ChainQueryPort;
import io.github.open55.otx.domain.ledger.port.ChainTxReceipt;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.methods.response.EthBlockNumber;
import org.web3j.protocol.core.methods.response.EthGetTransactionReceipt;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Web3j 链上查询适配器，实现 ChainQueryPort 出站端口。
 * <p>
 * 支持单链多 RPC 节点故障切换：按配置顺序依次尝试，网络异常时自动切换到下一节点，
 * 全部失败时抛出 Web3jRpcException。Web3j 客户端从 web3jPool 中按 URL 获取共享实例。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class Web3jChainQueryAdapter implements ChainQueryPort {

    private final Map<String, Web3j> web3jPool;

    private final Web3jProperties properties;

    @Override
    public Optional<ChainTxReceipt> queryTxReceipt(String chainId, String txHash) {
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
                EthGetTransactionReceipt response = web3j.ethGetTransactionReceipt(txHash).send();
                Optional<org.web3j.protocol.core.methods.response.TransactionReceipt> receiptOpt = response.getTransactionReceipt();
                if (receiptOpt.isEmpty()) {
                    return Optional.empty();
                }
                org.web3j.protocol.core.methods.response.TransactionReceipt receipt = receiptOpt.get();
                ChainTxReceipt result = new ChainTxReceipt(
                        chainId,
                        receipt.getTransactionHash(),
                        receipt.getBlockNumber(),
                        receipt.getStatus(),
                        0,
                        receipt.getFrom(),
                        receipt.getTo(),
                        receipt.getCumulativeGasUsed()
                );
                // queryTxReceipt 调用成功，记录交易哈希和使用的 RPC URL
                log.debug("queryTxReceipt succeeded, txHash={}, url={}", txHash, url);
                return Optional.of(result);
            } catch (Exception e) {
                // 当前 RPC 节点不可用，尝试切换到下一个节点
                log.warn("RPC node unavailable, switching to next, url={}, error={}", url, e.getMessage());
                failedUrls.add(url);
            }
        }
        throw new Web3jRpcException(chainId, failedUrls, null);
    }

    @Override
    public Long currentBlockNumber(String chainId) {
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
                EthBlockNumber blockNumber = web3j.ethBlockNumber().send();
                // currentBlockNumber 调用成功，记录当前块高和使用的 RPC URL
                log.debug("currentBlockNumber succeeded, blockNumber={}, url={}", blockNumber.getBlockNumber(), url);
                return blockNumber.getBlockNumber().longValue();
            } catch (Exception e) {
                // 当前 RPC 节点不可用，尝试切换到下一个节点
                log.warn("RPC node unavailable, switching to next, url={}, error={}", url, e.getMessage());
                failedUrls.add(url);
            }
        }
        throw new Web3jRpcException(chainId, failedUrls, null);
    }

    @Override
    public boolean isConfirmed(String chainId, String txHash, int requiredConfirmations) {
        Optional<ChainTxReceipt> receiptOpt = queryTxReceipt(chainId, txHash);
        if (receiptOpt.isEmpty()) {
            return false;
        }
        ChainTxReceipt receipt = receiptOpt.get();
        if (receipt.getBlockNumber() == null) {
            return false;
        }
        // 链上交易失败（回执状态非 0x1）视为未确认：已确认的失败交易（revert）同样打包进块且确认数达标，
        // 若不校验状态将允许伪造充值入账（与提现结算路径的 isSuccessReceipt 校验保持一致）
        if (!"0x1".equals(receipt.getStatus())) {
            return false;
        }
        long currentBlock = currentBlockNumber(chainId);
        long txBlock = receipt.getBlockNumber().longValue();
        long confirmations = currentBlock - txBlock + 1;
        return confirmations >= requiredConfirmations;
    }

    private void assertChainIdMatches(String chainId) {
        if (!properties.getChainId().equals(chainId)) {
            throw BizException.get(BizErrorEnum.LEDGER_CHAIN_NOT_CONFIGURED);
        }
    }
}
