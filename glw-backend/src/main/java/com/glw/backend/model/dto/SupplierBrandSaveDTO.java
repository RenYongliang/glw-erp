package com.glw.backend.model.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SupplierBrandSaveDTO {
    @NotNull private Long supplierId;
    @NotNull private Long brandId;
    @Size(max = 255) private String remark;
    @PositiveOrZero private Integer version;
}
