package io.github.open55.otx.po;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class BaseDO {
    public static final String FIELD_CREATE_TIME = "createTime";
    public static final String FIELD_LAST_UPDATE_TIME = "lastUpdateTime";
    public static final String FIELD_CREATED_BY = "createdBy";
    public static final String FIELD_LAST_UPDATED_BY = "lastUpdatedBy";

    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @Version
    private Long version;

    @TableLogic
    private String deleteFlag;


    @TableField(fill = FieldFill.INSERT)
    private Long createdBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime lastUpdateTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long lastUpdatedBy;

    private String tenantId;
}
