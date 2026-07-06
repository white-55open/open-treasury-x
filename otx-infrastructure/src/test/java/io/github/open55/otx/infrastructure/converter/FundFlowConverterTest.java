package io.github.open55.otx.infrastructure.converter;

import io.github.open55.otx.domain.fundflow.entity.FundFlowEntity;
import io.github.open55.otx.domain.fundflow.enums.FundFlowDirectionEnum;
import io.github.open55.otx.domain.fundflow.enums.FundFlowTypeEnum;
import io.github.open55.otx.infrastructure.po.FundFlowPO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * FundFlowConverter PO ↔ Entity 双向映射单元测试。
 * <p>
 * 验证同名字段自动映射，以及枚举字段 direction（String ↔ FundFlowDirectionEnum）
 * 和 type（String ↔ FundFlowTypeEnum）的双向转换。
 */
@DisplayName("FundFlowConverter PO ↔ Entity 映射测试 | FundFlowConverter mapping tests")
class FundFlowConverterTest {

    private static final String FLOW_NO = "1234567890123456";
    private static final long TEST_UID = 12345L;
    private static final String BIZ_NO = "BIZ-20260101-0001";
    private static final BigDecimal AMOUNT_100 = new BigDecimal("100");
    private static final BigDecimal BALANCE_1000 = new BigDecimal("1000");
    private static final BigDecimal BALANCE_1100 = new BigDecimal("1100");

    /**
     * PO → Entity：同名字段正确透传，direction 和 type 从 String 转为 Enum。
     */
    @Test
    @DisplayName("PO → Entity 字段正确映射，枚举 String → Enum 互转")
    void po2Entity_mapsAllFields() {
        FundFlowPO po = new FundFlowPO();
        po.setId(1L);
        po.setFlowNo(FLOW_NO);
        po.setUid(TEST_UID);
        po.setBizNo(BIZ_NO);
        po.setAmount(AMOUNT_100);
        po.setBalanceBefore(BALANCE_1000);
        po.setBalanceAfter(BALANCE_1100);
        po.setDirection("IN");
        po.setType("DEPOSIT");

        FundFlowEntity entity = FundFlowConverter.INSTANCE.po2Entity(po);

        assertEquals(1L, entity.getId());
        assertEquals(FLOW_NO, entity.getFlowNo());
        assertEquals(TEST_UID, entity.getUid());
        assertEquals(BIZ_NO, entity.getBizNo());
        assertEquals(AMOUNT_100, entity.getAmount());
        assertEquals(BALANCE_1000, entity.getBalanceBefore());
        assertEquals(BALANCE_1100, entity.getBalanceAfter());
        assertEquals(FundFlowDirectionEnum.IN, entity.getDirection());
        assertEquals(FundFlowTypeEnum.DEPOSIT, entity.getType());
    }

    /**
     * Entity → PO：同名字段正确透传，direction 和 type 从 Enum 转为 String。
     */
    @Test
    @DisplayName("Entity → PO 字段正确映射，枚举 Enum → String 互转")
    void entity2po_mapsAllFields() {
        FundFlowEntity entity = new FundFlowEntity();
        entity.setId(1L);
        entity.setFlowNo(FLOW_NO);
        entity.setUid(TEST_UID);
        entity.setBizNo(BIZ_NO);
        entity.setAmount(AMOUNT_100);
        entity.setBalanceBefore(BALANCE_1000);
        entity.setBalanceAfter(BALANCE_1100);
        entity.setDirection(FundFlowDirectionEnum.OUT);
        entity.setType(FundFlowTypeEnum.WITHDRAW);

        FundFlowPO po = FundFlowConverter.INSTANCE.entity2po(entity);

        assertEquals(1L, po.getId());
        assertEquals(FLOW_NO, po.getFlowNo());
        assertEquals(TEST_UID, po.getUid());
        assertEquals(BIZ_NO, po.getBizNo());
        assertEquals(AMOUNT_100, po.getAmount());
        assertEquals(BALANCE_1000, po.getBalanceBefore());
        assertEquals(BALANCE_1100, po.getBalanceAfter());
        assertEquals("OUT", po.getDirection());
        assertEquals("WITHDRAW", po.getType());
    }

    /**
     * 双向映射：PO → Entity → PO，所有字段值一致。
     */
    @Test
    @DisplayName("双向映射字段一致")
    void roundTrip_mapsCorrectly() {
        FundFlowPO po = new FundFlowPO();
        po.setId(1L);
        po.setFlowNo(FLOW_NO);
        po.setUid(TEST_UID);
        po.setBizNo(BIZ_NO);
        po.setAmount(AMOUNT_100);
        po.setBalanceBefore(BALANCE_1000);
        po.setBalanceAfter(BALANCE_1100);
        po.setDirection("IN");
        po.setType("DEPOSIT");

        FundFlowEntity entity = FundFlowConverter.INSTANCE.po2Entity(po);
        FundFlowPO resultPo = FundFlowConverter.INSTANCE.entity2po(entity);

        assertEquals(po.getId(), resultPo.getId());
        assertEquals(po.getFlowNo(), resultPo.getFlowNo());
        assertEquals(po.getUid(), resultPo.getUid());
        assertEquals(po.getBizNo(), resultPo.getBizNo());
        assertEquals(po.getAmount(), resultPo.getAmount());
        assertEquals(po.getBalanceBefore(), resultPo.getBalanceBefore());
        assertEquals(po.getBalanceAfter(), resultPo.getBalanceAfter());
        assertEquals(po.getDirection(), resultPo.getDirection());
        assertEquals(po.getType(), resultPo.getType());
    }
}
