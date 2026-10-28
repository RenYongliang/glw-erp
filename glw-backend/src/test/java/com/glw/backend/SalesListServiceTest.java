package com.glw.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.glw.backend.converter.SalesOrderConverter;
import com.glw.backend.converter.OutboundOrderConverter;
import com.glw.backend.mapper.CustomerMapper;
import com.glw.backend.mapper.OutboundOrderItemAllocMapper;
import com.glw.backend.mapper.SalesOrderMapper;
import com.glw.backend.model.dto.SalesOrderPageDTO;
import com.glw.backend.model.dto.OutboundOrderPageDTO;
import com.glw.backend.model.entity.*;
import com.glw.backend.service.*;
import com.glw.backend.service.impl.SalesOrderServiceImpl;
import com.glw.backend.service.impl.OutboundOrderServiceImpl;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

class SalesListServiceTest {
    @SuppressWarnings("unchecked")
    private <T> LambdaQueryChainWrapper<T> query() {
        return mock(LambdaQueryChainWrapper.class, invocation ->
                invocation.getMethod().getReturnType() == Object.class
                        ? invocation.getMock() : RETURNS_SELF.answer(invocation));
    }

    @Test
    void salesListGroupsItemsByOrderAndPreservesCancellationAndPrice() {
        var items = mock(SalesOrderItemService.class);
        var customers = mock(CustomerMapper.class);
        var outbound = mock(OutboundOrderItemAllocMapper.class);
        var service = spy(new SalesOrderServiceImpl(items, Mappers.getMapper(SalesOrderConverter.class), customers, outbound, mock(com.glw.backend.service.DocumentNumberService.class)));
        LambdaQueryChainWrapper<SalesOrder> ordersQuery = query();
        LambdaQueryChainWrapper<SalesOrderItem> itemsQuery = query();
        doReturn(ordersQuery).when(service).lambdaQuery();
        when(items.lambdaQuery()).thenReturn(itemsQuery);
        var first = new SalesOrder(); first.setId(1L);
        var second = new SalesOrder(); second.setId(2L);
        first.setCustomerId(7L); second.setCustomerId(7L);
        var customer = new Customer(); customer.setId(7L);
        customer.setName("Customer Full Name"); customer.setShortName("Short");
        when(customers.selectByIds(List.of(7L))).thenReturn(List.of(customer));
        var page = new Page<SalesOrder>(1, 10);
        page.setRecords(List.of(first, second)); page.setTotal(2);
        when(ordersQuery.page(any())).thenReturn(page);
        var item = new SalesOrderItem(); item.setId(11L); item.setSalesOrderId(2L);
        item.setQuantity(1000); item.setCancelledQuantity(600); item.setUnitPrice(new BigDecimal("0.0750"));
        when(itemsQuery.list()).thenReturn(List.of(item));
        when(outbound.sumCompletedBySalesOrderIds(List.of(1L, 2L)))
                .thenReturn(List.of(new OutboundOrderItemAllocMapper.ShippedQuantity(11L, 250L)));
        var dto = new SalesOrderPageDTO(); dto.setPageNo(1L); dto.setPageSize(10L);
        var records = service.pageSalesOrder(dto).getRecords();
        assertThat(records.getFirst().getItems()).isEmpty();
        assertThat(records.getFirst().getCustomerShortName()).isEqualTo("Short");
        assertThat(records.get(1).getCustomerName()).isEqualTo("Customer Full Name");
        verify(customers, times(1)).selectByIds(List.of(7L));
        var actual = records.get(1).getItems().getFirst();
        assertThat(actual.getId()).isEqualTo(11L);
        assertThat(actual.getCancelledQuantity()).isEqualTo(600);
        assertThat(actual.getPendingQuantity()).isEqualTo(150);
        assertThat(actual.getUnitPrice()).isEqualByComparingTo("0.0750");
    }

    @Test
    void missingProductMatchReturnsEmptyInsteadOfAllOrders() {
        var items = mock(SalesOrderItemService.class);
        var service = spy(new SalesOrderServiceImpl(items, Mappers.getMapper(SalesOrderConverter.class), mock(CustomerMapper.class), mock(OutboundOrderItemAllocMapper.class), mock(com.glw.backend.service.DocumentNumberService.class)));
        LambdaQueryChainWrapper<SalesOrderItem> itemsQuery = query();
        when(items.lambdaQuery()).thenReturn(itemsQuery);
        when(itemsQuery.list()).thenReturn(List.of());
        var dto = new SalesOrderPageDTO(); dto.setPageNo(1L); dto.setPageSize(10L); dto.setBrand("missing");
        assertThat(service.pageSalesOrder(dto).getTotal()).isZero();
        verify(service, never()).lambdaQuery();
    }

    @Test
    void outboundListGroupsActualItemsAndBatchesWithoutMixingOrders() {
        var items = mock(OutboundOrderItemService.class);
        var allocations = mock(OutboundOrderItemAllocService.class);
        var batches = mock(OutboundOrderItemBatchService.class);
        var relations = mock(OutboundOrderSalesOrderService.class);
        var customers = mock(CustomerMapper.class);
        var service = spy(new OutboundOrderServiceImpl(items, allocations, batches, mock(OutboundOrderItemAllocMapper.class),
                relations, mock(SalesOrderMapper.class), mock(SalesOrderItemService.class), mock(InventoryService.class),
                Mappers.getMapper(OutboundOrderConverter.class), customers, mock(com.glw.backend.service.DocumentNumberService.class)));
        LambdaQueryChainWrapper<OutboundOrder> ordersQuery = query();
        LambdaQueryChainWrapper<OutboundOrderItem> itemsQuery = query();
        LambdaQueryChainWrapper<OutboundOrderItemAlloc> allocsQuery = query();
        LambdaQueryChainWrapper<OutboundOrderItemBatch> batchesQuery = query();
        LambdaQueryChainWrapper<OutboundOrderSalesOrder> relationsQuery = query();
        doReturn(ordersQuery).when(service).lambdaQuery();
        when(items.lambdaQuery()).thenReturn(itemsQuery);
        when(allocations.lambdaQuery()).thenReturn(allocsQuery);
        when(batches.lambdaQuery()).thenReturn(batchesQuery);
        when(relations.lambdaQuery()).thenReturn(relationsQuery);
        var first = new OutboundOrder(); first.setId(1L);
        var second = new OutboundOrder(); second.setId(2L);
        first.setCustomerId(7L); second.setCustomerId(7L);
        var customer = new Customer(); customer.setId(7L);
        customer.setName("Customer Full Name"); customer.setShortName("Short");
        when(customers.selectByIds(List.of(7L))).thenReturn(List.of(customer));
        var page = new Page<OutboundOrder>(1, 10); page.setRecords(List.of(first, second));
        when(ordersQuery.page(any())).thenReturn(page);
        var relation = new OutboundOrderSalesOrder(); relation.setOutboundOrderId(2L);
        relation.setSalesOrderId(21L); relation.setSalesOrderNo("SO-21"); relation.setCustomerOrderNo("CO-21");
        when(relationsQuery.list()).thenReturn(List.of(relation));
        var item = new OutboundOrderItem(); item.setId(11L); item.setOutboundOrderId(2L);
        item.setOutboundQuantity(400);
        when(itemsQuery.list()).thenReturn(List.of(item));
        var allocation = new OutboundOrderItemAlloc(); allocation.setId(51L);
        allocation.setOutboundOrderItemId(11L); allocation.setSalesOrderId(21L);
        allocation.setSalesOrderItemId(21L); allocation.setAllocatedQuantity(400);
        when(allocsQuery.list()).thenReturn(List.of(allocation));
        var batch = new OutboundOrderItemBatch(); batch.setId(31L); batch.setAllocId(51L);
        batch.setInventoryId(41L); batch.setQuantity(400); batch.setUnitCost(new BigDecimal("0.0350"));
        when(batchesQuery.list()).thenReturn(List.of(batch));
        var dto = new OutboundOrderPageDTO(); dto.setPageNo(1L); dto.setPageSize(10L);
        var records = service.pageOutboundOrder(dto).getRecords();
        assertThat(records.getFirst().getCustomerShortName()).isEqualTo("Short");
        assertThat(records.get(1).getCustomerName()).isEqualTo("Customer Full Name");
        assertThat(records.get(1).getSalesOrders()).extracting(vo -> vo.getSalesOrderNo()).containsExactly("SO-21");
        verify(customers, times(1)).selectByIds(List.of(7L));
        assertThat(records.getFirst().getItems()).isEmpty();
        var actual = records.get(1).getItems().getFirst();
        assertThat(actual.getOutboundQuantity()).isEqualTo(400);
        var actualAlloc = actual.getAllocations().getFirst();
        assertThat(actualAlloc.getSalesOrderItemId()).isEqualTo(21L);
        assertThat(actualAlloc.getOutboundQuantity()).isEqualTo(400);
        assertThat(actualAlloc.getBatches().getFirst().getInventoryId()).isEqualTo(41L);
        assertThat(actualAlloc.getBatches().getFirst().getUnitCost()).isEqualByComparingTo("0.0350");
    }

    @Test
    void emptyOutboundPageDoesNotQueryItemsOrBatches() {
        var items = mock(OutboundOrderItemService.class);
        var allocations = mock(OutboundOrderItemAllocService.class);
        var batches = mock(OutboundOrderItemBatchService.class);
        var service = spy(new OutboundOrderServiceImpl(items, allocations, batches, mock(OutboundOrderItemAllocMapper.class),
                mock(OutboundOrderSalesOrderService.class), mock(SalesOrderMapper.class), mock(SalesOrderItemService.class),
                mock(InventoryService.class), Mappers.getMapper(OutboundOrderConverter.class), mock(CustomerMapper.class),
                mock(com.glw.backend.service.DocumentNumberService.class)));
        LambdaQueryChainWrapper<OutboundOrder> ordersQuery = query();
        doReturn(ordersQuery).when(service).lambdaQuery();
        when(ordersQuery.page(any())).thenReturn(new Page<OutboundOrder>(1, 10));
        var dto = new OutboundOrderPageDTO(); dto.setPageNo(1L); dto.setPageSize(10L);
        assertThat(service.pageOutboundOrder(dto).getRecords()).isEmpty();
        verifyNoInteractions(items, allocations, batches);
    }
}
