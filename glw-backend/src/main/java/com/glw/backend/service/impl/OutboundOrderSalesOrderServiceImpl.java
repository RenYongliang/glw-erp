package com.glw.backend.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.glw.backend.mapper.OutboundOrderSalesOrderMapper;
import com.glw.backend.model.entity.OutboundOrderSalesOrder;
import com.glw.backend.service.OutboundOrderSalesOrderService;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/** 出库单与销售订单关联基础服务实现。 */
@Service
@Profile("!mock")
public class OutboundOrderSalesOrderServiceImpl
        extends ServiceImpl<OutboundOrderSalesOrderMapper, OutboundOrderSalesOrder>
        implements OutboundOrderSalesOrderService {}
