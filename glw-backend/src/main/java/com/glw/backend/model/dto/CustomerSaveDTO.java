package com.glw.backend.model.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class CustomerSaveDTO {
    @NotBlank @Size(max = 255) private String name;
    @Size(max = 64) private String shortName;
    @Pattern(regexp = "^(潜在客户|成交客户|战略合作)?$") private String category;
    @Size(max = 255) private String address;
    @Size(max = 32) private String contact;
    @Size(max = 11) private String contactPhone;
    @Size(max = 64) private String settleType;
    @PositiveOrZero private Integer version;
}
