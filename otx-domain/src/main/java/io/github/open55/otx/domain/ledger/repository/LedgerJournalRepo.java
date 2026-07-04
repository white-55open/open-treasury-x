package io.github.open55.otx.domain.ledger.repository;

import io.github.open55.otx.domain.ledger.LedgerJournalEntity;

import java.util.Optional;

/**
 * 凭证聚合根仓储接口。
 * <p>
 * 操作 LedgerJournalEntity 聚合根，提供 save / findByBizNo / existsByBizNo / update 方法。
 * 仓储只操作聚合根，不直接操作 Entry 值对象。实现类在基础设施层。
 */
public interface LedgerJournalRepo {

    /**
     * 持久化新凭证。
     *
     * @param journal 待持久化的凭证聚合根
     */
    void save(LedgerJournalEntity journal);

    /**
     * 按业务流水号查询凭证。
     *
     * @param bizNo 业务流水号
     * @return 凭证聚合根，不存在时返回 Optional.empty()
     */
    Optional<LedgerJournalEntity> findByBizNo(String bizNo);

    /**
     * 判断指定业务流水号是否已存在。
     *
     * @param bizNo 业务流水号
     * @return true 表示已存在
     */
    boolean existsByBizNo(String bizNo);

    /**
     * 更新凭证（如过账后状态变更或冲销后状态变更）。
     *
     * @param journal 待更新的凭证聚合根
     */
    void update(LedgerJournalEntity journal);
}
