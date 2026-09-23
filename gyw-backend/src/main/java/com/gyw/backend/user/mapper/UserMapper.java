package com.gyw.backend.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gyw.backend.user.entity.SysUser;

// 由真实模式下的 @MapperScan 注册；mock 模式不连接数据库。
public interface UserMapper extends BaseMapper<SysUser> {}
