package com.glw.backend.model.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import lombok.Data;

import java.util.List;

@Data
public class OutboundOrderAllocationCreateDTO {
    @NotNull
    private Long salesOrderItemId;

    @Valid
    @NotEmpty
    private List<OutboundOrderBatchCreateDTO> batches;
}
