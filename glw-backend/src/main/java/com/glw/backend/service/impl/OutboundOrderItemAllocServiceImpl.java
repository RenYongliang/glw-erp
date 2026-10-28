package com.glw.backend.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.glw.backend.mapper.OutboundOrderItemAllocMapper;
import com.glw.backend.model.entity.OutboundOrderItemAlloc;
import com.glw.backend.service.OutboundOrderItemAllocService;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/** 出库产品行-销售行分配基础服务实现。 */
@Service
@Profile("!mock")
public class OutboundOrderItemAllocServiceImpl
        extends ServiceImpl<OutboundOrderItemAllocMapper, OutboundOrderItemAlloc>
        implements OutboundOrderItemAllocService {}
