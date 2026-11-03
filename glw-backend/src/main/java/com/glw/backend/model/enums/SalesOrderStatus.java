package com.glw.backend.model.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 销售订单状态（生命周期），编码与 glw.sql 保持一致。 */
@Getter
@RequiredArgsConstructor
public enum SalesOrderStatus {
    /** 预留：以后启用审核时使用，当前创建订单直接进入 CONFIRMED。 */
    PENDING_APPROVAL("PENDING_APPROVAL", "待审核"),
    CONFIRMED("CONFIRMED", "执行中"),
    /** 有效数量全部发货且应收全部核销。 */
    COMPLETED("COMPLETED", "已完结"),
    CANCELLED("CANCELLED", "已取消");

    @EnumValue @JsonValue private final String code;

    private final String description;

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static SalesOrderStatus fromCode(String code) {
        if (code == null) return null;
        for (SalesOrderStatus value : values()) {
            if (value.code.equals(code)) return value;
        }
        throw new IllegalArgumentException("未知的销售订单状态编码: " + code);
    }
}
