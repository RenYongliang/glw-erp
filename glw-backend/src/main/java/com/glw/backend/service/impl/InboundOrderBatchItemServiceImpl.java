package com.glw.backend.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.glw.backend.mapper.InboundOrderBatchItemMapper;
import com.glw.backend.model.entity.InboundOrderBatchItem;
import com.glw.backend.service.InboundOrderBatchItemService;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/** 入库商品批次表基础服务实现。 */
@Service
@Profile("!mock")
public class InboundOrderBatchItemServiceImpl
        extends ServiceImpl<InboundOrderBatchItemMapper, InboundOrderBatchItem>
        implements InboundOrderBatchItemService {}
