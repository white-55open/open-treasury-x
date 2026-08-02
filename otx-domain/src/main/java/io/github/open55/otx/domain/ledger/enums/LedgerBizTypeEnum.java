package io.github.open55.otx.domain.ledger.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum LedgerBizTypeEnum {
    DEPOSIT_ONCHAIN("DEPOSIT_ONCHAIN"),
    WITHDRAW_ONCHAIN("WITHDRAW_ONCHAIN"),
    INTERNAL_TRANSFER("INTERNAL_TRANSFER"),
    FEE("FEE"),
    REVERSAL("REVERSAL"),
    ADJUSTMENT("ADJUSTMENT"),
    FREEZE("FREEZE"),
    UNFREEZE("UNFREEZE");

    private final String code;
}
