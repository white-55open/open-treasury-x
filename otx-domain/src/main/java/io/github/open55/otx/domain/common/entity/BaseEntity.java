package io.github.open55.otx.domain.common.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 领域实体基类，所有实体和持久化值对象必须继承此类。
 * <p>
 * 统一管理主键、乐观锁版本号、逻辑删除标记、审计字段和租户标识。
 */
@Data
public class BaseEntity {

    /**
     * 主键，由雪花算法生成
     */
    private Long id;

    /**
     * 乐观锁版本号，每次更新时自增
     */
    private Long version;

    /**
     * 逻辑删除标记（0-正常，1-已删除）
     */
    private String deleteFlag;

    /**
     * 创建人标识
     */
    private Long createdBy;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 最后更新时间
     */
    private LocalDateTime lastUpdateTime;

    /**
     * 最后更新人标识
     */
    private Long lastUpdatedBy;

    /**
     * 租户标识，用于多租户隔离
     */
    private String tenantId;
}
