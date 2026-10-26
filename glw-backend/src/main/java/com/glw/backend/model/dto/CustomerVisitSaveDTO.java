package com.glw.backend.model.dto;

import com.glw.backend.model.enums.TaxIncluded;
import jakarta.validation.constraints.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class CustomerVisitSaveDTO {
    @NotNull @Positive private Long customerId;
    @NotNull private LocalDateTime visitTime;
    @Size(max = 64) private String project;
    @Size(max = 64) private String originalPartNo;
    @Positive private Long productId;
    @PositiveOrZero private Integer demandQuantity;
    @NotNull @DecimalMin("0") @Digits(integer = 8, fraction = 4) private BigDecimal unitPrice;
    @NotNull private TaxIncluded taxIncluded;
    @Size(max = 64) private String progress;
    @Pattern(regexp = "^(初次沟通|需求沟通|方案确认|报价|合同签署|销售回访)?$") private String stage;
    @Size(max = 16000) private String detail;
    @PositiveOrZero private Integer version;
}
