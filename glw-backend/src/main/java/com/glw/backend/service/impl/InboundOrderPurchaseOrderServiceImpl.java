package com.glw.backend.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.glw.backend.mapper.InboundOrderPurchaseOrderMapper;
import com.glw.backend.model.entity.InboundOrderPurchaseOrder;
import com.glw.backend.service.InboundOrderPurchaseOrderService;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("!mock")
public class InboundOrderPurchaseOrderServiceImpl
        extends ServiceImpl<InboundOrderPurchaseOrderMapper, InboundOrderPurchaseOrder>
        implements InboundOrderPurchaseOrderService {}
