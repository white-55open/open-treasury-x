package io.github.open55.otx.application.withdraw.service.impl;

import io.github.open55.otx.application.fundflow.dto.request.CreateFundFlowRequest;
import io.github.open55.otx.application.fundflow.service.FundFlowAppService;
import io.github.open55.otx.application.ledger.dto.request.LedgerEntryRequestDTO;
import io.github.open55.otx.application.ledger.dto.request.PostJournalRequestDTO;
import io.github.open55.otx.application.ledger.dto.response.JournalDetailResponseDTO;
import io.github.open55.otx.application.ledger.service.LedgerAppService;
import io.github.open55.otx.application.withdraw.dto.request.WithdrawRequestDTO;
import io.github.open55.otx.application.withdraw.dto.response.WithdrawBroadcastResponseDTO;
import io.github.open55.otx.application.withdraw.dto.response.WithdrawSettleResponseDTO;
import io.github.open55.otx.application.withdraw.dto.response.WithdrawStatusResponseDTO;
import io.github.open55.otx.common.exception.BizException;
import io.github.open55.otx.domain.account.entity.AccountEntity;
import io.github.open55.otx.domain.account.repository.AccountRepo;
import io.github.open55.otx.domain.chain.port.BroadcastResult;
import io.github.open55.otx.domain.chain.port.SignRequest;
import io.github.open55.otx.domain.chain.port.SignedTx;
import io.github.open55.otx.domain.chain.port.SignerPort;
import io.github.open55.otx.domain.chain.port.TxBroadcastPort;
import io.github.open55.otx.domain.fundflow.enums.FundFlowDirectionEnum;
import io.github.open55.otx.domain.fundflow.enums.FundFlowTypeEnum;
import io.github.open55.otx.domain.ledger.enums.LedgerAccountCodeEnum;
import io.github.open55.otx.domain.ledger.enums.LedgerBizTypeEnum;
import io.github.open55.otx.domain.ledger.enums.LedgerEntryTypeEnum;
import io.github.open55.otx.domain.ledger.port.ChainQueryPort;
import io.github.open55.otx.domain.ledger.port.ChainTxReceipt;
import io.github.open55.otx.domain.withdraw.WithdrawRequestEntity;
import io.github.open55.otx.domain.withdraw.WithdrawRequestRepo;
import io.github.open55.otx.domain.withdraw.WithdrawRequestStatusEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * WithdrawAppServiceImpl 提现应用服务单元测试。
 * <p>
 * 覆盖冻结、结算、解冻三个用例的资金变更、流水快照、凭证映射、
 * 幂等处理（前置短路与唯一键冲突兜底）以及过账失败容错行为。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("WithdrawAppServiceImpl 提现应用服务单元测试 | WithdrawAppServiceImpl unit tests")
class WithdrawAppServiceImplTest {

    private static final Long TEST_UID = 12345L;
    private static final String CURRENCY_USDT = "USDT";
    private static final String BIZ_NO_FREEZE = "FREEZE-20260101-0001";
    private static final String BIZ_NO_SETTLE = "WITHDRAW-20260101-0001";
    private static final String BIZ_NO_UNFREEZE = "UNFREEZE-20260101-0001";
    private static final String BIZ_NO_BROADCAST = "BROADCAST-20260101-0001";
    private static final BigDecimal AMOUNT_50 = new BigDecimal("50");
    private static final BigDecimal AMOUNT_60 = new BigDecimal("60");
    private static final BigDecimal BALANCE_100 = new BigDecimal("100");
    private static final BigDecimal BALANCE_30 = new BigDecimal("30");
    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final String ERR_INSUFFICIENT_BALANCE = "INSUFFICIENT_BALANCE";
    private static final String ERR_INSUFFICIENT_FROZEN_BALANCE = "INSUFFICIENT_FROZEN_BALANCE";
    private static final String ERR_ACCOUNT_NOT_EXIST = "ACCOUNT_NOT_EXIST";
    private static final String ERR_FREEZE_AMOUNT_INVALID = "FREEZE_AMOUNT_INVALID";
    private static final String ERR_WITHDRAW_AMOUNT_INVALID = "WITHDRAW_AMOUNT_INVALID";
    private static final String ERR_UNFREEZE_AMOUNT_INVALID = "UNFREEZE_AMOUNT_INVALID";
    private static final String ERR_TX_SIGN_FAILED = "TX_SIGN_FAILED";
    private static final String ERR_TX_BROADCAST_FAILED = "TX_BROADCAST_FAILED";
    private static final String ERR_TX_NOT_CONFIRMED_YET = "TX_NOT_CONFIRMED_YET";
    private static final String ERR_TX_CHAIN_FAILED = "TX_CHAIN_FAILED";
    private static final String ERR_WITHDRAW_REQUEST_NOT_FOUND = "WITHDRAW_REQUEST_NOT_FOUND";
    private static final String ERR_WITHDRAW_REQUEST_STATUS_INVALID = "WITHDRAW_REQUEST_STATUS_INVALID";
    private static final String CHAIN_ID = "11155111";
    private static final String TO_ADDRESS = "0xUserToAddress0000000000000000000000000001";
    private static final String TOKEN_ADDRESS = "0xTokenAddress0000000000000000000000000001";
    private static final String HOT_WALLET_ADDRESS = "0xHotWalletAddress00000000000000000000000";
    private static final String TX_HASH = "0xabc123456789abcdef";
    private static final String RAW_TRANSACTION = "0xf86c...";
    private static final int REQUIRED_CONFIRMATIONS = 12;
    private static final BigInteger NONCE_5 = BigInteger.valueOf(5);
    private static final BigInteger AMOUNT_50_WEI = new BigInteger("50000000000000000000");
    private static final BigInteger BLOCK_NUMBER = BigInteger.valueOf(1000);

    @Mock
    private AccountRepo accountRepo;

    @Mock
    private FundFlowAppService fundFlowAppService;

    @Mock
    private LedgerAppService ledgerAppService;

    @Mock
    private WithdrawRequestRepo withdrawRequestRepo;

    @Mock
    private SignerPort signerPort;

    @Mock
    private TxBroadcastPort txBroadcastPort;

    @Mock
    private ChainQueryPort chainQueryPort;

    @Captor
    private ArgumentCaptor<AccountEntity> accountCaptor;

    @Captor
    private ArgumentCaptor<CreateFundFlowRequest> fundFlowCaptor;

    @Captor
    private ArgumentCaptor<PostJournalRequestDTO> journalCaptor;

    @Captor
    private ArgumentCaptor<SignRequest> signRequestCaptor;

    private WithdrawAppServiceImpl service;

    @BeforeEach
    void setUp() throws Exception {
        service = new WithdrawAppServiceImpl(accountRepo, fundFlowAppService, ledgerAppService,
                withdrawRequestRepo, signerPort, txBroadcastPort, chainQueryPort);
        setField(service, "self", service);
        setField(service, "requiredConfirmations", REQUIRED_CONFIRMATIONS);
        setField(service, "fromAddress", HOT_WALLET_ADDRESS);
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static WithdrawRequestDTO newRequest(String bizNo, BigDecimal amount) {
        WithdrawRequestDTO request = new WithdrawRequestDTO();
        request.setUid(TEST_UID);
        request.setBizNo(bizNo);
        request.setAmount(amount);
        request.setCurrency(CURRENCY_USDT);
        return request;
    }

    private static AccountEntity newAccount(BigDecimal available, BigDecimal frozen) {
        AccountEntity entity = AccountEntity.builder()
                .uid(TEST_UID)
                .availableBalance(available)
                .frozenBalance(frozen)
                .build();
        entity.setId(1L);
        return entity;
    }

    private static void assertJournalEntries(PostJournalRequestDTO journal, LedgerAccountCodeEnum debitAccount,
                                             Long debitUid, LedgerAccountCodeEnum creditAccount, Long creditUid) {
        assertEquals(2, journal.getEntries().size());
        LedgerEntryRequestDTO debit = journal.getEntries().get(0);
        assertEquals(debitAccount, debit.getAccountCode());
        assertEquals(LedgerEntryTypeEnum.DEBIT, debit.getEntryType());
        assertEquals(AMOUNT_50, debit.getAmount());
        assertEquals(debitUid, debit.getUid());

        LedgerEntryRequestDTO credit = journal.getEntries().get(1);
        assertEquals(creditAccount, credit.getAccountCode());
        assertEquals(LedgerEntryTypeEnum.CREDIT, credit.getEntryType());
        assertEquals(AMOUNT_50, credit.getAmount());
        assertEquals(creditUid, credit.getUid());
    }

    @Nested
    @DisplayName("freeze 冻结用例 | freeze use case")
    class Freeze {

        /**
         * 场景：可用余额充足时冻结成功。
         * Scenario: freeze succeeds with sufficient available balance.
         * 断言双余额联动、FREEZE 流水方向与快照、冻结凭证分录科目与用户归属。
         */
        @Test
        @DisplayName("可用余额充足时冻结成功 | freeze succeeds with sufficient balance")
        void freeze_withSufficientBalance_freezesAndRecordsFlowAndPostsJournal() {
            WithdrawRequestDTO request = newRequest(BIZ_NO_FREEZE, AMOUNT_50);
            when(fundFlowAppService.existsBizNo(BIZ_NO_FREEZE)).thenReturn(false);
            when(accountRepo.findByUid(TEST_UID)).thenReturn(newAccount(BALANCE_100, ZERO));

            String result = service.freeze(request);

            assertEquals(BIZ_NO_FREEZE, result);
            verify(accountRepo).update(accountCaptor.capture());
            assertEquals(AMOUNT_50, accountCaptor.getValue().getAvailableBalance());
            assertEquals(AMOUNT_50, accountCaptor.getValue().getFrozenBalance());
            verify(fundFlowAppService).record(fundFlowCaptor.capture());
            CreateFundFlowRequest flow = fundFlowCaptor.getValue();
            assertEquals(FundFlowDirectionEnum.OUT, flow.getDirection());
            assertEquals(FundFlowTypeEnum.FREEZE, flow.getType());
            assertEquals(BALANCE_100, flow.getBalanceBefore());
            assertEquals(AMOUNT_50, flow.getBalanceAfter());
            verify(ledgerAppService).postJournal(journalCaptor.capture());
            PostJournalRequestDTO journal = journalCaptor.getValue();
            assertEquals(LedgerBizTypeEnum.FREEZE, journal.getBizType());
            assertJournalEntries(journal, LedgerAccountCodeEnum.USER_AVAILABLE, TEST_UID,
                    LedgerAccountCodeEnum.USER_FROZEN, TEST_UID);
        }

        /**
         * 场景：冻结金额超过可用余额时被拒绝。
         * Scenario: freeze is rejected when amount exceeds available balance.
         * 断言抛 INSUFFICIENT_BALANCE 且无更新、无流水、无过账。
         */
        @Test
        @DisplayName("冻结金额超过可用余额被拒绝 | freeze rejected on insufficient balance")
        void freeze_withInsufficientBalance_throwsAndNoSideEffects() {
            WithdrawRequestDTO request = newRequest(BIZ_NO_FREEZE, AMOUNT_60);
            when(fundFlowAppService.existsBizNo(BIZ_NO_FREEZE)).thenReturn(false);
            when(accountRepo.findByUid(TEST_UID)).thenReturn(newAccount(AMOUNT_50, ZERO));

            BizException ex = assertThrows(BizException.class, () -> service.freeze(request));

            assertEquals(ERR_INSUFFICIENT_BALANCE, ex.getErrorCode());
            verify(accountRepo, never()).update(any());
            verify(fundFlowAppService, never()).record(any());
            verify(ledgerAppService, never()).postJournal(any());
        }

        /**
         * 场景：冻结金额非法（null 或非正数）时被拒绝。
         * Scenario: freeze is rejected with an invalid amount.
         * 断言抛 FREEZE_AMOUNT_INVALID 且不触发幂等检查与任何副作用。
         */
        @Test
        @DisplayName("冻结金额非法被拒绝 | freeze rejected on invalid amount")
        void freeze_withInvalidAmount_throwsFreezeAmountInvalid() {
            WithdrawRequestDTO request = newRequest(BIZ_NO_FREEZE, new BigDecimal("-1"));

            BizException ex = assertThrows(BizException.class, () -> service.freeze(request));

            assertEquals(ERR_FREEZE_AMOUNT_INVALID, ex.getErrorCode());
            verify(fundFlowAppService, never()).existsBizNo(any());
            verify(accountRepo, never()).update(any());
            verify(fundFlowAppService, never()).record(any());
            verify(ledgerAppService, never()).postJournal(any());
        }

        /**
         * 场景：相同 bizNo 已存在时幂等短路。
         * Scenario: freeze returns early when bizNo already exists.
         * 断言直接返回 bizNo 且不触发查询、更新、流水与过账副作用。
         */
        @Test
        @DisplayName("相同 bizNo 已存在时幂等短路 | freeze returns early for existing bizNo")
        void freeze_withExistingBizNo_returnsEarly() {
            WithdrawRequestDTO request = newRequest(BIZ_NO_FREEZE, AMOUNT_50);
            when(fundFlowAppService.existsBizNo(BIZ_NO_FREEZE)).thenReturn(true);

            String result = service.freeze(request);

            assertEquals(BIZ_NO_FREEZE, result);
            verify(accountRepo, never()).findByUid(any());
            verify(accountRepo, never()).update(any());
            verify(fundFlowAppService, never()).record(any());
            verify(ledgerAppService, never()).postJournal(any());
        }

        /**
         * 场景：并发重复提交触发流水唯一键冲突时兜底为幂等成功。
         * Scenario: freeze treats duplicate key conflict as idempotent success.
         * 断言入口不抛出、不更新账户且继续走过账（postJournal 自身幂等）。
         */
        @Test
        @DisplayName("流水唯一键冲突兜底为幂等成功 | freeze treats duplicate key as idempotent")
        void freeze_withDuplicateKeyConflict_returnsBizNo() {
            WithdrawRequestDTO request = newRequest(BIZ_NO_FREEZE, AMOUNT_50);
            when(fundFlowAppService.existsBizNo(BIZ_NO_FREEZE)).thenReturn(false);
            when(accountRepo.findByUid(TEST_UID)).thenReturn(newAccount(BALANCE_100, ZERO));
            doThrow(new DuplicateKeyException("uk_biz_no")).when(fundFlowAppService).record(any());

            String result = service.freeze(request);

            assertEquals(BIZ_NO_FREEZE, result);
            verify(accountRepo, never()).update(any());
            verify(ledgerAppService).postJournal(any());
        }

        /**
         * 场景：总账过账失败不影响资金冻结结果。
         * Scenario: freeze still succeeds when journal posting fails.
         * 断言用例不抛出且余额已变更，过账异常仅被记录。
         */
        @Test
        @DisplayName("过账失败不影响冻结结果 | freeze tolerates posting failure")
        void freeze_whenPostingFails_stillReturnsOk() {
            WithdrawRequestDTO request = newRequest(BIZ_NO_FREEZE, AMOUNT_50);
            when(fundFlowAppService.existsBizNo(BIZ_NO_FREEZE)).thenReturn(false);
            when(accountRepo.findByUid(TEST_UID)).thenReturn(newAccount(BALANCE_100, ZERO));
            doThrow(new RuntimeException("RPC timeout")).when(ledgerAppService).postJournal(any());

            String result = service.freeze(request);

            assertEquals(BIZ_NO_FREEZE, result);
            verify(accountRepo).update(accountCaptor.capture());
            assertEquals(AMOUNT_50, accountCaptor.getValue().getAvailableBalance());
        }
    }

    @Nested
    @DisplayName("withdraw 结算用例 | withdraw use case")
    class Withdraw {

        /**
         * 场景：冻结余额充足时结算成功，仅动冻结余额。
         * Scenario: withdraw succeeds with sufficient frozen balance.
         * 断言冻结扣减、可用不变、WITHDRAW 流水冻结快照、
         * 结算凭证映射为借记 USER_FROZEN 贷记 WITHDRAW_IN_TRANSIT。
         */
        @Test
        @DisplayName("结算成功且仅动冻结余额 | withdraw succeeds deducting frozen only")
        void withdraw_withSufficientFrozenBalance_deductsFrozenAndRecordsFlowAndPostsJournal() {
            WithdrawRequestDTO request = newRequest(BIZ_NO_SETTLE, AMOUNT_50);
            when(fundFlowAppService.existsBizNo(BIZ_NO_SETTLE)).thenReturn(false);
            when(accountRepo.findByUid(TEST_UID)).thenReturn(newAccount(BALANCE_100, AMOUNT_50));

            String result = service.withdraw(request);

            assertEquals(BIZ_NO_SETTLE, result);
            verify(accountRepo).update(accountCaptor.capture());
            assertEquals(BALANCE_100, accountCaptor.getValue().getAvailableBalance());
            assertEquals(ZERO, accountCaptor.getValue().getFrozenBalance());
            verify(fundFlowAppService).record(fundFlowCaptor.capture());
            CreateFundFlowRequest flow = fundFlowCaptor.getValue();
            assertEquals(FundFlowDirectionEnum.OUT, flow.getDirection());
            assertEquals(FundFlowTypeEnum.WITHDRAW, flow.getType());
            assertEquals(AMOUNT_50, flow.getBalanceBefore());
            assertEquals(ZERO, flow.getBalanceAfter());
            verify(ledgerAppService).postJournal(journalCaptor.capture());
            PostJournalRequestDTO journal = journalCaptor.getValue();
            assertEquals(LedgerBizTypeEnum.WITHDRAW_ONCHAIN, journal.getBizType());
            assertJournalEntries(journal, LedgerAccountCodeEnum.USER_FROZEN, TEST_UID,
                    LedgerAccountCodeEnum.WITHDRAW_IN_TRANSIT, null);
        }

        /**
         * 场景：结算金额恰好等于冻结余额时允许全额结算。
         * Scenario: withdraw allows settling the full frozen balance.
         * 断言冻结余额归零且不抛异常。
         */
        @Test
        @DisplayName("结算金额等于冻结余额可全额结算 | withdraw equals frozen balance succeeds")
        void withdraw_withAmountEqualsFrozenBalance_succeeds() {
            WithdrawRequestDTO request = newRequest(BIZ_NO_SETTLE, AMOUNT_50);
            when(fundFlowAppService.existsBizNo(BIZ_NO_SETTLE)).thenReturn(false);
            when(accountRepo.findByUid(TEST_UID)).thenReturn(newAccount(AMOUNT_50, AMOUNT_50));

            String result = service.withdraw(request);

            assertEquals(BIZ_NO_SETTLE, result);
            verify(accountRepo).update(accountCaptor.capture());
            assertEquals(ZERO, accountCaptor.getValue().getFrozenBalance());
        }

        /**
         * 场景：可用余额为 0 时结算仍成功（结算只校验冻结余额）。
         * Scenario: withdraw succeeds even with zero available balance.
         * 断言不抛 INSUFFICIENT_BALANCE 且冻结余额正常扣减。
         */
        @Test
        @DisplayName("可用余额为 0 时结算仍成功 | withdraw succeeds with zero available balance")
        void withdraw_withZeroAvailableBalance_succeeds() {
            WithdrawRequestDTO request = newRequest(BIZ_NO_SETTLE, AMOUNT_50);
            when(fundFlowAppService.existsBizNo(BIZ_NO_SETTLE)).thenReturn(false);
            when(accountRepo.findByUid(TEST_UID)).thenReturn(newAccount(ZERO, AMOUNT_50));

            String result = service.withdraw(request);

            assertEquals(BIZ_NO_SETTLE, result);
            verify(accountRepo).update(accountCaptor.capture());
            assertEquals(ZERO, accountCaptor.getValue().getFrozenBalance());
            assertEquals(ZERO, accountCaptor.getValue().getAvailableBalance());
        }

        /**
         * 场景：冻结余额不足时结算被拒绝。
         * Scenario: withdraw is rejected on insufficient frozen balance.
         * 断言抛 INSUFFICIENT_FROZEN_BALANCE 且无更新、无流水、无过账。
         */
        @Test
        @DisplayName("冻结余额不足被拒绝 | withdraw rejected on insufficient frozen balance")
        void withdraw_withInsufficientFrozenBalance_throwsAndNoSideEffects() {
            WithdrawRequestDTO request = newRequest(BIZ_NO_SETTLE, AMOUNT_50);
            when(fundFlowAppService.existsBizNo(BIZ_NO_SETTLE)).thenReturn(false);
            when(accountRepo.findByUid(TEST_UID)).thenReturn(newAccount(BALANCE_100, BALANCE_30));

            BizException ex = assertThrows(BizException.class, () -> service.withdraw(request));

            assertEquals(ERR_INSUFFICIENT_FROZEN_BALANCE, ex.getErrorCode());
            verify(accountRepo, never()).update(any());
            verify(fundFlowAppService, never()).record(any());
            verify(ledgerAppService, never()).postJournal(any());
        }

        /**
         * 场景：结算金额非法（null 或非正数）时被拒绝。
         * Scenario: withdraw is rejected with an invalid amount.
         * 断言抛 WITHDRAW_AMOUNT_INVALID 且不触发幂等检查与任何副作用。
         */
        @Test
        @DisplayName("结算金额非法被拒绝 | withdraw rejected on invalid amount")
        void withdraw_withInvalidAmount_throwsWithdrawAmountInvalid() {
            WithdrawRequestDTO request = newRequest(BIZ_NO_SETTLE, new BigDecimal("-1"));

            BizException ex = assertThrows(BizException.class, () -> service.withdraw(request));

            assertEquals(ERR_WITHDRAW_AMOUNT_INVALID, ex.getErrorCode());
            verify(fundFlowAppService, never()).existsBizNo(any());
            verify(accountRepo, never()).update(any());
            verify(fundFlowAppService, never()).record(any());
            verify(ledgerAppService, never()).postJournal(any());
        }

        /**
         * 场景：相同 bizNo 已存在时幂等短路。
         * Scenario: withdraw returns early when bizNo already exists.
         * 断言直接返回 bizNo 且不触发任何副作用。
         */
        @Test
        @DisplayName("相同 bizNo 已存在时幂等短路 | withdraw returns early for existing bizNo")
        void withdraw_withExistingBizNo_returnsEarly() {
            WithdrawRequestDTO request = newRequest(BIZ_NO_SETTLE, AMOUNT_50);
            when(fundFlowAppService.existsBizNo(BIZ_NO_SETTLE)).thenReturn(true);

            String result = service.withdraw(request);

            assertEquals(BIZ_NO_SETTLE, result);
            verify(accountRepo, never()).findByUid(any());
            verify(accountRepo, never()).update(any());
            verify(fundFlowAppService, never()).record(any());
            verify(ledgerAppService, never()).postJournal(any());
        }

        /**
         * 场景：并发重复提交触发流水唯一键冲突时兜底为幂等成功。
         * Scenario: withdraw treats duplicate key conflict as idempotent success.
         * 断言入口不抛出、不更新账户且继续走过账。
         */
        @Test
        @DisplayName("流水唯一键冲突兜底为幂等成功 | withdraw treats duplicate key as idempotent")
        void withdraw_withDuplicateKeyConflict_returnsBizNo() {
            WithdrawRequestDTO request = newRequest(BIZ_NO_SETTLE, AMOUNT_50);
            when(fundFlowAppService.existsBizNo(BIZ_NO_SETTLE)).thenReturn(false);
            when(accountRepo.findByUid(TEST_UID)).thenReturn(newAccount(BALANCE_100, AMOUNT_50));
            doThrow(new DuplicateKeyException("uk_biz_no")).when(fundFlowAppService).record(any());

            String result = service.withdraw(request);

            assertEquals(BIZ_NO_SETTLE, result);
            verify(accountRepo, never()).update(any());
            verify(ledgerAppService).postJournal(any());
        }

        /**
         * 场景：总账过账失败不影响结算结果。
         * Scenario: withdraw still succeeds when journal posting fails.
         * 断言用例不抛出且冻结余额已扣减。
         */
        @Test
        @DisplayName("过账失败不影响结算结果 | withdraw tolerates posting failure")
        void withdraw_whenPostingFails_stillReturnsOk() {
            WithdrawRequestDTO request = newRequest(BIZ_NO_SETTLE, AMOUNT_50);
            when(fundFlowAppService.existsBizNo(BIZ_NO_SETTLE)).thenReturn(false);
            when(accountRepo.findByUid(TEST_UID)).thenReturn(newAccount(BALANCE_100, AMOUNT_50));
            doThrow(new RuntimeException("RPC timeout")).when(ledgerAppService).postJournal(any());

            String result = service.withdraw(request);

            assertEquals(BIZ_NO_SETTLE, result);
            verify(accountRepo).update(accountCaptor.capture());
            assertEquals(ZERO, accountCaptor.getValue().getFrozenBalance());
        }
    }

    @Nested
    @DisplayName("unfreeze 解冻用例 | unfreeze use case")
    class Unfreeze {

        /**
         * 场景：冻结余额充足时解冻成功。
         * Scenario: unfreeze succeeds with sufficient frozen balance.
         * 断言冻结释放、可用增加、UNFREEZE 流水方向与快照、解冻凭证分录。
         */
        @Test
        @DisplayName("解冻成功释放冻结余额 | unfreeze succeeds releasing frozen balance")
        void unfreeze_withSufficientFrozenBalance_releasesAndRecordsFlowAndPostsJournal() {
            WithdrawRequestDTO request = newRequest(BIZ_NO_UNFREEZE, AMOUNT_50);
            when(fundFlowAppService.existsBizNo(BIZ_NO_UNFREEZE)).thenReturn(false);
            when(accountRepo.findByUid(TEST_UID)).thenReturn(newAccount(ZERO, AMOUNT_50));

            String result = service.unfreeze(request);

            assertEquals(BIZ_NO_UNFREEZE, result);
            verify(accountRepo).update(accountCaptor.capture());
            assertEquals(AMOUNT_50, accountCaptor.getValue().getAvailableBalance());
            assertEquals(ZERO, accountCaptor.getValue().getFrozenBalance());
            verify(fundFlowAppService).record(fundFlowCaptor.capture());
            CreateFundFlowRequest flow = fundFlowCaptor.getValue();
            assertEquals(FundFlowDirectionEnum.IN, flow.getDirection());
            assertEquals(FundFlowTypeEnum.UNFREEZE, flow.getType());
            assertEquals(ZERO, flow.getBalanceBefore());
            assertEquals(AMOUNT_50, flow.getBalanceAfter());
            verify(ledgerAppService).postJournal(journalCaptor.capture());
            PostJournalRequestDTO journal = journalCaptor.getValue();
            assertEquals(LedgerBizTypeEnum.UNFREEZE, journal.getBizType());
            assertJournalEntries(journal, LedgerAccountCodeEnum.USER_FROZEN, TEST_UID,
                    LedgerAccountCodeEnum.USER_AVAILABLE, TEST_UID);
        }

        /**
         * 场景：冻结余额不足时解冻被拒绝。
         * Scenario: unfreeze is rejected on insufficient frozen balance.
         * 断言抛 INSUFFICIENT_FROZEN_BALANCE 且无更新、无流水、无过账。
         */
        @Test
        @DisplayName("冻结余额不足被拒绝 | unfreeze rejected on insufficient frozen balance")
        void unfreeze_withInsufficientFrozenBalance_throwsAndNoSideEffects() {
            WithdrawRequestDTO request = newRequest(BIZ_NO_UNFREEZE, AMOUNT_50);
            when(fundFlowAppService.existsBizNo(BIZ_NO_UNFREEZE)).thenReturn(false);
            when(accountRepo.findByUid(TEST_UID)).thenReturn(newAccount(ZERO, BALANCE_30));

            BizException ex = assertThrows(BizException.class, () -> service.unfreeze(request));

            assertEquals(ERR_INSUFFICIENT_FROZEN_BALANCE, ex.getErrorCode());
            verify(accountRepo, never()).update(any());
            verify(fundFlowAppService, never()).record(any());
            verify(ledgerAppService, never()).postJournal(any());
        }

        /**
         * 场景：解冻金额非法（null 或非正数）时被拒绝。
         * Scenario: unfreeze is rejected with an invalid amount.
         * 断言抛 UNFREEZE_AMOUNT_INVALID 且不触发幂等检查与任何副作用。
         */
        @Test
        @DisplayName("解冻金额非法被拒绝 | unfreeze rejected on invalid amount")
        void unfreeze_withInvalidAmount_throwsUnfreezeAmountInvalid() {
            WithdrawRequestDTO request = newRequest(BIZ_NO_UNFREEZE, new BigDecimal("-1"));

            BizException ex = assertThrows(BizException.class, () -> service.unfreeze(request));

            assertEquals(ERR_UNFREEZE_AMOUNT_INVALID, ex.getErrorCode());
            verify(fundFlowAppService, never()).existsBizNo(any());
            verify(accountRepo, never()).update(any());
            verify(fundFlowAppService, never()).record(any());
            verify(ledgerAppService, never()).postJournal(any());
        }

        /**
         * 场景：相同 bizNo 已存在时幂等短路。
         * Scenario: unfreeze returns early when bizNo already exists.
         * 断言直接返回 bizNo 且不触发任何副作用。
         */
        @Test
        @DisplayName("相同 bizNo 已存在时幂等短路 | unfreeze returns early for existing bizNo")
        void unfreeze_withExistingBizNo_returnsEarly() {
            WithdrawRequestDTO request = newRequest(BIZ_NO_UNFREEZE, AMOUNT_50);
            when(fundFlowAppService.existsBizNo(BIZ_NO_UNFREEZE)).thenReturn(true);

            String result = service.unfreeze(request);

            assertEquals(BIZ_NO_UNFREEZE, result);
            verify(accountRepo, never()).findByUid(any());
            verify(accountRepo, never()).update(any());
            verify(fundFlowAppService, never()).record(any());
            verify(ledgerAppService, never()).postJournal(any());
        }

        /**
         * 场景：并发重复提交触发流水唯一键冲突时兜底为幂等成功。
         * Scenario: unfreeze treats duplicate key conflict as idempotent success.
         * 断言入口不抛出、不更新账户且继续走过账。
         */
        @Test
        @DisplayName("流水唯一键冲突兜底为幂等成功 | unfreeze treats duplicate key as idempotent")
        void unfreeze_withDuplicateKeyConflict_returnsBizNo() {
            WithdrawRequestDTO request = newRequest(BIZ_NO_UNFREEZE, AMOUNT_50);
            when(fundFlowAppService.existsBizNo(BIZ_NO_UNFREEZE)).thenReturn(false);
            when(accountRepo.findByUid(TEST_UID)).thenReturn(newAccount(ZERO, AMOUNT_50));
            doThrow(new DuplicateKeyException("uk_biz_no")).when(fundFlowAppService).record(any());

            String result = service.unfreeze(request);

            assertEquals(BIZ_NO_UNFREEZE, result);
            verify(accountRepo, never()).update(any());
            verify(ledgerAppService).postJournal(any());
        }

        /**
         * 场景：总账过账失败不影响解冻结果。
         * Scenario: unfreeze still succeeds when journal posting fails.
         * 断言用例不抛出且冻结余额已释放。
         */
        @Test
        @DisplayName("过账失败不影响解冻结果 | unfreeze tolerates posting failure")
        void unfreeze_whenPostingFails_stillReturnsOk() {
            WithdrawRequestDTO request = newRequest(BIZ_NO_UNFREEZE, AMOUNT_50);
            when(fundFlowAppService.existsBizNo(BIZ_NO_UNFREEZE)).thenReturn(false);
            when(accountRepo.findByUid(TEST_UID)).thenReturn(newAccount(ZERO, AMOUNT_50));
            doThrow(new RuntimeException("RPC timeout")).when(ledgerAppService).postJournal(any());

            String result = service.unfreeze(request);

            assertEquals(BIZ_NO_UNFREEZE, result);
            verify(accountRepo).update(accountCaptor.capture());
            assertEquals(ZERO, accountCaptor.getValue().getFrozenBalance());
            assertEquals(AMOUNT_50, accountCaptor.getValue().getAvailableBalance());
        }
    }

    @Nested
    @DisplayName("broadcast 广播用例 | broadcast use case")
    class Broadcast {

        /**
         * 场景：签名与广播均成功时返回交易哈希并创建 DRAFT 凭证。
         * Scenario: broadcast succeeds and creates a DRAFT journal with the tx hash.
         * 断言请求置 BROADCASTED、DRAFT 凭证映射为借记 USER_FROZEN 贷记
         * WITHDRAW_IN_TRANSIT 且携带链上字段、无任何余额扣减。
         */
        @Test
        @DisplayName("广播成功返回交易哈希并创建 DRAFT 凭证 | broadcast succeeds with tx hash and draft journal")
        void broadcast_broadcastSuccess_returnsTxHashAndCreatesDraftJournal() {
            WithdrawRequestDTO request = newBroadcastRequest();
            WithdrawRequestEntity entity = newPendingEntity();
            when(withdrawRequestRepo.existsByBizNo(BIZ_NO_BROADCAST)).thenReturn(false);
            when(withdrawRequestRepo.findByBizNo(BIZ_NO_BROADCAST)).thenReturn(Optional.of(entity));
            when(accountRepo.findByUid(TEST_UID)).thenReturn(newAccount(BALANCE_100, AMOUNT_50));
            when(txBroadcastPort.currentNonce(CHAIN_ID, HOT_WALLET_ADDRESS)).thenReturn(NONCE_5);
            when(signerPort.sign(any(SignRequest.class)))
                    .thenReturn(new SignedTx(CHAIN_ID, RAW_TRANSACTION, HOT_WALLET_ADDRESS, TO_ADDRESS));
            when(txBroadcastPort.broadcast(any(SignedTx.class)))
                    .thenReturn(new BroadcastResult(CHAIN_ID, TX_HASH, HOT_WALLET_ADDRESS, TO_ADDRESS));

            WithdrawBroadcastResponseDTO result = service.broadcast(request);

            assertEquals(BIZ_NO_BROADCAST, result.getBizNo());
            assertEquals(TX_HASH, result.getTxHash());
            assertEquals("BROADCASTED", result.getStatus());
            assertEquals(WithdrawRequestStatusEnum.BROADCASTED, entity.getStatus());
            assertEquals(TX_HASH, entity.getTxHash());

            // 签名请求：链 ID、目标地址、金额按 18 位精度转换、代币地址透传
            verify(signerPort).sign(signRequestCaptor.capture());
            SignRequest signRequest = signRequestCaptor.getValue();
            assertEquals(CHAIN_ID, signRequest.getChainId());
            assertEquals(HOT_WALLET_ADDRESS, signRequest.getFromAddress());
            assertEquals(TO_ADDRESS, signRequest.getToAddress());
            assertEquals(AMOUNT_50_WEI, signRequest.getAmountWei());
            assertEquals(TOKEN_ADDRESS, signRequest.getTokenAddress());

            // DRAFT 凭证：WITHDRAW_ONCHAIN、借记 USER_FROZEN、链上字段透传
            verify(ledgerAppService).createDraftJournal(journalCaptor.capture());
            PostJournalRequestDTO journal = journalCaptor.getValue();
            assertEquals(LedgerBizTypeEnum.WITHDRAW_ONCHAIN, journal.getBizType());
            assertEquals(CHAIN_ID, journal.getChainId());
            assertEquals(TX_HASH, journal.getChainTxHash());
            assertEquals(TOKEN_ADDRESS, journal.getTokenAddress());
            assertJournalEntries(journal, LedgerAccountCodeEnum.USER_FROZEN, TEST_UID,
                    LedgerAccountCodeEnum.WITHDRAW_IN_TRANSIT, null);

            // 广播编排不触碰账户余额
            verify(accountRepo, never()).update(any());
            verify(fundFlowAppService, never()).record(any());
        }

        /**
         * 场景：广播失败时请求置 FAILED 且不创建凭证。
         * Scenario: broadcast failure marks the request FAILED without a journal.
         * 断言抛 TX_BROADCAST_FAILED、请求状态 FAILED、无凭证创建。
         */
        @Test
        @DisplayName("广播失败置 FAILED 且不创建凭证 | broadcast failure marks FAILED without journal")
        void broadcast_broadcastFailure_throwsTxBroadcastFailedAndMarksFailed() {
            WithdrawRequestDTO request = newBroadcastRequest();
            WithdrawRequestEntity entity = newPendingEntity();
            when(withdrawRequestRepo.existsByBizNo(BIZ_NO_BROADCAST)).thenReturn(false);
            when(withdrawRequestRepo.findByBizNo(BIZ_NO_BROADCAST)).thenReturn(Optional.of(entity));
            when(accountRepo.findByUid(TEST_UID)).thenReturn(newAccount(BALANCE_100, AMOUNT_50));
            when(txBroadcastPort.currentNonce(CHAIN_ID, HOT_WALLET_ADDRESS)).thenReturn(NONCE_5);
            when(signerPort.sign(any(SignRequest.class)))
                    .thenReturn(new SignedTx(CHAIN_ID, RAW_TRANSACTION, HOT_WALLET_ADDRESS, TO_ADDRESS));
            when(txBroadcastPort.broadcast(any(SignedTx.class)))
                    .thenThrow(new RuntimeException("RPC timeout"));

            BizException ex = assertThrows(BizException.class, () -> service.broadcast(request));

            assertEquals(ERR_TX_BROADCAST_FAILED, ex.getErrorCode());
            assertEquals(WithdrawRequestStatusEnum.FAILED, entity.getStatus());
            verify(withdrawRequestRepo).update(entity);
            verify(ledgerAppService, never()).createDraftJournal(any());
            verify(accountRepo, never()).update(any());
        }

        /**
         * 场景：签名失败时请求置 FAILED 且不创建凭证。
         * Scenario: signing failure marks the request FAILED without a journal.
         * 断言抛 TX_SIGN_FAILED、请求状态 FAILED、无凭证创建。
         */
        @Test
        @DisplayName("签名失败置 FAILED 且不创建凭证 | signing failure marks FAILED without journal")
        void broadcast_signFailure_throwsTxSignFailed() {
            WithdrawRequestDTO request = newBroadcastRequest();
            WithdrawRequestEntity entity = newPendingEntity();
            when(withdrawRequestRepo.existsByBizNo(BIZ_NO_BROADCAST)).thenReturn(false);
            when(withdrawRequestRepo.findByBizNo(BIZ_NO_BROADCAST)).thenReturn(Optional.of(entity));
            when(accountRepo.findByUid(TEST_UID)).thenReturn(newAccount(BALANCE_100, AMOUNT_50));
            when(txBroadcastPort.currentNonce(CHAIN_ID, HOT_WALLET_ADDRESS)).thenReturn(NONCE_5);
            when(signerPort.sign(any(SignRequest.class)))
                    .thenThrow(new RuntimeException("keystore password error"));

            BizException ex = assertThrows(BizException.class, () -> service.broadcast(request));

            assertEquals(ERR_TX_SIGN_FAILED, ex.getErrorCode());
            assertEquals(WithdrawRequestStatusEnum.FAILED, entity.getStatus());
            verify(withdrawRequestRepo).update(entity);
            verify(ledgerAppService, never()).createDraftJournal(any());
            verify(txBroadcastPort, never()).broadcast(any());
        }

        /**
         * 场景：相同 bizNo 已存在时幂等返回已有请求的广播结果。
         * Scenario: broadcast returns the existing request for a duplicate bizNo.
         * 断言不触发签名、广播与凭证创建等任何副作用。
         */
        @Test
        @DisplayName("相同 bizNo 幂等返回已有广播结果 | broadcast returns existing result for duplicate bizNo")
        void broadcast_duplicateBizNo_returnsExistingRequest() {
            WithdrawRequestDTO request = newBroadcastRequest();
            WithdrawRequestEntity entity = newBroadcastedEntity();
            when(withdrawRequestRepo.existsByBizNo(BIZ_NO_BROADCAST)).thenReturn(true);
            when(withdrawRequestRepo.findByBizNo(BIZ_NO_BROADCAST)).thenReturn(Optional.of(entity));

            WithdrawBroadcastResponseDTO result = service.broadcast(request);

            assertEquals(BIZ_NO_BROADCAST, result.getBizNo());
            assertEquals(TX_HASH, result.getTxHash());
            assertEquals("BROADCASTED", result.getStatus());
            verify(signerPort, never()).sign(any());
            verify(txBroadcastPort, never()).broadcast(any());
            verify(ledgerAppService, never()).createDraftJournal(any());
            verify(accountRepo, never()).update(any());
        }

        /**
         * 场景：冻结余额不足以覆盖提现金额时广播被拒绝。
         * Scenario: broadcast is rejected when frozen balance is insufficient.
         * 断言抛 INSUFFICIENT_FROZEN_BALANCE 且不落库、不查询 nonce、不签名、不广播。
         */
        @Test
        @DisplayName("冻结余额不足广播被拒绝 | broadcast rejected on insufficient frozen balance")
        void broadcast_withoutFrozenBalance_throwsInsufficientFrozenBalance() {
            WithdrawRequestDTO request = newBroadcastRequest();
            when(withdrawRequestRepo.existsByBizNo(BIZ_NO_BROADCAST)).thenReturn(false);
            when(accountRepo.findByUid(TEST_UID)).thenReturn(newAccount(BALANCE_100, BALANCE_30));

            BizException ex = assertThrows(BizException.class, () -> service.broadcast(request));

            assertEquals(ERR_INSUFFICIENT_FROZEN_BALANCE, ex.getErrorCode());
            verify(withdrawRequestRepo, never()).save(any());
            verify(txBroadcastPort, never()).currentNonce(any(), any());
            verify(signerPort, never()).sign(any());
            verify(txBroadcastPort, never()).broadcast(any());
            verify(ledgerAppService, never()).createDraftJournal(any());
        }

        /**
         * 场景：账户不存在时广播被拒绝。
         * Scenario: broadcast is rejected when the account does not exist.
         * 断言抛 ACCOUNT_NOT_EXIST 且不落库、不签名、不广播。
         */
        @Test
        @DisplayName("账户不存在广播被拒绝 | broadcast rejected when account not found")
        void broadcast_accountNotFound_throwsAccountNotExist() {
            WithdrawRequestDTO request = newBroadcastRequest();
            when(withdrawRequestRepo.existsByBizNo(BIZ_NO_BROADCAST)).thenReturn(false);
            when(accountRepo.findByUid(TEST_UID)).thenReturn(null);

            BizException ex = assertThrows(BizException.class, () -> service.broadcast(request));

            assertEquals(ERR_ACCOUNT_NOT_EXIST, ex.getErrorCode());
            verify(withdrawRequestRepo, never()).save(any());
            verify(signerPort, never()).sign(any());
            verify(txBroadcastPort, never()).broadcast(any());
        }

        /**
         * 场景：FAILED 请求重试广播时放行并重新广播成功。
         * Scenario: retrying a FAILED request resumes broadcast and succeeds.
         * 断言 FAILED 状态不幂等短路，重新走 nonce/签名/广播并将状态置回 BROADCASTED。
         */
        @Test
        @DisplayName("FAILED 请求重试广播恢复流程 | broadcast retry from FAILED resumes")
        void broadcast_retryFromFailed_resumesBroadcast() {
            WithdrawRequestDTO request = newBroadcastRequest();
            WithdrawRequestEntity entity = newPendingEntity();
            entity.markFailed();
            when(withdrawRequestRepo.existsByBizNo(BIZ_NO_BROADCAST)).thenReturn(true);
            when(withdrawRequestRepo.findByBizNo(BIZ_NO_BROADCAST)).thenReturn(Optional.of(entity));
            when(accountRepo.findByUid(TEST_UID)).thenReturn(newAccount(BALANCE_100, AMOUNT_50));
            when(txBroadcastPort.currentNonce(CHAIN_ID, HOT_WALLET_ADDRESS)).thenReturn(NONCE_5);
            when(signerPort.sign(any(SignRequest.class)))
                    .thenReturn(new SignedTx(CHAIN_ID, RAW_TRANSACTION, HOT_WALLET_ADDRESS, TO_ADDRESS));
            when(txBroadcastPort.broadcast(any(SignedTx.class)))
                    .thenReturn(new BroadcastResult(CHAIN_ID, TX_HASH, HOT_WALLET_ADDRESS, TO_ADDRESS));

            WithdrawBroadcastResponseDTO result = service.broadcast(request);

            assertEquals(BIZ_NO_BROADCAST, result.getBizNo());
            assertEquals(TX_HASH, result.getTxHash());
            assertEquals("BROADCASTED", result.getStatus());
            assertEquals(WithdrawRequestStatusEnum.BROADCASTED, entity.getStatus());
            assertEquals(TX_HASH, entity.getTxHash());
            verify(accountRepo).findByUid(TEST_UID);
            verify(signerPort).sign(any(SignRequest.class));
            verify(txBroadcastPort).broadcast(any(SignedTx.class));
            verify(ledgerAppService).createDraftJournal(any());
        }
    }

    @Nested
    @DisplayName("confirmAndSettle 确认结算用例 | confirmAndSettle use case")
    class ConfirmAndSettle {

        /**
         * 场景：链上确认达标后结算成功并过账凭证。
         * Scenario: confirmed settlement deducts frozen balance and posts the journal.
         * 断言扣款金额、WITHDRAW/OUT 流水快照、凭证过账与请求置 SETTLED。
         */
        @Test
        @DisplayName("确认达标后结算成功并过账凭证 | confirmed settlement settles and posts journal")
        void confirmAndSettle_confirmed_settlesFrozenAndPostsJournal() {
            WithdrawRequestEntity entity = newBroadcastedEntity();
            when(withdrawRequestRepo.findByBizNo(BIZ_NO_BROADCAST)).thenReturn(Optional.of(entity));
            when(chainQueryPort.queryTxReceipt(CHAIN_ID, TX_HASH)).thenReturn(Optional.of(newReceipt("0x1")));
            when(chainQueryPort.isConfirmed(CHAIN_ID, TX_HASH, REQUIRED_CONFIRMATIONS)).thenReturn(true);
            when(accountRepo.findByUid(TEST_UID)).thenReturn(newAccount(BALANCE_100, AMOUNT_50));
            // 凭证已过账：resolveJournalStatus 从总账实时查询返回 POSTED
            when(ledgerAppService.findByBizNo(BIZ_NO_BROADCAST)).thenReturn(newJournalDetail("POSTED"));

            WithdrawSettleResponseDTO result = service.confirmAndSettle(BIZ_NO_BROADCAST);

            assertEquals(BIZ_NO_BROADCAST, result.getBizNo());
            assertEquals("SETTLED", result.getStatus());
            assertEquals("POSTED", result.getJournalStatus());
            assertEquals(WithdrawRequestStatusEnum.SETTLED, entity.getStatus());

            // 扣款断言：仅扣冻结余额
            verify(accountRepo).update(accountCaptor.capture());
            assertEquals(BALANCE_100, accountCaptor.getValue().getAvailableBalance());
            assertEquals(ZERO, accountCaptor.getValue().getFrozenBalance());
            // 流水断言：WITHDRAW/OUT，快照为冻结余额变动前后
            verify(fundFlowAppService).record(fundFlowCaptor.capture());
            CreateFundFlowRequest flow = fundFlowCaptor.getValue();
            assertEquals(FundFlowDirectionEnum.OUT, flow.getDirection());
            assertEquals(FundFlowTypeEnum.WITHDRAW, flow.getType());
            assertEquals(AMOUNT_50, flow.getBalanceBefore());
            assertEquals(ZERO, flow.getBalanceAfter());
            // 凭证过账断言
            verify(ledgerAppService).postJournalByBizNo(BIZ_NO_BROADCAST);
        }

        /**
         * 场景：确认数未达标时抛 TX_NOT_CONFIRMED_YET 且状态不变。
         * Scenario: insufficient confirmations throw TX_NOT_CONFIRMED_YET without side effects.
         * 断言无任何状态变化、无扣款、无流水、无过账。
         */
        @Test
        @DisplayName("确认数未达标抛 TX_NOT_CONFIRMED_YET 且无副作用 | insufficient confirmations are rejected")
        void confirmAndSettle_notConfirmed_throwsTxNotConfirmedYet() {
            WithdrawRequestEntity entity = newBroadcastedEntity();
            when(withdrawRequestRepo.findByBizNo(BIZ_NO_BROADCAST)).thenReturn(Optional.of(entity));
            when(chainQueryPort.queryTxReceipt(CHAIN_ID, TX_HASH)).thenReturn(Optional.of(newReceipt("0x1")));
            when(chainQueryPort.isConfirmed(CHAIN_ID, TX_HASH, REQUIRED_CONFIRMATIONS)).thenReturn(false);

            BizException ex = assertThrows(BizException.class,
                    () -> service.confirmAndSettle(BIZ_NO_BROADCAST));

            assertEquals(ERR_TX_NOT_CONFIRMED_YET, ex.getErrorCode());
            assertEquals(WithdrawRequestStatusEnum.BROADCASTED, entity.getStatus());
            verify(withdrawRequestRepo, never()).update(any());
            verify(accountRepo, never()).update(any());
            verify(fundFlowAppService, never()).record(any());
            verify(ledgerAppService, never()).postJournalByBizNo(any());
        }

        /**
         * 场景：已结算请求幂等返回且不重复扣款。
         * Scenario: already settled request returns the same result without re-deduction.
         * 断言不再触发链上查询、扣款、流水与过账。
         */
        @Test
        @DisplayName("已结算请求幂等返回且不重复扣款 | settled request returns idempotently")
        void confirmAndSettle_alreadySettled_returnsSameResult() {
            WithdrawRequestEntity entity = newBroadcastedEntity();
            entity.markSettled();
            when(withdrawRequestRepo.findByBizNo(BIZ_NO_BROADCAST)).thenReturn(Optional.of(entity));
            // 凭证已过账：resolveJournalStatus 从总账实时查询返回 POSTED
            when(ledgerAppService.findByBizNo(BIZ_NO_BROADCAST)).thenReturn(newJournalDetail("POSTED"));

            WithdrawSettleResponseDTO result = service.confirmAndSettle(BIZ_NO_BROADCAST);

            assertEquals(BIZ_NO_BROADCAST, result.getBizNo());
            assertEquals("SETTLED", result.getStatus());
            assertEquals("POSTED", result.getJournalStatus());
            verify(chainQueryPort, never()).queryTxReceipt(any(), any());
            verify(chainQueryPort, never()).isConfirmed(any(), any(), anyInt());
            verify(accountRepo, never()).update(any());
            verify(fundFlowAppService, never()).record(any());
            verify(ledgerAppService, never()).postJournalByBizNo(any());
        }

        /**
         * 场景：链上交易失败时解冻资金、置 FAILED 并保留 DRAFT 凭证。
         * Scenario: on-chain failure unfreezes the funds and keeps the DRAFT journal.
         * 断言 UNFREEZE/IN 流水、解冻后的余额快照、请求 FAILED、未过账凭证。
         */
        @Test
        @DisplayName("链上失败解冻资金置 FAILED 且保留 DRAFT 凭证 | chain failure unfreezes and marks FAILED")
        void confirmAndSettle_chainFailed_unfreezesAndMarksFailed() {
            WithdrawRequestEntity entity = newBroadcastedEntity();
            when(withdrawRequestRepo.findByBizNo(BIZ_NO_BROADCAST)).thenReturn(Optional.of(entity));
            when(chainQueryPort.queryTxReceipt(CHAIN_ID, TX_HASH)).thenReturn(Optional.of(newReceipt("0x0")));
            when(accountRepo.findByUid(TEST_UID)).thenReturn(newAccount(ZERO, AMOUNT_50));

            BizException ex = assertThrows(BizException.class,
                    () -> service.confirmAndSettle(BIZ_NO_BROADCAST));

            assertEquals(ERR_TX_CHAIN_FAILED, ex.getErrorCode());
            assertEquals(WithdrawRequestStatusEnum.FAILED, entity.getStatus());
            // 解冻断言：冻结余额释放回可用余额
            verify(accountRepo).update(accountCaptor.capture());
            assertEquals(AMOUNT_50, accountCaptor.getValue().getAvailableBalance());
            assertEquals(ZERO, accountCaptor.getValue().getFrozenBalance());
            // 流水断言：UNFREEZE/IN，快照为可用余额变动前后
            verify(fundFlowAppService).record(fundFlowCaptor.capture());
            CreateFundFlowRequest flow = fundFlowCaptor.getValue();
            assertEquals(FundFlowDirectionEnum.IN, flow.getDirection());
            assertEquals(FundFlowTypeEnum.UNFREEZE, flow.getType());
            assertEquals(ZERO, flow.getBalanceBefore());
            assertEquals(AMOUNT_50, flow.getBalanceAfter());
            // DRAFT 凭证保留：不执行过账
            verify(ledgerAppService, never()).postJournalByBizNo(any());
        }

        /**
         * 场景：提现请求不存在时抛 WITHDRAW_REQUEST_NOT_FOUND。
         * Scenario: confirmAndSettle throws WITHDRAW_REQUEST_NOT_FOUND for unknown bizNo.
         */
        @Test
        @DisplayName("请求不存在抛 WITHDRAW_REQUEST_NOT_FOUND | request not found is rejected")
        void confirmAndSettle_requestNotFound_throwsWithdrawRequestNotFound() {
            when(withdrawRequestRepo.findByBizNo(BIZ_NO_BROADCAST)).thenReturn(Optional.empty());

            BizException ex = assertThrows(BizException.class,
                    () -> service.confirmAndSettle(BIZ_NO_BROADCAST));

            assertEquals(ERR_WITHDRAW_REQUEST_NOT_FOUND, ex.getErrorCode());
            verify(chainQueryPort, never()).queryTxReceipt(any(), any());
        }

        /**
         * 场景：FAILED 请求不可结算（未广播或链上已失败）。
         * Scenario: confirmAndSettle rejects a FAILED request before any on-chain query.
         * 断言抛 WITHDRAW_REQUEST_STATUS_INVALID 且不触发链上回执查询与任何状态变更。
         */
        @Test
        @DisplayName("FAILED 请求不可结算抛状态非法 | failed request is rejected before on-chain query")
        void confirmAndSettle_onFailedRequest_throwsStatusInvalid() {
            WithdrawRequestEntity entity = newPendingEntity();
            entity.markFailed();
            when(withdrawRequestRepo.findByBizNo(BIZ_NO_BROADCAST)).thenReturn(Optional.of(entity));

            BizException ex = assertThrows(BizException.class,
                    () -> service.confirmAndSettle(BIZ_NO_BROADCAST));

            assertEquals(ERR_WITHDRAW_REQUEST_STATUS_INVALID, ex.getErrorCode());
            verify(chainQueryPort, never()).queryTxReceipt(any(), any());
            verify(chainQueryPort, never()).isConfirmed(any(), any(), anyInt());
            verify(withdrawRequestRepo, never()).update(any());
            verify(accountRepo, never()).update(any());
            verify(fundFlowAppService, never()).record(any());
        }
    }

    @Nested
    @DisplayName("cancelWithdraw 取消用例与 queryStatus 状态查询 | cancel and query use cases")
    class CancelAndQuery {

        /**
         * 场景：广播后取消成功，解冻资金并置 CANCELLED。
         * Scenario: cancelling a broadcasted request unfreezes the funds.
         * 断言 UNFREEZE/IN 流水、解冻后的余额快照与请求状态 CANCELLED。
         */
        @Test
        @DisplayName("广播后取消成功解冻资金 | cancel unfreezes funds after broadcast")
        void cancelWithdraw_broadcasted_unfreezesAndMarksCancelled() {
            WithdrawRequestEntity entity = newBroadcastedEntity();
            when(withdrawRequestRepo.findByBizNo(BIZ_NO_BROADCAST)).thenReturn(Optional.of(entity));
            when(accountRepo.findByUid(TEST_UID)).thenReturn(newAccount(ZERO, AMOUNT_50));

            WithdrawStatusResponseDTO result = service.cancelWithdraw(BIZ_NO_BROADCAST);

            assertEquals(BIZ_NO_BROADCAST, result.getBizNo());
            assertEquals("CANCELLED", result.getStatus());
            assertEquals(WithdrawRequestStatusEnum.CANCELLED, entity.getStatus());
            verify(accountRepo).update(accountCaptor.capture());
            assertEquals(AMOUNT_50, accountCaptor.getValue().getAvailableBalance());
            assertEquals(ZERO, accountCaptor.getValue().getFrozenBalance());
            verify(fundFlowAppService).record(fundFlowCaptor.capture());
            CreateFundFlowRequest flow = fundFlowCaptor.getValue();
            assertEquals(FundFlowDirectionEnum.IN, flow.getDirection());
            assertEquals(FundFlowTypeEnum.UNFREEZE, flow.getType());
            assertEquals(ZERO, flow.getBalanceBefore());
            assertEquals(AMOUNT_50, flow.getBalanceAfter());
        }

        /**
         * 场景：已结算请求不可取消。
         * Scenario: a settled request cannot be cancelled.
         * 断言抛 WITHDRAW_REQUEST_STATUS_INVALID 且无任何副作用。
         */
        @Test
        @DisplayName("已结算请求不可取消 | settled request cannot be cancelled")
        void cancelWithdraw_settled_throwsStatusInvalid() {
            WithdrawRequestEntity entity = newBroadcastedEntity();
            entity.markSettled();
            when(withdrawRequestRepo.findByBizNo(BIZ_NO_BROADCAST)).thenReturn(Optional.of(entity));

            BizException ex = assertThrows(BizException.class,
                    () -> service.cancelWithdraw(BIZ_NO_BROADCAST));

            assertEquals(ERR_WITHDRAW_REQUEST_STATUS_INVALID, ex.getErrorCode());
            verify(accountRepo, never()).update(any());
            verify(fundFlowAppService, never()).record(any());
            verify(withdrawRequestRepo, never()).update(any());
        }

        /**
         * 场景：已取消请求重复取消时幂等返回成功。
         * Scenario: cancelling an already cancelled request is idempotent.
         * 断言直接返回成功且不重复解冻。
         */
        @Test
        @DisplayName("已取消请求重复取消幂等返回 | duplicate cancel is idempotent")
        void cancelWithdraw_duplicateCall_idempotent() {
            WithdrawRequestEntity entity = newBroadcastedEntity();
            entity.cancel();
            when(withdrawRequestRepo.findByBizNo(BIZ_NO_BROADCAST)).thenReturn(Optional.of(entity));

            WithdrawStatusResponseDTO result = service.cancelWithdraw(BIZ_NO_BROADCAST);

            assertEquals(BIZ_NO_BROADCAST, result.getBizNo());
            assertEquals("CANCELLED", result.getStatus());
            verify(accountRepo, never()).update(any());
            verify(fundFlowAppService, never()).record(any());
            verify(withdrawRequestRepo, never()).update(any());
        }

        /**
         * 场景：按业务流水号查询状态返回交易哈希与业务要素。
         * Scenario: queryStatus returns the tx hash and business fields.
         */
        @Test
        @DisplayName("状态查询返回交易哈希与业务要素 | queryStatus returns tx hash and fields")
        void queryStatus_withTxHash_returnsStatusWithTxHash() {
            WithdrawRequestEntity entity = newBroadcastedEntity();
            when(withdrawRequestRepo.findByBizNo(BIZ_NO_BROADCAST)).thenReturn(Optional.of(entity));

            WithdrawStatusResponseDTO result = service.queryStatus(BIZ_NO_BROADCAST);

            assertEquals(BIZ_NO_BROADCAST, result.getBizNo());
            assertEquals("BROADCASTED", result.getStatus());
            assertEquals(TX_HASH, result.getTxHash());
            assertEquals(AMOUNT_50, result.getAmount());
            assertEquals(CURRENCY_USDT, result.getCurrency());
        }
    }

    private static WithdrawRequestDTO newBroadcastRequest() {
        WithdrawRequestDTO request = newRequest(BIZ_NO_BROADCAST, AMOUNT_50);
        request.setChainId(CHAIN_ID);
        request.setToAddress(TO_ADDRESS);
        request.setTokenAddress(TOKEN_ADDRESS);
        return request;
    }

    private static WithdrawRequestEntity newPendingEntity() {
        return WithdrawRequestEntity.create(TEST_UID, BIZ_NO_BROADCAST, AMOUNT_50,
                CURRENCY_USDT, CHAIN_ID, TO_ADDRESS, TOKEN_ADDRESS);
    }

    private static WithdrawRequestEntity newBroadcastedEntity() {
        WithdrawRequestEntity entity = newPendingEntity();
        entity.markBroadcasted(TX_HASH);
        return entity;
    }

    private static ChainTxReceipt newReceipt(String status) {
        return new ChainTxReceipt(CHAIN_ID, TX_HASH, BLOCK_NUMBER, status,
                REQUIRED_CONFIRMATIONS, HOT_WALLET_ADDRESS, TO_ADDRESS, AMOUNT_50_WEI);
    }

    private static JournalDetailResponseDTO newJournalDetail(String status) {
        JournalDetailResponseDTO dto = new JournalDetailResponseDTO();
        dto.setStatus(status);
        return dto;
    }
}
