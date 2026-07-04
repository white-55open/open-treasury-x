package io.github.open55.otx.domain.ledger.service;

import io.github.open55.otx.domain.ledger.LedgerJournalEntity;

/**
 * 过账领域服务实现。
 * <p>
 * 编排过账流程：先校验借贷平衡，再校验同户同向唯一，
 * 最后推进凭证状态。不引入额外业务规则，仅复用聚合根领域方法。
 */
public class LedgerPostingDomainServiceImpl implements LedgerPostingDomainService {

    @Override
    public void postJournal(LedgerJournalEntity journal) {
        // 编排过账流程：post() 内部自动完成 assertBalanced + assertNoDuplicateAccount + 状态推进
        journal.post();
    }
}
