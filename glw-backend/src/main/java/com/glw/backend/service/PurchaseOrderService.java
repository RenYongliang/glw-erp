package com.glw.backend.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.glw.backend.model.dto.PurchaseOrderPageDTO;
import com.glw.backend.model.vo.PurchaseOrderVO;

import com.baomidou.mybatisplus.spring.service.IService;
import com.glw.backend.model.entity.PurchaseOrder;

/** 采购订单表基础服务。业务状态流转及关联表事务在此基础上扩展。 */
public interface PurchaseOrderService extends IService<PurchaseOrder> {
    IPage<PurchaseOrderVO> pagePurchaseOrder(PurchaseOrderPageDTO pageDTO);
}
