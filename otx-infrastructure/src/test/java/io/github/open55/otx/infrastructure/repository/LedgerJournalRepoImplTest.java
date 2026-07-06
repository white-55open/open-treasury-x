package io.github.open55.otx.infrastructure.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.github.open55.otx.domain.ledger.LedgerEntryEntity;
import io.github.open55.otx.domain.ledger.LedgerJournalEntity;
import io.github.open55.otx.domain.ledger.enums.LedgerAccountCodeEnum;
import io.github.open55.otx.domain.ledger.enums.LedgerEntryTypeEnum;
import io.github.open55.otx.domain.ledger.repository.LedgerJournalRepo;
import io.github.open55.otx.infrastructure.mapper.LedgerJournalMapper;
import io.github.open55.otx.infrastructure.po.LedgerJournalPO;
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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * LedgerJournalRepoImpl 凭证聚合根仓储实现单元测试。
 * <p>
 * 覆盖保存、按 bizNo 查询、幂等校验、更新路径，验证 Mapper 调用和 PO ↔ Entity 转换。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("LedgerJournalRepoImpl 凭证仓储单元测试 | LedgerJournalRepoImpl unit tests")
class LedgerJournalRepoImplTest {

    private static final String BIZ_NO = "BIZ-20260101-0001";

    @Mock
    private LedgerJournalMapper ledgerJournalMapper;

    @InjectMocks
    private LedgerJournalRepoImpl journalRepo;

    @Captor
    private ArgumentCaptor<LedgerJournalPO> poCaptor;

    @Nested
    @DisplayName("save 保存凭证 | save journal")
    class Save {

        /**
         * 保存凭证时调用 Mapper.insert，PO 的 ID 回写到 Entity。
         */
        @Test
        @DisplayName("保存调用 Mapper.insert 并回写 ID")
        void save_insertsPoAndSetsId() {
            LedgerJournalEntity entity = LedgerJournalEntity.create(
                    BIZ_NO, null, "USDT", null, null, createValidEntries(), null, null, null, null);
            LedgerJournalPO po = new LedgerJournalPO();
            po.setId(100L);
            when(ledgerJournalMapper.insert(any(LedgerJournalPO.class))).thenAnswer(invocation -> {
                LedgerJournalPO arg = invocation.getArgument(0);
                arg.setId(100L);
                return 1;
            });

            journalRepo.save(entity);

            verify(ledgerJournalMapper).insert(poCaptor.capture());
            assertEquals(100L, entity.getId());
        }
    }

    @Nested
    @DisplayName("findByBizNo 按业务号查询 | find by bizNo")
    class FindByBizNo {

        /**
         * 凭证存在时返回 Optional.of(Entity)。
         */
        @Test
        @DisplayName("凭证存在返回 Optional.of")
        void findByBizNo_whenExists_returnsEntity() {
            LedgerJournalPO po = new LedgerJournalPO();
            po.setId(1L);
            po.setBizNo(BIZ_NO);
            po.setBizType("DEPOSIT_ONCHAIN");
            po.setCurrency("USDT");
            when(ledgerJournalMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(po);

            Optional<LedgerJournalEntity> result = journalRepo.findByBizNo(BIZ_NO);

            assertTrue(result.isPresent());
            assertEquals(BIZ_NO, result.get().getBizNo());
        }

        /**
         * 凭证不存在时返回 Optional.empty()。
         */
        @Test
        @DisplayName("凭证不存在返回 Optional.empty")
        void findByBizNo_whenNotExists_returnsEmpty() {
            when(ledgerJournalMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

            Optional<LedgerJournalEntity> result = journalRepo.findByBizNo(BIZ_NO);

            assertFalse(result.isPresent());
        }
    }

    @Nested
    @DisplayName("existsByBizNo 幂等校验 | exists by bizNo")
    class ExistsByBizNo {

        /**
         * bizNo 存在时返回 true。
         */
        @Test
        @DisplayName("bizNo 存在返回 true")
        void existsByBizNo_whenExists_returnsTrue() {
            when(ledgerJournalMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

            assertTrue(journalRepo.existsByBizNo(BIZ_NO));
        }

        /**
         * bizNo 不存在时返回 false。
         */
        @Test
        @DisplayName("bizNo 不存在返回 false")
        void existsByBizNo_whenNotExists_returnsFalse() {
            when(ledgerJournalMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

            assertFalse(journalRepo.existsByBizNo(BIZ_NO));
        }
    }

    @Nested
    @DisplayName("update 更新凭证 | update journal")
    class Update {

        /**
         * 更新凭证时调用 Mapper.updateById。
         */
        @Test
        @DisplayName("更新调用 Mapper.updateById")
        void update_updatesById() {
            LedgerJournalEntity entity = LedgerJournalEntity.create(
                    BIZ_NO, null, "USDT", null, null, createValidEntries(), null, null, null, null);

            journalRepo.update(entity);

            verify(ledgerJournalMapper).updateById(any(LedgerJournalPO.class));
        }
    }

    private static List<LedgerEntryEntity> createValidEntries() {
        return List.of(
                new LedgerEntryEntity(LedgerAccountCodeEnum.PLATFORM_HOT, LedgerEntryTypeEnum.DEBIT,
                        new BigDecimal("100"), null, null, null, null),
                new LedgerEntryEntity(LedgerAccountCodeEnum.DEPOSIT_IN_TRANSIT, LedgerEntryTypeEnum.CREDIT,
                        new BigDecimal("100"), null, null, null, null)
        );
    }
}
