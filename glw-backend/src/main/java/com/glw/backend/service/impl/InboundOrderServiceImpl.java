package com.glw.backend.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.glw.backend.mapper.InboundOrderMapper;
import com.glw.backend.model.entity.InboundOrder;
import com.glw.backend.service.InboundOrderService;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/** 入库表基础服务实现。 */
@Service
@Profile("!mock")
public class InboundOrderServiceImpl extends ServiceImpl<InboundOrderMapper, InboundOrder>
        implements InboundOrderService {}
