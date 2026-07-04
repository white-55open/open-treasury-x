package io.github.open55.otx.infrastructure.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.github.open55.otx.domain.ledger.LedgerEntryEntity;
import io.github.open55.otx.domain.ledger.repository.LedgerEntryRepo;
import io.github.open55.otx.infrastructure.converter.LedgerEntryConverter;
import io.github.open55.otx.infrastructure.mapper.LedgerEntryMapper;
import io.github.open55.otx.infrastructure.po.LedgerEntryPO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 分录值对象仓储实现。
 * <p>
 * 使用 MyBatis-Plus 的 BaseMapper 实现持久化，通过 LedgerEntryConverter 完成 PO ↔ Entity 互转。
 */
@Repository
public class LedgerEntryRepoImpl implements LedgerEntryRepo {

    @Resource
    private LedgerEntryMapper ledgerEntryMapper;

    /**
     * 批量持久化分录。
     * <p>
     * 将 Entity 列表转换为 PO 列表后逐条 insert 写入 ledger_entry_t 表。
     * 批量插入在过账事务内由聚合根驱动，每次过账对应 2~N 条分录。
     *
     * @param entries 分录列表
     */
    @Override
    public void saveBatch(List<LedgerEntryEntity> entries, Long journalId, String bizNo) {
        List<LedgerEntryPO> poList = LedgerEntryConverter.INSTANCE.entity2poList(entries);
        for (LedgerEntryPO po : poList) {
            po.setJournalId(journalId);
            po.setBizNo(bizNo);
            ledgerEntryMapper.insert(po);
        }
    }

    /**
     * 按凭证 ID 查询分录。
     * <p>
     * 查询 ledger_entry_t 表中 journal_id 匹配的记录列表，转换为 Entity 列表返回。
     *
     * @param journalId 凭证 ID
     * @return 分录列表
     */
    @Override
    public List<LedgerEntryEntity> findByJournalId(Long journalId) {
        List<LedgerEntryPO> poList = ledgerEntryMapper.selectList(
                new LambdaQueryWrapper<LedgerEntryPO>()
                        .eq(LedgerEntryPO::getJournalId, journalId));
        return LedgerEntryConverter.INSTANCE.po2EntityList(poList);
    }

    /**
     * 按业务流水号查询分录。
     * <p>
     * 查询 ledger_entry_t 表中 biz_no 匹配的记录列表，转换为 Entity 列表返回。
     *
     * @param bizNo 业务流水号
     * @return 分录列表
     */
    @Override
    public List<LedgerEntryEntity> findByBizNo(String bizNo) {
        List<LedgerEntryPO> poList = ledgerEntryMapper.selectList(
                new LambdaQueryWrapper<LedgerEntryPO>()
                        .eq(LedgerEntryPO::getBizNo, bizNo));
        return LedgerEntryConverter.INSTANCE.po2EntityList(poList);
    }
}
