package com.glw.backend.model.vo;

import lombok.Data;

@Data
public class CustomerProductCodeVO {
    private Long id;
    private Long customerId;
    private Long productId;
    private String brand;
    private String partNo;
    private String packageType;
    private String customerCode;
    private String source;
    private Boolean enabled;
    private String remark;
    private Integer version;
}
