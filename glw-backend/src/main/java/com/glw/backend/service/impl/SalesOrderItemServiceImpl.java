package com.glw.backend.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.glw.backend.mapper.SalesOrderItemMapper;
import com.glw.backend.model.entity.SalesOrderItem;
import com.glw.backend.service.SalesOrderItemService;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/** 销售订单商品表基础服务实现。 */
@Service
@Profile("!mock")
public class SalesOrderItemServiceImpl extends ServiceImpl<SalesOrderItemMapper, SalesOrderItem>
        implements SalesOrderItemService {}
