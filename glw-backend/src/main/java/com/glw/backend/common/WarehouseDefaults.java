package com.glw.backend.common;

/** 当前业务固定使用一个仓库，字段先落库，后续扩展多仓时再替换为仓库主数据。 */
public final class WarehouseDefaults {
    public static final String CODE = "SZYT";
    public static final String NAME = "深圳盐田仓";

    private WarehouseDefaults() {}
}
