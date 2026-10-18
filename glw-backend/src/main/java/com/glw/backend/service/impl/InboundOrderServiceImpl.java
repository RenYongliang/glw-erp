package com.glw.backend.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.glw.backend.common.BusinessException;
import com.glw.backend.common.ErrorCode;
import com.glw.backend.converter.InboundOrderConverter;
import com.glw.backend.mapper.InboundOrderItemMapper;
import com.glw.backend.mapper.InboundOrderMapper;
import com.glw.backend.mapper.ProductMapper;
import com.glw.backend.model.dto.InboundOrderBatchCreateDTO;
import com.glw.backend.model.dto.InboundOrderCreateDTO;
import com.glw.backend.model.dto.InboundOrderItemCreateDTO;
import com.glw.backend.model.dto.InboundOrderPageDTO;
import com.glw.backend.model.entity.InboundOrderBatchItem;
import com.glw.backend.model.entity.Inventory;
import com.glw.backend.model.entity.InboundOrder;
import com.glw.backend.model.entity.InboundOrderItem;
import com.glw.backend.model.entity.InboundOrderPurchaseOrder;
import com.glw.backend.model.entity.Product;
import com.glw.backend.model.entity.PurchaseOrder;
import com.glw.backend.model.entity.PurchaseOrderItem;
import com.glw.backend.model.enums.InboundStatus;
import com.glw.backend.model.enums.InboundType;
import com.glw.backend.model.enums.PurchaseOrderStatus;
import com.glw.backend.model.vo.InboundOrderBatchItemVO;
import com.glw.backend.model.vo.InboundOrderVO;
import com.glw.backend.model.vo.InboundPurchaseOrderVO;
import com.glw.backend.service.InboundOrderBatchItemService;
import com.glw.backend.service.DocumentNumberService;
import com.glw.backend.service.InboundOrderItemService;
import com.glw.backend.service.InboundOrderService;
import com.glw.backend.service.InboundOrderPurchaseOrderService;
import com.glw.backend.service.InventoryService;
import com.glw.backend.service.PurchaseOrderItemService;
import com.glw.backend.service.PurchaseOrderService;

import lombok.AllArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** 入库表基础服务实现。 */
@Service
@Profile("!mock")
@AllArgsConstructor
public class InboundOrderServiceImpl extends ServiceImpl<InboundOrderMapper, InboundOrder>
        implements InboundOrderService {

    private final InboundOrderItemService inboundOrderItemService;
    private final InboundOrderItemMapper inboundOrderItemMapper;
    private final InboundOrderBatchItemService inboundOrderBatchItemService;
    private final PurchaseOrderService purchaseOrderService;
    private final PurchaseOrderItemService purchaseOrderItemService;
    private final InventoryService inventoryService;
    private final InboundOrderConverter inboundOrderConverter;
    private final InboundOrderPurchaseOrderService relationService;
    private final ProductMapper productMapper;
    private final DocumentNumberService documentNumberService;

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
                    .filter(item -> item.getPurchaseOrderId().equals(purchaseOrderId)).toList());
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
                        "EXISTS (SELECT 1 FROM inbound_order_purchase_order rel WHERE rel.inbound_order_id = inbound_order.id AND rel.purchase_order_id = {0})", pageDTO.getPurchaseOrderId())
                .apply(StringUtils.hasText(pageDTO.getPurchaseOrderNo()),
                        "EXISTS (SELECT 1 FROM inbound_order_purchase_order rel WHERE rel.inbound_order_id = inbound_order.id AND rel.purchase_order_no = {0})", pageDTO.getPurchaseOrderNo())
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
        Map<Long, Long> receivedMap = inboundOrderItemMapper
                .sumCompletedByPurchaseOrderIds(purchaseIds)
                .stream()
                .collect(Collectors.toMap(
                        InboundOrderItemMapper.ReceivedQuantity::purchaseOrderItemId,
                        InboundOrderItemMapper.ReceivedQuantity::quantity));

        validateItems(createDTO, orderItemMap, receivedMap);
        Set<Long> allocatedOrders = createDTO.getItems().stream()
                .map(item -> orderItemMap.get(item.getPurchaseOrderItemId()).getPurchaseOrderId()).collect(Collectors.toSet());
        if (!allocatedOrders.equals(new HashSet<>(purchaseIds)))
            throw new BusinessException(ErrorCode.BAD_REQUEST, "每个关联采购单都必须有本次入库明细");
        List<Long> productIds = createDTO.getItems().stream()
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
        for (PurchaseOrder order : purchaseOrders) {
            var relation = new InboundOrderPurchaseOrder();
            relation.setInboundOrderId(inboundOrder.getId());
            relation.setPurchaseOrderId(order.getId());
            relation.setPurchaseOrderNo(order.getPurchaseOrderNo());
            relationService.save(relation);
        }

        Map<Long, Long> receivedAfter = new java.util.HashMap<>(receivedMap);
        for (InboundOrderItemCreateDTO itemDTO : createDTO.getItems()) {
            PurchaseOrderItem orderItem = orderItemMap.get(itemDTO.getPurchaseOrderItemId());
            PurchaseOrder purchaseOrder = purchaseOrderMap.get(orderItem.getPurchaseOrderId());
            int itemQuantity = itemDTO.getBatches().stream()
                    .mapToInt(batch -> batch.getInboundQuantity())
                    .sum();
            receivedAfter.merge(orderItem.getId(), (long) itemQuantity, Long::sum);

            InboundOrderItem inboundOrderItem = new InboundOrderItem();
            inboundOrderItem.setInboundOrderId(inboundOrder.getId());
            inboundOrderItem.setPurchaseOrderId(purchaseOrder.getId());
            inboundOrderItem.setPurchaseOrderNo(purchaseOrder.getPurchaseOrderNo());
            inboundOrderItem.setPurchaseOrderItemId(orderItem.getId());
            inboundOrderItem.setInboundQuantity(itemQuantity);
            inboundOrderItemService.save(inboundOrderItem);

            for (InboundOrderBatchCreateDTO batchDTO : itemDTO.getBatches()) {
                InboundOrderBatchItem batchItem = new InboundOrderBatchItem();
                batchItem.setInboundOrderId(inboundOrder.getId());
                batchItem.setInboundOrderItemId(inboundOrderItem.getId());
                batchItem.setPurchaseOrderId(purchaseOrder.getId());
                batchItem.setPurchaseOrderNo(purchaseOrder.getPurchaseOrderNo());
                batchItem.setPurchaseOrderItemId(orderItem.getId());
                batchItem.setProductId(orderItem.getProductId());
                batchItem.setDateCode(batchDTO.getDateCode().trim());
                batchItem.setLotCode(batchDTO.getLotCode().trim());
                batchItem.setInboundQuantity(batchDTO.getInboundQuantity());
                inboundOrderBatchItemService.save(batchItem);

                Inventory inventory = new Inventory();
                inventory.setProductId(orderItem.getProductId());
                inventory.setDateCode(batchItem.getDateCode());
                inventory.setLotCode(batchItem.getLotCode());
                inventory.setBatchNo(nextBatchNo(orderItem.getProductId(), inboundTime));
                inventory.setInboundOrderItemId(inboundOrderItem.getId());
                inventory.setInboundOrderBatchItemId(batchItem.getId());
                inventory.setUnitCost(orderItem.getUnitPrice());
                inventory.setInitialQuantity(batchItem.getInboundQuantity());
                inventory.setQuantity(batchItem.getInboundQuantity());
                inventory.setReservedQuantity(0);
                inventory.setVersion(0);
                inventoryService.save(inventory);

            }
        }

        for (PurchaseOrder purchaseOrder : purchaseOrders) {
            List<PurchaseOrderItem> items = orderItems.stream()
                    .filter(item -> item.getPurchaseOrderId().equals(purchaseOrder.getId())).toList();
            long totalReceivedAfter = items.stream().mapToLong(item -> receivedAfter.getOrDefault(item.getId(), 0L)).sum();
            purchaseOrder.setOrderStatus(resolveOrderStatus(items, totalReceivedAfter));
            if (!purchaseOrderService.updateById(purchaseOrder))
                throw new BusinessException(ErrorCode.CONFLICT, "采购订单已发生变化，请刷新后重试");
        }
        InboundOrderVO result = inboundOrderConverter.toVO(inboundOrder);
        fillItems(List.of(result));
        return result;
    }

    private void validateItems(
            InboundOrderCreateDTO createDTO,
            Map<Long, PurchaseOrderItem> orderItemMap,
            Map<Long, Long> receivedMap) {
        Set<Long> itemIds = new HashSet<>();
        for (InboundOrderItemCreateDTO itemDTO : createDTO.getItems()) {
            if (!itemIds.add(itemDTO.getPurchaseOrderItemId())) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "入库产品明细不可重复");
            }
            PurchaseOrderItem orderItem = orderItemMap.get(itemDTO.getPurchaseOrderItemId());
            if (orderItem == null) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "入库产品不属于当前采购订单");
            }

            long quantity = itemDTO.getBatches().stream()
                    .mapToLong(batch -> batch.getInboundQuantity())
                    .sum();
            long remaining = orderItem.getQuantity() - receivedMap.getOrDefault(orderItem.getId(), 0L);
            if (quantity < 1 || quantity > Integer.MAX_VALUE || quantity > remaining) {
                throw new BusinessException(
                        ErrorCode.BAD_REQUEST,
                        "产品 " + orderItem.getPartNo() + " 的入库数量不能超过待入库数量");
            }

            Set<String> batchKeys = new HashSet<>();
            for (InboundOrderBatchCreateDTO batch : itemDTO.getBatches()) {
                if (!batchKeys.add(batch.getDateCode().trim() + '|' + batch.getLotCode().trim())) {
                    throw new BusinessException(
                            ErrorCode.BAD_REQUEST,
                            "产品 " + orderItem.getPartNo() + " 的批次明细不可重复");
                }
            }
        }
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
        Map<Long, List<InboundOrderPurchaseOrder>> relations = relationService.lambdaQuery()
                .in(InboundOrderPurchaseOrder::getInboundOrderId, orderIds)
                .orderByAsc(InboundOrderPurchaseOrder::getPurchaseOrderId).list().stream()
                .collect(Collectors.groupingBy(InboundOrderPurchaseOrder::getInboundOrderId));
        records.forEach(order -> order.setPurchaseOrders(relations.getOrDefault(order.getId(), List.of())
                .stream().map(relation -> {
                    var vo = new InboundPurchaseOrderVO();
                    vo.setPurchaseOrderId(relation.getPurchaseOrderId());
                    vo.setPurchaseOrderNo(relation.getPurchaseOrderNo());
                    return vo;
                }).toList()));
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
        List<Long> purchaseOrderItemIds = items.values().stream()
                .flatMap(List::stream)
                .map(InboundOrderItem::getPurchaseOrderItemId)
                .distinct()
                .toList();
        Map<Long, List<InboundOrderBatchItem>> batches = itemIds.isEmpty() ? Map.of()
                : inboundOrderBatchItemService.lambdaQuery()
                        .in(InboundOrderBatchItem::getInboundOrderItemId, itemIds)
                        .orderByAsc(InboundOrderBatchItem::getId)
                        .list()
                        .stream()
                        .collect(Collectors.groupingBy(InboundOrderBatchItem::getInboundOrderItemId));
        Map<Long, PurchaseOrderItem> purchaseItems = purchaseOrderItemIds.isEmpty() ? Map.of()
                : purchaseOrderItemService.lambdaQuery()
                        .in(PurchaseOrderItem::getId, purchaseOrderItemIds)
                        .list()
                        .stream()
                        .collect(Collectors.toMap(PurchaseOrderItem::getId, item -> item));
        records.forEach(order -> order.setItems(
                items.getOrDefault(order.getId(), List.of()).stream()
                        .map(item -> {
                            var itemVO = inboundOrderConverter.toItemVO(item);
                            PurchaseOrderItem source = purchaseItems.get(item.getPurchaseOrderItemId());
                            if (source != null) {
                                itemVO.setProductId(source.getProductId());
                                itemVO.setBrand(source.getBrand());
                                itemVO.setPartNo(source.getPartNo());
                                itemVO.setPackageType(source.getPackageType());
                                itemVO.setUnitPrice(source.getUnitPrice());
                            }
                            List<InboundOrderBatchItemVO> batchVOs = batches
                                    .getOrDefault(item.getId(), List.of())
                                    .stream()
                                    .map(inboundOrderConverter::toBatchItemVO)
                                    .toList();
                            if (batchVOs.isEmpty()) {
                                InboundOrderBatchItemVO fallback = new InboundOrderBatchItemVO();
                                fallback.setInboundOrderItemId(item.getId());
                                fallback.setProductId(itemVO.getProductId());
                                fallback.setDateCode("");
                                fallback.setLotCode("");
                                fallback.setInboundQuantity(item.getInboundQuantity());
                                batchVOs = List.of(fallback);
                            }
                            itemVO.setBatches(batchVOs);
                            return itemVO;
                        })
                        .toList()));
    }
}
