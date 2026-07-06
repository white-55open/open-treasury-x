package io.github.open55.otx.infrastructure.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.github.open55.otx.domain.ledger.LedgerEntryEntity;
import io.github.open55.otx.domain.ledger.enums.LedgerAccountCodeEnum;
import io.github.open55.otx.domain.ledger.enums.LedgerEntryTypeEnum;
import io.github.open55.otx.infrastructure.mapper.LedgerEntryMapper;
import io.github.open55.otx.infrastructure.po.LedgerEntryPO;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * LedgerEntryRepoImpl 分录值对象仓储实现单元测试。
 * <p>
 * 覆盖批量保存、按凭证 ID 查询、按业务号查询路径。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("LedgerEntryRepoImpl 分录仓储单元测试 | LedgerEntryRepoImpl unit tests")
class LedgerEntryRepoImplTest {

    private static final Long JOURNAL_ID = 100L;
    private static final String BIZ_NO = "BIZ-20260101-0001";

    @Mock
    private LedgerEntryMapper ledgerEntryMapper;

    @InjectMocks
    private LedgerEntryRepoImpl entryRepo;

    @Captor
    private ArgumentCaptor<LedgerEntryPO> poCaptor;

    @Nested
    @DisplayName("saveBatch 批量保存 | save batch")
    class SaveBatch {

        /**
         * 批量保存时逐条调用 Mapper.insert，且 journalId 和 bizNo 正确设置。
         */
        @Test
        @DisplayName("批量保存调用 Mapper.insert 并设置 journalId/bizNo")
        void saveBatch_insertsEachEntry() {
            LedgerEntryEntity entry = new LedgerEntryEntity(
                    LedgerAccountCodeEnum.PLATFORM_HOT, LedgerEntryTypeEnum.DEBIT,
                    new BigDecimal("100"), null, null, null, null);

            entryRepo.saveBatch(List.of(entry), JOURNAL_ID, BIZ_NO);

            verify(ledgerEntryMapper, times(1)).insert(poCaptor.capture());
            LedgerEntryPO saved = poCaptor.getValue();
            assertEquals(JOURNAL_ID, saved.getJournalId());
            assertEquals(BIZ_NO, saved.getBizNo());
            assertEquals("PLATFORM_HOT", saved.getAccountCode());
        }
    }

    @Nested
    @DisplayName("findByJournalId 按凭证 ID 查询 | find by journal id")
    class FindByJournalId {

        /**
         * 按凭证 ID 查询返回对应的分录列表。
         */
        @Test
        @DisplayName("按凭证 ID 查询成功")
        void findByJournalId_returnsEntries() {
            LedgerEntryPO po = new LedgerEntryPO();
            po.setId(1L);
            po.setJournalId(JOURNAL_ID);
            po.setAccountCode("PLATFORM_HOT");
            po.setEntryType("DEBIT");
            po.setAmount(new BigDecimal("100"));
            when(ledgerEntryMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(po));

            List<LedgerEntryEntity> result = entryRepo.findByJournalId(JOURNAL_ID);

            assertEquals(1, result.size());
            assertEquals(LedgerAccountCodeEnum.PLATFORM_HOT, result.get(0).getAccountCode());
        }
    }

    @Nested
    @DisplayName("findByBizNo 按业务号查询 | find by bizNo")
    class FindByBizNo {

        /**
         * 按业务号查询返回对应的分录列表。
         */
        @Test
        @DisplayName("按业务号查询成功")
        void findByBizNo_returnsEntries() {
            LedgerEntryPO po = new LedgerEntryPO();
            po.setId(1L);
            po.setBizNo(BIZ_NO);
            po.setAccountCode("DEPOSIT_IN_TRANSIT");
            po.setEntryType("CREDIT");
            po.setAmount(new BigDecimal("100"));
            when(ledgerEntryMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(po));

            List<LedgerEntryEntity> result = entryRepo.findByBizNo(BIZ_NO);

            assertEquals(1, result.size());
            assertEquals(LedgerEntryTypeEnum.CREDIT, result.get(0).getEntryType());
        }

        /**
         * bizNo 不存在时返回空列表。
         */
        @Test
        @DisplayName("bizNo 不存在返回空列表")
        void findByBizNo_whenNotExists_returnsEmpty() {
            when(ledgerEntryMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

            List<LedgerEntryEntity> result = entryRepo.findByBizNo(BIZ_NO);

            assertTrue(result.isEmpty());
        }
    }
}
