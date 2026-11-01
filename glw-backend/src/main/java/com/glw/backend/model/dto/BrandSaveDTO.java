package com.glw.backend.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class BrandSaveDTO {
    @Size(max = 32) private String code;
    @NotBlank @Size(max = 64) private String name;
    @Size(max = 128) private String manufacturer;
    private Boolean enabled;
    @Size(max = 255) private String remark;
    @PositiveOrZero private Integer version;
}
