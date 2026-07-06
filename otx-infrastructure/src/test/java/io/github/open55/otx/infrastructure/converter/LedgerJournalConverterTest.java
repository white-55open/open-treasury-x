package io.github.open55.otx.infrastructure.converter;

import io.github.open55.otx.domain.ledger.LedgerJournalEntity;
import io.github.open55.otx.domain.ledger.enums.LedgerBizTypeEnum;
import io.github.open55.otx.domain.ledger.enums.LedgerJournalStatusEnum;
import io.github.open55.otx.infrastructure.po.LedgerJournalPO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * LedgerJournalConverter PO ↔ Entity 双向映射单元测试。
 * <p>
 * 验证凭证聚合根的全部字段正确映射，包括枚举字段 bizType/status 的 String ↔ Enum 互转。
 */
@DisplayName("LedgerJournalConverter PO ↔ Entity 映射测试 | LedgerJournalConverter mapping tests")
class LedgerJournalConverterTest {

    private static final String BIZ_NO = "BIZ-20260101-0001";
    private static final LocalDate POSTING_DATE = LocalDate.of(2026, 1, 1);
    private static final String CURRENCY = "USDT";
    private static final BigDecimal TOTAL_AMOUNT = new BigDecimal("200");
    private static final String DESCRIPTION = "test deposit";
    private static final String CHAIN_ID = "1";
    private static final String CHAIN_TX_HASH = "0xabc";
    private static final Long BLOCK_NUMBER = 18000000L;
    private static final String TOKEN_ADDRESS = "0xdac";
    private static final Integer CONFIRMATIONS = 12;
    private static final Long REVERSED_BY = 99L;

    /**
     * PO → Entity：全部字段正确透传，bizType/status 从 String 转为 Enum。
     */
    @Test
    @DisplayName("PO → Entity 全部字段映射正确")
    void po2Entity_mapsAllFields() {
        LedgerJournalPO po = new LedgerJournalPO();
        po.setId(1L);
        po.setBizNo(BIZ_NO);
        po.setBizType("DEPOSIT_ONCHAIN");
        po.setPostingDate(POSTING_DATE);
        po.setCurrency(CURRENCY);
        po.setStatus("DRAFT");
        po.setTotalAmount(TOTAL_AMOUNT);
        po.setDescription(DESCRIPTION);
        po.setChainId(CHAIN_ID);
        po.setChainTxHash(CHAIN_TX_HASH);
        po.setBlockNumber(BLOCK_NUMBER);
        po.setTokenAddress(TOKEN_ADDRESS);
        po.setConfirmations(CONFIRMATIONS);
        po.setReversedBy(REVERSED_BY);

        LedgerJournalEntity entity = LedgerJournalConverter.INSTANCE.po2Entity(po);

        assertEquals(1L, entity.getId());
        assertEquals(BIZ_NO, entity.getBizNo());
        assertEquals(LedgerBizTypeEnum.DEPOSIT_ONCHAIN, entity.getBizType());
        assertEquals(POSTING_DATE, entity.getPostingDate());
        assertEquals(CURRENCY, entity.getCurrency());
        assertEquals(LedgerJournalStatusEnum.DRAFT, entity.getStatus());
        assertEquals(TOTAL_AMOUNT, entity.getTotalAmount());
        assertEquals(DESCRIPTION, entity.getDescription());
        assertEquals(CHAIN_ID, entity.getChainId());
        assertEquals(CHAIN_TX_HASH, entity.getChainTxHash());
        assertEquals(BLOCK_NUMBER, entity.getBlockNumber());
        assertEquals(TOKEN_ADDRESS, entity.getTokenAddress());
        assertEquals(CONFIRMATIONS, entity.getConfirmations());
        assertEquals(REVERSED_BY, entity.getReversedBy());
    }

    /**
     * Entity → PO：全部字段正确透传，bizType/status 从 Enum 转为 String。
     */
    @Test
    @DisplayName("Entity → PO 全部字段映射正确")
    void entity2po_mapsAllFields() {
        LedgerJournalEntity entity = new LedgerJournalEntity();
        entity.setId(1L);
        entity.setBizNo(BIZ_NO);
        entity.setBizType(LedgerBizTypeEnum.DEPOSIT_ONCHAIN);
        entity.setPostingDate(POSTING_DATE);
        entity.setCurrency(CURRENCY);
        entity.setStatus(LedgerJournalStatusEnum.DRAFT);
        entity.setTotalAmount(TOTAL_AMOUNT);
        entity.setDescription(DESCRIPTION);
        entity.setChainId(CHAIN_ID);
        entity.setChainTxHash(CHAIN_TX_HASH);
        entity.setBlockNumber(BLOCK_NUMBER);
        entity.setTokenAddress(TOKEN_ADDRESS);
        entity.setConfirmations(CONFIRMATIONS);
        entity.setReversedBy(REVERSED_BY);

        LedgerJournalPO po = LedgerJournalConverter.INSTANCE.entity2po(entity);

        assertEquals(1L, po.getId());
        assertEquals(BIZ_NO, po.getBizNo());
        assertEquals("DEPOSIT_ONCHAIN", po.getBizType());
        assertEquals(POSTING_DATE, po.getPostingDate());
        assertEquals(CURRENCY, po.getCurrency());
        assertEquals("DRAFT", po.getStatus());
        assertEquals(TOTAL_AMOUNT, po.getTotalAmount());
        assertEquals(DESCRIPTION, po.getDescription());
        assertEquals(CHAIN_ID, po.getChainId());
        assertEquals(CHAIN_TX_HASH, po.getChainTxHash());
        assertEquals(BLOCK_NUMBER, po.getBlockNumber());
        assertEquals(TOKEN_ADDRESS, po.getTokenAddress());
        assertEquals(CONFIRMATIONS, po.getConfirmations());
        assertEquals(REVERSED_BY, po.getReversedBy());
    }

    /**
     * 双向映射：PO → Entity → PO，所有字段值一致。
     */
    @Test
    @DisplayName("双向映射字段一致")
    void roundTrip_mapsCorrectly() {
        LedgerJournalPO po = new LedgerJournalPO();
        po.setId(1L);
        po.setBizNo(BIZ_NO);
        po.setBizType("WITHDRAW_ONCHAIN");
        po.setPostingDate(POSTING_DATE);
        po.setCurrency(CURRENCY);
        po.setStatus("POSTED");
        po.setTotalAmount(TOTAL_AMOUNT);
        po.setDescription(DESCRIPTION);
        po.setChainId(null);
        po.setChainTxHash(null);
        po.setBlockNumber(null);
        po.setTokenAddress(null);
        po.setConfirmations(0);
        po.setReversedBy(null);

        LedgerJournalEntity entity = LedgerJournalConverter.INSTANCE.po2Entity(po);
        LedgerJournalPO resultPo = LedgerJournalConverter.INSTANCE.entity2po(entity);

        assertEquals(po.getId(), resultPo.getId());
        assertEquals(po.getBizNo(), resultPo.getBizNo());
        assertEquals(po.getBizType(), resultPo.getBizType());
        assertEquals(po.getPostingDate(), resultPo.getPostingDate());
        assertEquals(po.getCurrency(), resultPo.getCurrency());
        assertEquals(po.getStatus(), resultPo.getStatus());
        assertEquals(po.getTotalAmount(), resultPo.getTotalAmount());
        assertEquals(po.getConfirmations(), resultPo.getConfirmations());
    }
}
