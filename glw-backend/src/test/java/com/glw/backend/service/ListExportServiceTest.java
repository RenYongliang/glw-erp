package com.glw.backend.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.glw.backend.common.*;
import com.glw.backend.model.dto.*;
import com.glw.backend.model.vo.*;
import com.glw.backend.model.enums.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

class ListExportServiceTest {
    private final SalesOrderService sales = mock(SalesOrderService.class);
    private final PurchaseOrderService purchases = mock(PurchaseOrderService.class);
    private final InboundOrderService inbound = mock(InboundOrderService.class);
    private final OutboundOrderService outbound = mock(OutboundOrderService.class);
    private final InventoryService inventory = mock(InventoryService.class);
    private final CrmManagementService crm = mock(CrmManagementService.class);
    private final ListExportService service = new ListExportService(mock(CustomerService.class), crm,
            sales, purchases, inbound, outbound, inventory);

    @Test void csvQuotesUnicodeMultilineAndEscapesFormulasWithoutRoundingDecimals() {
        var bytes = CsvExport.encode(List.of(Arrays.asList("客户,\"A\"\n第二行", " =1+2", new BigDecimal("0.0750"), null)));
        assertThat(new String(bytes, StandardCharsets.UTF_8)).isEqualTo("\uFEFF\"客户,\"\"A\"\"\n第二行\",\"' =1+2\",\"0.0750\",\"\"\r\n");
    }

    @Test void salesProductConditionsMustMatchOneLineAndRangesAreInclusive() {
        var q = new ListExportDTO();
        q.getSales().getText().put("brands", "Alpha");
        q.getSales().getNumbers().put("ordered", Arrays.asList(new BigDecimal("100"), null));
        var row = row(Map.of("total", 1000), List.of(Map.of("brands", "Alpha", "ordered", 10), Map.of("brands", "Beta", "ordered", 100)));
        assertThat(ListExportService.matches("sales-orders", row, q)).isFalse();
        q.getSales().getText().put("brands", "Beta");
        assertThat(ListExportService.matches("sales-orders", row, q)).isTrue();
        q.getSales().getNumbers().put("total", List.of(new BigDecimal("1001")));
        assertThat(ListExportService.matches("sales-orders", row, q)).isFalse();
    }

    @Test void purchaseFiltersSupportPaymentReceiptDatesAndSameProductLine() {
        var q = new ListExportDTO();
        var f = q.getPurchase();
        f.setPaymentStatus("已付款"); f.setReceiptStatus("部分收货");
        f.setDates(List.of("2026-10-01", "2026-10-10"));
        f.setProduct(Map.of("brand", "Alpha", "model", "ET5"));
        var row = row(Map.of("signed", "2026-10-10", "paymentStatus", "已付款", "receiptStatus", "部分收货"),
                List.of(Map.of("brands", "Alpha", "productNames", "OTHER"), Map.of("brands", "Beta", "productNames", "ET5")));
        assertThat(ListExportService.matches("purchase-orders", row, q)).isFalse();
        f.setProduct(Map.of("brand", "Beta", "model", "et5"));
        assertThat(ListExportService.matches("purchase-orders", row, q)).isTrue();
        f.setDates(List.of("2026-10-11", "2026-10-12"));
        assertThat(ListExportService.matches("purchase-orders", row, q)).isFalse();
    }

    @Test void customerAndVisitFiltersIncludeUnclassifiedStageCustomerAndDate() {
        var q = new ListExportDTO(); q.setCategory("unclassified"); q.setKeyword("phone"); q.setSettlement("MONTHLY");
        assertThat(ListExportService.matches("customers", row(Map.of("phone", "PHONE123", "rawSettlement", "MONTHLY"), List.of()), q)).isTrue();
        assertThat(ListExportService.matches("customers", row(Map.of("rawCategory", "成交客户"), List.of()), q)).isFalse();
        q.setKeyword("Quote"); q.setCustomerId(5L); q.setStage("报价"); q.setDates(List.of("2026-10-10", "2026-10-10"));
        var visit = row(Map.of("customerId", 5L, "stage", "报价", "visitTime", "2026-10-10T23:59:00", "detail", "Quote"), List.of());
        assertThat(ListExportService.matches("followups", visit, q)).isTrue();
        q.setCustomerId(6L);
        assertThat(ListExportService.matches("followups", visit, q)).isFalse();
    }

    @Test void receiptAndShipmentFiltersRespectTabsAndOperators() {
        var q = new ListExportDTO(); q.setTab("已入库"); q.setFilterOpen(true); q.setFilterKey("supplier"); q.setFilterValue("Alpha");
        var receipt = row(Map.of("status", "已入库", "supplier", "Alpha"), List.of());
        assertThat(ListExportService.matches("inbound-orders", receipt, q)).isTrue();
        q.setFilterOperator("不等于");
        assertThat(ListExportService.matches("inbound-orders", receipt, q)).isFalse();
        q.setTab("待出库");
        assertThat(ListExportService.matches("outbound-orders", row(Map.of("status", "已出库"), List.of()), q)).isFalse();
    }

    @Test void exportReadsAllPagesAndAppliesSelectionAndColumnOrderOnServer() {
        var first = sale(1L, "SO20261010001"); var second = sale(2L, "SO20261010002");
        when(sales.pageSalesOrder(any())).thenAnswer(call -> {
            SalesOrderPageDTO q = call.getArgument(0);
            return new Page<SalesOrderVO>(q.getPageNo(), 200, 201).setRecords(List.of(q.getPageNo() == 1 ? first : second));
        });
        var q = new ListExportDTO(); q.setColumns(List.of("code", "prices")); q.setSelectedIds(List.of(2L));
        var result = service.export("sales-orders", q);
        var csv = new String(result.content(), StandardCharsets.UTF_8);
        assertThat(csv).startsWith("\uFEFF\"订单编号\",\"单价\"\r\n").contains("SO20261010002", "0.075").doesNotContain("SO20261010001");
        verify(sales, times(2)).pageSalesOrder(any());
    }

    @Test void exportsUnpaidUsingUiLabelsAndRejectsInvalidColumnsBeforeQuery() {
        when(sales.pageSalesOrder(any())).thenReturn(new Page<SalesOrderVO>(1, 200, 1).setRecords(List.of(sale(1L, "SO1"))));
        var q = new ListExportDTO(); q.getSales().setPaymentStatus("未收款");
        assertThat(new String(service.export("sales-orders", q).content(), StandardCharsets.UTF_8)).contains("SO1");
        q.setColumns(List.of("password"));
        assertThatThrownBy(() -> service.export("sales-orders", q)).isInstanceOf(BusinessException.class).hasMessage("导出列无效");
    }

    @Test void formattedPriceAndAmountSearchMatchesTheListWithoutRoundingExportPrice() {
        var order = sale(1L, "SO1");
        order.getItems().getFirst().setUnitPrice(new BigDecimal("0.1000"));
        order.getItems().getFirst().setAmount(new BigDecimal("1000.00"));
        when(sales.pageSalesOrder(any())).thenReturn(new Page<SalesOrderVO>(1, 200, 1).setRecords(List.of(order)));
        var q = new ListExportDTO(); q.setKeyword("0.10");
        assertThat(new String(service.export("sales-orders", q).content(), StandardCharsets.UTF_8)).contains("SO1");
        q.setKeyword("1,000.00");
        assertThat(new String(service.export("sales-orders", q).content(), StandardCharsets.UTF_8)).contains("SO1");
    }

    @Test void inventoryUsesListQueryAndExportsRemainingBatchesWithFourDecimalCost() {
        var product = new InventoryProductVO(); product.setProductId(6L); product.setPartNo("ET5");
        var batch = new InventoryBatchVO(); batch.setAvailableQuantity(123L); batch.setBatchNo("BAT26101000006001"); batch.setUnitCost(new BigDecimal("0.0750"));
        var exhausted = new InventoryBatchVO(); exhausted.setAvailableQuantity(0L); exhausted.setBatchNo("EXHAUSTED");
        product.setBatches(List.of(batch, exhausted));
        when(inventory.pageProducts(any())).thenReturn(new Page<InventoryProductVO>(1, 200, 1).setRecords(List.of(product)));
        var q = new ListExportDTO(); q.getInventory().setKeyword("ET5"); q.getInventory().setBrand("Alpha"); q.getInventory().setInStockOnly(false);
        var csv = new String(service.export("inventory", q).content(), StandardCharsets.UTF_8);
        assertThat(csv).contains("BAT26101000006001", "0.0750").doesNotContain("EXHAUSTED");
        verify(inventory).pageProducts(argThat(p -> "ET5".equals(p.getKeyword()) && "Alpha".equals(p.getBrand()) && !p.isInStockOnly()));
    }

    @Test void partialShipmentSearchUsesOrderLineAmountAndExportsOnlyShippedQuantity() {
        var order = sale(1L, "SO1");
        order.getItems().getFirst().setQuantity(1000);
        order.getItems().getFirst().setAmount(new BigDecimal("75.00"));
        var shipment = new OutboundOrderVO(); shipment.setId(2L); shipment.setSalesOrderId(1L); shipment.setOutboundOrderNo("OUT1");
        var item = new OutboundOrderItemVO(); item.setSalesOrderItemId(1L); item.setOutboundQuantity(100);
        shipment.setItems(List.of(item));
        when(sales.pageSalesOrder(any())).thenReturn(new Page<SalesOrderVO>(1, 200, 1).setRecords(List.of(order)));
        when(outbound.pageOutboundOrder(any())).thenReturn(new Page<OutboundOrderVO>(1, 200, 1).setRecords(List.of(shipment)));
        var q = new ListExportDTO(); q.setKeyword("75.00"); q.setColumns(List.of("code", "ordered", "pending"));
        assertThat(new String(service.export("outbound-orders", q).content(), StandardCharsets.UTF_8)).contains("\"OUT1\",\"1000\",\"100\"");
    }

    private static ListExportService.Row row(Map<String, Object> values, List<Map<String, Object>> lines) { return new ListExportService.Row(1L, values, lines); }
    private static SalesOrderVO sale(Long id, String code) {
        var o = new SalesOrderVO(); o.setId(id); o.setOrderNo(code); o.setPaymentStatus(SalesPaymentStatus.UNPAID);
        var item = new SalesOrderItemVO(); item.setId(id); item.setUnitPrice(new BigDecimal("0.0750")); item.setQuantity(100); item.setAmount(new BigDecimal("7.50"));
        o.setItems(List.of(item)); return o;
    }
}
