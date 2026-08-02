package io.github.open55.otx.infrastructure.blockchain;

import io.github.open55.otx.domain.chain.port.SignRequest;
import io.github.open55.otx.domain.chain.port.SignedTx;
import io.github.open55.otx.domain.chain.port.SignerPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.RawTransaction;
import org.web3j.crypto.TransactionEncoder;
import org.web3j.crypto.WalletUtils;
import org.web3j.utils.Numeric;

import java.io.InputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;

/**
 * 本地 keystore 签名适配器，实现 SignerPort 出站端口。
 * <p>
 * <strong>仅限开发/演示环境使用（dev-only）</strong>：私钥保存在本地 keystore 文件中，
 * 生产环境必须对接 KMS / MPC / 托管钱包等外部签名设施，私钥绝不进入 OTX 核心。
 * keystore 密码经环境变量注入（OTX_KEYSTORE_PASSWORD），禁止硬编码。
 * 签名使用 web3j 标准库本地计算，零网络依赖。
 * <p>
 * 装配门禁：仅 dev/demo profile 装配（{@code @Profile({"dev", "demo"})}），
 * 生产环境必须对接外部签名设施（KMS/MPC）。
 * <p>
 * 本期仅支持原生币转账，ERC-20 代币转账留待后续变更（需实现 transfer calldata 编码）。
 */
@Slf4j
@Component
@Profile({"dev", "demo"})
@RequiredArgsConstructor
public class LocalKeystoreSigner implements SignerPort {

    /**
     * 默认 gas 单价（1 Gwei），SignRequest 无 gasPrice 字段时使用
     */
    private static final BigInteger DEFAULT_GAS_PRICE = BigInteger.valueOf(1_000_000_000L);

    /**
     * 默认 gas 上限（原生币转账 21000），SignRequest 未携带 gasLimit 时使用
     */
    private static final BigInteger DEFAULT_GAS_LIMIT = BigInteger.valueOf(21_000L);

    private final ChainTxProperties properties;

    private final ResourceLoader resourceLoader;

    /**
     * 对签名请求执行链上交易签名。
     * <p>
     * 加载本地 keystore 获得凭据，构造 RawTransaction 并本地签名，
     * 返回可直接广播的 SignedTx（含 0x 前缀 RLP 编码原始交易）。
     *
     * @param request 签名请求，包含链 ID、收发地址、金额等交易要素
     * @return 已签名交易
     * @throws IllegalStateException 当请求携带代币地址（本期不支持 ERC-20 转账）、
     *                               keystore 文件缺失、密码错误或签名计算失败时抛出
     *                               （应用层转换为 TX_SIGN_FAILED）
     */
    @Override
    public SignedTx sign(SignRequest request) {
        // ERC-20 代币转账需构造 transfer calldata 并按代币精度编码，
        // 本期签名适配器仅支持原生币转账；携带代币地址直接拒绝，
        // 防止代币提现被降级为原生币转账造成资金类型错配（资金安全）
        if (request.getTokenAddress() != null && !request.getTokenAddress().isBlank()) {
            throw new IllegalStateException("ERC-20 token transfer is not supported yet, only native coin transfer");
        }
        try {
            Credentials credentials = loadCredentials();
            // 交易 nonce：request 未携带时使用默认 0（应用层通常在签名前通过 currentNonce 获取）
            BigInteger nonce = request.getNonce() != null ? request.getNonce() : BigInteger.ZERO;
            // 交易 gas 上限：request 未携带时使用默认值
            BigInteger gasLimit = request.getGasLimit() != null ? request.getGasLimit() : DEFAULT_GAS_LIMIT;
            RawTransaction rawTransaction = RawTransaction.createTransaction(
                    nonce, DEFAULT_GAS_PRICE, gasLimit,
                    request.getToAddress(), request.getAmountWei(), request.getData());
            byte[] signed = TransactionEncoder.signMessage(rawTransaction, credentials);
            String rawTx = Numeric.toHexString(signed);
            // 签名成功，记录发送方地址与交易要素摘要
            log.debug("Local keystore signed transaction, from={}, to={}", credentials.getAddress(), request.getToAddress());
            return new SignedTx(request.getChainId(), rawTx, credentials.getAddress(), request.getToAddress());
        } catch (Exception e) {
            // 签名失败（keystore 文件缺失/密码错误/签名计算异常），统一包装抛出，由应用层转换为 TX_SIGN_FAILED
            log.error("Local keystore signing failed, reason={}", e.getMessage());
            throw new IllegalStateException("Local keystore signing failed: " + e.getMessage(), e);
        }
    }

    /**
     * 加载 keystore 文件并解密得到凭据。
     * <p>
     * keyFile 支持 classpath: 前缀（如 classpath:keystore/dev-withdrawer.json）与文件系统路径。
     *
     * @return 解密后的凭据（私钥与派生地址）
     * @throws Exception 文件缺失（IOException）或密码错误（CipherException）时抛出
     */
    private Credentials loadCredentials() throws Exception {
        String keyFile = properties.getLocalKeystore().getKeyFile();
        String password = properties.getLocalKeystore().getPassword();
        // classpath:/file: 前缀交给 Spring ResourceLoader 解析，无前缀路径按文件系统路径处理
        Resource resource = (keyFile.startsWith("classpath:") || keyFile.startsWith("file:"))
                ? resourceLoader.getResource(keyFile)
                : new FileSystemResource(keyFile);
        try (InputStream in = resource.getInputStream()) {
            // 读取 keystore JSON 内容并解密为凭据
            String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return WalletUtils.loadJsonCredentials(password, json);
        }
    }
}
