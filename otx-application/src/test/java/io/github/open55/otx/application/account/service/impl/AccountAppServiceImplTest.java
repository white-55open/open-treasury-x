package io.github.open55.otx.application.account.service.impl;

import io.github.open55.otx.application.account.dto.response.GetAccountResponse;
import io.github.open55.otx.application.deposit.dto.ChangeAmountRequest;
import io.github.open55.otx.application.fundflow.service.FundFlowAppService;
import io.github.open55.otx.common.exception.BizException;
import io.github.open55.otx.common.exception.BizIdempotentException;
import io.github.open55.otx.common.exception.OptimisticLockException;
import io.github.open55.otx.domain.account.entity.AccountEntity;
import io.github.open55.otx.domain.account.repository.AccountRepo;
import io.github.open55.otx.domain.fundflow.enums.FundFlowDirectionEnum;
import io.github.open55.otx.domain.fundflow.enums.FundFlowTypeEnum;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * AccountAppServiceImpl 账户应用服务单元测试。
 * <p>
 * 覆盖账户创建、余额变更、冻结、查询、资金流水的用例编排逻辑。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AccountAppServiceImpl 账户应用服务单元测试 | AccountAppServiceImpl unit tests")
class AccountAppServiceImplTest {

    private static final long TEST_UID = 12345L;
    private static final BigDecimal AMOUNT_100 = new BigDecimal("100");
    private static final BigDecimal BALANCE_1000 = new BigDecimal("1000");
    private static final String BIZ_NO = "BIZ-20260101-0001";

    @Mock
    private AccountRepo accountRepo;

    @Mock
    private FundFlowAppService fundFlowAppService;

    @Captor
    private ArgumentCaptor<AccountEntity> accountCaptor;

    private AccountAppServiceImpl service;

    @BeforeEach
    void setUp() throws Exception {
        service = new AccountAppServiceImpl(accountRepo);
        setField(service, "fundFlowAppService", fundFlowAppService);
        setField(service, "self", service);
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static AccountEntity createAccountEntity() {
        AccountEntity entity = AccountEntity.builder()
                .uid(TEST_UID)
                .availableBalance(BALANCE_1000)
                .frozenBalance(BigDecimal.ZERO)
                .build();
        entity.setId(1L);
        return entity;
    }

    @Nested
    @DisplayName("createAccount 创建账户 | create account")
    class CreateAccount {

        /**
         * 新建账户：账户不存在时创建并返回新 ID。
         */
        @Test
        @DisplayName("新建账户成功")
        void createAccount_whenNotExists_savesAndReturnsId() {
            when(accountRepo.findByUid(TEST_UID)).thenReturn(null);
            doAnswer(invocation -> {
                AccountEntity entity = invocation.getArgument(0);
                entity.setId(1L);
                return null;
            }).when(accountRepo).save(any());

            Long result = service.createAccount(TEST_UID);

            assertNotNull(result);
            assertEquals(1L, result);
            verify(accountRepo).save(accountCaptor.capture());
            assertEquals(TEST_UID, accountCaptor.getValue().getUid());
        }

        /**
         * 幂等返回：账户已存在时直接返回现有 ID，不创建新账户。
         */
        @Test
        @DisplayName("账户已存在时幂等返回")
        void createAccount_whenExists_returnsExistingId() {
            AccountEntity existing = createAccountEntity();
            existing.setId(99L);
            when(accountRepo.findByUid(TEST_UID)).thenReturn(existing);

            Long result = service.createAccount(TEST_UID);

            assertEquals(99L, result);
            verify(accountRepo, never()).save(any());
        }
    }

    @Nested
    @DisplayName("increaseBalance 增加余额 | increase balance")
    class IncreaseBalance {

        /**
         * 账户存在时正常增加余额。
         */
        @Test
        @DisplayName("正常增加余额成功")
        void increaseBalance_whenAccountExists_succeeds() {
            when(accountRepo.findByUid(TEST_UID)).thenReturn(createAccountEntity());

            service.increaseBalance(TEST_UID, AMOUNT_100);

            verify(accountRepo).update(accountCaptor.capture());
            assertEquals(new BigDecimal("1100"), accountCaptor.getValue().getAvailableBalance());
        }

        /**
         * 账户不存在时抛 ACCOUNT_NOT_EXIST。
         */
        @Test
        @DisplayName("账户不存在抛 ACCOUNT_NOT_EXIST")
        void increaseBalance_whenAccountNotExists_throws() {
            when(accountRepo.findByUid(TEST_UID)).thenReturn(null);

            BizException ex = assertThrows(BizException.class,
                    () -> service.increaseBalance(TEST_UID, AMOUNT_100));
            assertEquals("ACCOUNT_NOT_EXIST", ex.getErrorCode());
            verify(accountRepo, never()).update(any());
        }
    }

    @Nested
    @DisplayName("freezeBalance 冻结余额 | freeze balance")
    class FreezeBalance {

        /**
         * 账户存在时正常冻结。
         */
        @Test
        @DisplayName("正常冻结成功")
        void freezeBalance_whenAccountExists_succeeds() {
            when(accountRepo.findByUid(TEST_UID)).thenReturn(createAccountEntity());

            service.freezeBalance(TEST_UID, AMOUNT_100);

            verify(accountRepo).update(accountCaptor.capture());
            assertEquals(new BigDecimal("900"), accountCaptor.getValue().getAvailableBalance());
            assertEquals(AMOUNT_100, accountCaptor.getValue().getFrozenBalance());
        }

        /**
         * 账户不存在时抛 ACCOUNT_NOT_EXIST。
         */
        @Test
        @DisplayName("账户不存在抛 ACCOUNT_NOT_EXIST")
        void freezeBalance_whenAccountNotExists_throws() {
            when(accountRepo.findByUid(TEST_UID)).thenReturn(null);

            BizException ex = assertThrows(BizException.class,
                    () -> service.freezeBalance(TEST_UID, AMOUNT_100));
            assertEquals("ACCOUNT_NOT_EXIST", ex.getErrorCode());
        }
    }

    @Nested
    @DisplayName("getByUid 查询账户 | get by uid")
    class GetByUid {

        /**
         * 账户存在时返回正确的账户详情。
         */
        @Test
        @DisplayName("账户存在返回详情")
        void getByUid_whenExists_returnsResponse() {
            when(accountRepo.findByUid(TEST_UID)).thenReturn(createAccountEntity());

            GetAccountResponse response = service.getByUid(TEST_UID);

            assertEquals(TEST_UID, response.getUid());
            assertEquals(BALANCE_1000, response.getAvailableBalance());
            assertEquals(BigDecimal.ZERO, response.getFrozenBalance());
        }

        /**
         * 账户不存在时抛 ACCOUNT_NOT_EXIST。
         */
        @Test
        @DisplayName("账户不存在抛 ACCOUNT_NOT_EXIST")
        void getByUid_whenNotExists_throws() {
            when(accountRepo.findByUid(TEST_UID)).thenReturn(null);

            BizException ex = assertThrows(BizException.class,
                    () -> service.getByUid(TEST_UID));
            assertEquals("ACCOUNT_NOT_EXIST", ex.getErrorCode());
        }
    }

    @Nested
    @DisplayName("changeAmountWithFundFlowAtomic 原子动账 | atomic change amount with fund flow")
    class ChangeAmountWithFundFlowAtomic {

        /**
         * DEPOSIT 入账：可用余额增加，流水方向为 IN。
         */
        @Test
        @DisplayName("DEPOSIT 入账成功")
        void atomic_withDeposit_succeeds() {
            ChangeAmountRequest request = buildRequest(FundFlowTypeEnum.DEPOSIT);
            when(accountRepo.findByUid(TEST_UID)).thenReturn(createAccountEntity());

            service.changeAmountWithFundFlowAtomic(request);

            verify(accountRepo).update(accountCaptor.capture());
            assertEquals(new BigDecimal("1100"), accountCaptor.getValue().getAvailableBalance());
            verify(fundFlowAppService).record(argThat(r ->
                    r.getDirection() == FundFlowDirectionEnum.IN
                            && r.getBalanceBefore().equals(BALANCE_1000)
                            && r.getBalanceAfter().equals(new BigDecimal("1100"))));
        }

        /**
         * 场景：WITHDRAW 类型不再受支持。
         * Scenario: WITHDRAW type is rejected with FUND_FLOW_TYPE_NOT_SUPPORT.
         * 断言业务码正确且账户与流水均无副作用（提现已由 withdraw 上下文的两阶段用例承担）。
         */
        @Test
        @DisplayName("WITHDRAW 类型抛不支持异常 | WITHDRAW type throws not supported")
        void atomic_withWithdrawType_throwsFundFlowTypeNotSupport() {
            ChangeAmountRequest request = buildRequest(FundFlowTypeEnum.WITHDRAW);
            when(accountRepo.findByUid(TEST_UID)).thenReturn(createAccountEntity());

            BizException ex = assertThrows(BizException.class,
                    () -> service.changeAmountWithFundFlowAtomic(request));

            assertEquals("FUND_FLOW_TYPE_NOT_SUPPORT", ex.getErrorCode());
            verify(accountRepo, never()).update(any());
            verify(fundFlowAppService, never()).record(any());
        }

        /**
         * 不支持的资金类型抛 FUND_FLOW_TYPE_NOT_SUPPORT。
         */
        @Test
        @DisplayName("不支持的资金类型抛异常")
        void atomic_withUnsupportedType_throws() {
            ChangeAmountRequest request = buildRequest(FundFlowTypeEnum.FREEZE);
            when(accountRepo.findByUid(TEST_UID)).thenReturn(createAccountEntity());

            BizException ex = assertThrows(BizException.class,
                    () -> service.changeAmountWithFundFlowAtomic(request));
            assertEquals("FUND_FLOW_TYPE_NOT_SUPPORT", ex.getErrorCode());
        }

        /**
         * 流水记录时唯一键冲突转换为 BizIdempotentException。
         */
        @Test
        @DisplayName("流水唯一键冲突转幂等异常")
        void atomic_withDuplicateKey_throwsIdempotent() {
            ChangeAmountRequest request = buildRequest(FundFlowTypeEnum.DEPOSIT);
            when(accountRepo.findByUid(TEST_UID)).thenReturn(createAccountEntity());
            doThrow(new DuplicateKeyException("uk_biz_no"))
                    .when(fundFlowAppService).record(any());

            assertThrows(BizIdempotentException.class,
                    () -> service.changeAmountWithFundFlowAtomic(request));
        }

        private static ChangeAmountRequest buildRequest(FundFlowTypeEnum type) {
            ChangeAmountRequest req = new ChangeAmountRequest();
            req.setUid(TEST_UID);
            req.setAmount(AMOUNT_100);
            req.setBizNo(BIZ_NO);
            req.setFundFlowType(type);
            return req;
        }
    }

    @Nested
    @DisplayName("changeAmountWithFundFlow 含幂等的外层入口 | change amount with fund flow with idempotency")
    class ChangeAmountWithFundFlow {

        /**
         * 首次调用正常完成动账。
         */
        @Test
        @DisplayName("首次调用成功")
        void changeAmount_whenFirstCall_succeeds() {
            ChangeAmountRequest request = buildRequest(FundFlowTypeEnum.DEPOSIT);
            when(fundFlowAppService.existsBizNo(BIZ_NO)).thenReturn(false);
            when(accountRepo.findByUid(TEST_UID)).thenReturn(createAccountEntity());

            String result = service.changeAmountWithFundFlow(request);

            assertEquals(BIZ_NO, result);
            verify(fundFlowAppService).record(any());
            verify(accountRepo).update(any());
        }

        /**
         * bizNo 已存在时幂等早退，不执行动账。
         */
        @Test
        @DisplayName("bizNo 已存在幂等返回")
        void changeAmount_whenBizNoExists_returnsEarly() {
            ChangeAmountRequest request = buildRequest(FundFlowTypeEnum.DEPOSIT);
            when(fundFlowAppService.existsBizNo(BIZ_NO)).thenReturn(true);

            String result = service.changeAmountWithFundFlow(request);

            assertEquals(BIZ_NO, result);
            verify(fundFlowAppService, never()).record(any());
            verify(accountRepo, never()).update(any());
        }

        /**
         * 入参校验：request 为 null 抛 PARAM_MISS。
         */
        @Test
        @DisplayName("request=null 抛 PARAM_MISS")
        void changeAmount_withNullRequest_throws() {
            BizException ex = assertThrows(BizException.class,
                    () -> service.changeAmountWithFundFlow(null));
            assertEquals("PARAM_MISS", ex.getErrorCode());
        }

        private static ChangeAmountRequest buildRequest(FundFlowTypeEnum type) {
            ChangeAmountRequest req = new ChangeAmountRequest();
            req.setUid(TEST_UID);
            req.setAmount(AMOUNT_100);
            req.setBizNo(BIZ_NO);
            req.setFundFlowType(type);
            return req;
        }
    }
}
