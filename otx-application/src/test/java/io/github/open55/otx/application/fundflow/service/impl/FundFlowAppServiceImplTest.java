package io.github.open55.otx.application.fundflow.service.impl;

import io.github.open55.otx.application.fundflow.dto.request.CreateFundFlowRequest;
import io.github.open55.otx.common.util.SnowflakeIdUtil;
import io.github.open55.otx.domain.fundflow.entity.FundFlowEntity;
import io.github.open55.otx.domain.fundflow.enums.FundFlowDirectionEnum;
import io.github.open55.otx.domain.fundflow.enums.FundFlowTypeEnum;
import io.github.open55.otx.domain.fundflow.repository.FundFlowRepo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;

/**
 * FundFlowAppServiceImpl 资金流水应用服务单元测试。
 * <p>
 * 覆盖流水记录、幂等校验和按用户查询的编排逻辑。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("FundFlowAppServiceImpl 资金流水应用服务单元测试 | FundFlowAppServiceImpl unit tests")
class FundFlowAppServiceImplTest {

    private static final long TEST_UID = 12345L;
    private static final String BIZ_NO = "BIZ-20260101-0001";
    private static final BigDecimal AMOUNT_100 = new BigDecimal("100");
    private static final BigDecimal BALANCE_1000 = new BigDecimal("1000");
    private static final BigDecimal BALANCE_1100 = new BigDecimal("1100");

    @Mock
    private FundFlowRepo repository;

    @InjectMocks
    private FundFlowAppServiceImpl service;

    @Captor
    private ArgumentCaptor<FundFlowEntity> flowCaptor;

    private static CreateFundFlowRequest buildValidRequest() {
        return CreateFundFlowRequest.builder()
                .uid(TEST_UID)
                .bizNo(BIZ_NO)
                .amount(AMOUNT_100)
                .balanceBefore(BALANCE_1000)
                .balanceAfter(BALANCE_1100)
                .direction(FundFlowDirectionEnum.IN)
                .type(FundFlowTypeEnum.DEPOSIT)
                .build();
    }

    @Nested
    @DisplayName("record 记录流水 | record fund flow")
    class Record {

        /**
         * 正常记录：创建 FundFlowEntity 并传递给仓储。
         */
        @Test
        @DisplayName("正常记录成功")
        void record_withValidRequest_succeeds() {
            CreateFundFlowRequest input = buildValidRequest();

            try (var ignored = mockStatic(SnowflakeIdUtil.class)) {
                when(SnowflakeIdUtil.nextId()).thenReturn(1234567890123456L);
                service.record(input);
            }

            verify(repository).save(flowCaptor.capture());
            FundFlowEntity saved = flowCaptor.getValue();
            assertEquals(TEST_UID, saved.getUid());
            assertEquals(BIZ_NO, saved.getBizNo());
            assertEquals(AMOUNT_100, saved.getAmount());
            assertEquals(BALANCE_1000, saved.getBalanceBefore());
            assertEquals(BALANCE_1100, saved.getBalanceAfter());
            assertEquals(FundFlowDirectionEnum.IN, saved.getDirection());
            assertEquals(FundFlowTypeEnum.DEPOSIT, saved.getType());
            assertNotNull(saved.getFlowNo());
        }

        /**
         * 入参为 null 时抛 IllegalArgumentException。
         */
        @Test
        @DisplayName("input=null 抛 IllegalArgumentException")
        void record_withNullInput_throws() {
            assertThrows(IllegalArgumentException.class, () -> service.record(null));
            verify(repository, never()).save(any());
        }

        /**
         * uid 为 null 时抛 IllegalArgumentException。
         */
        @Test
        @DisplayName("uid=null 抛 IllegalArgumentException")
        void record_withNullUid_throws() {
            CreateFundFlowRequest input = buildValidRequest();
            input.setUid(null);
            assertThrows(IllegalArgumentException.class, () -> service.record(input));
        }

        /**
         * bizNo 为空白时抛 IllegalArgumentException。
         */
        @Test
        @DisplayName("bizNo=blank 抛 IllegalArgumentException")
        void record_withBlankBizNo_throws() {
            CreateFundFlowRequest input = buildValidRequest();
            input.setBizNo("");
            assertThrows(IllegalArgumentException.class, () -> service.record(input));
        }
    }

    @Nested
    @DisplayName("existsBizNo 幂等校验 | idempotency check")
    class ExistsBizNo {

        /**
         * bizNo 存在时返回 true。
         */
        @Test
        @DisplayName("bizNo 存在返回 true")
        void existsBizNo_whenExists_returnsTrue() {
            when(repository.existsByBizNo(BIZ_NO)).thenReturn(true);
            assertTrue(service.existsBizNo(BIZ_NO));
        }

        /**
         * bizNo 不存在时返回 false。
         */
        @Test
        @DisplayName("bizNo 不存在返回 false")
        void existsBizNo_whenNotExists_returnsFalse() {
            when(repository.existsByBizNo(BIZ_NO)).thenReturn(false);
            assertFalse(service.existsBizNo(BIZ_NO));
        }

        /**
         * bizNo 为空白时抛 IllegalArgumentException。
         */
        @Test
        @DisplayName("bizNo=blank 抛 IllegalArgumentException")
        void existsBizNo_withBlank_throws() {
            assertThrows(IllegalArgumentException.class, () -> service.existsBizNo(""));
            assertThrows(IllegalArgumentException.class, () -> service.existsBizNo(null));
            verify(repository, never()).existsByBizNo(any());
        }
    }

    @Nested
    @DisplayName("findByUid 按用户查询 | find by uid")
    class FindByUid {

        /**
         * 该用户有流水时返回列表。
         */
        @Test
        @DisplayName("有流水时返回列表")
        void findByUid_whenHasFlows_returnsList() {
            FundFlowEntity flow = new FundFlowEntity();
            flow.setUid(TEST_UID);
            when(repository.findByUid(TEST_UID)).thenReturn(List.of(flow));

            List<FundFlowEntity> result = service.findByUid(TEST_UID);

            assertEquals(1, result.size());
            assertEquals(TEST_UID, result.get(0).getUid());
        }

        /**
         * 该用户无流水时返回空列表。
         */
        @Test
        @DisplayName("无流水时返回空列表")
        void findByUid_whenNoFlows_returnsEmpty() {
            when(repository.findByUid(TEST_UID)).thenReturn(List.of());

            List<FundFlowEntity> result = service.findByUid(TEST_UID);

            assertTrue(result.isEmpty());
        }

        /**
         * uid 为 null 时抛 IllegalArgumentException。
         */
        @Test
        @DisplayName("uid=null 抛 IllegalArgumentException")
        void findByUid_withNullUid_throws() {
            assertThrows(IllegalArgumentException.class, () -> service.findByUid(null));
            verify(repository, never()).findByUid(any());
        }
    }
}
