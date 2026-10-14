package com.glw.backend.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.glw.backend.model.dto.SalesOrderPageDTO;
import com.glw.backend.model.vo.SalesOrderVO;

/** 销售订单表基础服务。业务状态流转及关联表事务在此基础上扩展。 */
public interface SalesOrderService {

    IPage<SalesOrderVO> pageSalesOrder(SalesOrderPageDTO pageDTO);
}
