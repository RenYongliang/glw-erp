package com.glw.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.glw.backend.model.entity.SysUser;

// 由真实模式下的 @MapperScan 注册；mock 模式不连接数据库。
public interface UserMapper extends BaseMapper<SysUser> {}
