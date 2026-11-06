package com.glw.backend.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.glw.backend.model.dto.SalesOrderCreateDTO;
import com.glw.backend.model.dto.SalesOrderPageDTO;
import com.glw.backend.model.vo.SalesOrderVO;

/** 销售订单表基础服务。业务状态流转及关联表事务在此基础上扩展。 */
public interface SalesOrderService {

    IPage<SalesOrderVO> pageSalesOrder(SalesOrderPageDTO pageDTO);

    /** 新增销售订单，直接进入执行中。 */
    SalesOrderVO createSalesOrder(SalesOrderCreateDTO createDTO);

    /** 修改销售订单基础数据；已有出库记录时拒绝修改。 */
    SalesOrderVO updateSalesOrder(Long id, SalesOrderCreateDTO createDTO);

    /** 根据发货状态与应收核销结果重算订单生命周期状态。 */
    void refreshCompletionStatus(Long orderId);

    /** 取消尚未出库的销售订单；有出库记录时拒绝。 */
    void cancelOrder(Long orderId);
}
