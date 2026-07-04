package io.github.open55.otx.domain.fundflow.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 资金流水类型枚举。
 */
@Getter
@RequiredArgsConstructor
public enum FundFlowTypeEnum {
    /** 充值 */
    DEPOSIT("DEPOSIT"),
    /** 提现 */
    WITHDRAW("WITHDRAW"),
    /** 冻结 */
    FREEZE("FREEZE"),
    /** 解冻 */
    UNFREEZE("UNFREEZE");
    private final String code;
}
