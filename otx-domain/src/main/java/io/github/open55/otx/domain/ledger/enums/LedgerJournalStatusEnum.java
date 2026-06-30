package io.github.open55.otx.domain.ledger.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum LedgerJournalStatusEnum {
    DRAFT("DRAFT"),
    POSTED("POSTED"),
    REVERSED("REVERSED");

    private final String code;
}
