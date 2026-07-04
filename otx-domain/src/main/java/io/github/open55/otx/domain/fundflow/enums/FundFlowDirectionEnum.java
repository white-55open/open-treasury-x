package io.github.open55.otx.domain.fundflow.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 资金变动方向枚举。
 */
@Getter
@RequiredArgsConstructor
public enum FundFlowDirectionEnum {
    /** 收入（可用余额增加） */
    IN("IN"),
    /** 支出（可用余额减少） */
    OUT("OUT");
    private final String code;
}
