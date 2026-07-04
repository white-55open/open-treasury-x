package io.github.open55.otx.application.ledger.service.impl;

import io.github.open55.otx.application.ledger.assembler.LedgerAssembler;
import io.github.open55.otx.application.ledger.dto.request.LedgerEntryRequestDTO;
import io.github.open55.otx.application.ledger.dto.request.PostJournalRequestDTO;
import io.github.open55.otx.domain.ledger.LedgerJournalEntity;
import io.github.open55.otx.domain.ledger.enums.LedgerAccountCodeEnum;
import io.github.open55.otx.domain.ledger.enums.LedgerBizTypeEnum;
import io.github.open55.otx.domain.ledger.enums.LedgerEntryTypeEnum;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * LedgerAppServiceImpl 单元测试。
 * <p>
 * 覆盖过账原子方法的正常路径与异常分支。
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
