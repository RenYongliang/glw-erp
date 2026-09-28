package com.glw.backend.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.glw.backend.mapper.PurchaseOrderItemMapper;
import com.glw.backend.model.entity.PurchaseOrderItem;
import com.glw.backend.service.PurchaseOrderItemService;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/** 采购订单商品表基础服务实现。 */
@Service
@Profile("!mock")
public class PurchaseOrderItemServiceImpl
        extends ServiceImpl<PurchaseOrderItemMapper, PurchaseOrderItem>
        implements PurchaseOrderItemService {}
