package com.glw.backend.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.glw.backend.common.BusinessException;
import com.glw.backend.common.ErrorCode;
import com.glw.backend.converter.SalesOrderConverter;
import com.glw.backend.mapper.SalesOrderMapper;
import com.glw.backend.mapper.CustomerMapper;
import com.glw.backend.mapper.CustomerProductCodeMapper;
import com.glw.backend.mapper.OutboundOrderItemAllocMapper;
import com.glw.backend.mapper.ProductMapper;
import com.glw.backend.model.dto.SalesOrderCreateDTO;
import com.glw.backend.model.dto.SalesOrderItemCreateDTO;
import com.glw.backend.model.entity.Customer;
import com.glw.backend.model.entity.CustomerProductCode;
import com.glw.backend.model.dto.SalesOrderPageDTO;
import com.glw.backend.model.entity.OutboundOrderItemAlloc;
import com.glw.backend.model.entity.Product;
import com.glw.backend.model.entity.SalesOrder;
import com.glw.backend.model.entity.SalesOrderItem;
import com.glw.backend.model.enums.SalesDeliveryStatus;
import com.glw.backend.model.enums.SalesOrderStatus;
import com.glw.backend.model.enums.SalesPaymentStatus;
import com.glw.backend.model.enums.SettlementType;
import com.glw.backend.model.enums.TaxIncluded;
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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/** 销售订单表基础服务实现。 */
@Service
@Profile("!mock")
@AllArgsConstructor
public class SalesOrderServiceImpl extends ServiceImpl<SalesOrderMapper, SalesOrder> implements SalesOrderService {

    private final SalesOrderItemService salesOrderItemService;
    private final SalesOrderConverter salesOrderConverter;
    private final CustomerMapper customerMapper;
    private final CustomerProductCodeMapper customerProductCodeMapper;
    private final ProductMapper productMapper;
    private final OutboundOrderItemAllocMapper outboundOrderItemAllocMapper;
    private final DocumentNumberService documentNumberService;
    private final com.glw.backend.mapper.FinanceOrderStatusMapper financeMapper;

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
    @Transactional(rollbackFor = Exception.class)
    public SalesOrderVO createSalesOrder(SalesOrderCreateDTO createDTO) {
        Customer customer = requireCustomer(createDTO.getCustomerId());
        Map<Long, Product> products = requireProducts(createDTO.getItems());
        LocalDateTime orderTime = parseOrderTime(createDTO.getOrderTime(), LocalDateTime.now());

        SalesOrder order = new SalesOrder();
        order.setOrderTime(orderTime);
        order.setCustomerId(customer.getId());
        order.setCustomerName(customer.getName());
        order.setCustomerOrderNo(trimText(createDTO.getCustomerOrderNo(), 64, "客户采购单号"));
        order.setSettleType(createDTO.getSettleType() == null ? SettlementType.CASH : createDTO.getSettleType());
        // 含税口径是订单级的：明细单价统一按订单口径解释，不再逐行选择。
        order.setTaxIncluded(createDTO.getTaxIncluded() == null ? TaxIncluded.INCLUDED : createDTO.getTaxIncluded());
        order.setDeliveryTime(parseOrderTime(createDTO.getDeliveryTime(), orderTime));
        order.setWarehouseCode(com.glw.backend.common.WarehouseDefaults.CODE);
        order.setWarehouseName(com.glw.backend.common.WarehouseDefaults.NAME);
        // 后端不设审批环节，新建销售订单直接进入执行中；待审核状态保留给以后启用。
        order.setOrderStatus(SalesOrderStatus.CONFIRMED);
        order.setDeliveryStatus(SalesDeliveryStatus.PENDING_SHIPMENT);
        order.setPaymentStatus(SalesPaymentStatus.UNPAID);
        order.setRemark(trimText(createDTO.getRemark(), 256, "备注"));
        order.setVersion(0);
        save(order);
        saveItems(order, createDTO.getItems(), products);
        return toDetailVO(order);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SalesOrderVO updateSalesOrder(Long id, SalesOrderCreateDTO createDTO) {
        SalesOrder order = getById(id);
        if (order == null) throw new BusinessException(ErrorCode.NOT_FOUND, "销售订单不存在");
        if (order.getOrderStatus() == SalesOrderStatus.CANCELLED)
            throw new BusinessException(ErrorCode.BAD_REQUEST, "已取消的销售订单不能修改");
        if (createDTO.getVersion() != null && !createDTO.getVersion().equals(order.getVersion()))
            throw new BusinessException(ErrorCode.CONFLICT, "销售订单已发生变化，请刷新后重试");
        if (outboundOrderItemAllocMapper.selectCount(Wrappers.<OutboundOrderItemAlloc>lambdaQuery()
                .eq(OutboundOrderItemAlloc::getSalesOrderId, id)) > 0)
            throw new BusinessException(ErrorCode.CONFLICT, "已有出库记录的销售订单不能修改");
        if (financeMapper.activePaymentCount("AR", id) > 0 && amountOrItemsChanged(order, createDTO))
            throw new BusinessException(ErrorCode.CONFLICT, "销售订单已有收付款记录，不能修改金额或产品明细");

        Customer customer = requireCustomer(createDTO.getCustomerId());
        Map<Long, Product> products = requireProducts(createDTO.getItems());
        LocalDateTime orderTime = parseOrderTime(createDTO.getOrderTime(), order.getOrderTime());

        order.setOrderTime(orderTime);
        order.setCustomerId(customer.getId());
        order.setCustomerName(customer.getName());
        order.setCustomerOrderNo(trimText(createDTO.getCustomerOrderNo(), 64, "客户采购单号"));
        if (createDTO.getSettleType() != null) order.setSettleType(createDTO.getSettleType());
        if (createDTO.getTaxIncluded() != null) order.setTaxIncluded(createDTO.getTaxIncluded());
        order.setDeliveryTime(parseOrderTime(createDTO.getDeliveryTime(), orderTime));
        order.setWarehouseCode(com.glw.backend.common.WarehouseDefaults.CODE);
        order.setWarehouseName(com.glw.backend.common.WarehouseDefaults.NAME);
        order.setRemark(trimText(createDTO.getRemark(), 256, "备注"));
        if (!updateById(order))
            throw new BusinessException(ErrorCode.CONFLICT, "销售订单已发生变化，请刷新后重试");

        salesOrderItemService.lambdaUpdate()
                .eq(SalesOrderItem::getSalesOrderId, id)
                .remove();
        saveItems(order, createDTO.getItems(), products);
        return toDetailVO(order);
    }

    /** 已有收付款后，只允许修改非金额字段；金额与产品明细变化在这里拦截。 */
    private boolean amountOrItemsChanged(SalesOrder order, SalesOrderCreateDTO createDTO) {
        if (createDTO.getTaxIncluded() != null && createDTO.getTaxIncluded() != order.getTaxIncluded())
            return true;
        List<SalesOrderItem> rows = salesOrderItemService.lambdaQuery()
                .eq(SalesOrderItem::getSalesOrderId, order.getId())
                .orderByAsc(SalesOrderItem::getId)
                .list();
        if (rows.size() != createDTO.getItems().size()) return true;
        Map<Long, SalesOrderItem> existing = rows.stream()
                .collect(Collectors.toMap(SalesOrderItem::getProductId, item -> item));
        TaxIncluded taxIncluded = createDTO.getTaxIncluded() == null
                ? order.getTaxIncluded() : createDTO.getTaxIncluded();
        for (SalesOrderItemCreateDTO item : createDTO.getItems()) {
            SalesOrderItem row = existing.get(item.getProductId());
            if (row == null) return true;
            if (!Objects.equals(row.getQuantity(), item.getQuantity())) return true;
            if (row.getUnitPrice().compareTo(item.getUnitPrice()) != 0) return true;
            Integer taxRate = taxIncluded == TaxIncluded.INCLUDED ? item.getTaxRate() : null;
            if (!Objects.equals(row.getTaxRate(), taxRate)) return true;
        }
        return false;
    }

    private Customer requireCustomer(Long customerId) {
        Customer customer = customerMapper.selectById(customerId);
        if (customer == null) throw new BusinessException(ErrorCode.NOT_FOUND, "客户不存在");
        return customer;
    }

    private Map<Long, Product> requireProducts(List<SalesOrderItemCreateDTO> items) {
        List<Long> productIds = items.stream().map(SalesOrderItemCreateDTO::getProductId).distinct().toList();
        if (productIds.size() != items.size())
            throw new BusinessException(ErrorCode.BAD_REQUEST, "同一销售订单中产品不可重复");
        Map<Long, Product> products = productMapper.selectByIds(productIds).stream()
                .collect(Collectors.toMap(Product::getId, product -> product));
        for (Long productId : productIds) {
            if (!products.containsKey(productId))
                throw new BusinessException(ErrorCode.NOT_FOUND, "销售产品不存在或已删除");
        }
        return products;
    }

    /** 产品品牌、型号、封装以产品档案为准，避免前端快照过期后写入不一致数据。 */
    private void saveItems(
            SalesOrder order, List<SalesOrderItemCreateDTO> items, Map<Long, Product> products) {
        List<SalesOrderItem> rows = new ArrayList<>(items.size());
        for (SalesOrderItemCreateDTO itemDTO : items) {
            Product product = products.get(itemDTO.getProductId());
            BigDecimal price = itemDTO.getUnitPrice().setScale(4, RoundingMode.HALF_UP);
            SalesOrderItem row = new SalesOrderItem();
            row.setSalesOrderId(order.getId());
            row.setCustomerOrderNo(order.getCustomerOrderNo());
            row.setProductId(product.getId());
            row.setBrand(product.getBrand());
            row.setPartNo(product.getPartNo());
            row.setPackageType(product.getPackageType());
            // 订单行保存客户物料编码快照；前端未传时回落到客户客件对照表。
            String customerPartNo = trimText(itemDTO.getCustomerPartNo(), 64, "客户物料编码");
            if (customerPartNo == null) {
                CustomerProductCode mapping = customerProductCodeMapper.selectOne(
                        Wrappers.<CustomerProductCode>lambdaQuery()
                                .eq(CustomerProductCode::getCustomerId, order.getCustomerId())
                                .eq(CustomerProductCode::getProductId, product.getId())
                                .eq(CustomerProductCode::getEnabled, true));
                if (mapping != null) customerPartNo = mapping.getCustomerCode();
            }
            row.setCustomerPartNo(customerPartNo);
            row.setUnitPrice(price);
            // 未税订单单价本身就是净价，不保存税率；含税订单才逐行记录税率。
            row.setTaxRate(order.getTaxIncluded() == TaxIncluded.INCLUDED ? itemDTO.getTaxRate() : null);
            row.setQuantity(itemDTO.getQuantity());
            row.setCancelledQuantity(0);
            row.setAmount(price.multiply(BigDecimal.valueOf(itemDTO.getQuantity()))
                    .setScale(4, RoundingMode.HALF_UP));
            rows.add(row);
        }
        salesOrderItemService.saveBatch(rows);
    }

    private SalesOrderVO toDetailVO(SalesOrder order) {
        SalesOrderVO vo = salesOrderConverter.toVO(order);
        Map<Long, Long> shipped = outboundOrderItemAllocMapper
                .sumCompletedBySalesOrderIds(List.of(order.getId())).stream()
                .collect(Collectors.toMap(OutboundOrderItemAllocMapper.ShippedQuantity::salesOrderItemId,
                        OutboundOrderItemAllocMapper.ShippedQuantity::quantity));
        vo.setItems(salesOrderItemService.lambdaQuery()
                .eq(SalesOrderItem::getSalesOrderId, order.getId())
                .orderByAsc(SalesOrderItem::getId)
                .list().stream()
                .map(item -> {
                    var itemVO = salesOrderConverter.toItemVO(item);
                    long pending = (long) item.getQuantity()
                            - (item.getCancelledQuantity() == null ? 0 : item.getCancelledQuantity())
                            - shipped.getOrDefault(item.getId(), 0L);
                    itemVO.setPendingQuantity((int) Math.max(0L, pending));
                    return itemVO;
                })
                .toList());
        vo.setPrepaymentAmount(financeMapper.orderPrepayments("AR", List.of(order.getId())).stream()
                .findFirst()
                .map(com.glw.backend.finance.FinanceOrderStatus.Prepayment::amount)
                .orElse(BigDecimal.ZERO));
        return vo;
    }

    private LocalDateTime parseOrderTime(String value, LocalDateTime fallback) {
        if (!StringUtils.hasText(value)) return fallback;
        String text = value.trim();
        try {
            return text.length() <= 10
                    ? LocalDate.parse(text).atStartOfDay()
                    : LocalDateTime.parse(text.replace(' ', 'T'));
        } catch (DateTimeParseException exception) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "销售订单日期格式不正确");
        }
    }

    private String trimText(String value, int maxLength, String label) {
        if (!StringUtils.hasText(value)) return null;
        String text = value.trim();
        if (text.length() > maxLength)
            throw new BusinessException(ErrorCode.BAD_REQUEST, label + "不能超过 " + maxLength + " 字");
        return text;
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
        List<Long> paymentOrderIds = StringUtils.hasText(pageDTO.getPaymentStatus())
                ? financeMapper.orderIdsByStatus("AR", pageDTO.getPaymentStatus()) : null;
        if (paymentOrderIds != null && paymentOrderIds.isEmpty()) paymentOrderIds = List.of(-1L);

        Page<SalesOrder> page = lambdaQuery()
                .in(paymentOrderIds != null, SalesOrder::getId, paymentOrderIds)
            .in(filterOrderIds != null, SalesOrder::getId, filterOrderIds)
            .eq(pageDTO.getId() != null, SalesOrder::getId, pageDTO.getId())
            .eq(pageDTO.getOrderNo() != null, SalesOrder::getOrderNo, pageDTO.getOrderNo())
            .eq(pageDTO.getCustomerOrderNo() != null, SalesOrder::getCustomerOrderNo, pageDTO.getCustomerOrderNo())
            .eq(pageDTO.getCustomerId() != null, SalesOrder::getCustomerId, pageDTO.getCustomerId())
            .eq(pageDTO.getSettleType() != null, SalesOrder::getSettleType, pageDTO.getSettleType())
            .eq(StringUtils.hasText(pageDTO.getOrderStatus()), SalesOrder::getOrderStatus, pageDTO.getOrderStatus())
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
            var financialStatuses = financeMapper.orderStatuses("AR", orderIds).stream().collect(Collectors.toMap(
                    com.glw.backend.finance.FinanceOrderStatus.OrderStatus::orderId,
                    com.glw.backend.finance.FinanceOrderStatus.OrderStatus::paymentStatus));
            Map<Long, BigDecimal> prepayments = financeMapper.orderPrepayments("AR", orderIds).stream()
                    .collect(Collectors.toMap(
                            com.glw.backend.finance.FinanceOrderStatus.Prepayment::orderId,
                            com.glw.backend.finance.FinanceOrderStatus.Prepayment::amount));
            result.getRecords().forEach(order -> {
                order.setPrepaymentAmount(prepayments.getOrDefault(order.getId(), BigDecimal.ZERO));
                if (financialStatuses.containsKey(order.getId())) order.setPaymentStatus(
                        com.glw.backend.model.enums.SalesPaymentStatus.fromCode(financialStatuses.get(order.getId())));
                // 兜底：已全部发货且已收款的历史数据即使漏了重算，也显示为已完结。
                if (order.getOrderStatus() == SalesOrderStatus.CONFIRMED
                        && order.getDeliveryStatus() == SalesDeliveryStatus.SHIPPED
                        && SalesPaymentStatus.PAID.name().equals(financialStatuses.get(order.getId())))
                    order.setOrderStatus(SalesOrderStatus.COMPLETED);
            });
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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelOrder(Long orderId) {
        SalesOrder order = getById(orderId);
        if (order == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "销售订单不存在");
        }
        if (order.getOrderStatus() == SalesOrderStatus.CANCELLED) return;
        if (order.getOrderStatus() == SalesOrderStatus.COMPLETED) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "已完结的销售订单不能取消");
        }
        long outbound = outboundOrderItemAllocMapper.selectCount(
                Wrappers.<OutboundOrderItemAlloc>lambdaQuery()
                        .eq(OutboundOrderItemAlloc::getSalesOrderId, orderId));
        if (outbound > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "订单已有出库记录，请先撤销出库");
        }
        if (financeMapper.activePaymentCount("AR", orderId) > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "订单已有收付款记录，请先在财务作废未核销收付款后再取消");
        }
        List<SalesOrderItem> items = salesOrderItemService.lambdaQuery()
                .eq(SalesOrderItem::getSalesOrderId, orderId)
                .list();
        for (SalesOrderItem item : items) {
            item.setCancelledQuantity(item.getQuantity());
            salesOrderItemService.updateById(item);
        }
        order.setOrderStatus(SalesOrderStatus.CANCELLED);
        order.setDeliveryStatus(SalesDeliveryStatus.PENDING_SHIPMENT);
        if (!updateById(order)) {
            throw new BusinessException(ErrorCode.CONFLICT, "销售订单已发生变化，请刷新后重试");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void refreshCompletionStatus(Long orderId) {
        if (orderId == null) return;
        SalesOrder order = getById(orderId);
        if (order == null) return;
        // 待审核和已取消不参与自动闭环。
        if (order.getOrderStatus() == SalesOrderStatus.CANCELLED
                || order.getOrderStatus() == SalesOrderStatus.PENDING_APPROVAL) return;
        String paymentStatus = financeMapper.orderStatuses("AR", List.of(orderId)).stream()
                .findFirst().map(com.glw.backend.finance.FinanceOrderStatus.OrderStatus::paymentStatus).orElse(null);
        SalesOrderStatus target = order.getDeliveryStatus() == SalesDeliveryStatus.SHIPPED
                && SalesPaymentStatus.PAID.name().equals(paymentStatus)
                ? SalesOrderStatus.COMPLETED : SalesOrderStatus.CONFIRMED;
        if (order.getOrderStatus() != target) {
            order.setOrderStatus(target);
            updateById(order);
        }
    }
}
