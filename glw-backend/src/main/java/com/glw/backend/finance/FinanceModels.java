package com.glw.backend.finance;

import jakarta.validation.constraints.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class FinanceModels {
    private FinanceModels() {}

    @Data public static class Query {
        @NotNull @Pattern(regexp = "AR|AP") private String direction = "AR";
        private Long partnerId;
        @Pattern(regexp = "\\d{4}-(0[1-9]|1[0-2])") private String period;
        @Size(max = 128) private String keyword = "";
        @Size(max = 30) private String status;
        private Boolean unsettledOnly;
        private Boolean overdueOnly;
        @Min(1) @Max(1000000) private int pageNo = 1;
        @Min(1) @Max(200) private int pageSize = 20;
        public int getOffset() { return Math.multiplyExact(pageNo - 1, pageSize); }
    }
    public record Page<T>(List<T> records, long total) {}
    @Data public static class OpenItem {
        private Long id, sourceId, sourceLineId, orderId, partnerId, productId, statementId;
        private String direction, sourceType, sourceNo, orderNo, partnerOrderNo, partnerName;
        private String brand, partNo, packageType, currency, period, reconcileStatus;
        private Integer quantity, taxIncluded, taxRate;
        private BigDecimal unitPrice, netAmount, taxAmount, grossAmount, settledAmount;
        private LocalDate bizDate, dueDate;
        private LocalDateTime createdAt;
    }
    @Data public static class Statement {
        private Long id, partnerId;
        private String statementNo, direction, partnerName, period, currency, status, remark;
        private BigDecimal grossAmount, settledAmount;
        private LocalDateTime createdAt, sentAt, confirmedAt;
    }
    @Data public static class Payment {
        private Long id, partnerId;
        private String paymentNo, requestKey, direction, partnerName, currency, bankReference, remark, status;
        private LocalDate paymentDate;
        private BigDecimal amount, allocatedAmount;
        private LocalDateTime createdAt;
    }
    @Data public static class Allocation {
        private Long id, paymentId, openItemId;
        private String requestKey, status, sourceNo, orderNo, paymentNo;
        private BigDecimal amount;
        private LocalDateTime createdAt, reversedAt;
    }
    @Data public static class Profile {
        private Long id;
        @NotNull @Pattern(regexp = "AR|AP") private String direction;
        @NotNull @Positive private Long partnerId;
        private String partnerName;
        @Min(0) @Max(3650) private int termDays;
        @NotNull @Pattern(regexp = "BIZ_DATE|MONTH_END") private String termBasis = "BIZ_DATE";
    }
    @Data public static class Partner {
        private Long id;
        private String name, shortName;
    }
    @Data public static class Generate {
        @NotEmpty @Size(max = 1000) private List<@NotNull @Positive Long> openItemIds;
        @Size(max = 500) private String remark = "";
    }
    @Data public static class PaymentInput {
        @NotBlank @Size(max = 64) private String requestKey;
        @NotNull @Pattern(regexp = "AR|AP") private String direction;
        @NotNull @Positive private Long partnerId;
        @NotNull private LocalDate paymentDate;
        @NotNull @DecimalMin("0.01") @Digits(integer = 16, fraction = 2) private BigDecimal amount;
        @NotNull @Size(max = 128) private String bankReference = "";
        @NotNull @Size(max = 500) private String remark = "";
    }
    @Data public static class Allocate {
        @NotBlank @Size(max = 64) private String requestKey;
        @NotNull @Positive private Long paymentId;
        @NotNull @Positive private Long openItemId;
        @NotNull @DecimalMin("0.01") @Digits(integer = 16, fraction = 2) private BigDecimal amount;
    }
    public record TaxInput(@NotNull @Min(0) @Max(100) Integer taxRate) {}
    public record Summary(BigDecimal grossAmount, BigDecimal settledAmount, BigDecimal outstandingAmount,
                          BigDecimal overdueAmount, BigDecimal unallocatedAmount, Long pendingTaxCount) {}
    public record StatementDetail(Statement statement, List<OpenItem> items, List<Allocation> allocations) {}
    public record PaymentDetail(Payment payment, List<Allocation> allocations) {}
}
