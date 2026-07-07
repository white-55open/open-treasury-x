package io.github.open55.otx.common.exception.blockchain;

import io.github.open55.otx.common.exception.BizException;
import io.github.open55.otx.common.exception.BizErrorEnum;

import java.util.List;

/**
 * 所有 RPC 节点均失败时抛出的异常。
 * 携带链 ID 和失败的 URL 列表，便于排查节点可用性问题。
 */
public class Web3jRpcException extends BizException {

    private final String chainId;

    private final List<String> failedUrls;

    public Web3jRpcException(String chainId, List<String> failedUrls, Throwable cause) {
        super(cause, BizErrorEnum.LEDGER_CHAIN_NOT_CONFIGURED);
        this.chainId = chainId;
        this.failedUrls = failedUrls;
    }

    /**
     * 返回请求的链 ID
     *
     * @return 链 ID
     */
    public String getChainId() {
        return chainId;
    }

    /**
     * 返回所有失败的 RPC URL 列表
     *
     * @return 失败 URL 列表
     */
    public List<String> getFailedUrls() {
        return failedUrls;
    }

    @Override
    public String getMessage() {
        return "所有 RPC 节点均失败，chainId=" + chainId + ", failedUrls=" + failedUrls;
    }
}
