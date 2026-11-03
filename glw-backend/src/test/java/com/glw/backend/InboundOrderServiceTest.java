package com.glw.backend;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.glw.backend.common.BusinessException;
import com.glw.backend.converter.InboundOrderConverter;
import com.glw.backend.mapper.InboundOrderItemAllocMapper;
import com.glw.backend.mapper.ProductMapper;
import com.glw.backend.model.dto.*;
import com.glw.backend.model.entity.*;
import com.glw.backend.model.enums.PurchaseReceiptStatus;
import com.glw.backend.model.enums.TaxIncluded;
import com.glw.backend.model.vo.InboundOrderItemAllocVO;
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
        final InboundOrderItemAllocService allocations = mock(InboundOrderItemAllocService.class);
        final PurchaseOrderService orders = mock(PurchaseOrderService.class);
        final PurchaseOrderItemService purchaseItems = mock(PurchaseOrderItemService.class);
        final InventoryService inventory = mock(InventoryService.class);
        final InboundOrderItemAllocMapper totals = mock(InboundOrderItemAllocMapper.class);
        final DocumentNumberService numbers = mock(DocumentNumberService.class);
        final InboundOrderServiceImpl service = spy(new InboundOrderServiceImpl(items, allocations, totals,
                orders, purchaseItems, inventory, Mappers.getMapper(InboundOrderConverter.class), mock(ProductMapper.class), numbers,
                mock(com.glw.backend.service.FinanceOpenItemService.class)));
        final List<InboundOrderItem> savedItems = new ArrayList<>();
        final List<InboundOrderItemAlloc> savedAllocations = new ArrayList<>();
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
            LambdaQueryChainWrapper<InboundOrderItemAlloc> allocQuery = query();
            when(allocations.lambdaQuery()).thenReturn(allocQuery);
            when(allocQuery.list()).thenReturn(savedAllocations);
            LambdaQueryChainWrapper<Inventory> inventoryQuery = query();
            when(inventory.lambdaQuery()).thenReturn(inventoryQuery);
            when(inventoryQuery.list()).thenAnswer(call -> new ArrayList<>(stock));
            when(totals.sumCompletedByPurchaseOrderIds(List.of(1L, 2L))).thenReturn(List.of(
                    new InboundOrderItemAllocMapper.ReceivedQuantity(11L, 50L)));
            doAnswer(call -> { ((InboundOrder) call.getArgument(0)).setId(100L); return true; }).when(service).save(any());
            doReturn(true).when(service).updateById(any(InboundOrder.class));
            when(orders.updateById(any(PurchaseOrder.class))).thenReturn(true);
            when(items.save(any())).thenAnswer(call -> { InboundOrderItem row = call.getArgument(0); row.setId((long) savedItems.size() + 1); savedItems.add(row); return true; });
            when(allocations.save(any())).thenAnswer(call -> { InboundOrderItemAlloc row = call.getArgument(0); row.setId((long) savedAllocations.size() + 1); savedAllocations.add(row); return true; });
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
        assertThat(result.getItems()).hasSize(1);
        assertThat(result.getItems().getFirst().getInboundQuantity()).isEqualTo(70);
        assertThat(result.getItems().getFirst().getAllocations())
                .extracting(InboundOrderItemAllocVO::getInboundQuantity).containsExactly(50, 20);
        assertThat(f.savedItems).extracting(InboundOrderItem::getInboundOrderId).containsOnly(100L);
        assertThat(f.savedAllocations).extracting(InboundOrderItemAlloc::getPurchaseOrderItemId)
                .containsExactly(11L, 22L);
        assertThat(f.stock).extracting(Inventory::getUnitCost).containsExactly(new BigDecimal("0.8500"), new BigDecimal("0.9000"));
        assertThat(f.stock).extracting(Inventory::getBatchNo).containsExactly("BAT26101800006001", "BAT26101800006002");
        assertThat(f.first.getReceiptStatus()).isEqualTo(PurchaseReceiptStatus.RECEIVED);
        assertThat(f.second.getReceiptStatus()).isEqualTo(PurchaseReceiptStatus.PARTIALLY_RECEIVED);
        doReturn(receipt()).when(f.service).getById(100L);
        var scoped = f.service.detail(100L, 2L);
        assertThat(scoped.getItems()).hasSize(1);
        assertThat(scoped.getItems().getFirst().getInboundQuantity()).isEqualTo(20);
        assertThat(scoped.getItems().getFirst().getAllocations())
                .extracting(InboundOrderItemAllocVO::getPurchaseOrderItemId).containsExactly(22L);
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
        request.setAllocations(List.of(request.getAllocations().getFirst()));
        assertThatThrownBy(() -> f.service.createInboundOrder(request)).isInstanceOf(BusinessException.class);
        verify(f.service, never()).save(any());
    }

    private static void prices(Fixture f, PurchaseOrderItem first, PurchaseOrderItem second) {
        when(f.purchaseItems.lambdaQuery().list()).thenReturn(List.of(first, second));
    }

    @Test void groupsByTaxInclusiveCostWhileKeepingOriginalPurchaseQuotes() {
        var f = new Fixture();
        var first = item(11L, 1L, "1.00"); f.first.setTaxIncluded(TaxIncluded.EXCLUDED); first.setTaxRate(13);
        var second = item(22L, 2L, "1.1300");
        prices(f, first, second);
        f.service.createInboundOrder(request(50, 20));
        assertThat(f.stock).hasSize(1);
        assertThat(f.stock.getFirst().getUnitCost()).isEqualByComparingTo("1.1300");
        assertThat(f.stock.getFirst().getInitialQuantity()).isEqualTo(70);
        assertThat(f.savedAllocations.getFirst().getUnitPrice()).isEqualByComparingTo("1.00");
        assertThat(f.savedAllocations.getFirst().getTaxIncluded()).isEqualTo(TaxIncluded.EXCLUDED);
    }

    @Test void identicalQuoteNumbersWithDifferentTaxTreatmentProduceDifferentCosts() {
        var f = new Fixture();
        var first = item(11L, 1L, "1.00"); f.first.setTaxIncluded(TaxIncluded.EXCLUDED); first.setTaxRate(13);
        prices(f, first, item(22L, 2L, "1.00"));
        f.service.createInboundOrder(request(50, 20));
        assertThat(f.stock).extracting(Inventory::getUnitCost).containsExactly(new BigDecimal("1.1300"), new BigDecimal("1.0000"));
    }

    @Test void roundsConvertedCostToFourDecimalsAndAcceptsExplicitZeroTax() {
        var f = new Fixture();
        var first = item(11L, 1L, "0.0750"); f.first.setTaxIncluded(TaxIncluded.EXCLUDED); first.setTaxRate(13);
        var second = item(22L, 2L, "0.0750"); f.second.setTaxIncluded(TaxIncluded.EXCLUDED); second.setTaxRate(0);
        prices(f, first, second);
        f.service.createInboundOrder(request(50, 20));
        assertThat(f.stock).extracting(Inventory::getUnitCost).containsExactly(new BigDecimal("0.0848"), new BigDecimal("0.0750"));
    }

    @Test void rejectsOverflowingOrOutOfRangeCostBeforeWritingReceipt() {
        for (Integer rate : java.util.Arrays.asList(-1, 101)) {
            var f = new Fixture();
            var first = item(11L, 1L, "1.00"); f.first.setTaxIncluded(TaxIncluded.EXCLUDED); first.setTaxRate(rate);
            prices(f, first, item(22L, 2L, "1.00"));
            assertThatThrownBy(() -> f.service.createInboundOrder(request(50, 20))).isInstanceOf(BusinessException.class).hasMessageContaining("税率");
            verify(f.service, never()).save(any());
        }
        var f = new Fixture();
        var first = item(11L, 1L, "99999999.9999"); f.first.setTaxIncluded(TaxIncluded.EXCLUDED); first.setTaxRate(13);
        prices(f, first, item(22L, 2L, "1.00"));
        assertThatThrownBy(() -> f.service.createInboundOrder(request(50, 20))).isInstanceOf(BusinessException.class).hasMessageContaining("超出");
        verify(f.service, never()).save(any());
    }

    @Test void excludedPriceWithoutTaxRateIsCostedAtFaceValue() {
        var f = new Fixture();
        var first = item(11L, 1L, "1.00"); f.first.setTaxIncluded(TaxIncluded.EXCLUDED); first.setTaxRate(null);
        prices(f, first, item(22L, 2L, "1.00"));
        f.service.createInboundOrder(request(50, 20));
        assertThat(f.stock).extracting(Inventory::getUnitCost).containsExactly(new BigDecimal("1.0000"));
        assertThat(f.stock.getFirst().getInitialQuantity()).isEqualTo(70);
        assertThat(f.savedAllocations.getFirst().getUnitPrice()).isEqualByComparingTo("1.00");
        assertThat(f.savedAllocations.getFirst().getTaxIncluded()).isEqualTo(TaxIncluded.EXCLUDED);
    }

    @Test void existingStockFromAnotherReceiptIsNotCombinedWithThisReceipt() {
        var f = new Fixture();
        var existing = new Inventory(); existing.setBatchNo("BAT26101800006001"); existing.setQuantity(9000);
        existing.setUnitCost(new BigDecimal("0.8500")); existing.setInboundOrderItemId(999L);
        f.stock.add(existing);
        f.service.createInboundOrder(request(50, 20));
        assertThat(f.stock).hasSize(3);
        assertThat(existing.getQuantity()).isEqualTo(9000);
        assertThat(f.stock.get(1).getBatchNo()).isEqualTo("BAT26101800006002");
    }

    @Test
    void purchaseAssociationsAreDeduplicatedSortedAndUseAllocationSnapshots() {
        var f = new Fixture();
        var request = request(20, 10);
        var anotherBatch = allocation(11L, 30);
        anotherBatch.setLotCode("LOT-B");
        request.setAllocations(List.of(request.getAllocations().get(1), anotherBatch,
                request.getAllocations().getFirst()));
        f.service.createInboundOrder(request);
        f.first.setPurchaseOrderNo("CHANGED-AFTER-RECEIPT");
        doReturn(receipt()).when(f.service).getById(100L);
        var result = f.service.detail(100L, null);
        assertThat(result.getPurchaseOrders()).extracting(vo -> vo.getPurchaseOrderId())
                .containsExactly(1L, 2L);
        assertThat(result.getPurchaseOrders()).extracting(vo -> vo.getPurchaseOrderNo())
                .containsExactly("PO-1", "PO-2");
        assertThat(result.getItems().getFirst().getAllocations()).hasSize(3);
        verify(f.orders, never()).getById(any());
    }

    private static InboundOrder receipt() {
        var row = new InboundOrder(); row.setId(100L); row.setInboundOrderNo("IN20261018001"); return row;
    }
    private static PurchaseOrder order(Long id) {
        var row = new PurchaseOrder(); row.setId(id); row.setPurchaseOrderNo("PO-" + id);
        row.setSupplierId(3L); row.setSupplierName("Supplier"); row.setOrderStatus(com.glw.backend.model.enums.PurchaseOrderStatus.CONFIRMED);
        row.setReceiptStatus(PurchaseReceiptStatus.PENDING_RECEIPT);
        row.setTaxIncluded(TaxIncluded.INCLUDED); return row;
    }
    private static PurchaseOrderItem item(Long id, Long orderId, String cost) {
        var row = new PurchaseOrderItem(); row.setId(id); row.setPurchaseOrderId(orderId); row.setProductId(6L);
        row.setPartNo("ET5907"); row.setQuantity(100); row.setUnitPrice(new BigDecimal(cost)); return row;
    }
    private static InboundOrderCreateDTO request(int first, int second) {
        var dto = new InboundOrderCreateDTO(); dto.setPurchaseOrderIds(List.of(1L, 2L)); dto.setInboundTime("2026-10-18T09:00:00");
        dto.setAllocations(List.of(allocation(11L, first), allocation(22L, second))); return dto;
    }
    private static InboundOrderAllocationCreateDTO allocation(Long id, int quantity) {
        var item = new InboundOrderAllocationCreateDTO();
        item.setPurchaseOrderItemId(id);
        item.setDateCode("2618");
        item.setLotCode("LOT-A");
        item.setAllocatedQuantity(quantity);
        return item;
    }
}
