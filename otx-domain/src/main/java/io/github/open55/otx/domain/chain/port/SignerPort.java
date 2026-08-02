package io.github.open55.otx.domain.chain.port;

/**
 * 签名出站端口（Outbound Port）。
 * <p>
 * 签名实现可插拔：dev 环境使用本地 keystore（local-keystore），
 * 生产环境对接 KMS / MPC / 托管钱包等外部签名设施（本期不实现）。
 * <p>
 * <strong>安全铁律：私钥绝不进入 OTX 核心</strong>——本端口是 OTX 唯一的签名入口，
 * domain / application / infrastructure 均不存储或接触任何私钥材料，
 * 签名结果仅以 {@link SignedTx}（含 rawTransaction）形式返回。
 */
public interface SignerPort {

    /**
     * 对签名请求执行链上交易签名。
     *
     * @param request 签名请求，包含链 ID、收发地址、金额等交易要素
     * @return 已签名交易，rawTransaction 可直接用于广播
     */
    SignedTx sign(SignRequest request);
}
