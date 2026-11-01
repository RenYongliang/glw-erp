package com.glw.backend.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.glw.backend.common.BusinessException;
import com.glw.backend.common.ErrorCode;
import com.glw.backend.converter.PurchaseOrderConverter;
import com.glw.backend.mapper.PurchaseOrderMapper;
import com.glw.backend.mapper.InboundOrderItemAllocMapper;
import com.glw.backend.mapper.ProductMapper;
import com.glw.backend.mapper.SupplierMapper;
import com.glw.backend.model.entity.InboundOrderItemAlloc;
import com.glw.backend.model.entity.Product;
import com.glw.backend.model.entity.Supplier;
import com.glw.backend.model.dto.PurchaseOrderCreateDTO;
import com.glw.backend.model.dto.PurchaseOrderItemCreateDTO;
import com.glw.backend.model.dto.PurchaseOrderPageDTO;
import com.glw.backend.model.entity.PurchaseOrder;
import com.glw.backend.model.entity.PurchaseOrderItem;
import com.glw.backend.model.enums.PurchaseOrderStatus;
import com.glw.backend.model.enums.PurchasePaymentStatus;
import com.glw.backend.model.vo.PurchaseOrderVO;
import com.glw.backend.service.PurchaseOrderItemService;
import com.glw.backend.service.PurchaseOrderService;
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
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 采购订单表基础服务实现。 */
@Service
@Profile("!mock")
@AllArgsConstructor
public class PurchaseOrderServiceImpl extends ServiceImpl<PurchaseOrderMapper, PurchaseOrder>
        implements PurchaseOrderService {

    private final PurchaseOrderItemService purchaseOrderItemService;
    private final PurchaseOrderConverter purchaseOrderConverter;
    private final InboundOrderItemAllocMapper inboundOrderItemAllocMapper;
    private final SupplierMapper supplierMapper;
    private final ProductMapper productMapper;
    private final DocumentNumberService documentNumberService;
    private final com.glw.backend.mapper.FinanceOrderStatusMapper financeMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean save(PurchaseOrder order) {
        if (order.getId() == null) {
            if (order.getOrderTime() == null) order.setOrderTime(LocalDateTime.now());
            order.setPurchaseOrderNo(documentNumberService.next(
                    DocumentNumberService.Kind.PURCHASE, order.getOrderTime()));
        }
        return super.save(order);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PurchaseOrderVO createPurchaseOrder(PurchaseOrderCreateDTO createDTO) {
        Supplier supplier = requireSupplier(createDTO.getSupplierId());
        Map<Long, Product> products = requireProducts(createDTO.getItems());
        LocalDateTime orderTime = parseOrderTime(createDTO.getOrderTime(), LocalDateTime.now());

        PurchaseOrder order = new PurchaseOrder();
        order.setOrderTime(orderTime);
        order.setSupplierId(supplier.getId());
        order.setSupplierName(supplier.getName());
        order.setDeliveryTime(parseOrderTime(createDTO.getDeliveryTime(), orderTime));
        // 业务上不设审批环节，新建订单直接进入待收货。
        order.setOrderStatus(PurchaseOrderStatus.PENDING_RECEIPT);
        order.setPaymentStatus(PurchasePaymentStatus.UNPAID);
        order.setRemark(trimRemark(createDTO.getRemark()));
        order.setVersion(0);
        save(order);
        saveItems(order, createDTO.getItems(), products);

        PurchaseOrderVO result = purchaseOrderConverter.toVO(order);
        fillItems(List.of(result));
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PurchaseOrderVO updatePurchaseOrder(Long id, PurchaseOrderCreateDTO createDTO) {
        PurchaseOrder order = getById(id);
        if (order == null) throw new BusinessException(ErrorCode.NOT_FOUND, "采购订单不存在");
        if (createDTO.getVersion() != null && !createDTO.getVersion().equals(order.getVersion()))
            throw new BusinessException(ErrorCode.CONFLICT, "采购订单已发生变化，请刷新后重试");
        if (inboundOrderItemAllocMapper.selectCount(Wrappers.<InboundOrderItemAlloc>lambdaQuery()
                .eq(InboundOrderItemAlloc::getPurchaseOrderId, id)) > 0)
            throw new BusinessException(ErrorCode.CONFLICT, "已有入库记录的采购订单不能修改");

        Supplier supplier = requireSupplier(createDTO.getSupplierId());
        Map<Long, Product> products = requireProducts(createDTO.getItems());
        LocalDateTime orderTime = parseOrderTime(createDTO.getOrderTime(), order.getOrderTime());

        order.setOrderTime(orderTime);
        order.setSupplierId(supplier.getId());
        order.setSupplierName(supplier.getName());
        order.setDeliveryTime(parseOrderTime(createDTO.getDeliveryTime(), orderTime));
        order.setRemark(trimRemark(createDTO.getRemark()));
        if (!updateById(order))
            throw new BusinessException(ErrorCode.CONFLICT, "采购订单已发生变化，请刷新后重试");

        purchaseOrderItemService.lambdaUpdate()
                .eq(PurchaseOrderItem::getPurchaseOrderId, id)
                .remove();
        saveItems(order, createDTO.getItems(), products);

        PurchaseOrderVO result = purchaseOrderConverter.toVO(order);
        fillItems(List.of(result));
        return result;
    }

    private Supplier requireSupplier(Long supplierId) {
        Supplier supplier = supplierMapper.selectById(supplierId);
        if (supplier == null) throw new BusinessException(ErrorCode.NOT_FOUND, "供应商不存在");
        if (Boolean.FALSE.equals(supplier.getEnabled()))
            throw new BusinessException(ErrorCode.BAD_REQUEST, "供应商已停用，不能保存采购订单");
        return supplier;
    }

    private Map<Long, Product> requireProducts(List<PurchaseOrderItemCreateDTO> items) {
        List<Long> productIds = items.stream().map(PurchaseOrderItemCreateDTO::getProductId).distinct().toList();
        if (productIds.size() != items.size())
            throw new BusinessException(ErrorCode.BAD_REQUEST, "同一采购订单中产品不可重复");
        Map<Long, Product> products = productMapper.selectByIds(productIds).stream()
                .collect(Collectors.toMap(Product::getId, product -> product));
        for (Long productId : productIds) {
            if (!products.containsKey(productId))
                throw new BusinessException(ErrorCode.NOT_FOUND, "采购产品不存在或已删除");
        }
        return products;
    }

    /** 产品品牌、型号、封装以产品档案为准，避免前端快照过期后写入不一致数据。 */
    private void saveItems(
            PurchaseOrder order, List<PurchaseOrderItemCreateDTO> items, Map<Long, Product> products) {
        List<PurchaseOrderItem> rows = new ArrayList<>(items.size());
        for (PurchaseOrderItemCreateDTO itemDTO : items) {
            Product product = products.get(itemDTO.getProductId());
            BigDecimal price = itemDTO.getUnitPrice().setScale(4, RoundingMode.HALF_UP);
            PurchaseOrderItem row = new PurchaseOrderItem();
            row.setPurchaseOrderId(order.getId());
            row.setPurchaseOrderNo(order.getPurchaseOrderNo());
            row.setProductId(product.getId());
            row.setBrand(product.getBrand());
            row.setPartNo(product.getPartNo());
            row.setPackageType(product.getPackageType());
            row.setUnitPrice(price);
            row.setTaxIncluded(itemDTO.getTaxIncluded());
            row.setTaxRate(itemDTO.getTaxRate());
            row.setQuantity(itemDTO.getQuantity());
            row.setAmount(price.multiply(BigDecimal.valueOf(itemDTO.getQuantity()))
                    .setScale(4, RoundingMode.HALF_UP));
            rows.add(row);
        }
        purchaseOrderItemService.saveBatch(rows);
    }

    private LocalDateTime parseOrderTime(String value, LocalDateTime fallback) {
        if (!StringUtils.hasText(value)) return fallback;
        String text = value.trim();
        try {
            return text.length() <= 10
                    ? LocalDate.parse(text).atStartOfDay()
                    : LocalDateTime.parse(text.replace(' ', 'T'));
        } catch (DateTimeParseException exception) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "采购订单日期格式不正确");
        }
    }

    private String trimRemark(String remark) {
        if (!StringUtils.hasText(remark)) return null;
        String text = remark.trim();
        if (text.length() > 256)
            throw new BusinessException(ErrorCode.BAD_REQUEST, "备注不能超过 256 字");
        return text;
    }

    @Override
    public IPage<PurchaseOrderVO> pagePurchaseOrder(PurchaseOrderPageDTO pageDTO) {
        boolean filterBrand = StringUtils.hasText(pageDTO.getBrand());
        boolean filterPartNo = StringUtils.hasText(pageDTO.getPartNo());
        boolean filterPackageType = StringUtils.hasText(pageDTO.getPackageType());

        List<Long> filterOrderIds = null;
        if (filterBrand || filterPartNo || filterPackageType) {
            filterOrderIds = findOrderIdsByItem(pageDTO, filterBrand, filterPartNo, filterPackageType);
            if (CollectionUtils.isEmpty(filterOrderIds)) return emptyPage(pageDTO);
        }
        List<Long> paymentOrderIds = pageDTO.getPaymentStatus() != null
                ? financeMapper.orderIdsByStatus("AP", pageDTO.getPaymentStatus().getCode()) : null;
        if (paymentOrderIds != null && paymentOrderIds.isEmpty()) paymentOrderIds = List.of(-1L);

        Page<PurchaseOrder> page = lambdaQuery()
                .in(paymentOrderIds != null, PurchaseOrder::getId, paymentOrderIds)
                .in(filterOrderIds != null, PurchaseOrder::getId, filterOrderIds)
                .eq(pageDTO.getId() != null, PurchaseOrder::getId, pageDTO.getId())
                .eq(StringUtils.hasText(pageDTO.getPurchaseOrderNo()), PurchaseOrder::getPurchaseOrderNo, pageDTO.getPurchaseOrderNo())
                .eq(pageDTO.getSupplierId() != null, PurchaseOrder::getSupplierId, pageDTO.getSupplierId())
                .like(StringUtils.hasText(pageDTO.getSupplierName()), PurchaseOrder::getSupplierName, pageDTO.getSupplierName())
                .eq(pageDTO.getOrderStatus() != null, PurchaseOrder::getOrderStatus, pageDTO.getOrderStatus())
                .ge(StringUtils.hasText(pageDTO.getOrderTimeStart()), PurchaseOrder::getOrderTimeStart, pageDTO.getOrderTimeStart())
                .le(StringUtils.hasText(pageDTO.getOrderTimeEnd()), PurchaseOrder::getOrderTimeEnd, pageDTO.getOrderTimeEnd())
                .orderByDesc(PurchaseOrder::getOrderTime)
                .orderByDesc(PurchaseOrder::getId)
                .page(pageDTO.toPage());

        IPage<PurchaseOrderVO> result = page.convert(purchaseOrderConverter::toVO);
        fillItems(result.getRecords());
        return result;
    }

    private List<Long> findOrderIdsByItem(
            PurchaseOrderPageDTO pageDTO, boolean filterBrand, boolean filterPartNo, boolean filterPackageType) {
        return purchaseOrderItemService.lambdaQuery()
                .eq(filterBrand, PurchaseOrderItem::getBrand, pageDTO.getBrand())
                .eq(filterPartNo, PurchaseOrderItem::getPartNo, pageDTO.getPartNo())
                .eq(filterPackageType, PurchaseOrderItem::getPackageType, pageDTO.getPackageType())
                .list()
                .stream()
                .map(PurchaseOrderItem::getPurchaseOrderId)
                .distinct()
                .toList();
    }

    private Page<PurchaseOrderVO> emptyPage(PurchaseOrderPageDTO pageDTO) {
        Page<PurchaseOrderVO> page = pageDTO.toPage();
        page.setRecords(List.of());
        page.setTotal(0);
        return page;
    }

    private void fillItems(List<PurchaseOrderVO> records) {
        if (CollectionUtils.isEmpty(records)) return;

        List<Long> orderIds = records.stream().map(PurchaseOrderVO::getId).toList();
        var financialStatuses = financeMapper.orderStatuses("AP", orderIds).stream().collect(Collectors.toMap(
                com.glw.backend.finance.FinanceOrderStatus.OrderStatus::orderId,
                com.glw.backend.finance.FinanceOrderStatus.OrderStatus::paymentStatus));
        records.forEach(order -> {
            if (financialStatuses.containsKey(order.getId())) order.setPaymentStatus(
                    com.glw.backend.model.enums.PurchasePaymentStatus.fromCode(financialStatuses.get(order.getId())));
        });
        List<Long> supplierIds = records.stream().map(PurchaseOrderVO::getSupplierId)
                .filter(java.util.Objects::nonNull).distinct().toList();
        Map<Long, Supplier> suppliers = supplierIds.isEmpty() ? Map.of() : supplierMapper.selectByIds(supplierIds)
                .stream().collect(Collectors.toMap(Supplier::getId, supplier -> supplier));
        records.forEach(order -> {
            Supplier supplier = order.getSupplierId() == null ? null : suppliers.get(order.getSupplierId());
            if (supplier != null) {
                order.setSupplierName(supplier.getName());
                order.setSupplierShortName(supplier.getShortName());
            }
        });
        Map<Long, Long> received = inboundOrderItemAllocMapper.sumCompletedByPurchaseOrderIds(orderIds)
                .stream()
                .collect(Collectors.toMap(InboundOrderItemAllocMapper.ReceivedQuantity::purchaseOrderItemId,
                        InboundOrderItemAllocMapper.ReceivedQuantity::quantity));
        Map<Long, List<PurchaseOrderItem>> items = purchaseOrderItemService.lambdaQuery()
                .in(PurchaseOrderItem::getPurchaseOrderId, orderIds)
                .orderByAsc(PurchaseOrderItem::getId)
                .list()
                .stream()
                .collect(Collectors.groupingBy(PurchaseOrderItem::getPurchaseOrderId));
        records.forEach(order -> order.setItems(
                items.getOrDefault(order.getId(), List.of()).stream()
                        .map(item -> {
                            var result = purchaseOrderConverter.toItemVO(item);
                            long pending = (long) item.getQuantity() - received.getOrDefault(item.getId(), 0L);
                            result.setPendingQuantity((int) Math.max(0L, pending));
                            return result;
                        })
                        .toList()));
    }
}
