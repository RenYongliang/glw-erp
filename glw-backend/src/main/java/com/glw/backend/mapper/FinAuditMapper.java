package com.glw.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.glw.backend.model.entity.FinAudit;

import org.apache.ibatis.annotations.Param;

public interface FinAuditMapper extends BaseMapper<FinAudit> {
    void audit(@Param("type") String type, @Param("id") Long id, @Param("action") String action, @Param("actor") Long actor, @Param("detail") String detail);
}
