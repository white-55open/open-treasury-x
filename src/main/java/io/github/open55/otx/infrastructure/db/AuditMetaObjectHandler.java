package io.github.open55.otx.infrastructure.db;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import io.github.open55.otx.common.entity.BaseEntity;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class AuditMetaObjectHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        this.strictInsertFill(metaObject, BaseEntity.FIELD_CREATE_TIME, LocalDateTime.class, LocalDateTime.now());
        this.strictInsertFill(metaObject, BaseEntity.FIELD_LAST_UPDATE_TIME, LocalDateTime.class, LocalDateTime.now());

        Long currentUserId = getCurrentUserId();
        this.strictInsertFill(metaObject, BaseEntity.FIELD_CREATED_BY, Long.class, currentUserId);
        this.strictInsertFill(metaObject, BaseEntity.FIELD_LAST_UPDATED_BY, Long.class, currentUserId);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        this.strictUpdateFill(metaObject, BaseEntity.FIELD_LAST_UPDATE_TIME, LocalDateTime.class, LocalDateTime.now());
        this.strictUpdateFill(metaObject, BaseEntity.FIELD_LAST_UPDATED_BY, Long.class, getCurrentUserId());
    }

    private Long getCurrentUserId() {
        return 1L;
    }
}
