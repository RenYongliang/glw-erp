package com.glw.backend.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.glw.backend.mapper.OutboundOrderBatchItemMapper;
import com.glw.backend.model.entity.OutboundOrderBatchItem;
import com.glw.backend.service.OutboundOrderBatchItemService;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/** 出库商品批次表基础服务实现。 */
@Service
@Profile("!mock")
public class OutboundOrderBatchItemServiceImpl
        extends ServiceImpl<OutboundOrderBatchItemMapper, OutboundOrderBatchItem>
        implements OutboundOrderBatchItemService {}
