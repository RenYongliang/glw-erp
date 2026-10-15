package com.glw.backend.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.glw.backend.converter.SalesOrderConverter;
import com.glw.backend.mapper.SalesOrderMapper;
import com.glw.backend.model.dto.PageDTO;
import com.glw.backend.model.dto.SalesOrderPageDTO;
import com.glw.backend.model.entity.SalesOrder;
import com.glw.backend.model.entity.SalesOrderItem;
import com.glw.backend.model.vo.SalesOrderVO;
import com.glw.backend.service.SalesOrderItemService;
import com.glw.backend.service.SalesOrderService;

import lombok.AllArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.List;

/** 销售订单表基础服务实现。 */
@Service
@Profile("!mock")
@AllArgsConstructor
public class SalesOrderServiceImpl extends ServiceImpl<SalesOrderMapper, SalesOrder> implements SalesOrderService {

    private final SalesOrderItemService salesOrderItemService;
    private final SalesOrderConverter salesOrderConverter;

    @Override
    public IPage<SalesOrderVO> pageSalesOrder(SalesOrderPageDTO pageDTO) {
        boolean filterBrand = StringUtils.hasText(pageDTO.getBrand());
        boolean filterPartNo = StringUtils.hasText(pageDTO.getPartNo());
        boolean filterPackageType = StringUtils.hasText(pageDTO.getPackageType());

        List<Long> filterOrderIds = null;
        if (filterBrand || filterPartNo || filterPackageType) {
            Page<SalesOrderItem> orderItemPages = salesOrderItemService.lambdaQuery()
                .eq(filterBrand, SalesOrderItem::getBrand, pageDTO.getBrand())
                .eq(filterPartNo, SalesOrderItem::getPartNo, pageDTO.getPartNo())
                .eq(filterPackageType, SalesOrderItem::getPackageType, pageDTO.getPackageType())
                .orderByDesc(SalesOrderItem::getCreatedAt)
                .page(pageDTO.toPage());
            filterOrderIds = orderItemPages.getRecords().stream().map(SalesOrderItem::getSalesOrderId).toList();
        }

        Page<SalesOrder> page = lambdaQuery()
            .in(!CollectionUtils.isEmpty(filterOrderIds), SalesOrder::getId, filterOrderIds)
            .eq(pageDTO.getOrderNo() != null, SalesOrder::getOrderNo, pageDTO.getOrderNo())
            .eq(pageDTO.getCustomerOrderNo() != null, SalesOrder::getCustomerOrderNo, pageDTO.getCustomerOrderNo())
            .eq(pageDTO.getCustomerId() != null, SalesOrder::getCustomerId, pageDTO.getCustomerId())
            .eq(StringUtils.hasText(pageDTO.getOrderStatus()), SalesOrder::getOrderStatus, pageDTO.getOrderStatus())
            .eq(StringUtils.hasText(pageDTO.getPaymentStatus()), SalesOrder::getPaymentStatus, pageDTO.getPaymentStatus())
            .ge(StringUtils.hasText(pageDTO.getOrderTimeStart()), SalesOrder::getOrderTimeStart, pageDTO.getOrderTimeStart())
            .le(StringUtils.hasText(pageDTO.getOrderTimeEnd()), SalesOrder::getOrderTimeEnd, pageDTO.getOrderTimeEnd())
            .orderByDesc(SalesOrder::getOrderTime)
            .page(pageDTO.toPage());

        return page.convert(salesOrderConverter::toVO);
    }
}
