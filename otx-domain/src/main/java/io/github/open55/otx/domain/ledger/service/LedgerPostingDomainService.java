package io.github.open55.otx.domain.ledger.service;

import io.github.open55.otx.domain.ledger.LedgerJournalEntity;

/**
 * 过账领域服务接口。
 * <p>
 * 定义过账流程的编排契约：负责校验借贷平衡、同户同向唯一约束，
 * 并推进凭证状态从 DRAFT 到 POSTED。实现类在基础设施层编排具体仓储调用。
 * 当前仅定义接口与 Javadoc，不写实现。
 */
public interface LedgerPostingDomainService {

    /**
     * 执行过账，将凭证持久化并推进状态至 POSTED。
     * <p>
     * 实现需依次完成：校验不变量 → 持久化凭证 → 持久化分录 → 更新凭证状态。
     *
     * @param journal 待过账的凭证聚合根，需满足借贷平衡和同户同向唯一约束
     */
    void postJournal(LedgerJournalEntity journal);
}
