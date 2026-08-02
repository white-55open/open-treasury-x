package io.github.open55.otx.domain.chain.port;

import lombok.Getter;

import java.math.BigInteger;

/**
 * 签名请求值对象：一次链上交易的签名入参。
 * <p>
 * 由应用层编排组装后传递给 {@link SignerPort}，包含发送方、接收方、
 * 转账金额（Wei）等必填信息，以及代币地址、nonce、gasLimit、data 等可选信息。
 * 构造时执行自校验，确保必填字段非空且金额为正，保证传入签名设施的数据始终有效。
 */
@Getter
public class SignRequest {

    /**
     * 区块链 ID
     * <p>如 "11155111"（Sepolia 测试网）、"1"（以太坊主网），用于签名适配器选择正确的链参数</p>
     */
    private final String chainId;

    /**
     * 发送方地址
     * <p>平台热钱包地址，签名结果将以此地址为交易 from</p>
     */
    private final String fromAddress;

    /**
     * 接收方地址
     * <p>用户提现的目标地址，交易将转账至此地址</p>
     */
    private final String toAddress;

    /**
     * 转账金额（Wei）
     * <p>必须为正数，以太坊最小计价单位 1 ETH = 10^18 Wei</p>
     */
    private final BigInteger amountWei;

    /**
     * 代币合约地址
     * <p>ERC-20 代币转账时必填；原生币（ETH 等）转账时为空</p>
     */
    private final String tokenAddress;

    /**
     * 交易 nonce
     * <p>可选；为空时由签名适配器自行获取链上当前 nonce</p>
     */
    private final BigInteger nonce;

    /**
     * 交易 gas 上限
     * <p>可选；为空时由签名适配器使用默认值或自动估算</p>
     */
    private final BigInteger gasLimit;

    /**
     * 合约调用 data
     * <p>可选；代币转账或合约调用时携带的十六进制 calldata</p>
     */
    private final String data;

    /**
     * 构造 SignRequest，执行自校验确保业务不变量。
     *
     * @param chainId      区块链 ID，不能为空
     * @param fromAddress  发送方地址，不能为空
     * @param toAddress    接收方地址，不能为空
     * @param amountWei    转账金额（Wei），不能为空且必须为正
     * @param tokenAddress 代币合约地址（可选）
     * @param nonce        交易 nonce（可选）
     * @param gasLimit     交易 gas 上限（可选）
     * @param data         合约调用 data（可选）
     * @throws IllegalArgumentException 当 chainId/fromAddress/toAddress/amountWei 为空或空白、amountWei 非正数时抛出
     */
    public SignRequest(String chainId, String fromAddress, String toAddress,
                       BigInteger amountWei, String tokenAddress,
                       BigInteger nonce, BigInteger gasLimit, String data) {
        // 校验区块链 ID：必填，不允许空白
        if (chainId == null || chainId.isBlank()) {
            throw new IllegalArgumentException("chainId must not be null or blank");
        }
        // 校验发送方地址：必填，不允许空白
        if (fromAddress == null || fromAddress.isBlank()) {
            throw new IllegalArgumentException("fromAddress must not be null or blank");
        }
        // 校验接收方地址：必填，不允许空白
        if (toAddress == null || toAddress.isBlank()) {
            throw new IllegalArgumentException("toAddress must not be null or blank");
        }
        // 校验转账金额：必填且必须为正数（零或负金额的转账请求非法）
        if (amountWei == null || amountWei.signum() <= 0) {
            throw new IllegalArgumentException("amountWei must not be null and must be positive");
        }
        this.chainId = chainId;
        this.fromAddress = fromAddress;
        this.toAddress = toAddress;
        this.amountWei = amountWei;
        this.tokenAddress = tokenAddress;
        this.nonce = nonce;
        this.gasLimit = gasLimit;
        this.data = data;
    }
}
