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

    /** 取消客户签收；已进入对账单或已核销的应收不允许取消。 */
    void unsignOutboundOrder(Long id);

    /** 撤销出库单：回补库存、作废未生效应收并回滚销售订单发货状态。 */
    void reverseOutboundOrder(Long id);

    /** 修改已出库单的收货与物流快照，不影响库存和应收。 */
    void updateShipping(Long id, com.glw.backend.model.dto.OutboundShippingDTO shipping);
}
