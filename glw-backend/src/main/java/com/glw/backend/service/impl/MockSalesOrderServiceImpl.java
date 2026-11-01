package com.glw.backend.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.glw.backend.common.BusinessException;
import com.glw.backend.common.ErrorCode;
import com.glw.backend.converter.SalesOrderConverter;
import com.glw.backend.model.dto.SalesOrderCreateDTO;
import com.glw.backend.model.dto.SalesOrderPageDTO;
import com.glw.backend.model.entity.SalesOrder;
import com.glw.backend.model.vo.SalesOrderVO;
import com.glw.backend.service.SalesOrderService;
import lombok.AllArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Profile("mock")
@AllArgsConstructor
public class MockSalesOrderServiceImpl implements SalesOrderService {
    private final SalesOrderConverter salesOrderConverter;

    @Override
    public IPage<SalesOrderVO> pageSalesOrder(SalesOrderPageDTO pageDTO) {
        SalesOrder salesOrder = new SalesOrder();
        salesOrder.setId(1L);
        salesOrder.setOrderNo("SO20261014001");
        salesOrder.setCustomerOrderNo("CO-20261014-001");
        salesOrder.setOrderTime(LocalDateTime.of(2026, 10, 14, 10, 30));
        salesOrder.setCustomerId(1L);
        salesOrder.setCustomerName("Mock Customer");
        salesOrder.setDeliveryTime(LocalDateTime.of(2026, 10, 16, 18, 0));
        salesOrder.setOrderStatus(com.glw.backend.model.enums.SalesOrderStatus.PENDING_SHIPMENT);
        salesOrder.setPaymentStatus(com.glw.backend.model.enums.SalesPaymentStatus.UNPAID);
        salesOrder.setVersion(0);

        Page<SalesOrderVO> page = pageDTO.toPage();
        page.setRecords(List.of(salesOrderConverter.toVO(salesOrder)));
        page.setTotal(1);
        return page;
    }

    @Override
    public SalesOrderVO createSalesOrder(SalesOrderCreateDTO createDTO) {
        throw new BusinessException(ErrorCode.BAD_REQUEST, "演示模式不支持保存销售订单");
    }

    @Override
    public SalesOrderVO updateSalesOrder(Long id, SalesOrderCreateDTO createDTO) {
        throw new BusinessException(ErrorCode.BAD_REQUEST, "演示模式不支持保存销售订单");
    }
}
