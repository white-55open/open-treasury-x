package io.github.open55.otx.domain.common.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class BaseEntity {
    private Long id;

    private Long version;

    private String deleteFlag;

    private Long createdBy;

    private LocalDateTime createTime;

    private LocalDateTime lastUpdateTime;

    private Long lastUpdatedBy;

    private String tenantId;
}
