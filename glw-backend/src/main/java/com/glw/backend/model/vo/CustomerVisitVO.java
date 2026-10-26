package com.glw.backend.model.vo;

import com.glw.backend.model.entity.CustomerVisit;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class CustomerVisitVO extends CustomerVisit {
    private String customerName;
    private String customerShortName;
    private String brand;
    private String partNo;
    private String packageType;
}
