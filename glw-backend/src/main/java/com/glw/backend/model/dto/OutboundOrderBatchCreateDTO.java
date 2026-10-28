package com.glw.backend.model.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import lombok.Data;

@Data
public class OutboundOrderBatchCreateDTO {
    @NotNull
    private Long inventoryId;

    @NotNull
    @Min(1)
    @Max(100000000)
    private Integer quantity;
}
