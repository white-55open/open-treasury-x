package io.github.open55.otx.domain.fundflow.entity;

import io.github.open55.otx.domain.fundflow.enums.FundFlowDirectionEnum;
import io.github.open55.otx.domain.fundflow.enums.FundFlowTypeEnum;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * FundFlowEntity 资金流水实体单元测试。
 * <p>
 * 覆盖全部业务字段的正确透传，以及 BaseEntity 继承字段的可访问性。
 */
@DisplayName("FundFlowEntity 资金流水实体单元测试 | FundFlowEntity unit tests")
class FundFlowEntityTest {

    private static final String FLOW_NO = "1234567890123456";
    private static final long TEST_UID = 12345L;
    private static final String BIZ_NO = "BIZ-20260101-0001";
    private static final BigDecimal AMOUNT_100 = new BigDecimal("100");
    private static final BigDecimal BALANCE_BEFORE = new BigDecimal("1000");
    private static final BigDecimal BALANCE_AFTER = new BigDecimal("1100");

    @Nested
    @DisplayName("业务字段透传 | business field delegation")
    class FieldDelegation {

        /**
         * 设置全部业务字段后应正确透传每个字段的值。
         */
        @Test
        @DisplayName("全部业务字段正确透传")
        void allBusinessFields_areExposed() {
            FundFlowEntity entity = new FundFlowEntity();
            entity.setFlowNo(FLOW_NO);
            entity.setUid(TEST_UID);
            entity.setBizNo(BIZ_NO);
            entity.setAmount(AMOUNT_100);
            entity.setBalanceBefore(BALANCE_BEFORE);
            entity.setBalanceAfter(BALANCE_AFTER);
            entity.setDirection(FundFlowDirectionEnum.IN);
            entity.setType(FundFlowTypeEnum.DEPOSIT);

            assertEquals(FLOW_NO, entity.getFlowNo());
            assertEquals(TEST_UID, entity.getUid());
            assertEquals(BIZ_NO, entity.getBizNo());
            assertEquals(AMOUNT_100, entity.getAmount());
            assertEquals(BALANCE_BEFORE, entity.getBalanceBefore());
            assertEquals(BALANCE_AFTER, entity.getBalanceAfter());
            assertEquals(FundFlowDirectionEnum.IN, entity.getDirection());
            assertEquals(FundFlowTypeEnum.DEPOSIT, entity.getType());
        }

        /**
         * OUT 方向和 WITHDRAW 类型组合也可正确透传。
         */
        @Test
        @DisplayName("OUT+WITHDRAW 组合正确透传")
        void outDirectionWithWithdraw_succeeds() {
            FundFlowEntity entity = new FundFlowEntity();
            entity.setFlowNo(FLOW_NO);
            entity.setUid(TEST_UID);
            entity.setBizNo(BIZ_NO);
            entity.setAmount(AMOUNT_100);
            entity.setBalanceBefore(BALANCE_AFTER);
            entity.setBalanceAfter(BALANCE_BEFORE);
            entity.setDirection(FundFlowDirectionEnum.OUT);
            entity.setType(FundFlowTypeEnum.WITHDRAW);

            assertEquals(FundFlowDirectionEnum.OUT, entity.getDirection());
            assertEquals(FundFlowTypeEnum.WITHDRAW, entity.getType());
        }
    }

    @Nested
    @DisplayName("BaseEntity 继承字段 | inherited BaseEntity fields")
    class BaseEntityInheritance {

        /**
         * BaseEntity 的 id/version/deleteFlag/tenantId 通过继承可正常 set/get。
         */
        @Test
        @DisplayName("id/version/deleteFlag/tenantId 可被 set/get")
        void baseEntityFields_areAccessible() {
            FundFlowEntity entity = new FundFlowEntity();
            entity.setId(1L);
            entity.setVersion(0L);
            entity.setDeleteFlag("0");
            entity.setTenantId("tenant-1");

            assertEquals(1L, entity.getId());
            assertEquals(0L, entity.getVersion());
            assertEquals("0", entity.getDeleteFlag());
            assertEquals("tenant-1", entity.getTenantId());
        }
    }
}
