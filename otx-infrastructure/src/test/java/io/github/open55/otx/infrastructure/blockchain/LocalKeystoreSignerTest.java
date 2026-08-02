package io.github.open55.otx.infrastructure.blockchain;

import io.github.open55.otx.domain.chain.port.SignRequest;
import io.github.open55.otx.domain.chain.port.SignedTx;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.DefaultResourceLoader;
import org.web3j.crypto.ECKeyPair;
import org.web3j.crypto.WalletUtils;

import java.math.BigInteger;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * LocalKeystoreSigner 本地 keystore 签名适配器单元测试（dev-only）。
 * <p>
 * 使用固定测试私钥生成临时 keystore 文件，验证签名确定性、文件缺失与密码错误分支，
 * 全部为本地计算，零网络零 DB。
 */
@DisplayName("LocalKeystoreSigner 本地 keystore 签名单元测试 | LocalKeystoreSigner unit tests")
class LocalKeystoreSignerTest {

    /**
     * 固定测试私钥（十六进制），保证同一 keystore 下签名结果可复现
     */
    private static final BigInteger PRIVATE_KEY = new BigInteger(
            "59c6995e998f97a5a0044966f0945389dc9e86dae88c7a8412f4603b6b78690d", 16);

    private static final String PASSWORD = "test-password";

    private static final String WRONG_PASSWORD = "wrong-password";

    private static final String CHAIN_ID = "11155111";

    private static final String FROM_ADDRESS = "0xFrom00000000000000000000000000000000000001";

    private static final String TO_ADDRESS = "0xTo0000000000000000000000000000000000000002";

    private static final BigInteger AMOUNT_WEI = BigInteger.valueOf(1_000_000_000_000_000_000L);

    @TempDir
    Path tempDir;

    private ChainTxProperties properties;

    private LocalKeystoreSigner signer;

    private Path keyFile;

    @BeforeEach
    void setUp() throws Exception {
        // 用固定测试私钥生成标准 keystore 文件（V3 格式）写入临时目录
        ECKeyPair keyPair = ECKeyPair.create(PRIVATE_KEY);
        String fileName = WalletUtils.generateWalletFile(PASSWORD, keyPair, tempDir.toFile(), false);
        keyFile = tempDir.resolve(fileName);

        properties = new ChainTxProperties();
        properties.getLocalKeystore().setKeyFile(keyFile.toString());
        properties.getLocalKeystore().setPassword(PASSWORD);
        signer = new LocalKeystoreSigner(properties, new DefaultResourceLoader());
    }

    /**
     * 同一固定私钥与相同输入，两次签名产生相同的 rawTransaction，且带 0x 前缀。
     */
    @Test
    @DisplayName("固定私钥两次签名结果确定性一致")
    void sign_withFixedKey_producesDeterministicRawTx() {
        SignRequest request = signRequest();

        SignedTx first = signer.sign(request);
        SignedTx second = signer.sign(request);

        assertEquals(first.getRawTransaction(), second.getRawTransaction());
        assertTrue(first.getRawTransaction().startsWith("0x"));
        assertEquals(CHAIN_ID, first.getChainId());
        assertEquals(TO_ADDRESS, first.getToAddress());
    }

    /**
     * keystore 文件缺失时抛异常（应用层转换为 TX_SIGN_FAILED）。
     */
    @Test
    @DisplayName("keystore 文件缺失抛异常")
    void sign_withMissingKeyFile_throws() {
        properties.getLocalKeystore().setKeyFile(tempDir.resolve("missing.json").toString());

        assertThrows(RuntimeException.class, () -> signer.sign(signRequest()));
    }

    /**
     * keystore 密码错误时抛异常（应用层转换为 TX_SIGN_FAILED）。
     */
    @Test
    @DisplayName("keystore 密码错误抛异常")
    void sign_withWrongPassword_throws() {
        properties.getLocalKeystore().setPassword(WRONG_PASSWORD);

        assertThrows(RuntimeException.class, () -> signer.sign(signRequest()));
    }

    /**
     * 签名请求携带代币地址时拒绝签名（本期不支持 ERC-20 转账），
     * 防止代币提现被降级为原生币转账造成资金类型错配。
     */
    @Test
    @DisplayName("带代币地址的签名请求被拒绝 | sign rejects token transfer requests")
    void sign_withTokenAddress_throwsUnsupportedTokenTransfer() {
        SignRequest request = new SignRequest(CHAIN_ID, FROM_ADDRESS, TO_ADDRESS, AMOUNT_WEI,
                "0xToken00000000000000000000000000000000000001", null, null, null);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> signer.sign(request));

        assertTrue(ex.getMessage().contains("ERC-20 token transfer is not supported"));
    }

    private static SignRequest signRequest() {
        return new SignRequest(CHAIN_ID, FROM_ADDRESS, TO_ADDRESS, AMOUNT_WEI, null, null, null, null);
    }
}
