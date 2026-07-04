package io.github.open55.otx.domain.ledger;

import io.github.open55.otx.common.exception.BizErrorEnum;
import io.github.open55.otx.common.exception.BizException;
import io.github.open55.otx.domain.common.entity.BaseEntity;
import io.github.open55.otx.domain.ledger.enums.LedgerBizTypeEnum;
import io.github.open55.otx.domain.ledger.enums.LedgerEntryTypeEnum;
import io.github.open55.otx.domain.ledger.enums.LedgerJournalStatusEnum;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 总账凭证聚合根（Aggregate Root）。
 * <p>
 * 代表一笔业务事件的全部借贷分录，承载复式记账的核心不变量：
 * 借贷必平、同户同向唯一、状态机合法性（DRAFT → POSTED → REVERSED）。
 * 作为聚合的唯一入口，仓储只操作该聚合根，Entry 值对象通过聚合根写入。
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class LedgerJournalEntity extends BaseEntity {

    /**
     * 业务流水号，用作幂等键，全局唯一
     */
    private String bizNo;

    /**
     * 业务类型，标识该凭证的业务来源（充值、提现、内部转账等）
     */
    private LedgerBizTypeEnum bizType;

    /**
     * 记账日期，业务实际发生日期（非系统日期）
     */
    private LocalDate postingDate;

    /**
     * 币种，如 USDT、ETH
     */
    private String currency;

    /**
     * 凭证状态：DRAFT（草稿）→ POSTED（已过账）→ REVERSED（已冲销）
     */
    private LedgerJournalStatusEnum status = LedgerJournalStatusEnum.DRAFT;

    /**
     * 凭证总金额，所有分录金额之和（借方金额之和 = 贷方金额之和）
     */
    private BigDecimal totalAmount;

    /**
     * 业务描述，说明该凭证的业务摘要
     */
    private String description;

    /**
     * 区块链 ID，如 "1"（以太坊主网），链下业务为空
     */
    private String chainId;

    /**
     * 链上交易哈希，链下业务为空
     */
    private String chainTxHash;

    /**
     * 区块高度，链下业务为空
     */
    private Long blockNumber;

    /**
     * 代币合约地址，链下业务为空
     */
    private String tokenAddress;

    /**
     * 链上确认数，链下业务为空
     */
    private Integer confirmations;

    /**
     * 被冲销时记录的反向凭证业务号，仅 REVERSED 状态时有值
     */
    private Long reversedBy;

    /**
     * 借贷分录列表，外部通过 getEntries() 获取不可修改视图
     */
    private List<LedgerEntryEntity> entries = new ArrayList<>();

    /**
     * 返回不可修改的分录列表视图。
     *
     * @return 不可修改的分录列表
     */
    public List<LedgerEntryEntity> getEntries() {
        return Collections.unmodifiableList(entries);
    }

    private LedgerJournalEntity(String bizNo, LedgerBizTypeEnum bizType, String currency,
                                LocalDate postingDate, String description,
                                List<LedgerEntryEntity> entries,
                                String chainId, String chainTxHash,
                                Long blockNumber, String tokenAddress) {
        this.bizNo = bizNo;
        this.bizType = bizType;
        this.currency = currency;
        this.postingDate = postingDate;
        this.description = description;
        this.chainId = chainId;
        this.chainTxHash = chainTxHash;
        this.blockNumber = blockNumber;
        this.tokenAddress = tokenAddress;
        this.entries = new ArrayList<>(entries);
        this.totalAmount = calculateTotalAmount(entries);
    }

    /**
     * 工厂方法，创建一张新的凭证。
     * <p>
     * 执行入参校验后才构造实例，确保聚合根始终有效。
     *
     * @param bizNo        业务流水号，不可为空
     * @param bizType      业务类型
     * @param currency     币种，不可为空
     * @param postingDate  记账日期
     * @param description  业务描述（可选）
     * @param entries      借贷分录列表，不可为空
     * @param chainId      链 ID（可选）
     * @param chainTxHash  链上交易哈希（可选）
     * @param blockNumber  区块高度（可选）
     * @param tokenAddress 代币地址（可选）
     * @return 新创建的凭证实例
     */
    public static LedgerJournalEntity create(String bizNo, LedgerBizTypeEnum bizType,
                                             String currency, LocalDate postingDate,
                                             String description, List<LedgerEntryEntity> entries,
                                             String chainId, String chainTxHash,
                                             Long blockNumber, String tokenAddress) {
        if (bizNo == null || bizNo.isBlank()) {
            throw BizException.get(BizErrorEnum.LEDGER_BIZ_NO_EMPTY);
        }
        if (currency == null || currency.isBlank()) {
            throw BizException.get(BizErrorEnum.LEDGER_CURRENCY_EMPTY);
        }
        if (entries == null || entries.isEmpty()) {
            throw BizException.get(BizErrorEnum.LEDGER_ENTRIES_EMPTY);
        }
        return new LedgerJournalEntity(bizNo, bizType, currency, postingDate, description,
                entries, chainId, chainTxHash, blockNumber, tokenAddress);
    }

    /**
     * 校验借贷平衡不变量。
     * <p>
     * 检查 entries 非空、至少各有一条 DEBIT 和 CREDIT 分录、DEBIT 总额等于 CREDIT 总额。
     * 任一条不满足即抛异常，确保过账前凭证的借贷平衡。
     */
    public void assertBalanced() {
        if (entries == null || entries.isEmpty()) {
            throw BizException.get(BizErrorEnum.LEDGER_ENTRIES_EMPTY);
        }
        BigDecimal debitSum = BigDecimal.ZERO;
        BigDecimal creditSum = BigDecimal.ZERO;
        boolean hasDebit = false;
        boolean hasCredit = false;
        for (LedgerEntryEntity entry : entries) {
            if (entry.getEntryType() == LedgerEntryTypeEnum.DEBIT) {
                hasDebit = true;
                debitSum = debitSum.add(entry.getAmount());
            } else if (entry.getEntryType() == LedgerEntryTypeEnum.CREDIT) {
                hasCredit = true;
                creditSum = creditSum.add(entry.getAmount());
            }
        }
        if (!hasDebit) {
            throw BizException.get(BizErrorEnum.LEDGER_ENTRIES_EMPTY);
        }
        if (!hasCredit) {
            throw BizException.get(BizErrorEnum.LEDGER_ENTRIES_EMPTY);
        }
        if (debitSum.compareTo(creditSum) != 0) {
            throw BizException.get(BizErrorEnum.LEDGER_NOT_BALANCED);
        }
    }

    /**
     * 校验同账户同方向不重复约束。
     * <p>
     * 在一张凭证内，同一个 accountCode 在同一 entryType 下最多出现一次。
     * 违反则抛 LEDGER_DUPLICATE_ACCOUNT，防止同一账户被重复记账。
     */
    public void assertNoDuplicateAccount() {
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (LedgerEntryEntity entry : entries) {
            String key = entry.getAccountCode().name() + "@" + entry.getEntryType().name();
            if (!seen.add(key)) {
                throw BizException.get(BizErrorEnum.LEDGER_DUPLICATE_ACCOUNT);
            }
        }
    }

    /**
     * 冲销：将已过账的凭证冲销，生成反向凭证。
     * <p>
     * 仅 POSTED 状态可冲销。冲销后原凭证状态置为 REVERSED，并生成一张新的反向凭证
     * （所有分录的借贷方向互换，bizNo 以 "RV-" 前缀标记），通过 reversedBy 关联。
     *
     * @param reason 作为冲销原因的反向凭证，调用前需已持久化
     * @throws BizException 非 POSTED 状态时抛 LEDGER_JOURNAL_NOT_POSTED
     */
    public void reverse(LedgerJournalEntity reason) {
        if (status != LedgerJournalStatusEnum.POSTED) {
            throw BizException.get(BizErrorEnum.LEDGER_JOURNAL_NOT_POSTED);
        }
        this.status = LedgerJournalStatusEnum.REVERSED;
        this.reversedBy = reason.getId();
    }

    /**
     * 基于当前凭证生成反向分录列表（借贷方向互换）。
     * <p>
     * 反向分录与原分录金额相同，仅 entryType 取反（DEBIT ↔ CREDIT），
     * bizNo 以 "RV-" 前缀标记区分。
     *
     * @param reversedBizNo 反向凭证的 bizNo
     * @return 反向分录列表
     */
    public List<LedgerEntryEntity> createReversedEntries(String reversedBizNo) {
        List<LedgerEntryEntity> reversed = new ArrayList<>(entries.size());
        for (LedgerEntryEntity entry : entries) {
            LedgerEntryTypeEnum reversedType = entry.getEntryType() == LedgerEntryTypeEnum.DEBIT
                    ? LedgerEntryTypeEnum.CREDIT
                    : LedgerEntryTypeEnum.DEBIT;
            reversed.add(new LedgerEntryEntity(
                    entry.getAccountCode(), reversedType, entry.getAmount(),
                    entry.getUid(), entry.getCounterparty(),
                    entry.getBalanceAfter(), entry.getRemark()));
        }
        return reversed;
    }

    /**
     * 过账：将凭证状态从 DRAFT 转换为 POSTED。
     * <p>
     * 过账前自动执行借贷平衡校验和同户同向唯一校验。
     * 仅 DRAFT 状态可过账，已 POSTED 的凭证调用此方法抛 LEDGER_JOURNAL_NOT_DRAFT。
     */
    public void post() {
        if (status != LedgerJournalStatusEnum.DRAFT) {
            throw BizException.get(BizErrorEnum.LEDGER_JOURNAL_NOT_DRAFT);
        }
        assertBalanced();
        assertNoDuplicateAccount();
        this.status = LedgerJournalStatusEnum.POSTED;
    }

    /**
     * 计算所有分录金额之和，作为凭证总金额。
     *
     * @param entries 分录列表
     * @return 总金额
     */
    private static BigDecimal calculateTotalAmount(List<LedgerEntryEntity> entries) {
        return entries.stream()
                .map(LedgerEntryEntity::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
