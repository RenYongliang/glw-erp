package com.glw.backend.model.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import lombok.Data;

import java.util.List;

@Data
public class OutboundOrderCreateDTO {
    @NotEmpty
    @Size(max = 100)
    private List<@NotNull Long> salesOrderIds;

    private String outboundTime;

    /** 销售行分配明细；服务端按产品汇总生成出库产品行。 */
    @Valid
    @NotEmpty
    private List<OutboundOrderAllocationCreateDTO> allocations;
}
