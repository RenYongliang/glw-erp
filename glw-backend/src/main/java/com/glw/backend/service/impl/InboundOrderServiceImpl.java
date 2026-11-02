package com.glw.backend.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.glw.backend.common.BusinessException;
import com.glw.backend.common.ErrorCode;
import com.glw.backend.converter.InboundOrderConverter;
import com.glw.backend.mapper.InboundOrderItemAllocMapper;
import com.glw.backend.mapper.InboundOrderMapper;
import com.glw.backend.mapper.ProductMapper;
import com.glw.backend.model.dto.InboundOrderAllocationCreateDTO;
import com.glw.backend.model.dto.InboundOrderCreateDTO;
import com.glw.backend.model.dto.InboundOrderPageDTO;
import com.glw.backend.model.entity.InboundOrder;
import com.glw.backend.model.entity.InboundOrderItem;
import com.glw.backend.model.entity.InboundOrderItemAlloc;
import com.glw.backend.model.entity.Inventory;
import com.glw.backend.model.entity.Product;
import com.glw.backend.model.entity.PurchaseOrder;
import com.glw.backend.model.entity.PurchaseOrderItem;
import com.glw.backend.model.enums.InboundStatus;
import com.glw.backend.model.enums.InboundType;
import com.glw.backend.model.enums.PurchaseOrderStatus;
import com.glw.backend.model.enums.TaxIncluded;
import com.glw.backend.model.vo.InboundOrderItemAllocVO;
import com.glw.backend.model.vo.InboundOrderVO;
import com.glw.backend.model.vo.InboundPurchaseOrderVO;
import com.glw.backend.service.DocumentNumberService;
import com.glw.backend.service.InboundOrderItemAllocService;
import com.glw.backend.service.InboundOrderItemService;
import com.glw.backend.service.InboundOrderService;
import com.glw.backend.service.InventoryService;
import com.glw.backend.service.PurchaseOrderItemService;
import com.glw.backend.service.PurchaseOrderService;

import lombok.AllArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/** 入库表基础服务实现。 */
@Service
@Profile("!mock")
@AllArgsConstructor
public class InboundOrderServiceImpl extends ServiceImpl<InboundOrderMapper, InboundOrder>
        implements InboundOrderService {

    private static final String DEFAULT_CURRENCY = "CNY";

    private final InboundOrderItemService inboundOrderItemService;
    private final InboundOrderItemAllocService inboundOrderItemAllocService;
    private final InboundOrderItemAllocMapper inboundOrderItemAllocMapper;
    private final PurchaseOrderService purchaseOrderService;
    private final PurchaseOrderItemService purchaseOrderItemService;
    private final InventoryService inventoryService;
    private final InboundOrderConverter inboundOrderConverter;
    private final ProductMapper productMapper;
    private final DocumentNumberService documentNumberService;
    private final com.glw.backend.service.FinanceOpenItemService financeService;

    @Override
    public InboundOrderVO detail(Long id, Long purchaseOrderId) {
        InboundOrder order = getById(id);
        if (order == null) throw new BusinessException(ErrorCode.NOT_FOUND, "入库单不存在");
        InboundOrderVO result = inboundOrderConverter.toVO(order);
        fillItems(List.of(result));
        if (purchaseOrderId != null) {
            if (result.getPurchaseOrders().stream().noneMatch(po -> po.getPurchaseOrderId().equals(purchaseOrderId)))
                throw new BusinessException(ErrorCode.NOT_FOUND, "该采购单不属于当前入库单");
            result.setItems(result.getItems().stream()
                    .map(item -> {
                        List<InboundOrderItemAllocVO> allocations = item.getAllocations().stream()
                                .filter(alloc -> alloc.getPurchaseOrderId().equals(purchaseOrderId))
                                .toList();
                        if (allocations.isEmpty()) return null;
                        item.setAllocations(allocations);
                        item.setInboundQuantity(allocations.stream()
                                .mapToInt(InboundOrderItemAllocVO::getInboundQuantity).sum());
                        return item;
                    })
                    .filter(Objects::nonNull)
                    .toList());
            result.setPurchaseOrders(result.getPurchaseOrders().stream()
                    .filter(po -> po.getPurchaseOrderId().equals(purchaseOrderId)).toList());
        }
        return result;
    }

    @Override
    public IPage<InboundOrderVO> pageInboundOrder(InboundOrderPageDTO pageDTO) {
        Page<InboundOrder> page = lambdaQuery()
                .eq(pageDTO.getId() != null, InboundOrder::getId, pageDTO.getId())
                .apply(pageDTO.getPurchaseOrderId() != null,
                        "EXISTS (SELECT 1 FROM inbound_order_item_alloc alloc WHERE alloc.inbound_order_id = inbound_order.id AND alloc.purchase_order_id = {0})", pageDTO.getPurchaseOrderId())
                .apply(StringUtils.hasText(pageDTO.getPurchaseOrderNo()),
                        "EXISTS (SELECT 1 FROM inbound_order_item_alloc alloc WHERE alloc.inbound_order_id = inbound_order.id AND alloc.purchase_order_no = {0})", pageDTO.getPurchaseOrderNo())
                .eq(pageDTO.getSupplierId() != null, InboundOrder::getSupplierId, pageDTO.getSupplierId())
                .like(StringUtils.hasText(pageDTO.getSupplierName()), InboundOrder::getSupplierName, pageDTO.getSupplierName())
                .eq(pageDTO.getInboundType() != null, InboundOrder::getInboundType, pageDTO.getInboundType())
                .eq(pageDTO.getStatus() != null, InboundOrder::getStatus, pageDTO.getStatus())
                .ge(StringUtils.hasText(pageDTO.getInboundTimeStart()), InboundOrder::getInboundTime, pageDTO.getInboundTimeStart())
                .le(StringUtils.hasText(pageDTO.getInboundTimeEnd()), InboundOrder::getInboundTime, pageDTO.getInboundTimeEnd())
                .orderByDesc(InboundOrder::getInboundTime)
                .orderByDesc(InboundOrder::getId)
                .page(pageDTO.toPage());

        IPage<InboundOrderVO> result = page.convert(inboundOrderConverter::toVO);
        fillItems(result.getRecords());
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InboundOrderVO createInboundOrder(InboundOrderCreateDTO createDTO) {
        List<Long> purchaseIds = createDTO.getPurchaseOrderIds().stream().distinct().sorted().toList();
        if (purchaseIds.size() != createDTO.getPurchaseOrderIds().size())
            throw new BusinessException(ErrorCode.BAD_REQUEST, "采购订单不可重复选择");
        // Lock orders in a stable order before checking their remaining quantities.
        List<PurchaseOrder> purchaseOrders = purchaseOrderService.lambdaQuery()
                .in(PurchaseOrder::getId, purchaseIds).orderByAsc(PurchaseOrder::getId).last("FOR UPDATE").list();
        if (purchaseOrders.size() != purchaseIds.size())
            throw new BusinessException(ErrorCode.NOT_FOUND, "采购订单不存在");
        PurchaseOrder supplierOrder = purchaseOrders.getFirst();
        if (purchaseOrders.stream().anyMatch(order -> !Objects.equals(order.getSupplierId(), supplierOrder.getSupplierId())))
            throw new BusinessException(ErrorCode.BAD_REQUEST, "同一入库单只能关联同一供应商的采购订单");
        if (purchaseOrders.stream().anyMatch(order -> order.getOrderStatus() == PurchaseOrderStatus.COMPLETED))
            throw new BusinessException(ErrorCode.BAD_REQUEST, "已完成的采购订单不能再次入库");
        Map<Long, PurchaseOrder> purchaseOrderMap = purchaseOrders.stream()
                .collect(Collectors.toMap(PurchaseOrder::getId, order -> order));

        List<PurchaseOrderItem> orderItems = purchaseOrderItemService.lambdaQuery()
                .in(PurchaseOrderItem::getPurchaseOrderId, purchaseIds)
                .orderByAsc(PurchaseOrderItem::getId)
                .list();
        Map<Long, PurchaseOrderItem> orderItemMap = orderItems.stream()
                .collect(Collectors.toMap(PurchaseOrderItem::getId, item -> item));
        Map<Long, Long> receivedMap = inboundOrderItemAllocMapper
                .sumCompletedByPurchaseOrderIds(purchaseIds)
                .stream()
                .collect(Collectors.toMap(
                        InboundOrderItemAllocMapper.ReceivedQuantity::purchaseOrderItemId,
                        InboundOrderItemAllocMapper.ReceivedQuantity::quantity));

        validateAllocations(createDTO, orderItemMap, purchaseOrderMap, receivedMap);
        Set<Long> allocatedOrders = createDTO.getAllocations().stream()
                .map(item -> orderItemMap.get(item.getPurchaseOrderItemId()).getPurchaseOrderId()).collect(Collectors.toSet());
        if (!allocatedOrders.equals(new HashSet<>(purchaseIds)))
            throw new BusinessException(ErrorCode.BAD_REQUEST, "每个关联采购单都必须有本次入库明细");
        List<Long> productIds = createDTO.getAllocations().stream()
                .map(item -> orderItemMap.get(item.getPurchaseOrderItemId()).getProductId()).distinct().sorted().toList();
        productMapper.selectList(Wrappers.<Product>lambdaQuery().in(Product::getId, productIds)
                .orderByAsc(Product::getId).last("FOR UPDATE"));
        LocalDateTime inboundTime = parseInboundTime(createDTO.getInboundTime());

        InboundOrder inboundOrder = new InboundOrder();
        inboundOrder.setSupplierId(supplierOrder.getSupplierId());
        inboundOrder.setSupplierName(supplierOrder.getSupplierName());
        inboundOrder.setInboundType(InboundType.PURCHASE);
        inboundOrder.setStatus(InboundStatus.COMPLETED);
        inboundOrder.setInboundTime(inboundTime);
        inboundOrder.setVersion(0);
        inboundOrder.setInboundOrderNo(documentNumberService.next(DocumentNumberService.Kind.INBOUND, inboundTime));
        save(inboundOrder);

        // One product line per product, allocations are kept as the financial/business split.
        Map<Long, List<InboundOrderAllocationCreateDTO>> allocationsByProduct =
                createDTO.getAllocations().stream().collect(Collectors.groupingBy(
                        allocation -> orderItemMap.get(allocation.getPurchaseOrderItemId()).getProductId(),
                        LinkedHashMap::new, Collectors.toList()));
        Map<Long, InboundOrderItem> itemByProduct = new HashMap<>();
        for (Map.Entry<Long, List<InboundOrderAllocationCreateDTO>> entry : allocationsByProduct.entrySet()) {
            List<InboundOrderAllocationCreateDTO> allocations = entry.getValue();
            PurchaseOrderItem sample = orderItemMap.get(allocations.getFirst().getPurchaseOrderItemId());
            int quantity = allocations.stream().mapToInt(this::allocationQuantity).sum();
            InboundOrderItem item = new InboundOrderItem();
            item.setInboundOrderId(inboundOrder.getId());
            item.setProductId(entry.getKey());
            item.setBrand(sample.getBrand());
            item.setPartNo(sample.getPartNo());
            item.setPackageType(sample.getPackageType());
            item.setInboundQuantity(quantity);
            inboundOrderItemService.save(item);
            itemByProduct.put(entry.getKey(), item);
        }

        Map<Long, Long> receivedAfter = new HashMap<>(receivedMap);
        List<InboundOrderItemAlloc> allocations = new ArrayList<>();
        for (InboundOrderAllocationCreateDTO allocationDTO : createDTO.getAllocations()) {
            PurchaseOrderItem orderItem = orderItemMap.get(allocationDTO.getPurchaseOrderItemId());
            PurchaseOrder purchaseOrder = purchaseOrderMap.get(orderItem.getPurchaseOrderId());
            InboundOrderItem orderItemLine = itemByProduct.get(orderItem.getProductId());
            int quantity = allocationQuantity(allocationDTO);
            receivedAfter.merge(orderItem.getId(), (long) quantity, Long::sum);

            InboundOrderItemAlloc allocation = new InboundOrderItemAlloc();
            allocation.setInboundOrderId(inboundOrder.getId());
            allocation.setInboundOrderItemId(orderItemLine.getId());
            allocation.setPurchaseOrderId(purchaseOrder.getId());
            allocation.setPurchaseOrderNo(purchaseOrder.getPurchaseOrderNo());
            allocation.setPurchaseOrderItemId(orderItem.getId());
            allocation.setDateCode(allocationDTO.getDateCode().trim());
            allocation.setLotCode(allocationDTO.getLotCode().trim());
            allocation.setAllocatedQuantity(quantity);
            allocation.setUnitPrice(orderItem.getUnitPrice());
            // 入库明细保留含税口径快照，但口径取整张采购订单，而不是订单行。
            allocation.setTaxIncluded(purchaseOrder.getTaxIncluded());
            allocation.setTaxRate(orderItem.getTaxRate());
            allocation.setAmount(amount(orderItem.getUnitPrice(), quantity));
            allocation.setCurrency(DEFAULT_CURRENCY);
            inboundOrderItemAllocService.save(allocation);
            allocations.add(allocation);
        }
        createInventoryBatches(allocations, orderItemMap, inboundTime);

        for (PurchaseOrder purchaseOrder : purchaseOrders) {
            List<PurchaseOrderItem> items = orderItems.stream()
                    .filter(item -> item.getPurchaseOrderId().equals(purchaseOrder.getId())).toList();
            long totalReceivedAfter = items.stream().mapToLong(item -> receivedAfter.getOrDefault(item.getId(), 0L)).sum();
            purchaseOrder.setOrderStatus(resolveOrderStatus(items, totalReceivedAfter));
            if (!purchaseOrderService.updateById(purchaseOrder))
                throw new BusinessException(ErrorCode.CONFLICT, "采购订单已发生变化，请刷新后重试");
        }
        financeService.project("AP", inboundOrder.getId());
        InboundOrderVO result = inboundOrderConverter.toVO(inboundOrder);
        fillItems(List.of(result));
        return result;
    }

    /**
     * 同一入库单内按“产品 + Date Code + Lot Code + 含税单位成本”生成库存记录，不跨入库单汇总。
     */
    private void createInventoryBatches(
            List<InboundOrderItemAlloc> allocations,
            Map<Long, PurchaseOrderItem> orderItemMap,
            LocalDateTime inboundTime) {
        Map<InventoryBatchKey, List<InboundOrderItemAlloc>> groups = new LinkedHashMap<>();
        for (InboundOrderItemAlloc allocation : allocations) {
            PurchaseOrderItem orderItem = orderItemMap.get(allocation.getPurchaseOrderItemId());
            BigDecimal cost = taxInclusiveCost(allocation.getUnitPrice(), allocation.getTaxIncluded(), allocation.getTaxRate());
            InventoryBatchKey key = new InventoryBatchKey(
                    orderItem.getProductId(),
                    allocation.getDateCode(),
                    allocation.getLotCode(),
                    cost.toPlainString());
            groups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(allocation);
        }
        for (Map.Entry<InventoryBatchKey, List<InboundOrderItemAlloc>> entry : groups.entrySet()) {
            InventoryBatchKey key = entry.getKey();
            List<InboundOrderItemAlloc> rows = entry.getValue();
            InboundOrderItemAlloc first = rows.getFirst();
            int quantity = rows.stream().mapToInt(InboundOrderItemAlloc::getAllocatedQuantity).sum();
            Inventory inventory = new Inventory();
            inventory.setProductId(key.productId());
            inventory.setDateCode(key.dateCode());
            inventory.setLotCode(key.lotCode());
            inventory.setBatchNo(nextBatchNo(key.productId(), inboundTime));
            inventory.setInboundOrderItemId(first.getInboundOrderItemId());
            inventory.setUnitCost(new BigDecimal(key.unitCost()));
            inventory.setInitialQuantity(quantity);
            inventory.setQuantity(quantity);
            inventory.setReservedQuantity(0);
            inventory.setVersion(0);
            inventoryService.save(inventory);
        }
    }

    private void validateAllocations(
            InboundOrderCreateDTO createDTO,
            Map<Long, PurchaseOrderItem> orderItemMap,
            Map<Long, PurchaseOrder> purchaseOrderMap,
            Map<Long, Long> receivedMap) {
        Map<Long, Long> requestedByPurchaseItem = new HashMap<>();
        Set<String> allocationKeys = new HashSet<>();
        for (InboundOrderAllocationCreateDTO allocationDTO : createDTO.getAllocations()) {
            PurchaseOrderItem orderItem = orderItemMap.get(allocationDTO.getPurchaseOrderItemId());
            if (orderItem == null) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "入库产品不属于当前采购订单");
            }
            PurchaseOrder purchaseOrder = purchaseOrderMap.get(orderItem.getPurchaseOrderId());
            taxInclusiveCost(orderItem.getUnitPrice(), purchaseOrder.getTaxIncluded(), orderItem.getTaxRate());
            String dateCode = allocationDTO.getDateCode().trim();
            String lotCode = allocationDTO.getLotCode().trim();
            String key = allocationDTO.getPurchaseOrderItemId() + "|"
                    + dateCode.toLowerCase(Locale.ROOT) + '|' + lotCode.toLowerCase(Locale.ROOT);
            if (!allocationKeys.add(key)) {
                throw new BusinessException(
                        ErrorCode.BAD_REQUEST,
                        "产品 " + orderItem.getPartNo() + " 的批次明细不可重复");
            }
            long quantity = allocationQuantity(allocationDTO);
            if (quantity < 1 || quantity > Integer.MAX_VALUE) {
                throw new BusinessException(
                        ErrorCode.BAD_REQUEST,
                        "产品 " + orderItem.getPartNo() + " 的入库数量不正确");
            }
            requestedByPurchaseItem.merge(orderItem.getId(), quantity, Long::sum);
        }
        for (Map.Entry<Long, Long> entry : requestedByPurchaseItem.entrySet()) {
            PurchaseOrderItem orderItem = orderItemMap.get(entry.getKey());
            long remaining = orderItem.getQuantity() - receivedMap.getOrDefault(orderItem.getId(), 0L);
            if (entry.getValue() > remaining) {
                throw new BusinessException(
                        ErrorCode.BAD_REQUEST,
                        "产品 " + orderItem.getPartNo() + " 的入库数量不能超过待入库数量");
            }
        }
    }

    private int allocationQuantity(InboundOrderAllocationCreateDTO allocation) {
        return allocation.getAllocatedQuantity();
    }

    private record InventoryBatchKey(Long productId, String dateCode, String lotCode, String unitCost) {}

    private BigDecimal taxInclusiveCost(BigDecimal price, TaxIncluded taxIncluded, Integer taxRate) {
        if (price == null || price.signum() < 0 || taxIncluded == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请补全采购单价及是否含税，才能计算含税库存成本");
        }
        // 未税单价仅在填写税率时换算为含税成本；未填税率按未税金额入库。
        if (taxIncluded == TaxIncluded.EXCLUDED && taxRate != null) {
            if (taxRate < 0 || taxRate > 100) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "未税采购单价需填写 0 至 100 的税率");
            }
            price = price.multiply(BigDecimal.ONE.add(BigDecimal.valueOf(taxRate).movePointLeft(2)));
        }
        BigDecimal cost = price.setScale(4, RoundingMode.HALF_UP);
        if (cost.compareTo(new BigDecimal("99999999.9999")) > 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "含税库存成本超出允许范围");
        }
        return cost;
    }

    private BigDecimal amount(BigDecimal unitPrice, int quantity) {
        BigDecimal price = unitPrice == null ? BigDecimal.ZERO : unitPrice;
        return price.multiply(BigDecimal.valueOf(quantity)).setScale(4, RoundingMode.HALF_UP);
    }

    private LocalDateTime parseInboundTime(String value) {
        if (!StringUtils.hasText(value)) return LocalDateTime.now();
        try {
            return LocalDateTime.parse(value.trim().replace(' ', 'T'));
        } catch (DateTimeParseException ex) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "入库时间格式不正确");
        }
    }

    private PurchaseOrderStatus resolveOrderStatus(
            List<PurchaseOrderItem> orderItems, long totalReceivedAfter) {
        long totalQuantity = orderItems.stream()
                .filter(Objects::nonNull)
                .mapToLong(PurchaseOrderItem::getQuantity)
                .sum();
        return totalReceivedAfter >= totalQuantity
                ? PurchaseOrderStatus.COMPLETED
                : PurchaseOrderStatus.PARTIALLY_RECEIVED;
    }

    private String nextBatchNo(Long productId, LocalDateTime inboundTime) {
        String prefix = "BAT"
                + inboundTime.format(DateTimeFormatter.ofPattern("yyMMdd"))
                + String.format("%05d", productId);
        int nextSequence = inventoryService.lambdaQuery()
                .likeRight(Inventory::getBatchNo, prefix)
                .last("FOR UPDATE")
                .list()
                .stream()
                .map(Inventory::getBatchNo)
                .filter(batchNo -> batchNo.length() > prefix.length())
                .map(batchNo -> batchNo.substring(prefix.length()))
                .filter(suffix -> suffix.chars().allMatch(Character::isDigit))
                .mapToInt(suffix -> Integer.parseInt(suffix))
                .max()
                .orElse(0) + 1;
        if (nextSequence > 999) {
            throw new BusinessException(ErrorCode.CONFLICT, "该产品当日库存批次号已用完");
        }
        return prefix + String.format("%03d", nextSequence);
    }

    private void fillItems(List<InboundOrderVO> records) {
        if (CollectionUtils.isEmpty(records)) return;

        List<Long> orderIds = records.stream().map(InboundOrderVO::getId).toList();
        Map<Long, List<InboundOrderItem>> items = inboundOrderItemService.lambdaQuery()
                .in(InboundOrderItem::getInboundOrderId, orderIds)
                .orderByAsc(InboundOrderItem::getId)
                .list()
                .stream()
                .collect(Collectors.groupingBy(InboundOrderItem::getInboundOrderId));
        List<Long> itemIds = items.values().stream()
                .flatMap(List::stream)
                .map(InboundOrderItem::getId)
                .toList();
        Map<Long, List<InboundOrderItemAlloc>> allocations = itemIds.isEmpty() ? Map.of()
                : inboundOrderItemAllocService.lambdaQuery()
                        .in(InboundOrderItemAlloc::getInboundOrderItemId, itemIds)
                        .orderByAsc(InboundOrderItemAlloc::getId)
                        .list()
                        .stream()
                        .collect(Collectors.groupingBy(InboundOrderItemAlloc::getInboundOrderItemId));
        records.forEach(order -> {
            Map<Long, InboundPurchaseOrderVO> purchaseOrders = new TreeMap<>();
            items.getOrDefault(order.getId(), List.of()).stream()
                    .flatMap(item -> allocations.getOrDefault(item.getId(), List.of()).stream())
                    .forEach(allocation -> purchaseOrders.computeIfAbsent(allocation.getPurchaseOrderId(), id -> {
                        var vo = new InboundPurchaseOrderVO();
                        vo.setPurchaseOrderId(id);
                        vo.setPurchaseOrderNo(allocation.getPurchaseOrderNo());
                        return vo;
                    }));
            order.setPurchaseOrders(List.copyOf(purchaseOrders.values()));
            order.setItems(
                items.getOrDefault(order.getId(), List.of()).stream()
                        .map(item -> {
                            var itemVO = inboundOrderConverter.toItemVO(item);
                            itemVO.setAllocations(
                                    allocations.getOrDefault(item.getId(), List.of()).stream()
                                            .map(inboundOrderConverter::toAllocVO)
                                            .toList());
                            return itemVO;
                        })
                        .toList());
        });
    }
}
