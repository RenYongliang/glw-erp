package com.glw.backend.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.glw.backend.mapper.OutboundOrderMapper;
import com.glw.backend.mapper.CustomerMapper;
import com.glw.backend.model.entity.Customer;
import com.glw.backend.model.entity.OutboundOrder;
import com.glw.backend.service.OutboundOrderService;
import com.glw.backend.service.DocumentNumberService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.glw.backend.model.dto.OutboundOrderPageDTO;
import com.glw.backend.model.vo.OutboundOrderVO;
import com.glw.backend.model.entity.OutboundOrderItem;
import com.glw.backend.model.entity.OutboundOrderBatchItem;
import com.glw.backend.service.OutboundOrderItemService;
import com.glw.backend.service.OutboundOrderBatchItemService;
import com.glw.backend.converter.OutboundOrderConverter;
import lombok.AllArgsConstructor;
import org.springframework.util.StringUtils;
import java.util.List;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 出库表基础服务实现。 */
@Service
@Profile("!mock")
@AllArgsConstructor
public class OutboundOrderServiceImpl extends ServiceImpl<OutboundOrderMapper, OutboundOrder>
        implements OutboundOrderService {
    private final OutboundOrderItemService itemService;
    private final OutboundOrderBatchItemService batchService;
    private final OutboundOrderConverter converter;
    private final CustomerMapper customerMapper;
    private final DocumentNumberService documentNumberService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean save(OutboundOrder order) {
        if (order.getId() == null) {
            if (order.getOutboundTime() == null) order.setOutboundTime(LocalDateTime.now());
            order.setOutboundOrderNo(documentNumberService.next(
                    DocumentNumberService.Kind.OUTBOUND, order.getOutboundTime()));
        }
        return super.save(order);
    }

    @Override
    public IPage<OutboundOrderVO> pageOutboundOrder(OutboundOrderPageDTO dto) {
        Page<OutboundOrder> page = lambdaQuery()
                .eq(dto.getId() != null, OutboundOrder::getId, dto.getId())
                .eq(dto.getSalesOrderId() != null, OutboundOrder::getSalesOrderId, dto.getSalesOrderId())
                .eq(StringUtils.hasText(dto.getCustomerOrderNo()), OutboundOrder::getCustomerOrderNo, dto.getCustomerOrderNo())
                .eq(dto.getCustomerId() != null, OutboundOrder::getCustomerId, dto.getCustomerId())
                .like(StringUtils.hasText(dto.getCustomerName()), OutboundOrder::getCustomerName, dto.getCustomerName())
                .eq(dto.getOutboundType() != null, OutboundOrder::getOutboundType, dto.getOutboundType())
                .eq(dto.getStatus() != null, OutboundOrder::getStatus, dto.getStatus())
                .ge(StringUtils.hasText(dto.getOutboundTimeStart()), OutboundOrder::getOutboundTime, dto.getOutboundTimeStart())
                .le(StringUtils.hasText(dto.getOutboundTimeEnd()), OutboundOrder::getOutboundTime, dto.getOutboundTimeEnd())
                .orderByDesc(OutboundOrder::getOutboundTime).orderByDesc(OutboundOrder::getId)
                .page(dto.toPage());
        IPage<OutboundOrderVO> result = page.convert(converter::toVO);
        if (result.getRecords().isEmpty()) return result;
        var customerIds = result.getRecords().stream().map(OutboundOrderVO::getCustomerId)
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
        var orderIds = result.getRecords().stream().map(OutboundOrderVO::getId).toList();
        var items = itemService.lambdaQuery().in(OutboundOrderItem::getOutboundOrderId, orderIds)
                .orderByAsc(OutboundOrderItem::getId).list();
        var itemIds = items.stream().map(OutboundOrderItem::getId).toList();
        var batches = itemIds.isEmpty() ? List.<OutboundOrderBatchItem>of() : batchService.lambdaQuery()
                .in(OutboundOrderBatchItem::getOutboundOrderItemId, itemIds)
                .orderByAsc(OutboundOrderBatchItem::getId).list();
        var groupedBatches = batches.stream().collect(Collectors.groupingBy(OutboundOrderBatchItem::getOutboundOrderItemId));
        var groupedItems = items.stream().collect(Collectors.groupingBy(OutboundOrderItem::getOutboundOrderId));
        result.getRecords().forEach(order -> order.setItems(groupedItems.getOrDefault(order.getId(), List.of())
                .stream().map(item -> {
                    var vo = converter.toItemVO(item);
                    vo.setBatches(groupedBatches.getOrDefault(item.getId(), List.of())
                            .stream().map(converter::toBatchVO).toList());
                    return vo;
                }).toList()));
        return result;
    }
}
