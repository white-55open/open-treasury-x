package io.github.open55.otx.application.deposit.service.impl;

import io.github.open55.otx.application.account.service.AccountAppService;
import io.github.open55.otx.application.deposit.dto.DepositRequestDTO;
import io.github.open55.otx.application.deposit.service.DepositAppService;
import io.github.open55.otx.application.fundflow.service.FundFlowAppService;
import io.github.open55.otx.application.ledger.dto.request.LedgerEntryRequestDTO;
import io.github.open55.otx.application.ledger.dto.request.PostJournalRequestDTO;
import io.github.open55.otx.application.ledger.service.LedgerAppService;
import io.github.open55.otx.common.exception.BizErrorEnum;
import io.github.open55.otx.common.exception.BizException;
import io.github.open55.otx.common.exception.blockchain.Web3jRpcException;
import io.github.open55.otx.domain.account.repository.AccountRepo;
import io.github.open55.otx.domain.fundflow.enums.FundFlowTypeEnum;
import io.github.open55.otx.domain.ledger.enums.LedgerAccountCodeEnum;
import io.github.open55.otx.domain.ledger.enums.LedgerBizTypeEnum;
import io.github.open55.otx.domain.ledger.enums.LedgerEntryTypeEnum;
import io.github.open55.otx.domain.ledger.port.ChainQueryPort;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/**
 * 充值应用服务实现，编排充值入账的链上确认闸门与账务入账流程。
 * <p>
 * 入账前强制链上确认检查：先校验请求携带链上证据（chainId / chainTxHash），
 * 再通过 ChainQueryPort 确认链上交易已达安全确认数，未确认或链查询失败
 * （fail-safe）一律拒绝入账，杜绝未确认 / 孤块 / 伪造交易产生账务副作用；
 * 确认通过后执行既有动账流程（余额变更 + 流水强一致、总账过账最终一致），
 * 凭证携带链上证据（chainId / chainTxHash / blockNumber / tokenAddress）
 * 支撑后续链上与链下对账。幂等语义不变（bizNo + 唯一索引），确认闸门每次执行。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DepositAppServiceImpl implements DepositAppService {

    private final AccountRepo accountRepo;
    private final FundFlowAppService fundFlowAppService;
    private final LedgerAppService ledgerAppService;
    private final ChainQueryPort chainQueryPort;

    /**
     * 充值入账所需的默认安全确认数，经配置注入。
     * <p>
     * 读取 web3j.required-confirmations 配置（缺省 12），请求级
     * requiredConfirmations 可单笔覆盖。通过 @Value 读取配置不违反
     * 依赖铁律（application 不依赖 infrastructure 的类）。
     * 非 final 字段，避免进入 @RequiredArgsConstructor 生成的构造器。
     */
    @Value("${web3j.required-confirmations:12}")
    private int defaultConfirmations;

    @Resource
    @Lazy
    private AccountAppService accountAppService;

    @Resource
    @Lazy
    private DepositAppServiceImpl self;


    @Override
    public String deposit(DepositRequestDTO request) {
        request.setFundFlowType(FundFlowTypeEnum.DEPOSIT);

        // 闸门一：链上证据校验（参数校验，无事务，零副作用）
        assertChainEvidence(request);
        // 闸门二：链上确认校验（只读 RPC，无事务，未确认或查询失败时零副作用）
        assertChainConfirmed(request);
        // 确认通过后查询一次回执取区块高度，供凭证填充（回执缺失时置 null 仍允许入账）
        Long blockNumber = resolveBlockNumber(request);

        String bizNo = accountAppService.changeAmountWithFundFlow(request);

        try {
            PostJournalRequestDTO journalReq = buildDepositJournalRequest(request, blockNumber);
            ledgerAppService.postJournal(journalReq);
        } catch (Exception e) {
            // 总账过账失败，余额和流水已提交，日志记录供后续对账修复
            log.warn("Journal posting failed, bizNo={}", bizNo, e);
        }

        return bizNo;
    }

    /**
     * 链上证据校验闸门：请求必须携带 chainId 与 chainTxHash，
     * requiredConfirmations 非空时必须为正整数，否则拒绝入账。
     *
     * @param request 充值入账请求
     * @throws BizException 链上证据缺失或非法时抛 DEPOSIT_CHAIN_INFO_MISS
     */
    private void assertChainEvidence(DepositRequestDTO request) {
        // chainId 必填：缺失或空白说明上游未携带链上证据，拒绝入账
        if (request.getChainId() == null || request.getChainId().isBlank()) {
            throw BizException.get(BizErrorEnum.DEPOSIT_CHAIN_INFO_MISS);
        }
        // chainTxHash 必填：缺失或空白说明无法定位链上交易，拒绝入账
        if (request.getChainTxHash() == null || request.getChainTxHash().isBlank()) {
            throw BizException.get(BizErrorEnum.DEPOSIT_CHAIN_INFO_MISS);
        }
        // requiredConfirmations 可选：非空时必须为正整数，0/负数视为非法覆盖值
        if (request.getRequiredConfirmations() != null && request.getRequiredConfirmations() <= 0) {
            throw BizException.get(BizErrorEnum.DEPOSIT_CHAIN_INFO_MISS);
        }
    }

    /**
     * 链上确认校验闸门：确认数取请求覆盖值，缺省用配置默认值；
     * 交易未达确认数抛 DEPOSIT_TX_NOT_CONFIRMED，链查询失败（RPC 全挂）
     * fail-safe 转 DEPOSIT_CHAIN_QUERY_FAILED 拒绝入账。
     *
     * @param request 充值入账请求
     * @throws BizException 未确认或链查询失败时抛对应业务异常
     */
    private void assertChainConfirmed(DepositRequestDTO request) {
        // 解析所需确认数：请求级覆盖值优先，否则用配置默认值（web3j.required-confirmations）
        int confirmations = request.getRequiredConfirmations() != null
                ? request.getRequiredConfirmations()
                : defaultConfirmations;
        try {
            // 链上确认校验：未达确认数或链上查无交易时拒绝入账（只读 RPC，无事务）
            if (!chainQueryPort.isConfirmed(request.getChainId(), request.getChainTxHash(), confirmations)) {
                throw BizException.get(BizErrorEnum.DEPOSIT_TX_NOT_CONFIRMED);
            }
        } catch (Web3jRpcException e) {
            // 链上查询失败（所有 RPC 节点不可用），fail-safe 拒绝入账，链状态未知不放行资金
            throw BizException.get(BizErrorEnum.DEPOSIT_CHAIN_QUERY_FAILED);
        }
    }

    /**
     * 确认通过后查询链上回执，取交易所在区块高度供凭证填充。
     * <p>
     * isConfirmed 内部已查过一次回执但端口不返回回执对象，为保持
     * ChainQueryPort 契约零改动，此处最多再发一次只读 RPC；回执缺失时
     * 返回 null 仍允许入账（保守兜底：确认已通过，凭证区块高度可空，对账可后补）。
     *
     * @param request 充值入账请求
     * @return 交易所在区块高度，回执缺失或区块高度为空时为 null
     * @throws BizException 回执查询失败（确认后二次 RPC 失败）时抛 DEPOSIT_CHAIN_QUERY_FAILED
     */
    private Long resolveBlockNumber(DepositRequestDTO request) {
        try {
            // 回执查询取区块高度；Optional 空（链上查无回执）时返回 null 仍允许入账
            return chainQueryPort.queryTxReceipt(request.getChainId(), request.getChainTxHash())
                    .map(receipt -> receipt.getBlockNumber() == null ? null : receipt.getBlockNumber().longValue())
                    .orElse(null);
        } catch (Web3jRpcException e) {
            // 回执查询失败，与确认闸门一致的 fail-safe 策略：拒绝入账
            throw BizException.get(BizErrorEnum.DEPOSIT_CHAIN_QUERY_FAILED);
        }
    }

    private PostJournalRequestDTO buildDepositJournalRequest(DepositRequestDTO req, Long blockNumber) {
        PostJournalRequestDTO dto = new PostJournalRequestDTO();
        dto.setBizNo(req.getBizNo());
        dto.setBizType(LedgerBizTypeEnum.DEPOSIT_ONCHAIN);
        dto.setCurrency(req.getCurrency());
        dto.setPostingDate(LocalDate.now());
        dto.setDescription("充值-" + req.getBizNo());
        // 链上证据填充：chainId/chainTxHash 取自请求，blockNumber 取自确认后回执，tokenAddress 取自请求可选值
        dto.setChainId(req.getChainId());
        dto.setChainTxHash(req.getChainTxHash());
        dto.setBlockNumber(blockNumber);
        dto.setTokenAddress(req.getTokenAddress());

        LedgerEntryRequestDTO debitEntry = new LedgerEntryRequestDTO();
        debitEntry.setAccountCode(LedgerAccountCodeEnum.DEPOSIT_IN_TRANSIT);
        debitEntry.setEntryType(LedgerEntryTypeEnum.DEBIT);
        debitEntry.setAmount(req.getAmount());
        debitEntry.setUid(null);
        debitEntry.setCounterparty(null);
        debitEntry.setBalanceAfter(null);
        debitEntry.setRemark(null);

        LedgerEntryRequestDTO creditEntry = new LedgerEntryRequestDTO();
        creditEntry.setAccountCode(LedgerAccountCodeEnum.USER_AVAILABLE);
        creditEntry.setEntryType(LedgerEntryTypeEnum.CREDIT);
        creditEntry.setAmount(req.getAmount());
        creditEntry.setUid(req.getUid());
        creditEntry.setCounterparty(null);
        creditEntry.setBalanceAfter(null);
        creditEntry.setRemark(null);

        dto.setEntries(List.of(debitEntry, creditEntry));
        return dto;
    }
}
