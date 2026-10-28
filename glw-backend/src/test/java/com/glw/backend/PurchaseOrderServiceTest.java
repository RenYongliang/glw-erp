package com.glw.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.glw.backend.converter.PurchaseOrderConverter;
import com.glw.backend.mapper.InboundOrderItemAllocMapper;
import com.glw.backend.mapper.SupplierMapper;
import com.glw.backend.model.entity.Supplier;
import com.glw.backend.model.dto.PurchaseOrderPageDTO;
import com.glw.backend.model.entity.PurchaseOrder;
import com.glw.backend.model.entity.PurchaseOrderItem;
import com.glw.backend.service.PurchaseOrderItemService;
import com.glw.backend.service.impl.PurchaseOrderServiceImpl;

import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.util.List;

class PurchaseOrderServiceTest {
    @Test
    @SuppressWarnings("unchecked")
    void pendingDeliveryUsesCompletedReceiptTotalsForEachPurchaseItem() {
        var itemService = mock(PurchaseOrderItemService.class);
        var inboundMapper = mock(InboundOrderItemAllocMapper.class);
        var supplierMapper = mock(SupplierMapper.class);
        var service = spy(new PurchaseOrderServiceImpl(itemService,
                Mappers.getMapper(PurchaseOrderConverter.class), inboundMapper, supplierMapper,
                mock(com.glw.backend.service.DocumentNumberService.class)));
        var orderQuery = mock(LambdaQueryChainWrapper.class, invocation ->
                invocation.getMethod().getReturnType() == Object.class
                        ? invocation.getMock() : RETURNS_SELF.answer(invocation));
        var itemQuery = mock(LambdaQueryChainWrapper.class, invocation ->
                invocation.getMethod().getReturnType() == Object.class
                        ? invocation.getMock() : RETURNS_SELF.answer(invocation));
        doReturn(orderQuery).when(service).lambdaQuery();
        when(itemService.lambdaQuery()).thenReturn(itemQuery);

        var order = new PurchaseOrder();
        order.setId(1L);
        order.setSupplierId(7L);
        var supplier = new Supplier();
        supplier.setId(7L);
        supplier.setName("Supplier Full Name");
        supplier.setShortName("Short");
        when(supplierMapper.selectByIds(List.of(7L))).thenReturn(List.of(supplier));
        var page = new Page<PurchaseOrder>(1, 10);
        page.setRecords(List.of(order));
        page.setTotal(1);
        when(orderQuery.page(any())).thenReturn(page);
        when(itemQuery.list()).thenReturn(List.of(item(11L, 100), item(12L, 40), item(13L, 10)));
        when(inboundMapper.sumCompletedByPurchaseOrderIds(List.of(1L))).thenReturn(List.of(
                new InboundOrderItemAllocMapper.ReceivedQuantity(11L, 35L),
                new InboundOrderItemAllocMapper.ReceivedQuantity(13L, 20L)));

        var request = new PurchaseOrderPageDTO();
        request.setPageNo(1L);
        request.setPageSize(10L);
        var result = service.pagePurchaseOrder(request);
        assertThat(result.getRecords().getFirst().getSupplierName()).isEqualTo("Supplier Full Name");
        assertThat(result.getRecords().getFirst().getSupplierShortName()).isEqualTo("Short");
        assertThat(result.getRecords().getFirst().getItems())
                .extracting(i -> i.getPendingQuantity()).containsExactly(65, 40, 0);
        verify(inboundMapper, times(1)).sumCompletedByPurchaseOrderIds(List.of(1L));
    }

    private PurchaseOrderItem item(long id, int quantity) {
        var item = new PurchaseOrderItem();
        item.setId(id);
        item.setPurchaseOrderId(1L);
        item.setProductId(8L);
        item.setQuantity(quantity);
        return item;
    }
}
