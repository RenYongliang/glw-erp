package com.glw.backend.model.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import java.math.BigDecimal;
import java.util.*;

/** Export receives query conditions only; row data is always read from the database. */
@Data
public class ListExportDTO {
    @Size(max = 256) private String keyword = "";
    private String tab = "";
    @Size(max = 10000) private List<@NotNull @Positive Long> selectedIds = new ArrayList<>();
    @Size(max = 30) private List<@NotNull String> columns = new ArrayList<>();
    private String sortKey = "";
    private String sortDirection = "asc";
    private boolean filterOpen;
    private String filterKey = "";
    private String filterOperator = "包含";
    private String filterValue = "";
    private String category = "";
    private String stage = "";
    private String settlement = "";
    private Long customerId;
    private List<String> dates = new ArrayList<>();
    @Valid @NotNull private PurchaseFilters purchase = new PurchaseFilters();
    @Valid @NotNull private SalesFilters sales = new SalesFilters();
    @Valid @NotNull private InventoryPageDTO inventory = new InventoryPageDTO();

    @Data
    public static class PurchaseFilters {
        private String code = "";
        private String supplier = "";
        private String paymentStatus = "";
        private String receiptStatus = "";
        private List<String> dates;
        @NotNull private Map<String, String> product = new HashMap<>();
    }

    @Data
    public static class SalesFilters {
        @NotNull private Map<String, String> text = new HashMap<>();
        @NotNull private Map<String, List<String>> dates = new HashMap<>();
        @NotNull private Map<String, List<BigDecimal>> numbers = new HashMap<>();
        private String status = "";
        private String paymentStatus = "";
    }
}
