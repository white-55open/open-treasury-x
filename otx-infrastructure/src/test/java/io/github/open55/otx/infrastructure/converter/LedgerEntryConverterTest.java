package io.github.open55.otx.infrastructure.converter;

import io.github.open55.otx.domain.ledger.LedgerEntryEntity;
import io.github.open55.otx.domain.ledger.enums.LedgerAccountCodeEnum;
import io.github.open55.otx.domain.ledger.enums.LedgerEntryTypeEnum;
import io.github.open55.otx.infrastructure.po.LedgerEntryPO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * LedgerEntryConverter PO ↔ Entity 双向映射单元测试。
 * <p>
 * 验证分录字段正确映射，包括 accountCode/entryType 的 String ↔ Enum 互转，
 * 以及 entity2po 忽略 journalId 和 bizNo 的语义。
 */
@DisplayName("LedgerEntryConverter PO ↔ Entity 映射测试 | LedgerEntryConverter mapping tests")
class LedgerEntryConverterTest {

    private static final String REMARK = "test entry";
    private static final BigDecimal AMOUNT_100 = new BigDecimal("100");
    private static final long TEST_UID = 12345L;

    /**
     * PO → Entity：accountCode 从 String 转为 LedgerAccountCodeEnum，
     * entryType 从 String 转为 LedgerEntryTypeEnum，其余字段直接透传。
     */
    @Test
    @DisplayName("PO → Entity 字段正确映射，枚举 String → Enum 互转")
    void po2Entity_mapsAllFields() {
        LedgerEntryPO po = new LedgerEntryPO();
        po.setId(1L);
        po.setJournalId(100L);
        po.setBizNo("BIZ-001");
        po.setAccountCode("PLATFORM_HOT");
        po.setEntryType("DEBIT");
        po.setAmount(AMOUNT_100);
        po.setUid(TEST_UID);
        po.setCounterparty("0xabc");
        po.setBalanceAfter(AMOUNT_100);
        po.setRemark(REMARK);

        LedgerEntryEntity entity = LedgerEntryConverter.INSTANCE.po2Entity(po);

        assertEquals(1L, entity.getId());
        assertEquals(LedgerAccountCodeEnum.PLATFORM_HOT, entity.getAccountCode());
        assertEquals(LedgerEntryTypeEnum.DEBIT, entity.getEntryType());
        assertEquals(AMOUNT_100, entity.getAmount());
        assertEquals(TEST_UID, entity.getUid());
        assertEquals("0xabc", entity.getCounterparty());
        assertEquals(AMOUNT_100, entity.getBalanceAfter());
        assertEquals(REMARK, entity.getRemark());
    }

    /**
     * Entity → PO：accountCode 从 Enum 转为 String，entryType 从 Enum 转为 String，
     * journalId 和 bizNo 被忽略（null）。
     */
    @Test
    @DisplayName("Entity → PO 字段正确映射，journalId/bizNo 忽略")
    void entity2po_mapsAllFields() {
        LedgerEntryEntity entity = new LedgerEntryEntity(
                LedgerAccountCodeEnum.DEPOSIT_IN_TRANSIT,
                LedgerEntryTypeEnum.CREDIT,
                AMOUNT_100,
                null,
                null,
                null,
                REMARK);

        LedgerEntryPO po = LedgerEntryConverter.INSTANCE.entity2po(entity);

        assertEquals("DEPOSIT_IN_TRANSIT", po.getAccountCode());
        assertEquals("CREDIT", po.getEntryType());
        assertEquals(AMOUNT_100, po.getAmount());
        assertEquals(REMARK, po.getRemark());
        assertNull(po.getJournalId());
        assertNull(po.getBizNo());
    }
}
