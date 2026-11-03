package com.glw.backend.model.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 采购订单状态（生命周期），编码与 glw.sql 保持一致。 */
@Getter
@RequiredArgsConstructor
public enum PurchaseOrderStatus {
    /** 预留：以后启用审核时使用，当前创建订单直接进入 CONFIRMED。 */
    PENDING_APPROVAL("PENDING_APPROVAL", "待审核"),
    CONFIRMED("CONFIRMED", "执行中"),
    /** 全部收货且应付全部核销。 */
    COMPLETED("COMPLETED", "已完结"),
    CANCELLED("CANCELLED", "已取消");

    @EnumValue @JsonValue private final String code;

    private final String description;

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static PurchaseOrderStatus fromCode(String code) {
        if (code == null) return null;
        for (PurchaseOrderStatus value : values()) {
            if (value.code.equals(code)) return value;
        }
        throw new IllegalArgumentException("未知的采购订单状态编码: " + code);
    }
}
