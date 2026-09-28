package com.glw.backend.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.glw.backend.mapper.SalesOrderMapper;
import com.glw.backend.model.entity.SalesOrder;
import com.glw.backend.service.SalesOrderService;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/** 销售订单表基础服务实现。 */
@Service
@Profile("!mock")
public class SalesOrderServiceImpl extends ServiceImpl<SalesOrderMapper, SalesOrder>
        implements SalesOrderService {}
