package io.github.open55.otx.domain.ledger;

import io.github.open55.otx.domain.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@EqualsAndHashCode(callSuper = true)
@Data
public class LedgerEntryEntity extends BaseEntity {

    private String bizNo;

    private Long uid;

    private String accountCode;

    private String entryType;

    private BigDecimal amount;
}