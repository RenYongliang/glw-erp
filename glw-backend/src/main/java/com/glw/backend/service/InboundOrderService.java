package com.glw.backend.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.glw.backend.model.dto.InboundOrderCreateDTO;
import com.glw.backend.model.dto.InboundOrderPageDTO;
import com.glw.backend.model.vo.InboundOrderVO;

import com.baomidou.mybatisplus.spring.service.IService;
import com.glw.backend.model.entity.InboundOrder;

/** 入库表基础服务。业务状态流转及关联表事务在此基础上扩展。 */
public interface InboundOrderService extends IService<InboundOrder> {
    IPage<InboundOrderVO> pageInboundOrder(InboundOrderPageDTO pageDTO);

    InboundOrderVO createInboundOrder(InboundOrderCreateDTO createDTO);

    InboundOrderVO detail(Long id, Long purchaseOrderId);

    /** 撤销已入库单：回补（删除）库存批次、作废未生效应付并回滚采购订单收货状态。 */
    void reverseInboundOrder(Long id);
}
