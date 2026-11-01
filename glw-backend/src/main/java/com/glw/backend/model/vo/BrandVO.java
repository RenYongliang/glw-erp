package com.glw.backend.model.vo;

/** 品牌主数据列表行。 */
public record BrandVO(Long id, String code, String name, String manufacturer, Boolean enabled,
                      String remark, Integer version) {}
