package io.github.open55.otx.domain.ledger.repository;

import io.github.open55.otx.domain.ledger.LedgerEntryEntity;

import java.util.List;

/**
 * 分录值对象仓储接口。
 * <p>
 * 提供 Entry 的批量写和按 journalId / bizNo 查询。
 * 写操作仅在过账事务内由聚合根驱动，不提供独立写入口。实现类在基础设施层。
 */
public interface LedgerEntryRepo {

    /**
     * 批量持久化分录。
     *
     * @param entries   分录列表
     * @param journalId 归属凭证 ID
     * @param bizNo     业务流水号（冗余，用于按 bizNo 查询）
     */
    void saveBatch(List<LedgerEntryEntity> entries, Long journalId, String bizNo);

    /**
     * 按凭证 ID 查询分录。
     *
     * @param journalId 凭证 ID
     * @return 分录列表
     */
    List<LedgerEntryEntity> findByJournalId(Long journalId);

    /**
     * 按业务流水号查询分录。
     *
     * @param bizNo 业务流水号
     * @return 分录列表
     */
    List<LedgerEntryEntity> findByBizNo(String bizNo);
}
