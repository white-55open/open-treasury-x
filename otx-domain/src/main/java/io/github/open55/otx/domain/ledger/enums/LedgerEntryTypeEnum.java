package io.github.open55.otx.domain.ledger.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum LedgerEntryTypeEnum {
    DEBIT("DEBIT"),
    CREDIT("CREDIT");

    private final String code;
}
