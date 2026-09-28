package com.glw.backend.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.glw.backend.mapper.OutboundOrderItemMapper;
import com.glw.backend.model.entity.OutboundOrderItem;
import com.glw.backend.service.OutboundOrderItemService;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/** 出库商品表基础服务实现。 */
@Service
@Profile("!mock")
public class OutboundOrderItemServiceImpl
        extends ServiceImpl<OutboundOrderItemMapper, OutboundOrderItem>
        implements OutboundOrderItemService {}
