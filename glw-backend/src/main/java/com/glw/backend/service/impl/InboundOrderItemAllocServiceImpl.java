package com.glw.backend.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.glw.backend.mapper.InboundOrderItemAllocMapper;
import com.glw.backend.model.entity.InboundOrderItemAlloc;
import com.glw.backend.service.InboundOrderItemAllocService;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/** 入库产品行-采购行分配基础服务实现。 */
@Service
@Profile("!mock")
public class InboundOrderItemAllocServiceImpl
        extends ServiceImpl<InboundOrderItemAllocMapper, InboundOrderItemAlloc>
        implements InboundOrderItemAllocService {}
