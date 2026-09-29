package com.glw.backend.model.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 采购订单状态，编码与 glw.sql 保持一致。 */
@Getter
@RequiredArgsConstructor
public enum PurchaseOrderStatus {
    PENDING_RECEIPT("PENDING_RECEIPT", "待收货"),
    PARTIALLY_RECEIVED("PARTIALLY_RECEIVED", "部分收货"),
    COMPLETED("COMPLETED", "已完成");

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
