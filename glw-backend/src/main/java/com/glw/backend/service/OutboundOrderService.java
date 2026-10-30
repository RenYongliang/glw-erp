package com.glw.backend.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.glw.backend.model.entity.OutboundOrder;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.glw.backend.model.dto.OutboundOrderCreateDTO;
import com.glw.backend.model.dto.OutboundOrderPageDTO;
import com.glw.backend.model.dto.OutboundOrderSignDTO;
import com.glw.backend.model.vo.OutboundOrderVO;

import java.util.List;

/** 出库表基础服务。业务状态流转及关联表事务在此基础上扩展。 */
public interface OutboundOrderService extends IService<OutboundOrder> {
    IPage<OutboundOrderVO> pageOutboundOrder(OutboundOrderPageDTO pageDTO);

    /** 创建并完成出库单：扣减库存、回写销售订单发货状态。 */
    OutboundOrderVO createOutboundOrder(OutboundOrderCreateDTO createDTO);

    /** 登记客户签收日期，并同步应收记录的账单月归属。 */
    List<OutboundOrderVO> signOutboundOrders(OutboundOrderSignDTO signDTO);
}
