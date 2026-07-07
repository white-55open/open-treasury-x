package io.github.open55.otx.infrastructure.blockchain;

import io.github.open55.otx.common.exception.BizException;
import io.github.open55.otx.common.exception.blockchain.Web3jRpcException;
import io.github.open55.otx.domain.ledger.port.ChainTxReceipt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.Request;
import org.web3j.protocol.core.Response;
import org.web3j.protocol.core.methods.response.EthBlockNumber;
import org.web3j.protocol.core.methods.response.EthGetTransactionReceipt;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Web3jChainQueryAdapter 链上查询适配器单元测试。
 * <p>
 * 覆盖 queryTxReceipt / currentBlockNumber / isConfirmed 的正常路径、故障切换和异常分支。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Web3jChainQueryAdapter 链上查询适配器单元测试 | Web3jChainQueryAdapter unit tests")
class Web3jChainQueryAdapterTest {

    private static final String CHAIN_ID = "11155111";

    private static final String TX_HASH = "0xabc123";

    private static final String URL_1 = "https://node1.example.com";

    private static final String URL_2 = "https://node2.example.com";

    @Mock
    private Web3j web3jNode1;

    @Mock
    private Web3j web3jNode2;

    private Web3jProperties properties;

    private Web3jChainQueryAdapter adapter;

    @BeforeEach
    void setUp() {
        properties = new Web3jProperties();
        properties.setChainId(CHAIN_ID);
        properties.setRpcUrls(List.of(URL_1, URL_2));
        properties.setReadTimeout(5000);

        Map<String, Web3j> pool = Map.of(URL_1, web3jNode1, URL_2, web3jNode2);
        adapter = new Web3jChainQueryAdapter(pool, properties);
    }

    @Nested
    @DisplayName("queryTxReceipt 查询交易回执 | query transaction receipt")
    class QueryTxReceipt {

        /**
         * chainId 与配置不匹配时抛 LEDGER_CHAIN_NOT_CONFIGURED。
         */
        @Test
        @DisplayName("chainId 不匹配抛异常")
        void queryTxReceipt_whenChainIdMismatch_throwsException() {
            assertThrows(BizException.class,
                    () -> adapter.queryTxReceipt("1", TX_HASH));
        }

        /**
         * 首个 RPC 节点成功返回 ChainTxReceipt，不尝试第二个节点。
         */
        @Test
        @DisplayName("首个节点成功返回回执")
        void queryTxReceipt_whenFirstNodeSucceeds_returnsReceipt() throws Exception {
            TransactionReceipt receipt = mockReceipt(TX_HASH, BigInteger.valueOf(100));
            stubGetTxReceipt(web3jNode1, TX_HASH, receipt);

            Optional<ChainTxReceipt> result = adapter.queryTxReceipt(CHAIN_ID, TX_HASH);

            assertTrue(result.isPresent());
            assertEquals(TX_HASH, result.get().getTxHash());
            assertEquals(CHAIN_ID, result.get().getChainId());
            verify(web3jNode1).ethGetTransactionReceipt(TX_HASH);
            verifyNoInteractions(web3jNode2);
        }

        /**
         * 首个节点抛出异常，自动切换到第二个节点并成功返回。
         */
        @Test
        @DisplayName("首个节点失败切换到第二个节点")
        void queryTxReceipt_whenFirstNodeFails_failsOverToSecondNode() throws Exception {
            when(web3jNode1.ethGetTransactionReceipt(TX_HASH)).thenThrow(new RuntimeException("Connection timeout"));
            TransactionReceipt receipt = mockReceipt(TX_HASH, BigInteger.valueOf(100));
            stubGetTxReceipt(web3jNode2, TX_HASH, receipt);

            Optional<ChainTxReceipt> result = adapter.queryTxReceipt(CHAIN_ID, TX_HASH);

            assertTrue(result.isPresent());
            assertEquals(TX_HASH, result.get().getTxHash());
            verify(web3jNode1).ethGetTransactionReceipt(TX_HASH);
            verify(web3jNode2).ethGetTransactionReceipt(TX_HASH);
        }

        /**
         * 全部 RPC 节点失败时抛 Web3jRpcException。
         */
        @Test
        @DisplayName("全部节点失败抛 Web3jRpcException")
        void queryTxReceipt_whenAllNodesFail_throwsWeb3jRpcException() throws Exception {
            when(web3jNode1.ethGetTransactionReceipt(TX_HASH)).thenThrow(new RuntimeException("Node1 down"));
            when(web3jNode2.ethGetTransactionReceipt(TX_HASH)).thenThrow(new RuntimeException("Node2 down"));

            Web3jRpcException ex = assertThrows(Web3jRpcException.class,
                    () -> adapter.queryTxReceipt(CHAIN_ID, TX_HASH));
            assertNotNull(ex.getFailedUrls());
            assertEquals(2, ex.getFailedUrls().size());
        }

        /**
         * 链上查不到交易时返回 Optional.empty()。
         */
        @Test
        @DisplayName("链上查不到返回 empty")
        void queryTxReceipt_whenTxNotFound_returnsEmpty() throws Exception {
            EthGetTransactionReceipt response = mock(EthGetTransactionReceipt.class);
            when(response.getTransactionReceipt()).thenReturn(Optional.empty());
            doReturn(mockRequest(response)).when(web3jNode1).ethGetTransactionReceipt(TX_HASH);

            Optional<ChainTxReceipt> result = adapter.queryTxReceipt(CHAIN_ID, TX_HASH);

            assertTrue(result.isEmpty());
        }
    }

    @Nested
    @DisplayName("currentBlockNumber 当前区块高度 | current block number")
    class CurrentBlockNumber {

        /**
         * chainId 不匹配时抛异常。
         */
        @Test
        @DisplayName("chainId 不匹配抛异常")
        void currentBlockNumber_whenChainIdMismatch_throwsException() {
            assertThrows(BizException.class,
                    () -> adapter.currentBlockNumber("1"));
        }

        /**
         * 首个节点成功返回区块高度。
         */
        @Test
        @DisplayName("首个节点成功返回高度")
        void currentBlockNumber_whenFirstNodeSucceeds_returnsBlockNumber() throws Exception {
            EthBlockNumber response = mock(EthBlockNumber.class);
            when(response.getBlockNumber()).thenReturn(BigInteger.valueOf(200));
            doReturn(mockRequest(response)).when(web3jNode1).ethBlockNumber();

            Long result = adapter.currentBlockNumber(CHAIN_ID);

            assertEquals(200L, result);
        }

        /**
         * 首个节点失败，切换到第二个节点成功。
         */
        @Test
        @DisplayName("首个节点失败切换到第二个节点")
        void currentBlockNumber_whenFirstNodeFails_failsOver() throws Exception {
            when(web3jNode1.ethBlockNumber()).thenThrow(new RuntimeException("Timeout"));
            EthBlockNumber response = mock(EthBlockNumber.class);
            when(response.getBlockNumber()).thenReturn(BigInteger.valueOf(300));
            doReturn(mockRequest(response)).when(web3jNode2).ethBlockNumber();

            Long result = adapter.currentBlockNumber(CHAIN_ID);

            assertEquals(300L, result);
        }
    }

    @Nested
    @DisplayName("isConfirmed 确认数判断 | confirmations check")
    class IsConfirmed {

        /**
         * 交易已满足确认数要求返回 true。
         */
        @Test
        @DisplayName("确认数满足要求返回 true")
        void isConfirmed_whenSufficientConfirmations_returnsTrue() throws Exception {
            TransactionReceipt receipt = mockReceipt(TX_HASH, BigInteger.valueOf(100));
            EthGetTransactionReceipt txResponse = mock(EthGetTransactionReceipt.class);
            when(txResponse.getTransactionReceipt()).thenReturn(Optional.of(receipt));
            doReturn(mockRequest(txResponse)).when(web3jNode1).ethGetTransactionReceipt(TX_HASH);

            EthBlockNumber blockResponse = mock(EthBlockNumber.class);
            when(blockResponse.getBlockNumber()).thenReturn(BigInteger.valueOf(115));
            doReturn(mockRequest(blockResponse)).when(web3jNode1).ethBlockNumber();

            boolean result = adapter.isConfirmed(CHAIN_ID, TX_HASH, 12);

            assertTrue(result);
        }

        /**
         * 确认数不足返回 false。
         */
        @Test
        @DisplayName("确认数不足返回 false")
        void isConfirmed_whenInsufficientConfirmations_returnsFalse() throws Exception {
            TransactionReceipt receipt = mockReceipt(TX_HASH, BigInteger.valueOf(100));
            EthGetTransactionReceipt txResponse = mock(EthGetTransactionReceipt.class);
            when(txResponse.getTransactionReceipt()).thenReturn(Optional.of(receipt));
            doReturn(mockRequest(txResponse)).when(web3jNode1).ethGetTransactionReceipt(TX_HASH);

            EthBlockNumber blockResponse = mock(EthBlockNumber.class);
            when(blockResponse.getBlockNumber()).thenReturn(BigInteger.valueOf(105));
            doReturn(mockRequest(blockResponse)).when(web3jNode1).ethBlockNumber();

            boolean result = adapter.isConfirmed(CHAIN_ID, TX_HASH, 12);

            assertFalse(result);
        }

        /**
         * 交易在链上不存在时返回 false。
         */
        @Test
        @DisplayName("交易不存在返回 false")
        void isConfirmed_whenTxNotFound_returnsFalse() throws Exception {
            EthGetTransactionReceipt response = mock(EthGetTransactionReceipt.class);
            when(response.getTransactionReceipt()).thenReturn(Optional.empty());
            doReturn(mockRequest(response)).when(web3jNode1).ethGetTransactionReceipt(TX_HASH);

            boolean result = adapter.isConfirmed(CHAIN_ID, TX_HASH, 12);

            assertFalse(result);
        }
    }

    private static TransactionReceipt mockReceipt(String txHash, BigInteger blockNumber) {
        TransactionReceipt receipt = mock(TransactionReceipt.class);
        when(receipt.getTransactionHash()).thenReturn(txHash);
        when(receipt.getBlockNumber()).thenReturn(blockNumber);
        when(receipt.getStatus()).thenReturn("0x1");
        when(receipt.getFrom()).thenReturn("0xfrom");
        when(receipt.getTo()).thenReturn("0xto");
        when(receipt.getCumulativeGasUsed()).thenReturn(BigInteger.valueOf(21000));
        return receipt;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Request mockRequest(Response response) throws Exception {
        Request request = mock(Request.class);
        doReturn(response).when(request).send();
        return request;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void stubGetTxReceipt(Web3j web3j, String txHash, TransactionReceipt receipt) throws Exception {
        EthGetTransactionReceipt response = mock(EthGetTransactionReceipt.class);
        when(response.getTransactionReceipt()).thenReturn(Optional.of(receipt));
        Request request = mock(Request.class);
        doReturn(response).when(request).send();
        doReturn(request).when(web3j).ethGetTransactionReceipt(txHash);
    }
}
