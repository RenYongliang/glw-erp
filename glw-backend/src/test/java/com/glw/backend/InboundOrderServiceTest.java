package com.glw.backend;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.glw.backend.common.BusinessException;
import com.glw.backend.converter.InboundOrderConverter;
import com.glw.backend.mapper.InboundOrderItemMapper;
import com.glw.backend.mapper.ProductMapper;
import com.glw.backend.model.dto.*;
import com.glw.backend.model.entity.*;
import com.glw.backend.model.enums.PurchaseOrderStatus;
import com.glw.backend.service.*;
import com.glw.backend.service.impl.InboundOrderServiceImpl;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.mapstruct.factory.Mappers;

class InboundOrderServiceTest {
    @BeforeAll
    static void productMetadata() {
        var configuration = new com.baomidou.mybatisplus.core.MybatisConfiguration();
        var assistant = new org.apache.ibatis.builder.MapperBuilderAssistant(configuration, "inbound-test");
        assistant.setCurrentNamespace("com.glw.backend.mapper.ProductMapper");
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, Product.class);
    }
    @SuppressWarnings("unchecked")
    private static <T> LambdaQueryChainWrapper<T> query() {
        return mock(LambdaQueryChainWrapper.class, invocation ->
                invocation.getMethod().getReturnType() == Object.class
                        ? invocation.getMock() : RETURNS_SELF.answer(invocation));
    }

    private static class Fixture {
        final InboundOrderItemService items = mock(InboundOrderItemService.class);
        final InboundOrderBatchItemService batches = mock(InboundOrderBatchItemService.class);
        final PurchaseOrderService orders = mock(PurchaseOrderService.class);
        final PurchaseOrderItemService purchaseItems = mock(PurchaseOrderItemService.class);
        final InventoryService inventory = mock(InventoryService.class);
        final InboundOrderPurchaseOrderService relations = mock(InboundOrderPurchaseOrderService.class);
        final InboundOrderItemMapper totals = mock(InboundOrderItemMapper.class);
        final DocumentNumberService numbers = mock(DocumentNumberService.class);
        final InboundOrderServiceImpl service = spy(new InboundOrderServiceImpl(items, totals, batches,
                orders, purchaseItems, inventory, Mappers.getMapper(InboundOrderConverter.class), relations, mock(ProductMapper.class), numbers));
        final List<InboundOrderItem> savedItems = new ArrayList<>();
        final List<InboundOrderBatchItem> savedBatches = new ArrayList<>();
        final List<InboundOrderPurchaseOrder> savedRelations = new ArrayList<>();
        final List<Inventory> stock = new ArrayList<>();
        final PurchaseOrder first = order(1L);
        final PurchaseOrder second = order(2L);

        Fixture() {
            when(numbers.next(eq(DocumentNumberService.Kind.INBOUND), any())).thenReturn("IN20261018001");
            LambdaQueryChainWrapper<PurchaseOrder> poQuery = query();
            when(orders.lambdaQuery()).thenReturn(poQuery);
            when(poQuery.list()).thenReturn(List.of(first, second));
            LambdaQueryChainWrapper<PurchaseOrderItem> piQuery = query();
            when(purchaseItems.lambdaQuery()).thenReturn(piQuery);
            when(piQuery.list()).thenReturn(List.of(item(11L, 1L, "0.85"), item(22L, 2L, "0.90")));
            LambdaQueryChainWrapper<InboundOrderItem> itemQuery = query();
            when(items.lambdaQuery()).thenReturn(itemQuery);
            when(itemQuery.list()).thenReturn(savedItems);
            LambdaQueryChainWrapper<InboundOrderBatchItem> batchQuery = query();
            when(batches.lambdaQuery()).thenReturn(batchQuery);
            when(batchQuery.list()).thenReturn(savedBatches);
            LambdaQueryChainWrapper<InboundOrderPurchaseOrder> relationQuery = query();
            when(relations.lambdaQuery()).thenReturn(relationQuery);
            when(relationQuery.list()).thenReturn(savedRelations);
            LambdaQueryChainWrapper<Inventory> inventoryQuery = query();
            when(inventory.lambdaQuery()).thenReturn(inventoryQuery);
            when(inventoryQuery.list()).thenReturn(stock);
            when(totals.sumCompletedByPurchaseOrderIds(List.of(1L, 2L))).thenReturn(List.of(
                    new InboundOrderItemMapper.ReceivedQuantity(11L, 50L)));
            doAnswer(call -> { ((InboundOrder) call.getArgument(0)).setId(100L); return true; }).when(service).save(any());
            doReturn(true).when(service).updateById(any(InboundOrder.class));
            when(orders.updateById(any(PurchaseOrder.class))).thenReturn(true);
            when(items.save(any())).thenAnswer(call -> { InboundOrderItem row = call.getArgument(0); row.setId((long) savedItems.size() + 1); savedItems.add(row); return true; });
            when(batches.save(any())).thenAnswer(call -> { InboundOrderBatchItem row = call.getArgument(0); row.setId((long) savedBatches.size() + 1); savedBatches.add(row); return true; });
            when(relations.save(any())).thenAnswer(call -> savedRelations.add(call.getArgument(0)));
            when(inventory.save(any())).thenAnswer(call -> stock.add(call.getArgument(0)));
        }
    }

    @Test
    void oneReceiptKeepsAllocationsCostsAndCompletionSeparateForEachPurchaseOrder() {
        var f = new Fixture();
        var result = f.service.createInboundOrder(request(50, 20));
        assertThat(result.getInboundOrderNo()).isEqualTo("IN20261018001");
        verify(f.numbers).next(DocumentNumberService.Kind.INBOUND, java.time.LocalDateTime.of(2026, 10, 18, 9, 0));
        assertThat(result.getPurchaseOrders()).extracting(vo -> vo.getPurchaseOrderId()).containsExactly(1L, 2L);
        assertThat(result.getItems()).extracting(vo -> vo.getInboundQuantity()).containsExactly(50, 20);
        assertThat(result.getItems()).extracting(vo -> vo.getProductId()).containsOnly(6L);
        assertThat(f.savedItems).extracting(InboundOrderItem::getInboundOrderId).containsOnly(100L);
        assertThat(f.stock).extracting(Inventory::getUnitCost).containsExactly(new BigDecimal("0.85"), new BigDecimal("0.90"));
        assertThat(f.stock).extracting(Inventory::getBatchNo).containsExactly("BAT26101800006001", "BAT26101800006002");
        assertThat(f.first.getOrderStatus()).isEqualTo(PurchaseOrderStatus.COMPLETED);
        assertThat(f.second.getOrderStatus()).isEqualTo(PurchaseOrderStatus.PARTIALLY_RECEIVED);
        doReturn(receipt()).when(f.service).getById(100L);
        var scoped = f.service.detail(100L, 2L);
        assertThat(scoped.getItems()).hasSize(1);
        assertThat(scoped.getItems().getFirst().getPurchaseOrderItemId()).isEqualTo(22L);
        assertThatThrownBy(() -> f.service.detail(100L, 999L)).isInstanceOf(BusinessException.class);
    }

    @Test
    void cannotBorrowRemainingQuantityFromAnotherPurchaseOrder() {
        var f = new Fixture();
        assertThatThrownBy(() -> f.service.createInboundOrder(request(51, 1))).isInstanceOf(BusinessException.class);
        verify(f.service, never()).save(any());
        assertThat(f.stock).isEmpty();
    }

    @Test
    void cannotMixSuppliersOrLinkAnOrderWithoutAnAllocation() {
        var f = new Fixture();
        f.second.setSupplierId(9L);
        assertThatThrownBy(() -> f.service.createInboundOrder(request(1, 1))).isInstanceOf(BusinessException.class);
        f.second.setSupplierId(3L);
        var request = request(1, 1);
        request.setItems(List.of(request.getItems().getFirst()));
        assertThatThrownBy(() -> f.service.createInboundOrder(request)).isInstanceOf(BusinessException.class);
        verify(f.service, never()).save(any());
    }

    private static InboundOrder receipt() {
        var row = new InboundOrder(); row.setId(100L); row.setInboundOrderNo("IN20261018001"); return row;
    }
    private static PurchaseOrder order(Long id) {
        var row = new PurchaseOrder(); row.setId(id); row.setPurchaseOrderNo("PO-" + id);
        row.setSupplierId(3L); row.setSupplierName("Supplier"); row.setOrderStatus(PurchaseOrderStatus.PENDING_RECEIPT); return row;
    }
    private static PurchaseOrderItem item(Long id, Long orderId, String cost) {
        var row = new PurchaseOrderItem(); row.setId(id); row.setPurchaseOrderId(orderId); row.setProductId(6L);
        row.setPartNo("ET5907"); row.setQuantity(100); row.setUnitPrice(new BigDecimal(cost)); return row;
    }
    private static InboundOrderCreateDTO request(int first, int second) {
        var dto = new InboundOrderCreateDTO(); dto.setPurchaseOrderIds(List.of(1L, 2L)); dto.setInboundTime("2026-10-18T09:00:00");
        dto.setItems(List.of(allocation(11L, first), allocation(22L, second))); return dto;
    }
    private static InboundOrderItemCreateDTO allocation(Long id, int quantity) {
        var batch = new InboundOrderBatchCreateDTO(); batch.setDateCode("2618"); batch.setLotCode("LOT-A"); batch.setInboundQuantity(quantity);
        var item = new InboundOrderItemCreateDTO(); item.setPurchaseOrderItemId(id); item.setBatches(List.of(batch)); return item;
    }
}
