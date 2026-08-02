package io.github.open55.otx.infrastructure.converter;

import io.github.open55.otx.domain.withdraw.WithdrawRequestEntity;
import io.github.open55.otx.domain.withdraw.WithdrawRequestStatusEnum;
import io.github.open55.otx.infrastructure.po.WithdrawRequestPO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * WithdrawRequestConverter PO ↔ Entity 双向映射单元测试。
 * <p>
 * 验证提现请求聚合根的全部业务字段正确映射，包括枚举字段 status 的 String ↔ Enum 互转。
 */
@DisplayName("WithdrawRequestConverter PO ↔ Entity 映射测试 | WithdrawRequestConverter mapping tests")
class WithdrawRequestConverterTest {

    private static final Long UID = 1L;
    private static final String BIZ_NO = "WITHDRAW-20260101-0001";
    private static final BigDecimal AMOUNT = new BigDecimal("100.5");
    private static final String CURRENCY = "USDT";
    private static final String CHAIN_ID = "11155111";
    private static final String TO_ADDRESS = "0xToAddress00000000000000000000000000000001";
    private static final String TOKEN_ADDRESS = "0xTokenAddress000000000000000000000000000002";
    private static final String TX_HASH = "0xTxHash00000000000000000000000000000000000000000000000000000000000003";

    /**
     * Entity → PO：status 从 WithdrawRequestStatusEnum 转为状态码 String，其余业务字段透传。
     */
    @Test
    @DisplayName("Entity → PO 映射 status 为枚举状态码")
    void convert_entityToPo_mapsStatusToEnumCode() {
        WithdrawRequestEntity entity = WithdrawRequestEntity.create(
                UID, BIZ_NO, AMOUNT, CURRENCY, CHAIN_ID, TO_ADDRESS, TOKEN_ADDRESS);
        entity.setStatus(WithdrawRequestStatusEnum.BROADCASTED);
        entity.setTxHash(TX_HASH);

        WithdrawRequestPO po = WithdrawRequestConverter.INSTANCE.entity2po(entity);

        assertEquals("BROADCASTED", po.getStatus());
        assertEquals(UID, po.getUid());
        assertEquals(BIZ_NO, po.getBizNo());
        assertEquals(AMOUNT, po.getAmount());
        assertEquals(CURRENCY, po.getCurrency());
        assertEquals(CHAIN_ID, po.getChainId());
        assertEquals(TO_ADDRESS, po.getToAddress());
        assertEquals(TOKEN_ADDRESS, po.getTokenAddress());
        assertEquals(TX_HASH, po.getTxHash());
    }

    /**
     * PO → Entity：status 从状态码 String 转为 WithdrawRequestStatusEnum，其余业务字段透传。
     */
    @Test
    @DisplayName("PO → Entity 映射状态码为枚举")
    void convert_poToEntity_mapsEnumCodeToStatus() {
        WithdrawRequestPO po = new WithdrawRequestPO();
        po.setUid(UID);
        po.setBizNo(BIZ_NO);
        po.setAmount(AMOUNT);
        po.setCurrency(CURRENCY);
        po.setChainId(CHAIN_ID);
        po.setToAddress(TO_ADDRESS);
        po.setTokenAddress(TOKEN_ADDRESS);
        po.setTxHash(TX_HASH);
        po.setStatus("SETTLED");

        WithdrawRequestEntity entity = WithdrawRequestConverter.INSTANCE.po2Entity(po);

        assertEquals(WithdrawRequestStatusEnum.SETTLED, entity.getStatus());
        assertEquals(UID, entity.getUid());
        assertEquals(BIZ_NO, entity.getBizNo());
        assertEquals(AMOUNT, entity.getAmount());
        assertEquals(CURRENCY, entity.getCurrency());
        assertEquals(CHAIN_ID, entity.getChainId());
        assertEquals(TO_ADDRESS, entity.getToAddress());
        assertEquals(TOKEN_ADDRESS, entity.getTokenAddress());
        assertEquals(TX_HASH, entity.getTxHash());
    }

    /**
     * 双向映射：PO → Entity → PO，所有业务字段值一致，包含空值字段。
     */
    @Test
    @DisplayName("双向映射业务字段一致")
    void convert_roundTrip_preservesAllBusinessFields() {
        WithdrawRequestPO po = new WithdrawRequestPO();
        po.setUid(UID);
        po.setBizNo(BIZ_NO);
        po.setAmount(AMOUNT);
        po.setCurrency(CURRENCY);
        po.setChainId(CHAIN_ID);
        po.setToAddress(TO_ADDRESS);
        po.setTokenAddress(null);
        po.setTxHash(null);
        po.setStatus("PENDING");

        WithdrawRequestEntity entity = WithdrawRequestConverter.INSTANCE.po2Entity(po);
        WithdrawRequestPO resultPo = WithdrawRequestConverter.INSTANCE.entity2po(entity);

        assertEquals(po.getUid(), resultPo.getUid());
        assertEquals(po.getBizNo(), resultPo.getBizNo());
        assertEquals(po.getAmount(), resultPo.getAmount());
        assertEquals(po.getCurrency(), resultPo.getCurrency());
        assertEquals(po.getChainId(), resultPo.getChainId());
        assertEquals(po.getToAddress(), resultPo.getToAddress());
        assertNull(resultPo.getTokenAddress());
        assertNull(resultPo.getTxHash());
        assertEquals(po.getStatus(), resultPo.getStatus());
    }
}
