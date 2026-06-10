package io.github.open55.otx.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum FundFlowDirectionEnum {
    IN("IN"),
    OUT("OUT");
    private final String code;
}
