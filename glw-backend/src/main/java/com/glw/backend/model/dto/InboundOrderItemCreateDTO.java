package com.glw.backend.model.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import lombok.Data;

import java.util.List;

@Data
public class InboundOrderItemCreateDTO {
    @NotNull
    private Long purchaseOrderItemId;

    @Valid
    @NotEmpty
    private List<InboundOrderBatchCreateDTO> batches;
}
