package com.glw.backend.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.glw.backend.common.BusinessException;
import com.glw.backend.common.ErrorCode;
import com.glw.backend.converter.InboundOrderConverter;
import com.glw.backend.mapper.InboundOrderItemMapper;
import com.glw.backend.mapper.InboundOrderMapper;
import com.glw.backend.model.dto.InboundOrderBatchCreateDTO;
import com.glw.backend.model.dto.InboundOrderCreateDTO;
import com.glw.backend.model.dto.InboundOrderItemCreateDTO;
import com.glw.backend.model.dto.InboundOrderPageDTO;
import com.glw.backend.model.entity.InboundOrderBatchItem;
import com.glw.backend.model.entity.Inventory;
import com.glw.backend.model.entity.InboundOrder;
import com.glw.backend.model.entity.InboundOrderItem;
import com.glw.backend.model.entity.PurchaseOrder;
import com.glw.backend.model.entity.PurchaseOrderItem;
import com.glw.backend.model.enums.InboundStatus;
import com.glw.backend.model.enums.InboundType;
import com.glw.backend.model.enums.PurchaseOrderStatus;
import com.glw.backend.model.vo.InboundOrderBatchItemVO;
import com.glw.backend.model.vo.InboundOrderVO;
import com.glw.backend.service.InboundOrderBatchItemService;
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

    @Override
    public IPage<InboundOrderVO> pageInboundOrder(InboundOrderPageDTO pageDTO) {
        Page<InboundOrder> page = lambdaQuery()
                .eq(pageDTO.getId() != null, InboundOrder::getId, pageDTO.getId())
                .eq(pageDTO.getPurchaseOrderId() != null, InboundOrder::getPurchaseOrderId, pageDTO.getPurchaseOrderId())
                .eq(StringUtils.hasText(pageDTO.getPurchaseOrderNo()), InboundOrder::getPurchaseOrderNo, pageDTO.getPurchaseOrderNo())
                .eq(pageDTO.getSupplierId() != null, InboundOrder::getSupplierId, pageDTO.getSupplierId())
                .like(StringUtils.hasText(pageDTO.getSupplierName()), InboundOrder::getSupplierName, pageDTO.getSupplierName())
                .eq(pageDTO.getInboundType() != null, InboundOrder::getInboundType, pageDTO.getInboundType())
                .eq(pageDTO.getStatus() != null, InboundOrder::getStatus, pageDTO.getStatus())
                .ge(StringUtils.hasText(pageDTO.getInboundTimeStart()), InboundOrder::getInboundTimeStart, pageDTO.getInboundTimeStart())
                .le(StringUtils.hasText(pageDTO.getInboundTimeEnd()), InboundOrder::getInboundTimeEnd, pageDTO.getInboundTimeEnd())
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
        PurchaseOrder purchaseOrder = purchaseOrderService.getById(createDTO.getPurchaseOrderId());
        if (purchaseOrder == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "采购订单不存在");
        }

        List<PurchaseOrderItem> orderItems = purchaseOrderItemService.lambdaQuery()
                .eq(PurchaseOrderItem::getPurchaseOrderId, purchaseOrder.getId())
                .orderByAsc(PurchaseOrderItem::getId)
                .list();
        Map<Long, PurchaseOrderItem> orderItemMap = orderItems.stream()
                .collect(Collectors.toMap(PurchaseOrderItem::getId, item -> item));
        Map<Long, Long> receivedMap = inboundOrderItemMapper
                .sumCompletedByPurchaseOrderIds(List.of(purchaseOrder.getId()))
                .stream()
                .collect(Collectors.toMap(
                        InboundOrderItemMapper.ReceivedQuantity::purchaseOrderItemId,
                        InboundOrderItemMapper.ReceivedQuantity::quantity));

        validateItems(createDTO, orderItemMap, receivedMap);
        LocalDateTime inboundTime = parseInboundTime(createDTO.getInboundTime());

        InboundOrder inboundOrder = new InboundOrder();
        inboundOrder.setPurchaseOrderId(purchaseOrder.getId());
        inboundOrder.setPurchaseOrderNo(purchaseOrder.getPurchaseOrderNo());
        inboundOrder.setSupplierId(purchaseOrder.getSupplierId());
        inboundOrder.setSupplierName(purchaseOrder.getSupplierName());
        inboundOrder.setInboundType(InboundType.PURCHASE);
        inboundOrder.setStatus(InboundStatus.COMPLETED);
        inboundOrder.setInboundTime(inboundTime);
        inboundOrder.setVersion(0);
        save(inboundOrder);

        InboundOrderVO result = inboundOrderConverter.toVO(inboundOrder);
        long totalReceivedAfter = receivedMap.values().stream().mapToLong(Long::longValue).sum();
        for (InboundOrderItemCreateDTO itemDTO : createDTO.getItems()) {
            PurchaseOrderItem orderItem = orderItemMap.get(itemDTO.getPurchaseOrderItemId());
            int itemQuantity = itemDTO.getBatches().stream()
                    .mapToInt(batch -> batch.getInboundQuantity())
                    .sum();
            totalReceivedAfter += itemQuantity;

            InboundOrderItem inboundOrderItem = new InboundOrderItem();
            inboundOrderItem.setInboundOrderId(inboundOrder.getId());
            inboundOrderItem.setPurchaseOrderId(purchaseOrder.getId());
            inboundOrderItem.setPurchaseOrderNo(purchaseOrder.getPurchaseOrderNo());
            inboundOrderItem.setPurchaseOrderItemId(orderItem.getId());
            inboundOrderItem.setInboundQuantity(itemQuantity);
            inboundOrderItemService.save(inboundOrderItem);

            var itemVO = inboundOrderConverter.toItemVO(inboundOrderItem);
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
                inventory.setBatchNo(nextBatchNo(orderItem.getProductId(), inboundTime));
                inventory.setInboundOrderItemId(inboundOrderItem.getId());
                inventory.setDateCode(batchItem.getDateCode());
                inventory.setLotCode(batchItem.getLotCode());
                inventory.setInboundOrderBatchItemId(batchItem.getId());
                inventory.setUnitCost(orderItem.getUnitPrice());
                inventory.setInitialQuantity(batchItem.getInboundQuantity());
                inventory.setQuantity(batchItem.getInboundQuantity());
                inventory.setReservedQuantity(0);
                inventory.setVersion(0);
                inventoryService.save(inventory);

                itemVO.getBatches().add(inboundOrderConverter.toBatchItemVO(batchItem));
            }
            result.getItems().add(itemVO);
        }

        purchaseOrder.setOrderStatus(resolveOrderStatus(orderItems, totalReceivedAfter));
        if (!purchaseOrderService.updateById(purchaseOrder)) {
            throw new BusinessException(ErrorCode.CONFLICT, "采购订单已发生变化，请刷新后重试");
        }
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
            if (quantity > remaining) {
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
        int totalQuantity = orderItems.stream()
                .filter(Objects::nonNull)
                .mapToInt(PurchaseOrderItem::getQuantity)
                .sum();
        return totalReceivedAfter >= totalQuantity
                ? PurchaseOrderStatus.COMPLETED
                : PurchaseOrderStatus.PARTIALLY_RECEIVED;
    }

    private synchronized String nextBatchNo(Long productId, LocalDateTime inboundTime) {
        String prefix = "BAT"
                + inboundTime.format(DateTimeFormatter.ofPattern("yyMMdd"))
                + String.format("%05d", productId);
        int nextSequence = inventoryService.lambdaQuery()
                .likeRight(Inventory::getBatchNo, prefix)
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
        Map<Long, Long> productIds = purchaseOrderItemIds.isEmpty() ? Map.of()
                : purchaseOrderItemService.lambdaQuery()
                        .in(PurchaseOrderItem::getId, purchaseOrderItemIds)
                        .list()
                        .stream()
                        .collect(Collectors.toMap(PurchaseOrderItem::getId, PurchaseOrderItem::getProductId));
        records.forEach(order -> order.setItems(
                items.getOrDefault(order.getId(), List.of()).stream()
                        .map(item -> {
                            var itemVO = inboundOrderConverter.toItemVO(item);
                            List<InboundOrderBatchItemVO> batchVOs = batches
                                    .getOrDefault(item.getId(), List.of())
                                    .stream()
                                    .map(inboundOrderConverter::toBatchItemVO)
                                    .toList();
                            if (batchVOs.isEmpty()) {
                                InboundOrderBatchItemVO fallback = new InboundOrderBatchItemVO();
                                fallback.setInboundOrderItemId(item.getId());
                                fallback.setProductId(productIds.get(item.getPurchaseOrderItemId()));
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
