package com.glw.backend.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.glw.backend.model.dto.PurchaseOrderCreateDTO;
import com.glw.backend.model.dto.PurchaseOrderPageDTO;
import com.glw.backend.model.vo.PurchaseOrderVO;

import com.baomidou.mybatisplus.spring.service.IService;
import com.glw.backend.model.entity.PurchaseOrder;

/** 采购订单表基础服务。业务状态流转及关联表事务在此基础上扩展。 */
public interface PurchaseOrderService extends IService<PurchaseOrder> {
    IPage<PurchaseOrderVO> pagePurchaseOrder(PurchaseOrderPageDTO pageDTO);

    /** 新增采购订单，创建后直接进入待收货状态。 */
    PurchaseOrderVO createPurchaseOrder(PurchaseOrderCreateDTO createDTO);

    /** 修改尚未入库的采购订单，已入库订单不允许修改。 */
    PurchaseOrderVO updatePurchaseOrder(Long id, PurchaseOrderCreateDTO createDTO);
}
