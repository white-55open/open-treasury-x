package io.github.open55.otx.domain.fundflow.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum FundFlowTypeEnum {
    DEPOSIT("DEPOSIT"),
    WITHDRAW("WITHDRAW"),
    FREEZE("FREEZE"),
    UNFREEZE("UNFREEZE");
    private final String code;
}
