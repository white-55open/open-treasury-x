package io.github.open55.otx.infrastructure.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.github.open55.otx.domain.ledger.LedgerJournalEntity;
import io.github.open55.otx.domain.ledger.repository.LedgerJournalRepo;
import io.github.open55.otx.infrastructure.converter.LedgerJournalConverter;
import io.github.open55.otx.infrastructure.mapper.LedgerJournalMapper;
import io.github.open55.otx.infrastructure.po.LedgerJournalPO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 凭证聚合根仓储实现。
 * <p>
 * 使用 MyBatis-Plus 的 BaseMapper 实现持久化，通过 LedgerJournalConverter 完成 PO ↔ Entity 互转。
 */
@Repository
public class LedgerJournalRepoImpl implements LedgerJournalRepo {

    @Resource
    private LedgerJournalMapper ledgerJournalMapper;

    /**
     * 持久化新凭证。
     * <p>
     * 将 Entity 转换为 PO 后调用 insert 写入 ledger_journal_t 表。
     *
     * @param journal 待持久化的凭证聚合根
     */
    @Override
    public void save(LedgerJournalEntity journal) {
        LedgerJournalPO po = LedgerJournalConverter.INSTANCE.entity2po(journal);
        ledgerJournalMapper.insert(po);
        // MyBatis-Plus 将生成的主键回写到 PO，需要同步回 Entity
        if (journal.getId() == null) {
            journal.setId(po.getId());
        }
    }

    /**
     * 按业务流水号查询凭证。
     * <p>
     * 查询 ledger_journal_t 表中 biz_no 匹配的记录，转换为 Entity 返回。
     * 查不到时返回 Optional.empty()。
     *
     * @param bizNo 业务流水号
     * @return 凭证聚合根，不存在时返回 Optional.empty()
     */
    @Override
    public Optional<LedgerJournalEntity> findByBizNo(String bizNo) {
        LedgerJournalPO po = ledgerJournalMapper.selectOne(
                new LambdaQueryWrapper<LedgerJournalPO>()
                        .eq(LedgerJournalPO::getBizNo, bizNo));
        return Optional.ofNullable(LedgerJournalConverter.INSTANCE.po2Entity(po));
    }

    /**
     * 判断指定业务流水号是否已存在。
     * <p>
     * 使用 selectCount 统计匹配 biz_no 的记录数，大于 0 表示已存在。
     *
     * @param bizNo 业务流水号
     * @return true 表示已存在
     */
    @Override
    public boolean existsByBizNo(String bizNo) {
        return ledgerJournalMapper.selectCount(
                new LambdaQueryWrapper<LedgerJournalPO>()
                        .eq(LedgerJournalPO::getBizNo, bizNo)) > 0;
    }

    /**
     * 更新凭证（如过账后状态变更或冲销后状态变更）。
     * <p>
     * 将 Entity 转换为 PO 后调用 updateById 更新 ledger_journal_t 表中对应行。
     *
     * @param journal 待更新的凭证聚合根
     */
    @Override
    public void update(LedgerJournalEntity journal) {
        LedgerJournalPO po = LedgerJournalConverter.INSTANCE.entity2po(journal);
        ledgerJournalMapper.updateById(po);
    }
}
