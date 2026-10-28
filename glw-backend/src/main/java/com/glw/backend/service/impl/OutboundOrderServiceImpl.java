package com.glw.backend.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.glw.backend.common.BusinessException;
import com.glw.backend.common.ErrorCode;
import com.glw.backend.converter.OutboundOrderConverter;
import com.glw.backend.mapper.CustomerMapper;
import com.glw.backend.mapper.OutboundOrderItemAllocMapper;
import com.glw.backend.mapper.OutboundOrderMapper;
import com.glw.backend.mapper.SalesOrderMapper;
import com.glw.backend.model.dto.OutboundOrderAllocationCreateDTO;
import com.glw.backend.model.dto.OutboundOrderBatchCreateDTO;
import com.glw.backend.model.dto.OutboundOrderCreateDTO;
import com.glw.backend.model.dto.OutboundOrderPageDTO;
import com.glw.backend.model.entity.Customer;
import com.glw.backend.model.entity.Inventory;
import com.glw.backend.model.entity.OutboundOrder;
import com.glw.backend.model.entity.OutboundOrderItem;
import com.glw.backend.model.entity.OutboundOrderItemAlloc;
import com.glw.backend.model.entity.OutboundOrderItemBatch;
import com.glw.backend.model.entity.SalesOrder;
import com.glw.backend.model.entity.SalesOrderItem;
import com.glw.backend.model.enums.OutboundStatus;
import com.glw.backend.model.enums.OutboundType;
import com.glw.backend.model.enums.SalesOrderStatus;
import com.glw.backend.model.vo.OutboundOrderItemAllocVO;
import com.glw.backend.model.vo.OutboundOrderItemBatchVO;
import com.glw.backend.model.vo.OutboundOrderVO;
import com.glw.backend.model.vo.OutboundSalesOrderVO;
import com.glw.backend.service.DocumentNumberService;
import com.glw.backend.service.InventoryService;
import com.glw.backend.service.OutboundOrderItemAllocService;
import com.glw.backend.service.OutboundOrderItemBatchService;
import com.glw.backend.service.OutboundOrderItemService;
import com.glw.backend.service.OutboundOrderService;
import com.glw.backend.service.SalesOrderItemService;

import lombok.AllArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/** 出库表基础服务实现。 */
@Service
@Profile("!mock")
@AllArgsConstructor
public class OutboundOrderServiceImpl extends ServiceImpl<OutboundOrderMapper, OutboundOrder>
        implements OutboundOrderService {

    private static final String DEFAULT_CURRENCY = "CNY";

    private final OutboundOrderItemService outboundOrderItemService;
    private final OutboundOrderItemAllocService outboundOrderItemAllocService;
    private final OutboundOrderItemBatchService outboundOrderItemBatchService;
    private final OutboundOrderItemAllocMapper outboundOrderItemAllocMapper;
    private final SalesOrderMapper salesOrderMapper;
    private final SalesOrderItemService salesOrderItemService;
    private final InventoryService inventoryService;
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
                .apply(dto.getSalesOrderId() != null,
                        "EXISTS (SELECT 1 FROM outbound_order_item_alloc alloc WHERE alloc.outbound_order_id = outbound_order.id AND alloc.sales_order_id = {0})", dto.getSalesOrderId())
                .apply(StringUtils.hasText(dto.getCustomerOrderNo()),
                        "EXISTS (SELECT 1 FROM outbound_order_item_alloc alloc WHERE alloc.outbound_order_id = outbound_order.id AND alloc.customer_order_no = {0})", dto.getCustomerOrderNo())
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
                .filter(Objects::nonNull).distinct().toList();
        Map<Long, Customer> customers = customerIds.isEmpty() ? Map.of() : customerMapper.selectByIds(customerIds)
                .stream().collect(Collectors.toMap(Customer::getId, customer -> customer));
        result.getRecords().forEach(order -> {
            Customer customer = order.getCustomerId() == null ? null : customers.get(order.getCustomerId());
            if (customer != null) {
                order.setCustomerName(customer.getName());
                order.setCustomerShortName(customer.getShortName());
            }
        });
        fillItems(result.getRecords());
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OutboundOrderVO createOutboundOrder(OutboundOrderCreateDTO createDTO) {
        List<Long> salesIds = createDTO.getSalesOrderIds().stream().distinct().sorted().toList();
        if (salesIds.size() != createDTO.getSalesOrderIds().size())
            throw new BusinessException(ErrorCode.BAD_REQUEST, "销售订单不可重复选择");
        // Lock orders in a stable order before checking their remaining quantities.
        List<SalesOrder> salesOrders = salesOrderMapper.selectList(Wrappers.<SalesOrder>lambdaQuery()
                .in(SalesOrder::getId, salesIds).orderByAsc(SalesOrder::getId).last("FOR UPDATE"));
        if (salesOrders.size() != salesIds.size())
            throw new BusinessException(ErrorCode.NOT_FOUND, "销售订单不存在");
        SalesOrder customerOrder = salesOrders.getFirst();
        if (salesOrders.stream().anyMatch(order -> !Objects.equals(order.getCustomerId(), customerOrder.getCustomerId())))
            throw new BusinessException(ErrorCode.BAD_REQUEST, "同一出库单只能关联同一客户的销售订单");
        if (salesOrders.stream().anyMatch(order -> order.getOrderStatus() == SalesOrderStatus.CANCELLED))
            throw new BusinessException(ErrorCode.BAD_REQUEST, "已取消的销售订单不能出库");
        if (salesOrders.stream().anyMatch(order -> order.getOrderStatus() == SalesOrderStatus.COMPLETED))
            throw new BusinessException(ErrorCode.BAD_REQUEST, "已完成的销售订单不能再出库");
        Map<Long, SalesOrder> salesOrderMap = salesOrders.stream()
                .collect(Collectors.toMap(SalesOrder::getId, order -> order));

        List<SalesOrderItem> salesOrderItems = salesOrderItemService.lambdaQuery()
                .in(SalesOrderItem::getSalesOrderId, salesIds)
                .orderByAsc(SalesOrderItem::getId)
                .list();
        Map<Long, SalesOrderItem> salesItemMap = salesOrderItems.stream()
                .collect(Collectors.toMap(SalesOrderItem::getId, item -> item));
        Map<Long, Long> shippedMap = outboundOrderItemAllocMapper
                .sumCompletedBySalesOrderIds(salesIds)
                .stream()
                .collect(Collectors.toMap(
                        OutboundOrderItemAllocMapper.ShippedQuantity::salesOrderItemId,
                        OutboundOrderItemAllocMapper.ShippedQuantity::quantity));

        validateAllocations(createDTO, salesItemMap, shippedMap);
        Set<Long> allocatedOrders = createDTO.getAllocations().stream()
                .map(item -> salesItemMap.get(item.getSalesOrderItemId()).getSalesOrderId())
                .collect(Collectors.toSet());
        if (!allocatedOrders.equals(new HashSet<>(salesIds)))
            throw new BusinessException(ErrorCode.BAD_REQUEST, "每个关联销售单都必须有本次出库明细");

        Map<Long, Integer> requestedByInventory = new LinkedHashMap<>();
        for (OutboundOrderAllocationCreateDTO allocation : createDTO.getAllocations()) {
            for (OutboundOrderBatchCreateDTO batch : allocation.getBatches()) {
                requestedByInventory.merge(batch.getInventoryId(), batch.getQuantity(), Integer::sum);
            }
        }
        List<Long> inventoryIds = requestedByInventory.keySet().stream().sorted().toList();
        List<Inventory> inventories = inventoryService.lambdaQuery()
                .in(Inventory::getId, inventoryIds).orderByAsc(Inventory::getId).last("FOR UPDATE").list();
        if (inventories.size() != inventoryIds.size())
            throw new BusinessException(ErrorCode.NOT_FOUND, "库存批次不存在");
        Map<Long, Inventory> inventoryMap = inventories.stream()
                .collect(Collectors.toMap(Inventory::getId, inventory -> inventory));
        for (var entry : requestedByInventory.entrySet()) {
            Inventory inventory = inventoryMap.get(entry.getKey());
            int available = availableQuantity(inventory);
            if (entry.getValue() > available) {
                throw new BusinessException(
                        ErrorCode.BAD_REQUEST,
                        "库存批次 " + inventory.getBatchNo() + " 可用数量不足");
            }
        }

        LocalDateTime outboundTime = parseOutboundTime(createDTO.getOutboundTime());
        OutboundOrder outboundOrder = new OutboundOrder();
        outboundOrder.setCustomerId(customerOrder.getCustomerId());
        outboundOrder.setCustomerName(customerOrder.getCustomerName());
        outboundOrder.setOutboundType(OutboundType.SALE);
        outboundOrder.setStatus(OutboundStatus.COMPLETED);
        outboundOrder.setOutboundTime(outboundTime);
        outboundOrder.setVersion(0);
        outboundOrder.setOutboundOrderNo(documentNumberService.next(
                DocumentNumberService.Kind.OUTBOUND, outboundTime));
        save(outboundOrder);

        // One product line per product, allocations are kept as the financial/business split.
        Map<Long, List<OutboundOrderAllocationCreateDTO>> allocationsByProduct =
                createDTO.getAllocations().stream().collect(Collectors.groupingBy(
                        allocation -> salesItemMap.get(allocation.getSalesOrderItemId()).getProductId(),
                        LinkedHashMap::new, Collectors.toList()));
        Map<Long, OutboundOrderItem> itemByProduct = new HashMap<>();
        for (Map.Entry<Long, List<OutboundOrderAllocationCreateDTO>> entry : allocationsByProduct.entrySet()) {
            List<OutboundOrderAllocationCreateDTO> allocations = entry.getValue();
            SalesOrderItem sample = salesItemMap.get(allocations.getFirst().getSalesOrderItemId());
            int quantity = allocations.stream().mapToInt(this::allocationQuantity).sum();
            OutboundOrderItem item = new OutboundOrderItem();
            item.setOutboundOrderId(outboundOrder.getId());
            item.setProductId(entry.getKey());
            item.setBrand(sample.getBrand());
            item.setPartNo(sample.getPartNo());
            item.setPackageType(sample.getPackageType());
            item.setOutboundQuantity(quantity);
            outboundOrderItemService.save(item);
            itemByProduct.put(entry.getKey(), item);
        }

        Map<Long, Long> shippedAfter = new HashMap<>(shippedMap);
        for (OutboundOrderAllocationCreateDTO allocationDTO : createDTO.getAllocations()) {
            SalesOrderItem salesOrderItem = salesItemMap.get(allocationDTO.getSalesOrderItemId());
            SalesOrder salesOrder = salesOrderMap.get(salesOrderItem.getSalesOrderId());
            OutboundOrderItem orderItemLine = itemByProduct.get(salesOrderItem.getProductId());
            int quantity = allocationQuantity(allocationDTO);
            shippedAfter.merge(salesOrderItem.getId(), (long) quantity, Long::sum);

            OutboundOrderItemAlloc allocation = new OutboundOrderItemAlloc();
            allocation.setOutboundOrderId(outboundOrder.getId());
            allocation.setOutboundOrderItemId(orderItemLine.getId());
            allocation.setSalesOrderId(salesOrder.getId());
            allocation.setSalesOrderNo(salesOrder.getOrderNo());
            allocation.setCustomerOrderNo(salesOrderItem.getCustomerOrderNo());
            allocation.setSalesOrderItemId(salesOrderItem.getId());
            allocation.setAllocatedQuantity(quantity);
            allocation.setUnitPrice(salesOrderItem.getUnitPrice());
            allocation.setTaxIncluded(salesOrderItem.getTaxIncluded());
            allocation.setTaxRate(salesOrderItem.getTaxRate());
            allocation.setAmount(amount(salesOrderItem.getUnitPrice(), quantity));
            allocation.setCurrency(DEFAULT_CURRENCY);
            outboundOrderItemAllocService.save(allocation);

            for (OutboundOrderBatchCreateDTO batchDTO : allocationDTO.getBatches()) {
                Inventory inventory = inventoryMap.get(batchDTO.getInventoryId());
                OutboundOrderItemBatch batchItem = new OutboundOrderItemBatch();
                batchItem.setOutboundOrderId(outboundOrder.getId());
                batchItem.setAllocId(allocation.getId());
                batchItem.setInventoryId(inventory.getId());
                batchItem.setQuantity(batchDTO.getQuantity());
                batchItem.setUnitCost(inventory.getUnitCost());
                outboundOrderItemBatchService.save(batchItem);
            }
        }
        // Deduct stock once per inventory row, regardless of how many allocations use it.
        for (var entry : requestedByInventory.entrySet()) {
            Inventory inventory = inventoryMap.get(entry.getKey());
            inventory.setQuantity(inventory.getQuantity() - entry.getValue());
            if (!inventoryService.updateById(inventory))
                throw new BusinessException(ErrorCode.CONFLICT, "库存已发生变化，请刷新后重试");
        }

        for (SalesOrder salesOrder : salesOrders) {
            List<SalesOrderItem> items = salesOrderItems.stream()
                    .filter(item -> item.getSalesOrderId().equals(salesOrder.getId())).toList();
            long totalShippedAfter = items.stream()
                    .mapToLong(item -> shippedAfter.getOrDefault(item.getId(), 0L)).sum();
            salesOrder.setOrderStatus(resolveOrderStatus(items, totalShippedAfter));
            if (salesOrderMapper.updateById(salesOrder) == 0)
                throw new BusinessException(ErrorCode.CONFLICT, "销售订单已发生变化，请刷新后重试");
        }
        OutboundOrderVO result = converter.toVO(outboundOrder);
        fillItems(List.of(result));
        return result;
    }

    private void validateAllocations(
            OutboundOrderCreateDTO createDTO,
            Map<Long, SalesOrderItem> salesItemMap,
            Map<Long, Long> shippedMap) {
        Set<Long> itemIds = new HashSet<>();
        for (OutboundOrderAllocationCreateDTO allocationDTO : createDTO.getAllocations()) {
            if (!itemIds.add(allocationDTO.getSalesOrderItemId())) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "出库明细不可重复");
            }
            SalesOrderItem salesOrderItem = salesItemMap.get(allocationDTO.getSalesOrderItemId());
            if (salesOrderItem == null) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "出库明细不属于当前销售订单");
            }

            long quantity = allocationQuantity(allocationDTO);
            long cancelled = salesOrderItem.getCancelledQuantity() == null
                    ? 0
                    : salesOrderItem.getCancelledQuantity();
            long remaining = salesOrderItem.getQuantity() - cancelled
                    - shippedMap.getOrDefault(salesOrderItem.getId(), 0L);
            if (quantity < 1 || quantity > Integer.MAX_VALUE || quantity > remaining) {
                throw new BusinessException(
                        ErrorCode.BAD_REQUEST,
                        "产品 " + salesOrderItem.getPartNo() + " 的出库数量不能超过待出库数量");
            }

            Set<Long> inventoryIds = new HashSet<>();
            for (OutboundOrderBatchCreateDTO batch : allocationDTO.getBatches()) {
                if (!inventoryIds.add(batch.getInventoryId())) {
                    throw new BusinessException(
                            ErrorCode.BAD_REQUEST,
                            "产品 " + salesOrderItem.getPartNo() + " 的库存批次不可重复");
                }
            }
        }
    }

    private int allocationQuantity(OutboundOrderAllocationCreateDTO allocation) {
        return allocation.getBatches().stream()
                .mapToInt(OutboundOrderBatchCreateDTO::getQuantity)
                .sum();
    }

    private int availableQuantity(Inventory inventory) {
        int reserved = inventory.getReservedQuantity() == null ? 0 : inventory.getReservedQuantity();
        return Math.max(0, inventory.getQuantity() - reserved);
    }

    private BigDecimal amount(BigDecimal unitPrice, int quantity) {
        BigDecimal price = unitPrice == null ? BigDecimal.ZERO : unitPrice;
        return price.multiply(BigDecimal.valueOf(quantity)).setScale(4, RoundingMode.HALF_UP);
    }

    private LocalDateTime parseOutboundTime(String value) {
        if (!StringUtils.hasText(value)) return LocalDateTime.now();
        try {
            return LocalDateTime.parse(value.trim().replace(' ', 'T'));
        } catch (DateTimeParseException ex) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "出库时间格式不正确");
        }
    }

    private SalesOrderStatus resolveOrderStatus(
            List<SalesOrderItem> salesOrderItems, long totalShippedAfter) {
        long totalCommitted = salesOrderItems.stream()
                .filter(Objects::nonNull)
                .mapToLong(item -> item.getQuantity()
                        - (item.getCancelledQuantity() == null ? 0 : item.getCancelledQuantity()))
                .sum();
        return totalShippedAfter >= totalCommitted
                ? SalesOrderStatus.COMPLETED
                : SalesOrderStatus.PARTIALLY_SHIPPED;
    }

    private void fillItems(List<OutboundOrderVO> records) {
        if (CollectionUtils.isEmpty(records)) return;

        List<Long> orderIds = records.stream().map(OutboundOrderVO::getId).toList();
        Map<Long, List<OutboundOrderItem>> items = outboundOrderItemService.lambdaQuery()
                .in(OutboundOrderItem::getOutboundOrderId, orderIds)
                .orderByAsc(OutboundOrderItem::getId)
                .list()
                .stream()
                .collect(Collectors.groupingBy(OutboundOrderItem::getOutboundOrderId));
        List<Long> itemIds = items.values().stream()
                .flatMap(List::stream)
                .map(OutboundOrderItem::getId)
                .toList();
        Map<Long, List<OutboundOrderItemAlloc>> allocations = itemIds.isEmpty() ? Map.of()
                : outboundOrderItemAllocService.lambdaQuery()
                        .in(OutboundOrderItemAlloc::getOutboundOrderItemId, itemIds)
                        .orderByAsc(OutboundOrderItemAlloc::getId)
                        .list()
                        .stream()
                        .collect(Collectors.groupingBy(OutboundOrderItemAlloc::getOutboundOrderItemId));
        List<Long> allocIds = allocations.values().stream()
                .flatMap(List::stream)
                .map(OutboundOrderItemAlloc::getId)
                .toList();
        Map<Long, List<OutboundOrderItemBatch>> batches = allocIds.isEmpty() ? Map.of()
                : outboundOrderItemBatchService.lambdaQuery()
                        .in(OutboundOrderItemBatch::getAllocId, allocIds)
                        .orderByAsc(OutboundOrderItemBatch::getId)
                        .list()
                        .stream()
                        .collect(Collectors.groupingBy(OutboundOrderItemBatch::getAllocId));
        records.forEach(order -> {
            Map<Long, OutboundSalesOrderVO> salesOrders = new TreeMap<>();
            items.getOrDefault(order.getId(), List.of()).stream()
                    .flatMap(item -> allocations.getOrDefault(item.getId(), List.of()).stream())
                    .forEach(allocation -> salesOrders.computeIfAbsent(allocation.getSalesOrderId(), id -> {
                        var vo = new OutboundSalesOrderVO();
                        vo.setSalesOrderId(id);
                        vo.setSalesOrderNo(allocation.getSalesOrderNo());
                        vo.setCustomerOrderNo(allocation.getCustomerOrderNo());
                        return vo;
                    }));
            order.setSalesOrders(List.copyOf(salesOrders.values()));
            order.setItems(
                items.getOrDefault(order.getId(), List.of()).stream()
                        .map(item -> {
                            var itemVO = converter.toItemVO(item);
                            itemVO.setAllocations(
                                    allocations.getOrDefault(item.getId(), List.of()).stream()
                                            .map(allocation -> {
                                                var allocVO = converter.toAllocVO(allocation);
                                                List<OutboundOrderItemBatchVO> batchVOs = batches
                                                        .getOrDefault(allocation.getId(), List.of())
                                                        .stream()
                                                        .map(converter::toBatchVO)
                                                        .toList();
                                                if (batchVOs.isEmpty()) {
                                                    OutboundOrderItemBatchVO fallback = new OutboundOrderItemBatchVO();
                                                    fallback.setAllocId(allocation.getId());
                                                    fallback.setQuantity(allocation.getAllocatedQuantity());
                                                    batchVOs = List.of(fallback);
                                                }
                                                allocVO.setBatches(batchVOs);
                                                return allocVO;
                                            })
                                            .toList());
                            return itemVO;
                        })
                        .toList());
        });
    }
}
