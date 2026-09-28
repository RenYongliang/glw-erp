package com.glw.backend.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.glw.backend.mapper.OutboundOrderMapper;
import com.glw.backend.model.entity.OutboundOrder;
import com.glw.backend.service.OutboundOrderService;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/** 出库表基础服务实现。 */
@Service
@Profile("!mock")
public class OutboundOrderServiceImpl extends ServiceImpl<OutboundOrderMapper, OutboundOrder>
        implements OutboundOrderService {}
