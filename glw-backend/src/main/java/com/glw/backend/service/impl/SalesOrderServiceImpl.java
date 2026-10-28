package com.glw.backend.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.glw.backend.converter.SalesOrderConverter;
import com.glw.backend.mapper.SalesOrderMapper;
import com.glw.backend.mapper.CustomerMapper;
import com.glw.backend.mapper.OutboundOrderItemAllocMapper;
import com.glw.backend.model.entity.Customer;
import com.glw.backend.model.dto.SalesOrderPageDTO;
import com.glw.backend.model.entity.SalesOrder;
import com.glw.backend.model.entity.SalesOrderItem;
import com.glw.backend.model.vo.SalesOrderVO;
import com.glw.backend.service.SalesOrderItemService;
import com.glw.backend.service.SalesOrderService;
import com.glw.backend.service.DocumentNumberService;

import lombok.AllArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.List;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.stream.Collectors;

/** 销售订单表基础服务实现。 */
@Service
@Profile("!mock")
@AllArgsConstructor
public class SalesOrderServiceImpl extends ServiceImpl<SalesOrderMapper, SalesOrder> implements SalesOrderService {

    private final SalesOrderItemService salesOrderItemService;
    private final SalesOrderConverter salesOrderConverter;
    private final CustomerMapper customerMapper;
    private final OutboundOrderItemAllocMapper outboundOrderItemAllocMapper;
    private final DocumentNumberService documentNumberService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean save(SalesOrder order) {
        if (order.getId() == null) {
            if (order.getOrderTime() == null) order.setOrderTime(LocalDateTime.now());
            order.setOrderNo(documentNumberService.next(
                    DocumentNumberService.Kind.SALES, order.getOrderTime()));
        }
        return super.save(order);
    }

    @Override
    public IPage<SalesOrderVO> pageSalesOrder(SalesOrderPageDTO pageDTO) {
        boolean filterBrand = StringUtils.hasText(pageDTO.getBrand());
        boolean filterPartNo = StringUtils.hasText(pageDTO.getPartNo());
        boolean filterPackageType = StringUtils.hasText(pageDTO.getPackageType());

        List<Long> filterOrderIds = null;
        if (filterBrand || filterPartNo || filterPackageType) {
            filterOrderIds = salesOrderItemService.lambdaQuery()
                .eq(filterBrand, SalesOrderItem::getBrand, pageDTO.getBrand())
                .eq(filterPartNo, SalesOrderItem::getPartNo, pageDTO.getPartNo())
                .eq(filterPackageType, SalesOrderItem::getPackageType, pageDTO.getPackageType())
                .list().stream().map(SalesOrderItem::getSalesOrderId).distinct().toList();
            if (filterOrderIds.isEmpty()) {
                Page<SalesOrderVO> empty = pageDTO.toPage();
                empty.setRecords(List.of());
                empty.setTotal(0);
                return empty;
            }
        }

        Page<SalesOrder> page = lambdaQuery()
            .in(filterOrderIds != null, SalesOrder::getId, filterOrderIds)
            .eq(pageDTO.getId() != null, SalesOrder::getId, pageDTO.getId())
            .eq(pageDTO.getOrderNo() != null, SalesOrder::getOrderNo, pageDTO.getOrderNo())
            .eq(pageDTO.getCustomerOrderNo() != null, SalesOrder::getCustomerOrderNo, pageDTO.getCustomerOrderNo())
            .eq(pageDTO.getCustomerId() != null, SalesOrder::getCustomerId, pageDTO.getCustomerId())
            .eq(pageDTO.getSettleType() != null, SalesOrder::getSettleType, pageDTO.getSettleType())
            .eq(StringUtils.hasText(pageDTO.getOrderStatus()), SalesOrder::getOrderStatus, pageDTO.getOrderStatus())
            .eq(StringUtils.hasText(pageDTO.getPaymentStatus()), SalesOrder::getPaymentStatus, pageDTO.getPaymentStatus())
            .ge(StringUtils.hasText(pageDTO.getOrderTimeStart()), SalesOrder::getOrderTime, pageDTO.getOrderTimeStart())
            .le(StringUtils.hasText(pageDTO.getOrderTimeEnd()), SalesOrder::getOrderTime, pageDTO.getOrderTimeEnd())
            .orderByDesc(SalesOrder::getOrderTime)
            .orderByDesc(SalesOrder::getId)
            .page(pageDTO.toPage());

        IPage<SalesOrderVO> result = page.convert(salesOrderConverter::toVO);
        if (!CollectionUtils.isEmpty(result.getRecords())) {
            var customerIds = result.getRecords().stream().map(SalesOrderVO::getCustomerId)
                    .filter(java.util.Objects::nonNull).distinct().toList();
            Map<Long, Customer> customers = customerIds.isEmpty() ? Map.of() : customerMapper.selectByIds(customerIds)
                    .stream().collect(Collectors.toMap(Customer::getId, customer -> customer));
            result.getRecords().forEach(order -> {
                Customer customer = order.getCustomerId() == null ? null : customers.get(order.getCustomerId());
                if (customer != null) {
                    order.setCustomerName(customer.getName());
                    order.setCustomerShortName(customer.getShortName());
                }
            });
            List<Long> orderIds = result.getRecords().stream().map(SalesOrderVO::getId).toList();
            var shipped = outboundOrderItemAllocMapper.sumCompletedBySalesOrderIds(orderIds).stream()
                    .collect(Collectors.toMap(OutboundOrderItemAllocMapper.ShippedQuantity::salesOrderItemId,
                            OutboundOrderItemAllocMapper.ShippedQuantity::quantity));
            Map<Long, List<SalesOrderItem>> items = salesOrderItemService.lambdaQuery()
                    .in(SalesOrderItem::getSalesOrderId, orderIds)
                    .orderByAsc(SalesOrderItem::getId).list().stream()
                    .collect(Collectors.groupingBy(SalesOrderItem::getSalesOrderId));
            result.getRecords().forEach(order -> order.setItems(items.getOrDefault(order.getId(), List.of())
                    .stream().map(item -> {
                        var vo = salesOrderConverter.toItemVO(item);
                        long pending = (long) item.getQuantity()
                                - (item.getCancelledQuantity() == null ? 0 : item.getCancelledQuantity())
                                - shipped.getOrDefault(item.getId(), 0L);
                        vo.setPendingQuantity((int) Math.max(0L, pending));
                        return vo;
                    }).toList()));
        }
        return result;
    }
}
