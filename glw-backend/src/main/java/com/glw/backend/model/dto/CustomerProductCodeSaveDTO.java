package com.glw.backend.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CustomerProductCodeSaveDTO {
    /** 新建时必填；编辑时忽略（不允许改客户）。 */
    private Long customerId;
    @NotNull private Long productId;
    @NotBlank @Size(max = 64) private String customerCode;
    private Boolean enabled;
    @Size(max = 256) private String remark;
    @PositiveOrZero private Integer version;
}
