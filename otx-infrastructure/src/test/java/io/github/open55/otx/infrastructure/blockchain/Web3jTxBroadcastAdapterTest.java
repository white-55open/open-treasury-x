package io.github.open55.otx.infrastructure.blockchain;

import io.github.open55.otx.common.exception.BizException;
import io.github.open55.otx.common.exception.blockchain.Web3jRpcException;
import io.github.open55.otx.domain.chain.port.BroadcastResult;
import io.github.open55.otx.domain.chain.port.SignedTx;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.Request;
import org.web3j.protocol.core.Response;
import org.web3j.protocol.core.methods.response.EthGetTransactionCount;
import org.web3j.protocol.core.methods.response.EthSendTransaction;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Web3jTxBroadcastAdapter 链上广播适配器单元测试。
 * <p>
 * 覆盖 broadcast / currentNonce 的正常路径、全部节点失败与链 ID 未配置分支（零网络）。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Web3jTxBroadcastAdapter 链上广播适配器单元测试 | Web3jTxBroadcastAdapter unit tests")
class Web3jTxBroadcastAdapterTest {

    private static final String CHAIN_ID = "11155111";

    private static final String RAW_TX = "0xf86c808504a817c800825208943535353535353535353535353535353535353535880de0b6b3a76400008025a02fc6f4f3a0e0d1b6b0e1b3a2c4d5e6f708192a3b4c5d6e7f8091a2b3c4d5e6f70a07f2f4f3a0e0d1b6b0e1b3a2c4d5e6f708192a3b4c5d6e7f8091a2b3c4d5e6f70";

    private static final String TX_HASH = "0xabc123";

    private static final String FROM_ADDRESS = "0xFrom00000000000000000000000000000000000001";

    private static final String TO_ADDRESS = "0xTo0000000000000000000000000000000000000002";

    private static final String URL_1 = "https://node1.example.com";

    private static final String URL_2 = "https://node2.example.com";

    @Mock
    private Web3j web3jNode1;

    @Mock
    private Web3j web3jNode2;

    private Web3jProperties properties;

    private Web3jTxBroadcastAdapter adapter;

    @BeforeEach
    void setUp() {
        properties = new Web3jProperties();
        properties.setChainId(CHAIN_ID);
        properties.setRpcUrls(List.of(URL_1, URL_2));
        properties.setReadTimeout(5000);

        Map<String, Web3j> pool = Map.of(URL_1, web3jNode1, URL_2, web3jNode2);
        adapter = new Web3jTxBroadcastAdapter(pool, properties);
    }

    @Nested
    @DisplayName("broadcast 广播交易 | broadcast raw transaction")
    class Broadcast {

        /**
         * 首个 RPC 节点成功返回交易哈希，BroadcastResult 携带链 ID 与收发地址。
         */
        @Test
        @DisplayName("mock 客户端成功返回交易哈希")
        void broadcast_withMockClient_returnsTxHashFromResponse() throws Exception {
            EthSendTransaction response = mock(EthSendTransaction.class);
            when(response.getTransactionHash()).thenReturn(TX_HASH);
            doReturn(mockRequest(response)).when(web3jNode1).ethSendRawTransaction(RAW_TX);

            BroadcastResult result = adapter.broadcast(signedTx(CHAIN_ID));

            assertEquals(CHAIN_ID, result.getChainId());
            assertEquals(TX_HASH, result.getTxHash());
            assertEquals(FROM_ADDRESS, result.getFromAddress());
            assertEquals(TO_ADDRESS, result.getToAddress());
        }

        /**
         * 全部 RPC 节点抛出异常时抛 Web3jRpcException，携带失败 URL 列表。
         */
        @Test
        @DisplayName("全部 RPC 节点失败抛 Web3jRpcException")
        void broadcast_allRpcNodesFail_throwsWeb3jRpcException() throws Exception {
            when(web3jNode1.ethSendRawTransaction(RAW_TX)).thenThrow(new RuntimeException("Node1 down"));
            when(web3jNode2.ethSendRawTransaction(RAW_TX)).thenThrow(new RuntimeException("Node2 down"));

            Web3jRpcException ex = assertThrows(Web3jRpcException.class,
                    () -> adapter.broadcast(signedTx(CHAIN_ID)));
            assertNotNull(ex.getFailedUrls());
            assertEquals(2, ex.getFailedUrls().size());
        }

        /**
         * chainId 与配置不匹配时抛 LEDGER_CHAIN_NOT_CONFIGURED。
         */
        @Test
        @DisplayName("链 ID 未配置抛 LEDGER_CHAIN_NOT_CONFIGURED")
        void broadcast_withUnconfiguredChain_throwsLedgerChainNotConfigured() {
            assertThrows(BizException.class,
                    () -> adapter.broadcast(signedTx("1")));
        }
    }

    @Nested
    @DisplayName("currentNonce 查询 nonce | query pending nonce")
    class CurrentNonce {

        /**
         * 首个 RPC 节点成功返回 PENDING 计数 nonce。
         */
        @Test
        @DisplayName("mock 客户端返回 PENDING nonce")
        void currentNonce_withMockClient_returnsPendingNonce() throws Exception {
            EthGetTransactionCount response = mock(EthGetTransactionCount.class);
            when(response.getTransactionCount()).thenReturn(BigInteger.valueOf(5));
            doReturn(mockRequest(response)).when(web3jNode1)
                    .ethGetTransactionCount(FROM_ADDRESS, DefaultBlockParameterName.PENDING);

            BigInteger nonce = adapter.currentNonce(CHAIN_ID, FROM_ADDRESS);

            assertEquals(BigInteger.valueOf(5), nonce);
        }

        /**
         * 全部 RPC 节点失败时抛 Web3jRpcException。
         */
        @Test
        @DisplayName("全部 RPC 节点失败抛 Web3jRpcException")
        void currentNonce_allRpcNodesFail_throwsWeb3jRpcException() throws Exception {
            when(web3jNode1.ethGetTransactionCount(FROM_ADDRESS, DefaultBlockParameterName.PENDING))
                    .thenThrow(new RuntimeException("Node1 down"));
            when(web3jNode2.ethGetTransactionCount(FROM_ADDRESS, DefaultBlockParameterName.PENDING))
                    .thenThrow(new RuntimeException("Node2 down"));

            assertThrows(Web3jRpcException.class,
                    () -> adapter.currentNonce(CHAIN_ID, FROM_ADDRESS));
        }
    }

    private static SignedTx signedTx(String chainId) {
        return new SignedTx(chainId, RAW_TX, FROM_ADDRESS, TO_ADDRESS);
    }

    private static Request<?, Response<?>> mockRequest(Response<?> response) throws Exception {
        Request<?, Response<?>> request = mock(Request.class);
        doReturn(response).when(request).send();
        return request;
    }
}
