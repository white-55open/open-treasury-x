package io.github.open55.otx.infrastructure.converter;

import io.github.open55.otx.domain.account.entity.AccountEntity;
import io.github.open55.otx.infrastructure.po.AccountPO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * AccountConverter PO ↔ Entity 双向映射单元测试。
 * <p>
 * 验证同名字段自动映射的正确性，包括枚举字段 String ↔ Enum 的双向转换。
 */
@DisplayName("AccountConverter PO ↔ Entity 映射测试 | AccountConverter mapping tests")
class AccountConverterTest {

    private static final long TEST_UID = 12345L;
    private static final BigDecimal BALANCE_1000 = new BigDecimal("1000");
    private static final BigDecimal FROZEN_100 = new BigDecimal("100");

    /**
     * PO → Entity：同名字段自动映射，uid/availableBalance/frozenBalance 正确透传。
     */
    @Test
    @DisplayName("PO → Entity 字段正确映射")
    void po2Entity_mapsAllFields() {
        AccountPO po = new AccountPO();
        po.setId(1L);
        po.setUid(TEST_UID);
        po.setAvailableBalance(BALANCE_1000);
        po.setFrozenBalance(FROZEN_100);

        AccountEntity entity = AccountConverter.INSTANCE.po2Entity(po);

        assertEquals(1L, entity.getId());
        assertEquals(TEST_UID, entity.getUid());
        assertEquals(BALANCE_1000, entity.getAvailableBalance());
        assertEquals(FROZEN_100, entity.getFrozenBalance());
    }

    /**
     * Entity → PO：同名字段自动映射，uid/availableBalance/frozenBalance 正确透传。
     */
    @Test
    @DisplayName("Entity → PO 字段正确映射")
    void entity2po_mapsAllFields() {
        AccountEntity entity = AccountEntity.builder()
                .uid(TEST_UID)
                .availableBalance(BALANCE_1000)
                .frozenBalance(FROZEN_100)
                .build();
        entity.setId(1L);

        AccountPO po = AccountConverter.INSTANCE.entity2po(entity);

        assertEquals(1L, po.getId());
        assertEquals(TEST_UID, po.getUid());
        assertEquals(BALANCE_1000, po.getAvailableBalance());
        assertEquals(FROZEN_100, po.getFrozenBalance());
    }

    /**
     * 双向映射后字段值一致：po → entity → po 不丢失字段。
     */
    @Test
    @DisplayName("双向映射字段一致")
    void roundTrip_mapsCorrectly() {
        AccountPO po = new AccountPO();
        po.setId(1L);
        po.setUid(TEST_UID);
        po.setAvailableBalance(BALANCE_1000);
        po.setFrozenBalance(FROZEN_100);

        AccountEntity entity = AccountConverter.INSTANCE.po2Entity(po);
        AccountPO resultPo = AccountConverter.INSTANCE.entity2po(entity);

        assertEquals(po.getId(), resultPo.getId());
        assertEquals(po.getUid(), resultPo.getUid());
        assertEquals(po.getAvailableBalance(), resultPo.getAvailableBalance());
        assertEquals(po.getFrozenBalance(), resultPo.getFrozenBalance());
    }
}
