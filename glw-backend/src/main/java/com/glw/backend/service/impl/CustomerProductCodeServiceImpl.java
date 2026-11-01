package com.glw.backend.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.glw.backend.mapper.CustomerProductCodeMapper;
import com.glw.backend.model.entity.CustomerProductCode;
import com.glw.backend.service.CustomerProductCodeService;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/** 客户物料编码对照表基础服务实现。 */
@Service
@Profile("!mock")
public class CustomerProductCodeServiceImpl
        extends ServiceImpl<CustomerProductCodeMapper, CustomerProductCode>
        implements CustomerProductCodeService {}
