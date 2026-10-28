package com.glw.backend;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.glw.backend.common.BusinessException;
import com.glw.backend.converter.OutboundOrderConverter;
import com.glw.backend.mapper.*;
import com.glw.backend.model.dto.*;
import com.glw.backend.model.entity.*;
import com.glw.backend.model.enums.SalesOrderStatus;
import com.glw.backend.model.enums.TaxIncluded;
import com.glw.backend.service.*;
import com.glw.backend.service.impl.OutboundOrderServiceImpl;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

class OutboundOrderServiceTest {
    @SuppressWarnings("unchecked")
    private static <T> LambdaQueryChainWrapper<T> query() {
        return mock(LambdaQueryChainWrapper.class, invocation -> invocation.getMethod().getReturnType() == Object.class
                ? invocation.getMock() : RETURNS_SELF.answer(invocation));
    }

    private static class Fixture {
        final OutboundOrderItemService items = mock(OutboundOrderItemService.class);
        final OutboundOrderItemAllocService allocations = mock(OutboundOrderItemAllocService.class);
        final OutboundOrderItemBatchService batches = mock(OutboundOrderItemBatchService.class);
        final SalesOrderMapper orders = mock(SalesOrderMapper.class);
        final SalesOrderItemService salesItems = mock(SalesOrderItemService.class);
        final InventoryService inventory = mock(InventoryService.class);
        final OutboundOrderServiceImpl service = spy(new OutboundOrderServiceImpl(items, allocations, batches,
                mock(OutboundOrderItemAllocMapper.class), orders, salesItems, inventory,
                Mappers.getMapper(OutboundOrderConverter.class), mock(CustomerMapper.class), mock(DocumentNumberService.class),
                mock(com.glw.backend.service.FinanceOpenItemService.class)));
        final List<OutboundOrderItem> savedItems = new ArrayList<>();
        final List<OutboundOrderItemAlloc> savedAllocations = new ArrayList<>();
        final List<OutboundOrderItemBatch> savedBatches = new ArrayList<>();
        final Inventory stock = new Inventory();
        final SalesOrder first = order(1L), second = order(2L);

        Fixture() {
            when(orders.selectList(any())).thenReturn(List.of(first, second));
            when(orders.updateById(any(SalesOrder.class))).thenReturn(1);
            LambdaQueryChainWrapper<SalesOrderItem> salesQuery = query();
            when(salesItems.lambdaQuery()).thenReturn(salesQuery);
            when(salesQuery.list()).thenReturn(List.of(item(11L, 1L), item(22L, 2L)));
            LambdaQueryChainWrapper<Inventory> stockQuery = query();
            when(inventory.lambdaQuery()).thenReturn(stockQuery);
            when(stockQuery.list()).thenReturn(List.of(stock));
            stock.setId(7L); stock.setProductId(6L); stock.setQuantity(100);
            stock.setReservedQuantity(0); stock.setUnitCost(new BigDecimal("0.0750"));
            when(inventory.updateById(any(Inventory.class))).thenReturn(true);
            LambdaQueryChainWrapper<OutboundOrderItem> itemQuery = query();
            when(items.lambdaQuery()).thenReturn(itemQuery); when(itemQuery.list()).thenReturn(savedItems);
            LambdaQueryChainWrapper<OutboundOrderItemAlloc> allocQuery = query();
            when(allocations.lambdaQuery()).thenReturn(allocQuery); when(allocQuery.list()).thenReturn(savedAllocations);
            LambdaQueryChainWrapper<OutboundOrderItemBatch> batchQuery = query();
            when(batches.lambdaQuery()).thenReturn(batchQuery); when(batchQuery.list()).thenReturn(savedBatches);
            doAnswer(call -> { ((OutboundOrder) call.getArgument(0)).setId(100L); return true; }).when(service).save(any());
            when(items.save(any())).thenAnswer(call -> { OutboundOrderItem row = call.getArgument(0); row.setId(10L); return savedItems.add(row); });
            when(allocations.save(any())).thenAnswer(call -> { OutboundOrderItemAlloc row = call.getArgument(0); row.setId((long) savedAllocations.size() + 1); return savedAllocations.add(row); });
            when(batches.save(any())).thenAnswer(call -> savedBatches.add(call.getArgument(0)));
        }
    }

    @Test void oneShipmentAllocatesOneBatchToTwoSalesOrdersAndDeductsStockOnce() {
        var f = new Fixture();
        var result = f.service.createOutboundOrder(request());
        assertThat(result.getSalesOrders()).extracting(vo -> vo.getSalesOrderNo()).containsExactly("SO-1", "SO-2");
        assertThat(f.savedItems).hasSize(1);
        assertThat(f.savedItems.getFirst().getOutboundQuantity()).isEqualTo(50);
        assertThat(f.savedAllocations).extracting(OutboundOrderItemAlloc::getAllocatedQuantity).containsExactly(30, 20);
        assertThat(f.savedAllocations).extracting(OutboundOrderItemAlloc::getSalesOrderNo).containsExactly("SO-1", "SO-2");
        assertThat(f.savedBatches).hasSize(2).allSatisfy(b -> {
            assertThat(b.getInventoryId()).isEqualTo(7L);
            assertThat(b.getUnitCost()).isEqualByComparingTo("0.0750");
        });
        assertThat(f.stock.getQuantity()).isEqualTo(50);
        verify(f.inventory, times(1)).updateById(f.stock);
        verify(f.service, times(1)).save(any());
    }

    @Test void rejectsAnotherProductsBatchBeforeCreatingTheShipment() {
        var f = new Fixture(); f.stock.setProductId(99L);
        assertThatThrownBy(() -> f.service.createOutboundOrder(request())).isInstanceOf(BusinessException.class).hasMessageContaining("不属于");
        verify(f.service, never()).save(any());
        verify(f.inventory, never()).updateById(any(Inventory.class));
    }

    @Test void persistsShippingSnapshotAndReturnsItForLaterPrinting() {
        var f = new Fixture();
        var request = request();
        var shipping = new OutboundShippingDTO();
        shipping.setRecipient("Receiver"); shipping.setPhone("13800000000");
        shipping.setAddress("Delivery address"); shipping.setSender("Sender");
        shipping.setSenderPhone("13900000000"); shipping.setMethod("Express");
        shipping.setCarrier("Carrier"); shipping.setTrackingNumber("TRACK-123");
        shipping.setPurchaseNumber("CO-1 / CO-2"); shipping.setRemark("Delivery remark");
        request.setShipping(shipping);
        var result = f.service.createOutboundOrder(request);
        assertThat(result.getShipping()).isEqualTo(shipping);
        var captured = org.mockito.ArgumentCaptor.forClass(OutboundOrder.class);
        verify(f.service).save(captured.capture());
        assertThat(captured.getValue().getShippingAddress()).isEqualTo("Delivery address");
        assertThat(captured.getValue().getShippingTrackingNumber()).isEqualTo("TRACK-123");
        shipping.setAddress("Changed after dispatch");
        assertThat(captured.getValue().getShippingAddress()).isEqualTo("Delivery address");
        var converter = Mappers.getMapper(OutboundOrderConverter.class);
        assertThat(converter.toVO(captured.getValue()).getShipping().getAddress()).isEqualTo("Delivery address");
    }

    @Test void validatesShippingContactFieldsWhenShippingIsProvided() {
        try (var factory = jakarta.validation.Validation.buildDefaultValidatorFactory()) {
            var request = request();
            assertThat(factory.getValidator().validate(request)).isEmpty();
            var shipping = new OutboundShippingDTO(); request.setShipping(shipping);
            assertThat(factory.getValidator().validate(request)).hasSize(3);
            shipping.setAddress("Address"); shipping.setRecipient("Receiver"); shipping.setPhone("Phone");
            assertThat(factory.getValidator().validate(request)).isEmpty();
            shipping.setTrackingNumber("x".repeat(101));
            assertThat(factory.getValidator().validate(request)).hasSize(1);
        }
    }

    @Test void checksCombinedBatchConsumptionAcrossOrdersAndCustomerConsistency() {
        var f = new Fixture(); f.stock.setQuantity(49);
        assertThatThrownBy(() -> f.service.createOutboundOrder(request())).isInstanceOf(BusinessException.class).hasMessageContaining("不足");
        verify(f.service, never()).save(any());
        f.stock.setQuantity(100); f.second.setCustomerId(99L);
        assertThatThrownBy(() -> f.service.createOutboundOrder(request())).isInstanceOf(BusinessException.class).hasMessageContaining("同一客户");
        verify(f.service, never()).save(any());
    }

    private static SalesOrder order(Long id) {
        var row = new SalesOrder(); row.setId(id); row.setOrderNo("SO-" + id); row.setCustomerId(3L);
        row.setCustomerName("Customer"); row.setOrderStatus(SalesOrderStatus.PENDING_SHIPMENT); return row;
    }
    private static SalesOrderItem item(Long id, Long orderId) {
        var row = new SalesOrderItem(); row.setId(id); row.setSalesOrderId(orderId); row.setProductId(6L);
        row.setPartNo("ET5907"); row.setQuantity(100); row.setUnitPrice(BigDecimal.ONE);
        row.setTaxIncluded(TaxIncluded.INCLUDED); return row;
    }
    private static OutboundOrderCreateDTO request() {
        var request = new OutboundOrderCreateDTO(); request.setSalesOrderIds(List.of(1L, 2L));
        var first = new OutboundOrderAllocationCreateDTO(); first.setSalesOrderItemId(11L);
        var firstBatch = new OutboundOrderBatchCreateDTO(); firstBatch.setInventoryId(7L); firstBatch.setQuantity(30);
        first.setBatches(List.of(firstBatch));
        var second = new OutboundOrderAllocationCreateDTO(); second.setSalesOrderItemId(22L);
        var secondBatch = new OutboundOrderBatchCreateDTO(); secondBatch.setInventoryId(7L); secondBatch.setQuantity(20);
        second.setBatches(List.of(secondBatch));
        request.setAllocations(List.of(first, second)); return request;
    }
}
