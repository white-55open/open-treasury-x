package io.github.open55.otx.application.withdraw.service.impl;

import io.github.open55.otx.application.account.service.AccountAppService;
import io.github.open55.otx.application.deposit.dto.ChangeAmountRequest;
import io.github.open55.otx.application.fundflow.service.FundFlowAppService;
import io.github.open55.otx.application.ledger.dto.request.LedgerEntryRequestDTO;
import io.github.open55.otx.application.ledger.dto.request.PostJournalRequestDTO;
import io.github.open55.otx.application.ledger.service.LedgerAppService;
import io.github.open55.otx.application.withdraw.service.WithdrawAppService;
import io.github.open55.otx.domain.account.repository.AccountRepo;
import io.github.open55.otx.domain.fundflow.enums.FundFlowTypeEnum;
import io.github.open55.otx.domain.ledger.enums.LedgerAccountCodeEnum;
import io.github.open55.otx.domain.ledger.enums.LedgerBizTypeEnum;
import io.github.open55.otx.domain.ledger.enums.LedgerEntryTypeEnum;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class WithdrawAppServiceImpl implements WithdrawAppService {
    private final AccountRepo accountRepo;

    private final FundFlowAppService fundFlowAppService;
    private final LedgerAppService ledgerAppService;

    @Resource
    @Lazy
    private AccountAppService accountAppService;

    @Resource
    @Lazy
    private WithdrawAppServiceImpl self;

    @Override
    public String withdraw(ChangeAmountRequest request) {
        request.setFundFlowType(FundFlowTypeEnum.WITHDRAW);
        String bizNo = accountAppService.changeAmountWithFundFlow(request);

        try {
            PostJournalRequestDTO journalReq = buildWithdrawJournalRequest(request);
            ledgerAppService.postJournal(journalReq);
        } catch (Exception e) {
            // 总账过账失败，余额和流水已提交，日志记录供后续对账修复
            log.warn("Journal posting failed, bizNo={}", bizNo, e);
        }

        return bizNo;
    }

    private PostJournalRequestDTO buildWithdrawJournalRequest(ChangeAmountRequest req) {
        PostJournalRequestDTO dto = new PostJournalRequestDTO();
        dto.setBizNo(req.getBizNo());
        dto.setBizType(LedgerBizTypeEnum.WITHDRAW_ONCHAIN);
        dto.setCurrency(req.getCurrency());
        dto.setPostingDate(LocalDate.now());
        dto.setDescription("提现-" + req.getBizNo());
        dto.setChainId(null);
        dto.setChainTxHash(null);
        dto.setBlockNumber(null);
        dto.setTokenAddress(null);

        LedgerEntryRequestDTO debitEntry = new LedgerEntryRequestDTO();
        debitEntry.setAccountCode(LedgerAccountCodeEnum.USER_AVAILABLE);
        debitEntry.setEntryType(LedgerEntryTypeEnum.DEBIT);
        debitEntry.setAmount(req.getAmount());
        debitEntry.setUid(req.getUid());
        debitEntry.setCounterparty(null);
        debitEntry.setBalanceAfter(null);
        debitEntry.setRemark(null);

        LedgerEntryRequestDTO creditEntry = new LedgerEntryRequestDTO();
        creditEntry.setAccountCode(LedgerAccountCodeEnum.WITHDRAW_IN_TRANSIT);
        creditEntry.setEntryType(LedgerEntryTypeEnum.CREDIT);
        creditEntry.setAmount(req.getAmount());
        creditEntry.setUid(null);
        creditEntry.setCounterparty(null);
        creditEntry.setBalanceAfter(null);
        creditEntry.setRemark(null);

        dto.setEntries(List.of(debitEntry, creditEntry));
        return dto;
    }
}