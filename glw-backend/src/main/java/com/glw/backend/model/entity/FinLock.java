package com.glw.backend.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.IdType;
import lombok.Data;

/** fin_lock persistence record. */
@Data
@TableName(value = "fin_lock", resultMap = "com.glw.backend.mapper.FinLockMapper.BaseResultMap")
public class FinLock {
    @TableId(value = "id", type = IdType.INPUT)
    private Integer id;
}
