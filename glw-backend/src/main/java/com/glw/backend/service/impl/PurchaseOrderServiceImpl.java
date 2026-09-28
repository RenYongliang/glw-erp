package com.glw.backend.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.glw.backend.mapper.PurchaseOrderMapper;
import com.glw.backend.model.entity.PurchaseOrder;
import com.glw.backend.service.PurchaseOrderService;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/** 采购订单表基础服务实现。 */
@Service
@Profile("!mock")
public class PurchaseOrderServiceImpl extends ServiceImpl<PurchaseOrderMapper, PurchaseOrder>
        implements PurchaseOrderService {}
