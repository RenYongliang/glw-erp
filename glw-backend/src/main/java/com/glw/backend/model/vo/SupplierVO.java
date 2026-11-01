package com.glw.backend.model.vo;

import java.util.List;

/** 供应商列表/详情，品牌以名称列表返回。 */
public record SupplierVO(Long id, String code, String name, String shortName, String category,
                         Boolean enabled, String address, String contact, String contactPhone,
                         String taxNo, String bankName, String bankAccount, String accountName,
                         String remark, Integer version, List<String> brands) {}
