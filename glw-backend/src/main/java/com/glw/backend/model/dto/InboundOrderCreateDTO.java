package com.glw.backend.model.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import lombok.Data;

import java.util.List;

@Data
public class InboundOrderCreateDTO {
    @NotEmpty
    @Size(max = 100)
    private List<@NotNull Long> purchaseOrderIds;

    private String inboundTime;

    /** 幂等键；同一 key 的重复提交返回原入库单。 */
    @Size(max = 64)
    private String requestKey;

    /** 采购行分配明细；服务端按产品汇总生成入库产品行。 */
    @Valid
    @NotEmpty
    private List<InboundOrderAllocationCreateDTO> allocations;
}
