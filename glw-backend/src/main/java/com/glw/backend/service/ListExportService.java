package com.glw.backend.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.glw.backend.common.*;
import com.glw.backend.model.dto.*;
import com.glw.backend.model.entity.Customer;
import com.glw.backend.model.vo.*;
import lombok.AllArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.*;
import java.text.Collator;
import java.text.NumberFormat;
import java.util.*;
import java.util.function.*;
import java.util.stream.Collectors;

@Service
@Profile("!mock")
@AllArgsConstructor
public class ListExportService {
    private final CustomerService customers;
    private final CrmManagementService crm;
    private final SalesOrderService sales;
    private final PurchaseOrderService purchases;
    private final InboundOrderService inbound;
    private final OutboundOrderService outbound;
    private final InventoryService inventory;

    public record ExportFile(String name, byte[] content) {}
    record Row(Long id, Map<String, Object> values, List<Map<String, Object>> lines) {}

    private static final Map<String, String> NAMES = Map.of("customers", "客户", "followups", "跟进记录",
            "sales-orders", "销售订单", "outbound-orders", "销售出库", "purchase-orders", "采购订单",
            "inbound-orders", "产品入库", "inventory", "库存明细");
    private static final Map<String, LinkedHashMap<String, String>> COLUMNS = Map.of(
            "customers", columns("name", "客户名称", "shortName", "客户简称", "category", "客户分类", "contact", "联系人", "phone", "手机", "address", "客户地址", "settlement", "结算方式"),
            "followups", columns("customer", "客户", "stage", "跟进阶段", "visitTime", "拜访时间", "project", "项目应用", "originalPartNo", "原用物料型号", "brands", "品牌", "productNames", "推荐型号", "models", "封装", "quantity", "项目用量", "price", "产品报价", "tax", "是否含税", "progress", "进度", "detail", "详细内容"),
            "sales-orders", columns("code", "订单编号", "purchaseNumber", "客户采购单号", "customer", "客户名称", "signed", "订单日期", "brands", "品牌", "productNames", "型号", "models", "封装", "prices", "单价", "ordered", "销售数量", "pendingShipment", "待发数量", "amounts", "金额", "total", "订单金额", "prepayment", "预收款", "term", "账期", "delivery", "交付日期", "owner", "销售负责人"),
            "outbound-orders", columns("code", "出库单号", "purchaseNumber", "客户采购单号", "customer", "客户名称", "shippedAt", "出库日期", "brands", "品牌", "productNames", "型号", "models", "封装", "ordered", "销售数量", "pending", "出库数量", "status", "出库状态"),
            "purchase-orders", columns("code", "采购单号", "supplier", "供应商", "signed", "采购日期", "brands", "品牌", "productNames", "型号", "models", "封装", "prices", "单价(含税)", "quantities", "采购数量", "pendingDelivery", "待交数量", "amounts", "金额", "deliveries", "交付日期", "total", "订单金额", "prepayment", "预付款", "owner", "采购负责人"),
            "inbound-orders", columns("code", "入库单号", "orderCode", "采购单号", "supplier", "供应商", "receivedAt", "入库日期", "brands", "品牌", "productNames", "型号", "models", "封装", "quantities", "采购数量", "received", "入库数量", "status", "入库状态"),
            "inventory", columns("productId", "产品ID", "brands", "品牌", "productNames", "型号", "models", "封装", "dateCode", "Date Code", "lotCode", "Lot Code", "batchNo", "批次号", "quantity", "可出库库存", "cost", "单位成本", "amount", "库存金额", "inboundDate", "入库日期"));

    @Transactional(readOnly = true)
    public ExportFile export(String kind, ListExportDTO query) {
        if (!NAMES.containsKey(kind)) throw new BusinessException(ErrorCode.NOT_FOUND, "不支持的导出类型");
        var definitions = COLUMNS.get(kind);
        List<String> keys = query.getColumns() == null || query.getColumns().isEmpty()
                ? new ArrayList<>(definitions.keySet()) : query.getColumns();
        if (keys.stream().anyMatch(k -> !definitions.containsKey(k)))
            throw new BusinessException(ErrorCode.BAD_REQUEST, "导出列无效");
        List<Row> rows = switch (kind) {
            case "customers" -> customerRows();
            case "followups" -> visitRows();
            case "sales-orders" -> salesRows();
            case "purchase-orders" -> purchaseRows();
            case "inbound-orders" -> inboundRows();
            case "outbound-orders" -> outboundRows();
            default -> inventoryRows(query.getInventory());
        };
        var selected = query.getSelectedIds() == null ? Set.<Long>of() : new HashSet<>(query.getSelectedIds());
        var matching = rows.stream().filter(row -> (selected.isEmpty() || selected.contains(row.id()))
                && matches(kind, row, query)).collect(Collectors.toCollection(ArrayList::new));
        if (!s(query.getSortKey()).isEmpty()) {
            var collator = Collator.getInstance(Locale.CHINA);
            matching.sort((a, b) -> {
                Object x = a.values().get(query.getSortKey()), y = b.values().get(query.getSortKey());
                int comparison = x instanceof Number && y instanceof Number ? decimal(x).compareTo(decimal(y))
                        : collator.compare(s(x), s(y));
                return "desc".equals(query.getSortDirection()) ? -comparison : comparison;
            });
        }
        List<List<?>> table = new ArrayList<>();
        table.add(keys.stream().map(definitions::get).toList());
        for (var row : matching) table.add(keys.stream().map(k -> row.values().get(k)).toList());
        return new ExportFile(NAMES.get(kind), CsvExport.encode(table));
    }

    private List<Row> customerRows() {
        return all((page, size) -> customers.lambdaQuery().orderByAsc(Customer::getId).page(new PageDTO(page, size).toPage()))
                .stream().map(c -> new Row(c.getId(), values("name", c.getName(), "shortName", c.getShortName(),
                        "category", s(c.getCategory()).isEmpty() ? "未分类" : "战略合作".equals(c.getCategory()) ? "战略客户" : c.getCategory(),
                        "rawCategory", c.getCategory(), "contact", c.getContact(), "phone", c.getContactPhone(), "address", c.getAddress(),
                        "settlement", settlement(c.getSettleType()), "rawSettlement", c.getSettleType()), List.of())).toList();
    }

    private List<Row> visitRows() {
        return all((page, size) -> crm.visits(new PageDTO(page, size))).stream().map(v -> new Row(v.getId(), values(
                "customer", v.getCustomerName(), "customerShortName", v.getCustomerShortName(), "customerId", v.getCustomerId(),
                "stage", v.getStage(), "visitTime", v.getVisitTime(), "project", v.getProject(), "originalPartNo", v.getOriginalPartNo(),
                "brands", v.getBrand(), "productNames", v.getPartNo(), "models", v.getPackageType(), "quantity", v.getDemandQuantity(),
                "price", v.getUnitPrice(), "tax", v.getTaxIncluded() != null && v.getTaxIncluded().getCode() == 1 ? "含税" : "未税",
                "progress", v.getProgress(), "detail", v.getDetail()), List.of())).toList();
    }

    private List<SalesOrderVO> allSales() {
        return all((page, size) -> { var q = new SalesOrderPageDTO(); q.setPageNo(page); q.setPageSize(size); return sales.pageSalesOrder(q); });
    }
    private List<PurchaseOrderVO> allPurchases() {
        return all((page, size) -> { var q = new PurchaseOrderPageDTO(); q.setPageNo(page); q.setPageSize(size); return purchases.pagePurchaseOrder(q); });
    }

    private Row salesRow(SalesOrderVO o) {
        List<Map<String, Object>> lines = o.getItems().stream().map(i -> values("id", i.getId(), "brands", i.getBrand(),
                "productNames", i.getPartNo(), "models", i.getPackageType(), "prices", i.getUnitPrice(), "ordered", i.getQuantity(),
                "pendingShipment", i.getPendingQuantity(), "amounts", i.getAmount())).toList();
        var v = values("code", o.getOrderNo(), "purchaseNumber", s(o.getCustomerOrderNo()).isEmpty() ? "—" : o.getCustomerOrderNo(),
                "customer", o.getCustomerName(), "customerShortName", fallback(o.getCustomerShortName(), o.getCustomerName()),
                "signed", date(o.getOrderTime()), "delivery", date(o.getDeliveryTime()), "owner", "", "term", settlement(o.getSettleType() == null ? "" : o.getSettleType().getCode()),
                "status", o.getOrderStatus() == null ? "待发货" : o.getOrderStatus().getDescription(),
                "paymentStatus", o.getPaymentStatus() == null || "UNPAID".equals(o.getPaymentStatus().getCode()) ? "未收款" : o.getPaymentStatus().getDescription(),
                "prepayment", o.getPrepaymentAmount() == null || o.getPrepaymentAmount().signum() == 0 ? "—" : o.getPrepaymentAmount().setScale(2, RoundingMode.HALF_UP),
                "name", s(o.getCustomerName()) + "销售订单", "priceLevel", "标准售价");
        summarize(v, lines, "\n"); v.put("total", total(lines, "prices", "ordered"));
        documentSearch(v, lines, false, "ordered");
        return new Row(o.getId(), v, lines);
    }
    private List<Row> salesRows() { return allSales().stream().map(this::salesRow).toList(); }

    private Row purchaseRow(PurchaseOrderVO o) {
        List<Map<String, Object>> lines = o.getItems().stream().map(i -> values("id", i.getId(), "brands", i.getBrand(),
                "productNames", i.getPartNo(), "models", i.getPackageType(), "prices", i.getUnitPrice(), "quantities", i.getQuantity(),
                "pendingDelivery", i.getPendingQuantity(), "amounts", i.getAmount(), "deliveries", date(o.getDeliveryTime()))).toList();
        boolean complete = !lines.isEmpty() && lines.stream().allMatch(l -> decimal(l.get("pendingDelivery")).signum() <= 0);
        boolean partial = lines.stream().anyMatch(l -> decimal(l.get("pendingDelivery")).compareTo(decimal(l.get("quantities"))) < 0);
        var v = values("code", o.getPurchaseOrderNo(), "supplier", o.getSupplierName(), "signed", date(o.getOrderTime()), "owner", "",
                "status", complete ? "已完成" : partial ? "部分收货" : "待收货", "receiptStatus", complete ? "已收货" : partial ? "部分收货" : "待收货",
                "paymentStatus", o.getPaymentStatus() == null || "UNPAID".equals(o.getPaymentStatus().getCode()) ? "未付款" : o.getPaymentStatus().getDescription(),
                "prepayment", o.getPrepaymentAmount() == null || o.getPrepaymentAmount().signum() == 0 ? "—" : o.getPrepaymentAmount().setScale(2, RoundingMode.HALF_UP),
                "name", s(o.getSupplierName()) + "采购订单");
        summarize(v, lines, "、"); v.put("total", total(lines, "prices", "quantities"));
        documentSearch(v, lines, true, "quantities");
        return new Row(o.getId(), v, lines);
    }
    private List<Row> purchaseRows() { return allPurchases().stream().map(this::purchaseRow).toList(); }

    private List<Row> inboundRows() {
        Map<Long, PurchaseOrderItemVO> source = allPurchases().stream().flatMap(o -> o.getItems().stream())
                .collect(Collectors.toMap(PurchaseOrderItemVO::getId, Function.identity()));
        List<InboundOrderVO> receipts = all((page, size) -> { var q = new InboundOrderPageDTO(); q.setPageNo(page); q.setPageSize(size); return inbound.pageInboundOrder(q); });
        return receipts.stream().map(o -> {
            Map<Long, Map<String, Object>> groups = new LinkedHashMap<>();
            BigDecimal amount = BigDecimal.ZERO;
            for (var item : o.getItems()) {
                var line = groups.computeIfAbsent(item.getProductId(), id -> values("brands", item.getBrand(), "productNames", item.getPartNo(),
                        "models", item.getPackageType(), "quantities", 0, "received", 0));
                for (var allocation : item.getAllocations()) {
                    var original = source.get(allocation.getPurchaseOrderItemId());
                    line.put("quantities", decimal(line.get("quantities")).add(original == null ? BigDecimal.ZERO : decimal(original.getQuantity())));
                    int quantity = allocation.getInboundQuantity() == null ? 0 : allocation.getInboundQuantity();
                    line.put("received", decimal(line.get("received")).add(BigDecimal.valueOf(quantity)));
                    amount = amount.add(allocation.getAmount() != null
                            ? decimal(allocation.getAmount())
                            : decimal(allocation.getUnitPrice()).multiply(BigDecimal.valueOf(quantity)).setScale(2, RoundingMode.HALF_UP));
                }
            }
            var lines = new ArrayList<>(groups.values());
            var v = values("code", o.getInboundOrderNo(), "orderCode", o.getPurchaseOrders().stream().map(InboundPurchaseOrderVO::getPurchaseOrderNo).collect(Collectors.joining("、")),
                    "supplier", o.getSupplierName(), "receivedAt", fallback(date(o.getInboundTime()), "—"), "status", o.getStatus() != null && "COMPLETED".equals(o.getStatus().getCode()) ? "已入库" : "待入库",
                    "quality", "合格", "inspectedAt", date(o.getInboundTime()), "name", s(o.getSupplierName()) + "采购订单", "total", amount);
            summarize(v, lines, "、");
            v.put("quantity", lines.stream().map(l -> decimal(l.get("received"))).reduce(BigDecimal.ZERO, BigDecimal::add));
            return new Row(o.getId(), v, lines);
        }).toList();
    }

    private List<Row> outboundRows() {
        Map<Long, SalesOrderVO> source = allSales().stream().collect(Collectors.toMap(SalesOrderVO::getId, Function.identity()));
        List<OutboundOrderVO> shipments = all((page, size) -> { var q = new OutboundOrderPageDTO(); q.setPageNo(page); q.setPageSize(size); return outbound.pageOutboundOrder(q); });
        return shipments.stream().map(o -> {
            Map<Long, SalesOrderItemVO> orderItems = o.getSalesOrders().stream()
                    .map(so -> source.get(so.getSalesOrderId()))
                    .filter(Objects::nonNull)
                    .flatMap(so -> so.getItems().stream())
                    .collect(Collectors.toMap(SalesOrderItemVO::getId, Function.identity(), (first, second) -> first));
            var shipped = o.getItems().stream()
                    .flatMap(item -> item.getAllocations().stream())
                    .collect(Collectors.groupingBy(OutboundOrderItemAllocVO::getSalesOrderItemId,
                            Collectors.summingInt(a -> a.getOutboundQuantity() == null ? 0 : a.getOutboundQuantity())));
            List<Map<String, Object>> lines = orderItems.entrySet().stream().filter(e -> shipped.containsKey(e.getKey()))
                    .map(e -> {
                        var i = e.getValue();
                        return values("brands", i.getBrand(), "productNames", i.getPartNo(), "models", i.getPackageType(),
                                "ordered", i.getQuantity(), "pending", shipped.get(e.getKey()), "prices", i.getUnitPrice(),
                                "amounts", i.getAmount(), "pendingShipment", i.getPendingQuantity());
                    }).toList();
            String orderCodes = o.getSalesOrders().stream().map(OutboundSalesOrderVO::getSalesOrderNo)
                    .filter(Objects::nonNull).collect(Collectors.joining("、"));
            String customerOrderNos = o.getSalesOrders().stream().map(OutboundSalesOrderVO::getCustomerOrderNo)
                    .filter(Objects::nonNull).collect(Collectors.joining("、"));
            var v = values("code", o.getOutboundOrderNo(), "orderCode", fallback(orderCodes, "—"), "customer", o.getCustomerName(),
                    "customerShortName", fallback(o.getCustomerShortName(), o.getCustomerName()), "purchaseNumber", fallback(customerOrderNos, "—"),
                    "shippedAt", fallback(date(o.getOutboundTime()), "—"), "status", o.getStatus() != null && "COMPLETED".equals(o.getStatus().getCode()) ? "已出库" : "待出库",
                    "tag", "出库", "name", o.getCustomerName() == null ? "—" : s(o.getCustomerName()) + "销售订单", "total", total(lines, "prices", "pending"));
            summarize(v, lines, "\n");
            v.put("quantity", lines.stream().map(l -> decimal(l.get("pending"))).reduce(BigDecimal.ZERO, BigDecimal::add));
            documentSearch(v, lines, false, "pending");
            return new Row(o.getId(), v, lines);
        }).toList();
    }

    private List<Row> inventoryRows(InventoryPageDTO query) {
        List<InventoryProductVO> products = all((page, size) -> {
            var q = new InventoryPageDTO(); q.setPageNo(page); q.setPageSize(size); q.setKeyword(query.getKeyword());
            q.setBrand(query.getBrand()); q.setPartNo(query.getPartNo()); q.setPackageType(query.getPackageType()); q.setInStockOnly(query.isInStockOnly());
            return inventory.pageProducts(q);
        });
        List<Row> rows = new ArrayList<>();
        for (var p : products) {
            var base = values("productId", p.getProductId(), "brands", p.getBrand(), "productNames", p.getPartNo(), "models", p.getPackageType());
            if (p.getBatches().isEmpty()) {
                base.putAll(values("quantity", 0, "amount", 0)); rows.add(new Row(p.getProductId(), base, List.of()));
            } else for (var batch : p.getBatches()) {
                if (batch.getAvailableQuantity() == null || batch.getAvailableQuantity() <= 0) continue;
                var v = new LinkedHashMap<>(base);
                v.putAll(values("dateCode", batch.getDateCode(), "lotCode", batch.getLotCode(), "batchNo", batch.getBatchNo(),
                        "quantity", batch.getAvailableQuantity(), "cost", batch.getUnitCost(), "amount", batch.getStockValue(), "inboundDate", date(batch.getInboundTime())));
                rows.add(new Row(p.getProductId(), v, List.of()));
            }
        }
        return rows;
    }

    static boolean matches(String kind, Row row, ListExportDTO q) {
        var v = row.values();
        if ("inventory".equals(kind)) return true; // Inventory uses the same SQL query as its list endpoint.
        if ("customers".equals(kind)) {
            if (!equalFilter(v.get("rawSettlement"), q.getSettlement())) return false;
            if ("unclassified".equals(q.getCategory()) ? !s(v.get("rawCategory")).isEmpty() : !equalFilter(v.get("rawCategory"), q.getCategory())) return false;
            return includes(join(v, "name", "shortName", "rawCategory", "address", "contact", "phone", "settlement"), q.getKeyword());
        }
        if ("followups".equals(kind)) return (q.getCustomerId() == null || q.getCustomerId().equals(v.get("customerId")))
                && equalFilter(v.get("stage"), q.getStage()) && dateRange(v.get("visitTime"), q.getDates())
                && includes(join(v, "customer", "customerShortName", "project", "originalPartNo", "brands", "productNames", "models", "stage", "progress", "detail"), q.getKeyword());
        if (!includes(v.containsKey("_search") ? v.get("_search") : v.values().stream().map(ListExportService::s).collect(Collectors.joining(" ")), q.getKeyword())) return false;
        if ("sales-orders".equals(kind)) {
            if (!s(q.getTab()).isEmpty() && !"全部订单".equals(q.getTab()) && !s(v.get("status")).equals(q.getTab().replaceAll("订单$", ""))) return false;
            var f = q.getSales();
            for (String key : List.of("code", "customer", "purchaseNumber", "term", "owner")) {
                Object value = "purchaseNumber".equals(key) && "—".equals(v.get(key)) ? "" : v.get(key);
                if (!includes(value, f.getText().get(key))) return false;
            }
            if (!equalFilter(v.get("customer"), f.getText().get("customer")) || !equalFilter(v.get("status"), f.getStatus())
                    || !equalFilter(v.get("paymentStatus"), f.getPaymentStatus())) return false;
            for (var key : List.of("signed", "delivery")) if (!dateRange(v.get(key), f.getDates().get(key))) return false;
            if (!numberRange(v.get("total"), f.getNumbers().get("total"))) return false;
            boolean product = List.of("brands", "productNames", "models").stream().anyMatch(k -> !s(f.getText().get(k)).trim().isEmpty())
                    || List.of("prices", "ordered", "amounts").stream().anyMatch(k -> hasRange(f.getNumbers().get(k)));
            return !product || row.lines().stream().anyMatch(l -> List.of("brands", "productNames", "models").stream().allMatch(k -> includes(l.get(k), f.getText().get(k)))
                    && numberRange(l.get("prices"), f.getNumbers().get("prices")) && numberRange(l.get("ordered"), f.getNumbers().get("ordered"))
                    && numberRange(decimal(l.get("prices")).multiply(decimal(l.get("ordered"))), f.getNumbers().get("amounts")));
        }
        if ("purchase-orders".equals(kind)) {
            var f = q.getPurchase();
            if (!includes(v.get("code"), f.getCode()) || !equalFilter(v.get("supplier"), f.getSupplier())
                    || !equalFilter(v.get("paymentStatus"), f.getPaymentStatus()) || !equalFilter(v.get("receiptStatus"), f.getReceiptStatus())
                    || !dateRange(v.get("signed"), f.getDates())) return false;
            var p = f.getProduct();
            return p.values().stream().allMatch(x -> s(x).trim().isEmpty()) || row.lines().stream().anyMatch(l -> includes(l.get("brands"), p.get("brand"))
                    && includes(l.get("productNames"), p.get("model")) && includes(l.get("models"), p.get("packaging")));
        }
        if (!s(q.getTab()).isEmpty() && !q.getTab().startsWith("全部") && !s(v.get("status")).equals(q.getTab())) return false;
        if (!q.isFilterOpen()) return true;
        String value = s(v.get(q.getFilterKey())), term = s(q.getFilterValue());
        return switch (s(q.getFilterOperator())) {
            case "为空" -> value.isEmpty();
            case "不为空" -> !value.isEmpty();
            case "等于" -> term.isEmpty() || value.equals(term);
            case "不等于" -> term.isEmpty() || !value.equals(term);
            case "不包含" -> term.isEmpty() || !value.contains(term);
            default -> term.isEmpty() || value.contains(term);
        };
    }

    private static <T> List<T> all(BiFunction<Long, Long, IPage<T>> fetch) {
        List<T> rows = new ArrayList<>();
        for (long page = 1; ; page++) {
            var result = fetch.apply(page, 200L); rows.addAll(result.getRecords());
            if (result.getRecords().isEmpty() || page >= result.getPages()) return rows;
        }
    }
    private static LinkedHashMap<String, String> columns(String... pairs) {
        var result = new LinkedHashMap<String, String>();
        for (int i = 0; i < pairs.length; i += 2) result.put(pairs[i], pairs[i + 1]);
        return result;
    }
    private static Map<String, Object> values(Object... pairs) {
        var result = new LinkedHashMap<String, Object>();
        for (int i = 0; i < pairs.length; i += 2) result.put((String) pairs[i], pairs[i + 1]);
        return result;
    }
    private static void summarize(Map<String, Object> values, List<Map<String, Object>> lines, String separator) {
        if (lines.isEmpty()) return;
        for (var key : lines.getFirst().keySet()) {
            if ("id".equals(key)) continue;
            values.put(key, lines.stream().map(l -> s(l.get(key))).collect(Collectors.joining(separator)));
        }
    }
    private static void documentSearch(Map<String, Object> values, List<Map<String, Object>> lines, boolean purchase, String quantityKey) {
        var search = new LinkedHashMap<>(values);
        search.remove("paymentStatus"); search.remove("receiptStatus");
        String separator = purchase ? "、" : "\n";
        search.put("prices", lines.stream().map(l -> formatted(l.get("prices"), purchase ? 2 : 4)).collect(Collectors.joining(separator)));
        search.put("amounts", lines.stream().map(l -> formatted(l.get("amounts") == null
                ? decimal(l.get("prices")).multiply(decimal(l.get(quantityKey))) : l.get("amounts"), 2)).collect(Collectors.joining(separator)));
        search.put("gross", values.get("total")); search.put("discount", 0); search.put("rate", "0.00%");
        values.put("_search", search.values().stream().map(ListExportService::s).collect(Collectors.joining(" ")));
    }
    private static String formatted(Object value, int precision) {
        var format = NumberFormat.getNumberInstance(Locale.CHINA);
        format.setMinimumFractionDigits(2); format.setMaximumFractionDigits(precision); format.setRoundingMode(RoundingMode.HALF_UP);
        return format.format(decimal(value));
    }
    private static BigDecimal total(List<Map<String, Object>> lines, String price, String quantity) {
        return lines.stream().map(l -> decimal(l.get(price)).multiply(decimal(l.get(quantity))).setScale(2, RoundingMode.HALF_UP)).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    private static String settlement(String code) {
        return switch (s(code)) { case "CASH" -> "现金"; case "MONTHLY" -> "月结"; case "PREPAID" -> "预付"; case "COD" -> "货到付款"; default -> fallback(code, "—"); };
    }
    private static String s(Object value) { return value == null ? "" : value instanceof BigDecimal n ? n.stripTrailingZeros().toPlainString() : value.toString(); }
    private static String fallback(String value, String other) { return s(value).isBlank() ? s(other) : value; }
    private static String date(Object value) { String text = s(value); return text.length() >= 10 ? text.substring(0, 10) : text; }
    private static BigDecimal decimal(Object value) { return s(value).isEmpty() ? BigDecimal.ZERO : new BigDecimal(s(value)); }
    private static boolean includes(Object value, String term) { return s(value).toLowerCase(Locale.ROOT).contains(s(term).trim().toLowerCase(Locale.ROOT)); }
    private static boolean equalFilter(Object value, String term) { return s(term).isEmpty() || s(value).equals(term); }
    private static String join(Map<String, Object> values, String... keys) { return Arrays.stream(keys).map(k -> s(values.get(k))).collect(Collectors.joining(" ")); }
    private static boolean hasRange(List<?> range) { return range != null && range.stream().anyMatch(Objects::nonNull); }
    private static boolean dateRange(Object value, List<String> range) {
        if (range == null || range.stream().allMatch(x -> s(x).isEmpty())) return true;
        String day = date(value);
        return !day.isEmpty() && (range.isEmpty() || s(range.getFirst()).isEmpty() || day.compareTo(range.getFirst()) >= 0)
                && (range.size() < 2 || s(range.get(1)).isEmpty() || day.compareTo(range.get(1)) <= 0);
    }
    private static boolean numberRange(Object value, List<BigDecimal> range) {
        if (!hasRange(range)) return true;
        return (range.getFirst() == null || decimal(value).compareTo(range.getFirst()) >= 0)
                && (range.size() < 2 || range.get(1) == null || decimal(value).compareTo(range.get(1)) <= 0);
    }
}
