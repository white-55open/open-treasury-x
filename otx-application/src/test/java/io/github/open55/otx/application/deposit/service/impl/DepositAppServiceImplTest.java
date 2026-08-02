package io.github.open55.otx.application.deposit.service.impl;

import io.github.open55.otx.application.account.service.AccountAppService;
import io.github.open55.otx.application.deposit.dto.DepositRequestDTO;
import io.github.open55.otx.application.ledger.dto.request.LedgerEntryRequestDTO;
import io.github.open55.otx.application.ledger.dto.request.PostJournalRequestDTO;
import io.github.open55.otx.application.ledger.service.LedgerAppService;
import io.github.open55.otx.common.exception.BizException;
import io.github.open55.otx.common.exception.blockchain.Web3jRpcException;
import io.github.open55.otx.domain.fundflow.enums.FundFlowTypeEnum;
import io.github.open55.otx.domain.ledger.enums.LedgerAccountCodeEnum;
import io.github.open55.otx.domain.ledger.enums.LedgerBizTypeEnum;
import io.github.open55.otx.domain.ledger.enums.LedgerEntryTypeEnum;
import io.github.open55.otx.domain.ledger.port.ChainQueryPort;
import io.github.open55.otx.domain.ledger.port.ChainTxReceipt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * DepositAppServiceImpl 充值应用服务单元测试。
 * <p>
 * 覆盖充值入口的链上确认闸门（证据校验 / 确认校验 / fail-safe）、确认数解析、
 * 凭证链字段填充、幂等行为与既有入账编排（余额变更 + 总账过账容错）。
 * English: Unit tests for the deposit application service, covering the
 * chain-confirmation gate, confirmation-count resolution, journal
 * chain-evidence fields, idempotency and the existing crediting orchestration.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DepositAppServiceImpl 充值应用服务单元测试 | DepositAppServiceImpl unit tests")
class DepositAppServiceImplTest {

    private static final String BIZ_NO = "BIZ-20260101-0001";
    private static final BigDecimal AMOUNT_100 = new BigDecimal("100");
    private static final Long TEST_UID = 12345L;
    private static final String TEST_CHAIN_ID = "11155111";
    private static final String TEST_TX_HASH = "0xabc123def456";
    private static final String TEST_CURRENCY = "USDT";
    private static final String TEST_TOKEN_ADDRESS = "0xTokenContract";
    private static final int DEFAULT_CONFIRMATIONS_12 = 12;
    private static final int REQ_CONFIRMATIONS_6 = 6;
    private static final BigInteger TEST_BLOCK_NUMBER = BigInteger.valueOf(55555L);
    private static final String ERR_DEPOSIT_CHAIN_INFO_MISS = "DEPOSIT_CHAIN_INFO_MISS";
    private static final String ERR_DEPOSIT_TX_NOT_CONFIRMED = "DEPOSIT_TX_NOT_CONFIRMED";
    private static final String ERR_DEPOSIT_CHAIN_QUERY_FAILED = "DEPOSIT_CHAIN_QUERY_FAILED";

    @Mock
    private AccountAppService accountAppService;

    @Mock
    private LedgerAppService ledgerAppService;

    @Mock
    private ChainQueryPort chainQueryPort;

    private DepositAppServiceImpl service;

    @BeforeEach
    void setUp() throws Exception {
        service = new DepositAppServiceImpl(null, null, null, null);
        setField(service, "accountAppService", accountAppService);
        setField(service, "ledgerAppService", ledgerAppService);
        setField(service, "chainQueryPort", chainQueryPort);
        setField(service, "defaultConfirmations", DEFAULT_CONFIRMATIONS_12);
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    /**
     * 构造带完整链上证据的合法充值请求，供确认闸门通过路径复用。
     *
     * @return 携带 chainId / chainTxHash 的充值请求
     */
    private DepositRequestDTO newValidRequest() {
        DepositRequestDTO request = new DepositRequestDTO();
        request.setBizNo(BIZ_NO);
        request.setAmount(AMOUNT_100);
        request.setUid(TEST_UID);
        request.setCurrency(TEST_CURRENCY);
        request.setChainId(TEST_CHAIN_ID);
        request.setChainTxHash(TEST_TX_HASH);
        return request;
    }

    /**
     * 构造链上交易回执，区块高度取测试常量。
     *
     * @return 已打包的成功交易回执
     */
    private ChainTxReceipt newConfirmedReceipt() {
        return new ChainTxReceipt(TEST_CHAIN_ID, TEST_TX_HASH, TEST_BLOCK_NUMBER,
                "0x1", DEFAULT_CONFIRMATIONS_12, "0xFrom", "0xTo", BigInteger.ZERO);
    }

    /**
     * 入账编排：充值成功时完成余额变更并自动过账总账。
     */
    @Nested
    @DisplayName("入账编排 | crediting orchestration")
    class CreditingOrchestrationTest {

        /**
         * 场景：链上确认通过后，余额变更与总账过账依次执行且返回业务流水号。
         * Scenario: after chain confirmation passes, balance change and journal posting run and the bizNo is returned.
         */
        @Test
        @DisplayName("充值成功后自动过账总账 | successful deposit posts the journal")
        void deposit_withCurrency_success() {
            // Arrange：确认通过 + 回执可查 + 动账幂等返回原业务号
            DepositRequestDTO request = newValidRequest();
            when(chainQueryPort.isConfirmed(TEST_CHAIN_ID, TEST_TX_HASH, DEFAULT_CONFIRMATIONS_12)).thenReturn(true);
            when(chainQueryPort.queryTxReceipt(TEST_CHAIN_ID, TEST_TX_HASH)).thenReturn(Optional.of(newConfirmedReceipt()));
            when(accountAppService.changeAmountWithFundFlow(request)).thenReturn(BIZ_NO);

            // Act：执行充值入账
            String result = service.deposit(request);

            // Assert：返回业务号、流水类型标记为充值、动账与过账均被调用
            assertEquals(BIZ_NO, result);
            assertEquals(FundFlowTypeEnum.DEPOSIT, request.getFundFlowType());
            verify(accountAppService).changeAmountWithFundFlow(request);
            verify(ledgerAppService).postJournal(any(PostJournalRequestDTO.class));
        }

        /**
         * 场景：过账参数正确性验证——业务类型、分录科目和金额。
         * Scenario: the journal request carries the correct biz type, entries and amounts.
         */
        @Test
        @DisplayName("过账参数包含正确的分录和业务类型 | journal request has correct entries and biz type")
        void deposit_passesCorrectJournalRequest() {
            // Arrange：确认通过 + 回执可查 + 动账成功
            DepositRequestDTO request = newValidRequest();
            when(chainQueryPort.isConfirmed(TEST_CHAIN_ID, TEST_TX_HASH, DEFAULT_CONFIRMATIONS_12)).thenReturn(true);
            when(chainQueryPort.queryTxReceipt(TEST_CHAIN_ID, TEST_TX_HASH)).thenReturn(Optional.of(newConfirmedReceipt()));
            when(accountAppService.changeAmountWithFundFlow(request)).thenReturn(BIZ_NO);

            // Act：执行充值入账
            service.deposit(request);

            // Assert：捕获过账请求并断言业务类型、币种与借贷分录
            ArgumentCaptor<PostJournalRequestDTO> captor = ArgumentCaptor.forClass(PostJournalRequestDTO.class);
            verify(ledgerAppService).postJournal(captor.capture());
            PostJournalRequestDTO captured = captor.getValue();

            assertEquals(BIZ_NO, captured.getBizNo());
            assertEquals(LedgerBizTypeEnum.DEPOSIT_ONCHAIN, captured.getBizType());
            assertEquals(TEST_CURRENCY, captured.getCurrency());
            assertNotNull(captured.getPostingDate());

            List<LedgerEntryRequestDTO> entries = captured.getEntries();
            assertEquals(2, entries.size());

            LedgerEntryRequestDTO debit = entries.get(0);
            assertEquals(LedgerAccountCodeEnum.DEPOSIT_IN_TRANSIT, debit.getAccountCode());
            assertEquals(LedgerEntryTypeEnum.DEBIT, debit.getEntryType());
            assertEquals(AMOUNT_100, debit.getAmount());

            LedgerEntryRequestDTO credit = entries.get(1);
            assertEquals(LedgerAccountCodeEnum.USER_AVAILABLE, credit.getAccountCode());
            assertEquals(LedgerEntryTypeEnum.CREDIT, credit.getEntryType());
            assertEquals(AMOUNT_100, credit.getAmount());
            assertEquals(TEST_UID, credit.getUid());
        }

        /**
         * 场景：未传 currency 时过账失败不影响充值结果。
         * Scenario: a journal failure caused by a missing currency does not affect the deposit result.
         */
        @Test
        @DisplayName("未传 currency 过账失败不影响余额变更 | missing currency journal failure keeps deposit ok")
        void deposit_withoutCurrency_failsJournal() {
            // Arrange：请求不带币种，过账抛异常（既有容错路径）
            DepositRequestDTO request = newValidRequest();
            request.setCurrency(null);
            when(chainQueryPort.isConfirmed(TEST_CHAIN_ID, TEST_TX_HASH, DEFAULT_CONFIRMATIONS_12)).thenReturn(true);
            when(chainQueryPort.queryTxReceipt(TEST_CHAIN_ID, TEST_TX_HASH)).thenReturn(Optional.of(newConfirmedReceipt()));
            when(accountAppService.changeAmountWithFundFlow(request)).thenReturn(BIZ_NO);
            doThrow(new RuntimeException("currency empty")).when(ledgerAppService).postJournal(any());

            // Act：执行充值入账
            String result = service.deposit(request);

            // Assert：过账失败被容错，充值结果不受影响
            assertEquals(BIZ_NO, result);
            verify(accountAppService).changeAmountWithFundFlow(request);
            verify(ledgerAppService).postJournal(any());
        }

        /**
         * 场景：过账抛出异常不阻断充值流程（最终一致性容错）。
         * Scenario: a posting exception is swallowed and the deposit still returns ok.
         */
        @Test
        @DisplayName("过账异常不阻断充值 | posting exception does not block the deposit")
        void deposit_ledgerFails_stillReturnsOk() {
            // Arrange：确认通过，过账抛运行时异常（RPC 超时模拟）
            DepositRequestDTO request = newValidRequest();
            when(chainQueryPort.isConfirmed(TEST_CHAIN_ID, TEST_TX_HASH, DEFAULT_CONFIRMATIONS_12)).thenReturn(true);
            when(chainQueryPort.queryTxReceipt(TEST_CHAIN_ID, TEST_TX_HASH)).thenReturn(Optional.of(newConfirmedReceipt()));
            when(accountAppService.changeAmountWithFundFlow(request)).thenReturn(BIZ_NO);
            doThrow(new RuntimeException("RPC timeout")).when(ledgerAppService).postJournal(any());

            // Act：执行充值入账
            String result = service.deposit(request);

            // Assert：过账失败被容错，余额变更已提交
            assertEquals(BIZ_NO, result);
            verify(accountAppService).changeAmountWithFundFlow(request);
            verify(ledgerAppService).postJournal(any());
        }

        /**
         * 场景：重复 bizNo 幂等处理——确认闸门每次执行，幂等由动账路径兜底。
         * Scenario: duplicate bizNo calls both run the gate and defer idempotency to the crediting path.
         */
        @Test
        @DisplayName("重复 bizNo 幂等处理 | duplicate bizNo handled idempotently")
        void deposit_idempotent_skipsJournal() {
            // Arrange：确认通过 + 回执可查 + 动账幂等返回原业务号
            DepositRequestDTO request = newValidRequest();
            when(chainQueryPort.isConfirmed(TEST_CHAIN_ID, TEST_TX_HASH, DEFAULT_CONFIRMATIONS_12)).thenReturn(true);
            when(chainQueryPort.queryTxReceipt(TEST_CHAIN_ID, TEST_TX_HASH)).thenReturn(Optional.of(newConfirmedReceipt()));
            when(accountAppService.changeAmountWithFundFlow(request)).thenReturn(BIZ_NO);

            // Act：同一请求重复入账两次
            service.deposit(request);
            service.deposit(request);

            // Assert：两次均进入动账与过账路径，幂等由 changeAmountWithFundFlow 内部唯一索引兜底
            verify(accountAppService, times(2)).changeAmountWithFundFlow(request);
            verify(ledgerAppService, times(2)).postJournal(any());
        }
    }

    /**
     * 链确认闸门：证据校验与确认校验的拒绝路径。
     */
    @Nested
    @DisplayName("链确认闸门 | chain-confirmation gate")
    class ChainConfirmationGateTest {

        /**
         * 场景：链上确认通过后余额变更与总账过账被调用，入账成功。
         * Scenario: a confirmed transaction proceeds to credit the balance and post the journal.
         */
        @Test
        @DisplayName("确认通过则入账 | confirmed transaction is credited and journaled")
        void deposit_withConfirmedTx_creditsBalanceAndPostsJournal() {
            // Arrange：确认通过 + 回执可查 + 动账成功
            DepositRequestDTO request = newValidRequest();
            when(chainQueryPort.isConfirmed(TEST_CHAIN_ID, TEST_TX_HASH, DEFAULT_CONFIRMATIONS_12)).thenReturn(true);
            when(chainQueryPort.queryTxReceipt(TEST_CHAIN_ID, TEST_TX_HASH)).thenReturn(Optional.of(newConfirmedReceipt()));
            when(accountAppService.changeAmountWithFundFlow(request)).thenReturn(BIZ_NO);

            // Act：执行充值入账
            String result = service.deposit(request);

            // Assert：入账成功，动账与过账均被调用
            assertEquals(BIZ_NO, result);
            verify(accountAppService).changeAmountWithFundFlow(request);
            verify(ledgerAppService).postJournal(any(PostJournalRequestDTO.class));
        }

        /**
         * 场景：交易未达确认数时抛 DEPOSIT_TX_NOT_CONFIRMED 且零账务副作用。
         * Scenario: an unconfirmed transaction is rejected and neither crediting nor posting runs.
         */
        @Test
        @DisplayName("未确认交易拒绝入账且无副作用 | unconfirmed transaction is rejected without side effects")
        void deposit_withUnconfirmedTx_throwsTxNotConfirmed_andNoSideEffects() {
            // Arrange：确认数不足，isConfirmed 返回 false
            DepositRequestDTO request = newValidRequest();
            when(chainQueryPort.isConfirmed(TEST_CHAIN_ID, TEST_TX_HASH, DEFAULT_CONFIRMATIONS_12)).thenReturn(false);

            // Act & Assert：抛未确认业务异常
            BizException ex = assertThrows(BizException.class, () -> service.deposit(request));
            assertEquals(ERR_DEPOSIT_TX_NOT_CONFIRMED, ex.getErrorCode());
            // Assert：动账与过账从未被调用，无任何账务副作用
            verifyNoInteractions(accountAppService, ledgerAppService);
        }

        /**
         * 场景：链查询抛 Web3jRpcException（RPC 全挂）时 fail-safe 抛 DEPOSIT_CHAIN_QUERY_FAILED。
         * Scenario: an RPC-wide failure is translated to DEPOSIT_CHAIN_QUERY_FAILED in fail-safe fashion.
         */
        @Test
        @DisplayName("链查询失败 fail-safe 拒绝入账 | chain query failure rejects the deposit")
        void deposit_whenChainQueryFails_throwsChainQueryFailed() {
            // Arrange：确认校验抛 Web3jRpcException（所有 RPC 节点不可用）
            DepositRequestDTO request = newValidRequest();
            when(chainQueryPort.isConfirmed(TEST_CHAIN_ID, TEST_TX_HASH, DEFAULT_CONFIRMATIONS_12))
                    .thenThrow(new Web3jRpcException(TEST_CHAIN_ID, List.of("https://rpc.invalid"), null));

            // Act & Assert：fail-safe 转 DEPOSIT_CHAIN_QUERY_FAILED
            BizException ex = assertThrows(BizException.class, () -> service.deposit(request));
            assertEquals(ERR_DEPOSIT_CHAIN_QUERY_FAILED, ex.getErrorCode());
            // Assert：无任何账务副作用
            verifyNoInteractions(accountAppService, ledgerAppService);
        }

        /**
         * 场景：请求缺失 chainId 时抛 DEPOSIT_CHAIN_INFO_MISS 拒绝入账。
         * Scenario: a request without chainId is rejected as missing chain evidence.
         */
        @Test
        @DisplayName("缺失 chainId 抛链上证据缺失 | missing chainId throws chain-info-miss")
        void deposit_withMissingChainId_throwsChainInfoMiss() {
            // Arrange：合法请求清除 chainId
            DepositRequestDTO request = newValidRequest();
            request.setChainId(null);

            // Act & Assert：证据校验拦截，抛 DEPOSIT_CHAIN_INFO_MISS
            BizException ex = assertThrows(BizException.class, () -> service.deposit(request));
            assertEquals(ERR_DEPOSIT_CHAIN_INFO_MISS, ex.getErrorCode());
            // Assert：链查询与账务均未触发
            verifyNoInteractions(chainQueryPort, accountAppService, ledgerAppService);
        }

        /**
         * 场景：请求缺失 chainTxHash 时抛 DEPOSIT_CHAIN_INFO_MISS 拒绝入账。
         * Scenario: a request without chainTxHash is rejected as missing chain evidence.
         */
        @Test
        @DisplayName("缺失 chainTxHash 抛链上证据缺失 | missing chainTxHash throws chain-info-miss")
        void deposit_withMissingTxHash_throwsChainInfoMiss() {
            // Arrange：合法请求清除 chainTxHash
            DepositRequestDTO request = newValidRequest();
            request.setChainTxHash(null);

            // Act & Assert：证据校验拦截，抛 DEPOSIT_CHAIN_INFO_MISS
            BizException ex = assertThrows(BizException.class, () -> service.deposit(request));
            assertEquals(ERR_DEPOSIT_CHAIN_INFO_MISS, ex.getErrorCode());
            // Assert：链查询与账务均未触发
            verifyNoInteractions(chainQueryPort, accountAppService, ledgerAppService);
        }

        /**
         * 场景：请求级确认数为 0 或负数时视为非法覆盖值，抛 DEPOSIT_CHAIN_INFO_MISS。
         * Scenario: a non-positive requiredConfirmations override is rejected as invalid chain evidence.
         */
        @ParameterizedTest(name = "requiredConfirmations={0}")
        @ValueSource(ints = {0, -1})
        @DisplayName("非正确认数抛链上证据缺失 | non-positive confirmation count throws chain-info-miss")
        void deposit_withInvalidRequiredConfirmations_throwsChainInfoMiss(int invalidConfirmations) {
            // Arrange：请求携带非法确认数覆盖值
            DepositRequestDTO request = newValidRequest();
            request.setRequiredConfirmations(invalidConfirmations);

            // Act & Assert：证据校验拦截，抛 DEPOSIT_CHAIN_INFO_MISS
            BizException ex = assertThrows(BizException.class, () -> service.deposit(request));
            assertEquals(ERR_DEPOSIT_CHAIN_INFO_MISS, ex.getErrorCode());
            // Assert：链查询与账务均未触发
            verifyNoInteractions(chainQueryPort, accountAppService, ledgerAppService);
        }
    }

    /**
     * 确认数解析：请求级覆盖值优先，缺省用配置默认值。
     */
    @Nested
    @DisplayName("确认数解析 | confirmation-count resolution")
    class ConfirmationResolutionTest {

        /**
         * 场景：请求未带 requiredConfirmations 时，确认校验收到配置默认值 12。
         * Scenario: without an override the confirmation check receives the configured default of 12.
         */
        @Test
        @DisplayName("无覆盖值用配置默认确认数 | default confirmation count is used without an override")
        void deposit_withoutOverrideUsesDefaultConfirmations() {
            // Arrange：请求不带确认数覆盖值，确认通过 + 回执可查 + 动账成功
            DepositRequestDTO request = newValidRequest();
            when(chainQueryPort.isConfirmed(eq(TEST_CHAIN_ID), eq(TEST_TX_HASH), anyInt())).thenReturn(true);
            when(chainQueryPort.queryTxReceipt(TEST_CHAIN_ID, TEST_TX_HASH)).thenReturn(Optional.of(newConfirmedReceipt()));
            when(accountAppService.changeAmountWithFundFlow(request)).thenReturn(BIZ_NO);

            // Act：执行充值入账
            service.deposit(request);

            // Assert：捕获确认数参数，断言为配置默认值 12
            ArgumentCaptor<Integer> captor = ArgumentCaptor.forClass(Integer.class);
            verify(chainQueryPort).isConfirmed(eq(TEST_CHAIN_ID), eq(TEST_TX_HASH), captor.capture());
            assertEquals(DEFAULT_CONFIRMATIONS_12, captor.getValue());
        }

        /**
         * 场景：请求携带 requiredConfirmations=6 时，确认校验收到请求覆盖值 6。
         * Scenario: a request-level override of 6 is honored by the confirmation check.
         */
        @Test
        @DisplayName("请求覆盖值优先于默认确认数 | request override takes precedence")
        void deposit_withOverrideUsesRequestConfirmations() {
            // Arrange：请求携带确认数覆盖值 6，确认通过 + 回执可查 + 动账成功
            DepositRequestDTO request = newValidRequest();
            request.setRequiredConfirmations(REQ_CONFIRMATIONS_6);
            when(chainQueryPort.isConfirmed(eq(TEST_CHAIN_ID), eq(TEST_TX_HASH), anyInt())).thenReturn(true);
            when(chainQueryPort.queryTxReceipt(TEST_CHAIN_ID, TEST_TX_HASH)).thenReturn(Optional.of(newConfirmedReceipt()));
            when(accountAppService.changeAmountWithFundFlow(request)).thenReturn(BIZ_NO);

            // Act：执行充值入账
            service.deposit(request);

            // Assert：捕获确认数参数，断言为请求覆盖值 6
            ArgumentCaptor<Integer> captor = ArgumentCaptor.forClass(Integer.class);
            verify(chainQueryPort).isConfirmed(eq(TEST_CHAIN_ID), eq(TEST_TX_HASH), captor.capture());
            assertEquals(REQ_CONFIRMATIONS_6, captor.getValue());
        }
    }

    /**
     * 凭证链字段：凭证携带链上证据支撑链上与链下对账。
     */
    @Nested
    @DisplayName("凭证链字段 | journal chain-evidence fields")
    class JournalChainEvidenceTest {

        /**
         * 场景：确认通过且回执可查时，凭证携带请求链字段与回执区块高度。
         * Scenario: the journal carries the requested chain fields and the receipt block number.
         */
        @Test
        @DisplayName("凭证携带链上证据 | journal carries chain evidence")
        void deposit_withConfirmedTx_journalCarriesChainEvidence() {
            // Arrange：请求携带 tokenAddress，确认通过 + 回执带区块高度 + 动账成功
            DepositRequestDTO request = newValidRequest();
            request.setTokenAddress(TEST_TOKEN_ADDRESS);
            when(chainQueryPort.isConfirmed(TEST_CHAIN_ID, TEST_TX_HASH, DEFAULT_CONFIRMATIONS_12)).thenReturn(true);
            when(chainQueryPort.queryTxReceipt(TEST_CHAIN_ID, TEST_TX_HASH)).thenReturn(Optional.of(newConfirmedReceipt()));
            when(accountAppService.changeAmountWithFundFlow(request)).thenReturn(BIZ_NO);

            // Act：执行充值入账
            service.deposit(request);

            // Assert：捕获过账请求，断言链字段与请求一致、区块高度与回执一致
            ArgumentCaptor<PostJournalRequestDTO> captor = ArgumentCaptor.forClass(PostJournalRequestDTO.class);
            verify(ledgerAppService).postJournal(captor.capture());
            PostJournalRequestDTO captured = captor.getValue();
            assertEquals(TEST_CHAIN_ID, captured.getChainId());
            assertEquals(TEST_TX_HASH, captured.getChainTxHash());
            assertEquals(TEST_BLOCK_NUMBER.longValue(), captured.getBlockNumber());
            assertEquals(TEST_TOKEN_ADDRESS, captured.getTokenAddress());
        }

        /**
         * 场景：回执缺失时凭证 blockNumber 置 null 仍允许入账（保守兜底）。
         * Scenario: when the receipt is missing the journal keeps a null block number and the deposit proceeds.
         */
        @Test
        @DisplayName("回执缺失时凭证区块高度为空仍入账 | missing receipt keeps null block number")
        void deposit_whenReceiptMissing_journalKeepsNullBlockNumber() {
            // Arrange：确认通过但回执查询返回空，动账成功
            DepositRequestDTO request = newValidRequest();
            when(chainQueryPort.isConfirmed(TEST_CHAIN_ID, TEST_TX_HASH, DEFAULT_CONFIRMATIONS_12)).thenReturn(true);
            when(chainQueryPort.queryTxReceipt(TEST_CHAIN_ID, TEST_TX_HASH)).thenReturn(Optional.empty());
            when(accountAppService.changeAmountWithFundFlow(request)).thenReturn(BIZ_NO);

            // Act：执行充值入账
            String result = service.deposit(request);

            // Assert：入账成功且凭证 blockNumber 为空
            assertEquals(BIZ_NO, result);
            ArgumentCaptor<PostJournalRequestDTO> captor = ArgumentCaptor.forClass(PostJournalRequestDTO.class);
            verify(ledgerAppService).postJournal(captor.capture());
            assertNull(captor.getValue().getBlockNumber());
        }
    }

    /**
     * 幂等：确认闸门执行后进入既有幂等路径，重复请求不重复动账。
     */
    @Nested
    @DisplayName("幂等 | idempotency")
    class IdempotencyTest {

        /**
         * 场景：重复 bizNo 时确认闸门每次执行，动账幂等返回原业务号且不重复入账。
         * Scenario: a duplicate bizNo reruns the gate, the crediting path returns the original bizNo and no double credit occurs.
         */
        @Test
        @DisplayName("重复业务号返回原业务号且不重复入账 | duplicate bizNo returns the original bizNo")
        void deposit_withDuplicateBizNo_returnsOriginalBizNoAndNoDoubleCredit() {
            // Arrange：确认通过 + 回执可查 + 动账幂等返回原业务号（唯一索引兜底）
            DepositRequestDTO request = newValidRequest();
            when(chainQueryPort.isConfirmed(TEST_CHAIN_ID, TEST_TX_HASH, DEFAULT_CONFIRMATIONS_12)).thenReturn(true);
            when(chainQueryPort.queryTxReceipt(TEST_CHAIN_ID, TEST_TX_HASH)).thenReturn(Optional.of(newConfirmedReceipt()));
            when(accountAppService.changeAmountWithFundFlow(request)).thenReturn(BIZ_NO);

            // Act：同一业务号重复入账两次
            String first = service.deposit(request);
            String second = service.deposit(request);

            // Assert：两次均返回原业务号，动账路径被调用两次但幂等返回相同结果（不重复加钱）
            assertEquals(BIZ_NO, first);
            assertEquals(BIZ_NO, second);
            verify(accountAppService, times(2)).changeAmountWithFundFlow(request);
        }
    }
}
