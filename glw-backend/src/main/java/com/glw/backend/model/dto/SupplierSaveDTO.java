package com.glw.backend.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SupplierSaveDTO {
    @Size(max = 32) private String code;
    @NotBlank @Size(max = 255) private String name;
    @Size(max = 64) private String shortName;
    @Size(max = 32) private String category;
    private Boolean enabled = Boolean.TRUE;
    @Size(max = 255) private String address;
    @Size(max = 32) private String contact;
    @Size(max = 32) private String contactPhone;
    @Size(max = 32) private String taxNo;
    @Size(max = 128) private String bankName;
    @Size(max = 64) private String bankAccount;
    @Size(max = 128) private String accountName;
    @Size(max = 255) private String remark;
    @PositiveOrZero private Integer version;
}
