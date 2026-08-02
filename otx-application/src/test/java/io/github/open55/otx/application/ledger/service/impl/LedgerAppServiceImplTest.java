package io.github.open55.otx.application.ledger.service.impl;

import io.github.open55.otx.application.ledger.assembler.LedgerAssembler;
import io.github.open55.otx.application.ledger.dto.request.LedgerEntryRequestDTO;
import io.github.open55.otx.application.ledger.dto.request.PostJournalRequestDTO;
import io.github.open55.otx.application.ledger.dto.response.JournalDetailResponseDTO;
import io.github.open55.otx.common.exception.BizException;
import io.github.open55.otx.domain.ledger.LedgerJournalEntity;
import io.github.open55.otx.domain.ledger.enums.LedgerAccountCodeEnum;
import io.github.open55.otx.domain.ledger.enums.LedgerBizTypeEnum;
import io.github.open55.otx.domain.ledger.enums.LedgerEntryTypeEnum;
import io.github.open55.otx.domain.ledger.enums.LedgerJournalStatusEnum;
import io.github.open55.otx.domain.ledger.repository.LedgerEntryRepo;
import io.github.open55.otx.domain.ledger.repository.LedgerJournalRepo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * LedgerAppServiceImpl 单元测试。
 * <p>
 * 覆盖过账原子方法、草稿凭证创建与按业务号过账的正常路径与异常分支。
 */
@ExtendWith(MockitoExtension.class)
class LedgerAppServiceImplTest {

    @Mock
    private LedgerJournalRepo journalRepo;

    @Mock
    private LedgerEntryRepo entryRepo;

    @Spy
    private LedgerAssembler assembler = LedgerAssembler.INSTANCE;

    @InjectMocks
    private LedgerAppServiceImpl service;

    @Captor
    private ArgumentCaptor<LedgerJournalEntity> journalCaptor;

    /**
     * 将 self 字段指向被测实例本身，模拟 Spring 自代理以触发 AOP 注解。
     *
     * @param target 被测实例
     * @throws Exception 反射异常
     */
    private static void setSelf(LedgerAppServiceImpl target) throws Exception {
        Field field = LedgerAppServiceImpl.class.getDeclaredField("self");
        field.setAccessible(true);
        field.set(target, target);
    }

    /**
     * 构造 DRAFT 状态的凭证实体，复用合法过账请求装配。
     *
     * @return DRAFT 凭证实体
     */
    private static LedgerJournalEntity draftEntity() {
        return LedgerAssembler.INSTANCE.toEntity(buildValidRequest());
    }

    /**
     * postJournalAtomic 正常过账。
     * <p>
     * 给定合法的过账请求，验证凭证被创建、持久化、过账、更新状态。
     */
    @Test
    void postJournalAtomic_withValidRequest_postsSuccessfully() {
        PostJournalRequestDTO req = buildValidRequest();

        service.postJournalAtomic(req);

        Mockito.verify(journalRepo).save(journalCaptor.capture());
        LedgerJournalEntity savedJournal = journalCaptor.getValue();
        assertNotNull(savedJournal.getBizNo());
        assertEquals("BIZ-001", savedJournal.getBizNo());

        Mockito.verify(entryRepo).saveBatch(eq(savedJournal.getEntries()), Mockito.any(), Mockito.any());
        Mockito.verify(journalRepo).update(savedJournal);
    }

    /**
     * postJournalAtomic DuplicateKey 转换为 BizIdempotentException。
     * <p>
     * 当持久化时触发唯一键冲突，验证异常被正确转换。
     */
    @Test
    void postJournalAtomic_whenDuplicateKey_throwsBizIdempotent() {
        PostJournalRequestDTO req = buildValidRequest();
        Mockito.doThrow(new DuplicateKeyException("uk_biz_no conflict")).when(journalRepo).save(Mockito.notNull());

        assertThrows(io.github.open55.otx.common.exception.BizIdempotentException.class,
                () -> service.postJournalAtomic(req));
    }

    /**
     * createDraftJournal 正常路径：创建 DRAFT 凭证并透传链上字段。
     * <p>
     * 给定携带链上字段的合法请求，断言凭证被持久化、状态保持 DRAFT、
     * 不更新状态（不调用 post()）、链上字段透传到响应。
     */
    @Test
    void createDraftJournal_withValidRequest_returnsDraftJournal() throws Exception {
        PostJournalRequestDTO req = buildValidRequest();
        req.setChainId("11155111");
        req.setChainTxHash("0xabc123");
        req.setTokenAddress("0xToken");
        setSelf(service);
        when(journalRepo.existsByBizNo("BIZ-001")).thenReturn(false);

        JournalDetailResponseDTO result = service.createDraftJournal(req);

        assertEquals("DRAFT", result.getStatus());
        assertEquals("11155111", result.getChainId());
        assertEquals("0xabc123", result.getChainTxHash());
        assertEquals("0xToken", result.getTokenAddress());
        verify(journalRepo).save(journalCaptor.capture());
        assertEquals(LedgerJournalStatusEnum.DRAFT, journalCaptor.getValue().getStatus());
        verify(entryRepo).saveBatch(eq(journalCaptor.getValue().getEntries()), Mockito.any(), Mockito.any());
        // DRAFT 凭证不执行过账，无状态更新
        verify(journalRepo, never()).update(any());
    }

    /**
     * createDraftJournal 幂等：相同 bizNo 已存在时返回已有 DRAFT 凭证。
     * <p>
     * 断言直接返回已存在的凭证且不重复持久化。
     */
    @Test
    void createDraftJournal_duplicateBizNo_returnsExistingDraft() {
        PostJournalRequestDTO req = buildValidRequest();
        when(journalRepo.existsByBizNo("BIZ-001")).thenReturn(true);
        when(journalRepo.findByBizNo("BIZ-001")).thenReturn(Optional.of(draftEntity()));
        when(entryRepo.findByBizNo("BIZ-001")).thenReturn(List.of());

        JournalDetailResponseDTO result = service.createDraftJournal(req);

        assertEquals("DRAFT", result.getStatus());
        verify(journalRepo, never()).save(any());
        verify(entryRepo, never()).saveBatch(any(), any(), any());
    }

    /**
     * createDraftJournal 借贷校验：借贷不平衡时拒绝且不持久化。
     * <p>
     * 断言抛 LEDGER_NOT_BALANCED 且凭证与分录均未落库。
     */
    @Test
    void createDraftJournal_unbalancedEntries_throwsLedgerNotBalanced() throws Exception {
        PostJournalRequestDTO req = buildValidRequest();
        // 修改贷方分录金额使借贷不平衡
        req.getEntries().get(1).setAmount(BigDecimal.valueOf(200));
        setSelf(service);
        when(journalRepo.existsByBizNo("BIZ-001")).thenReturn(false);

        BizException ex = assertThrows(BizException.class, () -> service.createDraftJournal(req));

        assertEquals("LEDGER_NOT_BALANCED", ex.getErrorCode());
        verify(journalRepo, never()).save(any());
        verify(entryRepo, never()).saveBatch(any(), any(), any());
    }

    /**
     * postJournalByBizNo 正常路径：DRAFT 凭证过账为 POSTED。
     * <p>
     * 断言聚合根 post() 后被持久化更新、响应状态为 POSTED。
     * 原子方法内 post() 的借贷平衡校验依赖分录装配，需 stub 分录查询。
     */
    @Test
    void postJournalByBizNo_withDraftJournal_changesStatusToPosted() throws Exception {
        LedgerJournalEntity draft = draftEntity();
        when(journalRepo.findByBizNo("BIZ-001")).thenReturn(Optional.of(draft));
        // 装配分录：原子方法内通过 entryRepo 加载分录后执行 post() 校验
        when(entryRepo.findByBizNo("BIZ-001")).thenReturn(new ArrayList<>(draft.getEntries()));
        setSelf(service);

        JournalDetailResponseDTO result = service.postJournalByBizNo("BIZ-001");

        assertEquals("POSTED", result.getStatus());
        assertEquals(LedgerJournalStatusEnum.POSTED, draft.getStatus());
        verify(journalRepo).update(draft);
    }

    /**
     * postJournalByBizNo 幂等：已 POSTED 凭证直接返回已有结果。
     * <p>
     * 断言不重复执行过账更新。
     */
    @Test
    void postJournalByBizNo_alreadyPosted_returnsExistingJournal() {
        LedgerJournalEntity posted = draftEntity();
        posted.post();
        when(journalRepo.findByBizNo("BIZ-001")).thenReturn(Optional.of(posted));
        when(entryRepo.findByBizNo("BIZ-001")).thenReturn(List.of());

        JournalDetailResponseDTO result = service.postJournalByBizNo("BIZ-001");

        assertEquals("POSTED", result.getStatus());
        verify(journalRepo, never()).update(any());
    }

    /**
     * postJournalByBizNo 异常分支：凭证不存在。
     * <p>
     * 断言抛 LEDGER_JOURNAL_NOT_FOUND。
     */
    @Test
    void postJournalByBizNo_notFound_throwsJournalNotFound() {
        when(journalRepo.findByBizNo("BIZ-001")).thenReturn(Optional.empty());

        BizException ex = assertThrows(BizException.class, () -> service.postJournalByBizNo("BIZ-001"));

        assertEquals("LEDGER_JOURNAL_NOT_FOUND", ex.getErrorCode());
    }

    /**
     * postJournalByBizNo 异常分支：REVERSED 凭证不可过账。
     * <p>
     * 断言抛 LEDGER_JOURNAL_NOT_DRAFT（聚合根 post() 校验）。
     */
    @Test
    void postJournalByBizNo_reversed_throwsJournalNotDraft() throws Exception {
        LedgerJournalEntity reversed = draftEntity();
        // 构造 REVERSED 状态的测试夹具：先过账再置为已冲销
        reversed.post();
        reversed.setStatus(LedgerJournalStatusEnum.REVERSED);
        when(journalRepo.findByBizNo("BIZ-001")).thenReturn(Optional.of(reversed));
        setSelf(service);

        BizException ex = assertThrows(BizException.class, () -> service.postJournalByBizNo("BIZ-001"));

        assertEquals("LEDGER_JOURNAL_NOT_DRAFT", ex.getErrorCode());
        verify(journalRepo, never()).update(any());
    }

    private static PostJournalRequestDTO buildValidRequest() {
        PostJournalRequestDTO req = new PostJournalRequestDTO();
        req.setBizNo("BIZ-001");
        req.setBizType(LedgerBizTypeEnum.DEPOSIT_ONCHAIN);
        req.setCurrency("USDT");
        req.setPostingDate(LocalDate.now());

        LedgerEntryRequestDTO debit = new LedgerEntryRequestDTO();
        debit.setAccountCode(LedgerAccountCodeEnum.PLATFORM_HOT);
        debit.setEntryType(LedgerEntryTypeEnum.DEBIT);
        debit.setAmount(BigDecimal.valueOf(100));

        LedgerEntryRequestDTO credit = new LedgerEntryRequestDTO();
        credit.setAccountCode(LedgerAccountCodeEnum.DEPOSIT_IN_TRANSIT);
        credit.setEntryType(LedgerEntryTypeEnum.CREDIT);
        credit.setAmount(BigDecimal.valueOf(100));

        req.setEntries(List.of(debit, credit));
        return req;
    }
}
