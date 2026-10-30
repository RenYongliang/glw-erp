package com.glw.backend.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import lombok.Data;

import java.util.List;

/** 出库单签收登记：可批量登记同一签收日期。 */
@Data
public class OutboundOrderSignDTO {
    @NotEmpty
    @Size(max = 200)
    private List<@NotNull Long> orderIds;

    /** 客户签收日期，支持 yyyy-MM-dd 或 yyyy-MM-dd HH:mm:ss。 */
    @NotBlank
    private String signTime;
}
