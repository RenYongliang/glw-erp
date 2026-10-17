package com.glw.backend.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.glw.backend.model.entity.OutboundOrder;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.glw.backend.model.dto.OutboundOrderPageDTO;
import com.glw.backend.model.vo.OutboundOrderVO;

/** 出库表基础服务。业务状态流转及关联表事务在此基础上扩展。 */
public interface OutboundOrderService extends IService<OutboundOrder> {
    IPage<OutboundOrderVO> pageOutboundOrder(OutboundOrderPageDTO pageDTO);
}
