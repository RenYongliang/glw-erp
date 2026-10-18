package com.glw.backend.model.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import lombok.Data;

import java.util.List;

@Data
public class InboundOrderCreateDTO {
    @NotNull
    private Long purchaseOrderId;

    private String inboundTime;

    @Valid
    @NotEmpty
    private List<InboundOrderItemCreateDTO> items;
}
