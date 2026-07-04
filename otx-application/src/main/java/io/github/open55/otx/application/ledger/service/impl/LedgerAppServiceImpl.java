package io.github.open55.otx.application.ledger.service.impl;

import cn.hutool.core.lang.Assert;
import io.github.open55.otx.application.ledger.assembler.LedgerAssembler;
import io.github.open55.otx.application.ledger.dto.request.PostJournalRequestDTO;
import io.github.open55.otx.application.ledger.dto.response.JournalDetailResponseDTO;
import io.github.open55.otx.application.ledger.service.LedgerAppService;
import io.github.open55.otx.common.exception.BizErrorEnum;
import io.github.open55.otx.common.exception.BizException;
import io.github.open55.otx.common.exception.BizIdempotentException;
import io.github.open55.otx.common.exception.OptimisticLockException;
import io.github.open55.otx.domain.ledger.LedgerEntryEntity;
import io.github.open55.otx.domain.ledger.LedgerJournalEntity;
import io.github.open55.otx.domain.ledger.repository.LedgerEntryRepo;
import io.github.open55.otx.domain.ledger.repository.LedgerJournalRepo;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Ledger 应用服务实现。
 * <p>
 * 编排领域对象完成过账与查询用例，管理事务边界。
 */
@Service
@RequiredArgsConstructor
public class LedgerAppServiceImpl implements LedgerAppService {

    private final LedgerJournalRepo journalRepo;

    private final LedgerEntryRepo entryRepo;

    @Lazy
    @Resource
    private LedgerAppServiceImpl self;

    /**
     * 过账入口方法，完成入参校验和幂等检查后委托给 postJournalAtomic。
     * <p>
     * 入参校验通过后先做幂等检查（bizNo 已存在则直接返回），
     * 再通过 self 代理调用 postJournalAtomic 以触发 Spring AOP 事务和重试。
     *
     * @param req 过账请求
     * @return 凭证详情
     */
    @Override
    public JournalDetailResponseDTO postJournal(PostJournalRequestDTO req) {
        // 入参校验：bizNo 非空、currency 非空、entries 非空
        Assert.notNull(req, () -> BizException.get(BizErrorEnum.PARAM_MISS));
        Assert.notBlank(req.getBizNo(), () -> BizException.get(BizErrorEnum.LEDGER_BIZ_NO_EMPTY));
        Assert.notBlank(req.getCurrency(), () -> BizException.get(BizErrorEnum.LEDGER_CURRENCY_EMPTY));
        if (req.getEntries() == null || req.getEntries().isEmpty()) {
            throw BizException.get(BizErrorEnum.LEDGER_ENTRIES_EMPTY);
        }

        // 幂等检查：相同 bizNo 的重复请求直接返回已存在的 Journal
        if (journalRepo.existsByBizNo(req.getBizNo())) {
            return findByBizNo(req.getBizNo());
        }

        try {
            // 通过 self 代理调用 postJournalAtomic，触发 Spring AOP 事务和重试注解
            return self.postJournalAtomic(req);
        } catch (BizIdempotentException e) {
            // 唯一键冲突被视为幂等成功，返回已存在的 Journal
            return findByBizNo(req.getBizNo());
        }
    }

    /**
     * 过账原子方法，在独立事务中执行。
     * <p>
     * 构造 Entity → 不变量校验 → 持久化 Journal → 持久化 Entry → 过账 → 更新状态。
     *
     * @param req 过账请求
     * @return 凭证详情
     */
    @Retryable(retryFor = {OptimisticLockException.class}, maxAttempts = 5,
            backoff = @Backoff(delay = 100, multiplier = 1.5, maxDelay = 500, random = true))
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public JournalDetailResponseDTO postJournalAtomic(PostJournalRequestDTO req) {
        try {
            LedgerJournalEntity journal = LedgerAssembler.INSTANCE.toEntity(req);
            journalRepo.save(journal);
            entryRepo.saveBatch(journal.getEntries(), journal.getId(), journal.getBizNo());
            journal.post();
            journalRepo.update(journal);
            return LedgerAssembler.INSTANCE.toResponse(journal);
        } catch (DuplicateKeyException ex) {
            throw new BizIdempotentException(ex);
        }
    }

    /**
     * 按业务流水号查询凭证详情。
     * <p>
     * 先查 Journal 聚合根，不存在则抛 LEDGER_JOURNAL_NOT_FOUND，
     * 存在则继续查询关联的 Entry 列表，装配为 JournalDetailResponseDTO 返回。
     *
     * @param bizNo 业务流水号
     * @return 凭证详情，包含 Journal 及其全部 Entry
     */
    @Override
    public JournalDetailResponseDTO findByBizNo(String bizNo) {
        LedgerJournalEntity journal = journalRepo.findByBizNo(bizNo).orElse(null);
        if (journal == null) {
            throw BizException.get(BizErrorEnum.LEDGER_JOURNAL_NOT_FOUND);
        }
        List<LedgerEntryEntity> entries = entryRepo.findByBizNo(bizNo);
        JournalDetailResponseDTO response = LedgerAssembler.INSTANCE.toResponse(journal);
        response.setEntries(LedgerAssembler.INSTANCE.toEntryResponses(entries));
        return response;
    }
}
