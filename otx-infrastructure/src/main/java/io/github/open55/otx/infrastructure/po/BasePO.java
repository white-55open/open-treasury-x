package io.github.open55.otx.infrastructure.po;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 持久化对象基类，所有 PO 类必须继承此类。
 * <p>
 * 统一管理主键（雪花算法自增）、乐观锁版本号、逻辑删除标记、
 * 审计字段（创建人/时间、更新人/时间）和租户标识。
 * MyBatis-Plus 注解驱动自动填充和乐观锁拦截。
 */
@Data
public class BasePO {
    public static final String FIELD_CREATE_TIME = "createTime";
    public static final String FIELD_LAST_UPDATE_TIME = "lastUpdateTime";
    public static final String FIELD_CREATED_BY = "createdBy";
    public static final String FIELD_LAST_UPDATED_BY = "lastUpdatedBy";

    /**
     * 主键，由雪花算法自动生成（ASSIGN_ID 策略）
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 乐观锁版本号，每次更新时自增
     */
    @Version
    private Long version;

    /**
     * 逻辑删除标记（0-正常，1-已删除）
     */
    @TableLogic
    private String deleteFlag;

    /**
     * 创建人标识，插入时自动填充
     */
    @TableField(fill = FieldFill.INSERT)
    private Long createdBy;

    /**
     * 创建时间，插入时自动填充
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 最后更新时间，插入和更新时自动填充
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime lastUpdateTime;

    /**
     * 最后更新人标识，插入和更新时自动填充
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long lastUpdatedBy;

    /**
     * 租户标识，用于多租户数据隔离
     */
    private String tenantId;
}
