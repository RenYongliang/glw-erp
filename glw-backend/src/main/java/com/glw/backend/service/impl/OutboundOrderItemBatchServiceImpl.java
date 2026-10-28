package com.glw.backend.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.glw.backend.mapper.OutboundOrderItemBatchMapper;
import com.glw.backend.model.entity.OutboundOrderItemBatch;
import com.glw.backend.service.OutboundOrderItemBatchService;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/** 出库分配行批次基础服务实现。 */
@Service
@Profile("!mock")
public class OutboundOrderItemBatchServiceImpl
        extends ServiceImpl<OutboundOrderItemBatchMapper, OutboundOrderItemBatch>
        implements OutboundOrderItemBatchService {}
