package com.glw.backend.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.glw.backend.mapper.SalesOrderBatchItemMapper;
import com.glw.backend.model.entity.SalesOrderBatchItem;
import com.glw.backend.service.SalesOrderBatchItemService;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/** 销售商品批次表基础服务实现。 */
@Service
@Profile("!mock")
public class SalesOrderBatchItemServiceImpl
        extends ServiceImpl<SalesOrderBatchItemMapper, SalesOrderBatchItem>
        implements SalesOrderBatchItemService {}
