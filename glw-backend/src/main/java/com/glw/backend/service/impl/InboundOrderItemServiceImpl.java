package com.glw.backend.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.glw.backend.mapper.InboundOrderItemMapper;
import com.glw.backend.model.entity.InboundOrderItem;
import com.glw.backend.service.InboundOrderItemService;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/** 入库商品表基础服务实现。 */
@Service
@Profile("!mock")
public class InboundOrderItemServiceImpl
        extends ServiceImpl<InboundOrderItemMapper, InboundOrderItem>
        implements InboundOrderItemService {}
