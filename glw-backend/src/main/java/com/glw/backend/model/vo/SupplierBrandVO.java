package com.glw.backend.model.vo;

/** 供应商品牌列表行，附带供应商与品牌名称便于展示。 */
public record SupplierBrandVO(Long id, Long supplierId, String supplierName, Long brandId,
                              String brandName, String remark, Integer version) {}
