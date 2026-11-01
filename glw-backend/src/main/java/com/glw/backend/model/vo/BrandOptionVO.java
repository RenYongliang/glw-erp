package com.glw.backend.model.vo;

/** 品牌下拉选项，仅返回启用品牌。 */
public record BrandOptionVO(Long id, String code, String name, String manufacturer) {}
