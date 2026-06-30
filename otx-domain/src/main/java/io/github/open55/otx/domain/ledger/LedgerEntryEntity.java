package io.github.open55.otx.domain.ledger;

import io.github.open55.otx.common.exception.BizErrorEnum;
import io.github.open55.otx.common.exception.BizException;
import io.github.open55.otx.domain.common.entity.BaseEntity;
import io.github.open55.otx.domain.ledger.enums.LedgerAccountCodeEnum;
import io.github.open55.otx.domain.ledger.enums.LedgerEntryTypeEnum;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 总账分录实体（不可变值对象）。
 * <p>
 * 表示复式记账中的单条借贷分录，包含会计科目、借贷方向、金额等核心业务字段。
 * 作为 LedgerJournal 聚合根的组成部分，构造时执行自校验确保业务不变量。
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class LedgerEntryEntity extends BaseEntity {

    /**
     * 会计科目编码，标识分录所属的系统账户
     * <p>可选值：USER_AVAILABLE、USER_FROZEN、PLATFORM_HOT、PLATFORM_COLD 等</p>
     */
    private final LedgerAccountCodeEnum accountCode;

    /**
     * 分录类型
     * <p>DEBIT（借方）：资产/费用增加或负债/权益减少；CREDIT（贷方）：资产/费用减少或负债/权益增加</p>
     */
    private final LedgerEntryTypeEnum entryType;

    /**
     * 分录金额，必须为正数
     * <p>复式记账不允许零或负金额，单位为该币种最小精度</p>
     */
    private final BigDecimal amount;

    /**
     * 用户唯一标识，作为账户的业务键
     * <p>平台类账户（如 PLATFORM_HOT）此字段可为空</p>
     */
    private final Long uid;

    /**
     * 交易对手地址或标识
     * <p>链上交易时为钱包地址，内部转账时可为空</p>
     */
    private final String counterparty;

    /**
     * 记账后该账户余额
     * <p>该分录执行完毕后对应账户的即时余额</p>
     */
    private final BigDecimal balanceAfter;

    /**
     * 备注说明
     * <p>用于记录该分录的业务摘要或附加信息</p>
     */
    private final String remark;

    /**
     * 构造 LedgerEntryEntity，执行自校验确保业务不变量。
     *
     * @param accountCode  会计科目编码，不能为空
     * @param entryType    分录类型，不能为空
     * @param amount       分录金额，必须为正数
     * @param uid          用户唯一标识（可选）
     * @param counterparty 交易对手（可选）
     * @param balanceAfter 记账后余额（可选）
     * @param remark       备注（可选）
     */
    public LedgerEntryEntity(
            LedgerAccountCodeEnum accountCode,
            LedgerEntryTypeEnum entryType,
            BigDecimal amount,
            Long uid,
            String counterparty,
            BigDecimal balanceAfter,
            String remark) {
        // 校验会计科目编码：枚举类型本身保证合法值，仅需非空检查
        if (accountCode == null) {
            throw BizException.get(BizErrorEnum.LEDGER_ACCOUNT_CODE_INVALID);
        }
        // 校验分录类型：不允许为空
        if (entryType == null) {
            throw BizException.get(BizErrorEnum.LEDGER_ENTRY_TYPE_INVALID);
        }
        // 校验金额：必须存在且为正数（复式记账不允许零或负金额）
        if (amount == null || amount.signum() <= 0) {
            throw BizException.get(BizErrorEnum.LEDGER_AMOUNT_INVALID);
        }
        this.accountCode = accountCode;
        this.entryType = entryType;
        this.amount = amount;
        this.uid = uid;
        this.counterparty = counterparty;
        this.balanceAfter = balanceAfter;
        this.remark = remark;
    }
}
