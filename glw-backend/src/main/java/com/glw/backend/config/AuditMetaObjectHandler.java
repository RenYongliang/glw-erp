package com.glw.backend.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.glw.backend.auth.UserContext;

import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

/**
 * 自动填充审计人字段。
 *
 * <p>insert 填 created_by/updated_by，update 填 updated_by；created_at/updated_at 由数据库默认值和
 * ON UPDATE 维护。无登录用户的系统任务保持为空，不覆盖已有值。
 */
@Component
public class AuditMetaObjectHandler implements MetaObjectHandler {
    @Override
    public void insertFill(MetaObject metaObject) {
        Long userId = UserContext.userId();
        if (userId == null) return;
        fillIfNull(metaObject, "createdBy", userId);
        fillIfNull(metaObject, "updatedBy", userId);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        Long userId = UserContext.userId();
        if (userId == null) return;
        if (metaObject.hasSetter("updatedBy")) {
            setFieldValByName("updatedBy", userId, metaObject);
        }
    }

    private void fillIfNull(MetaObject metaObject, String field, Long value) {
        if (metaObject.hasSetter(field) && getFieldValByName(field, metaObject) == null) {
            setFieldValByName(field, value, metaObject);
        }
    }
}
