package io.github.open55.otx.application.ledger.service.impl;

import cn.hutool.core.lang.Assert;
import io.github.open55.otx.application.ledger.assembler.LedgerAssembler;
import io.github.open55.otx.application.ledger.dto.request.PostJournalRequestDTO;
import io.github.open55.otx.application.ledger.dto.response.JournalDetailResponseDTO;
import io.github.open55.otx.application.ledger.dto.response.JournalSummaryDTO;
import io.github.open55.otx.application.ledger.service.LedgerAppService;
import io.github.open55.otx.common.exception.BizErrorEnum;
import io.github.open55.otx.common.exception.BizException;
import io.github.open55.otx.common.exception.BizIdempotentException;
import io.github.open55.otx.common.exception.OptimisticLockException;
import io.github.open55.otx.domain.ledger.LedgerEntryEntity;
import io.github.open55.otx.domain.ledger.LedgerJournalEntity;
import io.github.open55.otx.domain.ledger.enums.LedgerJournalStatusEnum;
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

    /**
     * 创建草稿凭证入口方法，完成入参校验和幂等检查后委托给 createDraftJournalAtomic。
     * <p>
     * 复用 {@link #postJournal(PostJournalRequestDTO)} 的幂等范式：
     * 入参校验（bizNo/currency/entries 非空）→ existsByBizNo 前置检查 →
     * 通过 self 代理调用 createDraftJournalAtomic 触发 Spring AOP 事务与重试，
     * 捕获 BizIdempotentException 视为幂等成功返回已存在的凭证。
     * 与 postJournal 的区别：不调用 journal.post()，凭证状态保持 DRAFT。
     *
     * @param req 过账请求
     * @return 凭证详情，status 为 DRAFT
     */
    @Override
    public JournalDetailResponseDTO createDraftJournal(PostJournalRequestDTO req) {
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
            // 通过 self 代理调用 createDraftJournalAtomic，触发 Spring AOP 事务和重试注解
            return self.createDraftJournalAtomic(req);
        } catch (BizIdempotentException e) {
            // 唯一键冲突被视为幂等成功，返回已存在的 Journal
            return findByBizNo(req.getBizNo());
        }
    }

    /**
     * 创建草稿凭证原子方法，在独立事务中执行。
     * <p>
     * 构造 Entity → 借贷平衡与同户同向唯一校验 → 持久化 Journal → 持久化 Entry，
     * 不调用 journal.post()，凭证状态保持 DRAFT，供链上确认后再过账。
     *
     * @param req 过账请求
     * @return 凭证详情，status 为 DRAFT
     */
    @Retryable(retryFor = {OptimisticLockException.class}, maxAttempts = 5,
            backoff = @Backoff(delay = 100, multiplier = 1.5, maxDelay = 500, random = true))
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public JournalDetailResponseDTO createDraftJournalAtomic(PostJournalRequestDTO req) {
        try {
            LedgerJournalEntity journal = LedgerAssembler.INSTANCE.toEntity(req);
            // 保存前校验借贷必平与同户同向唯一，不满足则整单不落库
            journal.assertBalanced();
            journal.assertNoDuplicateAccount();
            journalRepo.save(journal);
            entryRepo.saveBatch(journal.getEntries(), journal.getId(), journal.getBizNo());
            // 草稿凭证不调用 post()，状态保持 DRAFT
            return LedgerAssembler.INSTANCE.toResponse(journal);
        } catch (DuplicateKeyException ex) {
            throw new BizIdempotentException(ex);
        }
    }

    /**
     * 按业务流水号过账草稿凭证入口方法。
     * <p>
     * 加载凭证后先做状态判断：已 POSTED 直接幂等返回；
     * DRAFT 通过 self 代理调用 postJournalByBizNoAtomic 执行过账（REQUIRES_NEW + 重试）；
     * REVERSED 状态由聚合根 post() 抛 LEDGER_JOURNAL_NOT_DRAFT。
     *
     * @param bizNo 业务流水号
     * @return 凭证详情，status 为 POSTED
     */
    @Override
    public JournalDetailResponseDTO postJournalByBizNo(String bizNo) {
        LedgerJournalEntity journal = journalRepo.findByBizNo(bizNo).orElse(null);
        if (journal == null) {
            throw BizException.get(BizErrorEnum.LEDGER_JOURNAL_NOT_FOUND);
        }

        // 幂等检查：已 POSTED 的凭证直接返回已有结果，不重复过账
        if (journal.getStatus() == LedgerJournalStatusEnum.POSTED) {
            return findByBizNo(bizNo);
        }

        // DRAFT 过账；REVERSED 会在原子方法内由 post() 抛 LEDGER_JOURNAL_NOT_DRAFT
        return self.postJournalByBizNoAtomic(bizNo);
    }

    /**
     * 过账草稿凭证原子方法，在独立事务中执行。
     * <p>
     * 重新加载凭证（获取最新乐观锁版本）并装配其全部分录（journalRepo 只查主表，
     * 需经 entryRepo 加载分录后才能通过聚合根 post() 的借贷平衡校验）→
     * journal.post()（聚合根校验：借贷必平、同户同向唯一、仅 DRAFT 可过账）→
     * 更新状态为 POSTED。
     *
     * @param bizNo 业务流水号
     * @return 凭证详情，status 为 POSTED
     */
    @Retryable(retryFor = {OptimisticLockException.class}, maxAttempts = 5,
            backoff = @Backoff(delay = 100, multiplier = 1.5, maxDelay = 500, random = true))
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public JournalDetailResponseDTO postJournalByBizNoAtomic(String bizNo) {
        LedgerJournalEntity journal = journalRepo.findByBizNo(bizNo).orElse(null);
        if (journal == null) {
            throw BizException.get(BizErrorEnum.LEDGER_JOURNAL_NOT_FOUND);
        }
        // 装配分录：仓储查询仅返回主表数据，post() 的借贷平衡校验依赖完整分录列表
        journal.setEntries(entryRepo.findByBizNo(bizNo));
        // 聚合根校验：仅 DRAFT 可过账，REVERSED/POSTED 抛 LEDGER_JOURNAL_NOT_DRAFT
        journal.post();
        // 更新状态，乐观锁版本冲突时抛出 OptimisticLockException 触发重试
        journalRepo.update(journal);
        return LedgerAssembler.INSTANCE.toResponse(journal);
    }

    /**
     * 查询全部凭证摘要（不含分录），按创建时间降序返回（最新在前）。
     * <p>
     * 只读查询，数据量大时后续引入分页；管理控制台凭证列表的数据源，
     * 分录详情走 {@link #findByBizNo(String)}（含分录）。
     *
     * @return 凭证摘要列表
     */
    @Override
    public List<JournalSummaryDTO> listJournals() {
        return journalRepo.findAllOrderByCreateTimeDesc().stream()
                .map(this::toSummary)
                .toList();
    }

    /**
     * 凭证实体装配为凭证摘要 DTO（不含分录），枚举字段转字符串。
     *
     * @param journal 凭证实体
     * @return 凭证摘要 DTO
     */
    private JournalSummaryDTO toSummary(LedgerJournalEntity journal) {
        JournalSummaryDTO dto = new JournalSummaryDTO();
        dto.setBizNo(journal.getBizNo());
        dto.setBizType(journal.getBizType() == null ? null : journal.getBizType().name());
        dto.setStatus(journal.getStatus() == null ? null : journal.getStatus().name());
        dto.setTotalAmount(journal.getTotalAmount());
        dto.setPostingDate(journal.getPostingDate());
        dto.setChainTxHash(journal.getChainTxHash());
        return dto;
    }
}
