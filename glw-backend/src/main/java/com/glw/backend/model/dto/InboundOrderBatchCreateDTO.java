package com.glw.backend.model.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import lombok.Data;

@Data
public class InboundOrderBatchCreateDTO {
    @NotBlank
    @Size(max = 32)
    private String dateCode;

    @NotBlank
    @Size(max = 64)
    private String lotCode;

    @NotNull
    @Min(1)
    @Max(100000000)
    private Integer inboundQuantity;
}
