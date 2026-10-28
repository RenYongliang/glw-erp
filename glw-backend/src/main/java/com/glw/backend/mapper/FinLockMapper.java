package com.glw.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.glw.backend.model.entity.FinLock;


public interface FinLockMapper extends BaseMapper<FinLock> {
    int lock();
}
