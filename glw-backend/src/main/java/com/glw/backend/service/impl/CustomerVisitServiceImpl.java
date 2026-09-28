package com.glw.backend.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.glw.backend.mapper.CustomerVisitMapper;
import com.glw.backend.model.entity.CustomerVisit;
import com.glw.backend.service.CustomerVisitService;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/** 客户拜访记录表基础服务实现。 */
@Service
@Profile("!mock")
public class CustomerVisitServiceImpl extends ServiceImpl<CustomerVisitMapper, CustomerVisit>
        implements CustomerVisitService {}
