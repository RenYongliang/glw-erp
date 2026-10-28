package com.glw.backend.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.glw.backend.converter.PurchaseOrderConverter;
import com.glw.backend.mapper.PurchaseOrderMapper;
import com.glw.backend.mapper.InboundOrderItemAllocMapper;
import com.glw.backend.mapper.SupplierMapper;
import com.glw.backend.model.entity.Supplier;
import com.glw.backend.model.dto.PurchaseOrderPageDTO;
import com.glw.backend.model.entity.PurchaseOrder;
import com.glw.backend.model.entity.PurchaseOrderItem;
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

import java.util.List;
import java.time.LocalDateTime;
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
    private final DocumentNumberService documentNumberService;

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
    public IPage<PurchaseOrderVO> pagePurchaseOrder(PurchaseOrderPageDTO pageDTO) {
        boolean filterBrand = StringUtils.hasText(pageDTO.getBrand());
        boolean filterPartNo = StringUtils.hasText(pageDTO.getPartNo());
        boolean filterPackageType = StringUtils.hasText(pageDTO.getPackageType());

        List<Long> filterOrderIds = null;
        if (filterBrand || filterPartNo || filterPackageType) {
            filterOrderIds = findOrderIdsByItem(pageDTO, filterBrand, filterPartNo, filterPackageType);
            if (CollectionUtils.isEmpty(filterOrderIds)) return emptyPage(pageDTO);
        }

        Page<PurchaseOrder> page = lambdaQuery()
                .in(filterOrderIds != null, PurchaseOrder::getId, filterOrderIds)
                .eq(pageDTO.getId() != null, PurchaseOrder::getId, pageDTO.getId())
                .eq(StringUtils.hasText(pageDTO.getPurchaseOrderNo()), PurchaseOrder::getPurchaseOrderNo, pageDTO.getPurchaseOrderNo())
                .eq(pageDTO.getSupplierId() != null, PurchaseOrder::getSupplierId, pageDTO.getSupplierId())
                .like(StringUtils.hasText(pageDTO.getSupplierName()), PurchaseOrder::getSupplierName, pageDTO.getSupplierName())
                .eq(pageDTO.getOrderStatus() != null, PurchaseOrder::getOrderStatus, pageDTO.getOrderStatus())
                .eq(pageDTO.getPaymentStatus() != null, PurchaseOrder::getPaymentStatus, pageDTO.getPaymentStatus())
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
