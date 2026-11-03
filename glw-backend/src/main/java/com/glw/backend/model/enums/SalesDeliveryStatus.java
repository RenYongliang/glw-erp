package com.glw.backend.model.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 销售订单发货状态，编码与 glw.sql 保持一致。 */
@Getter
@RequiredArgsConstructor
public enum SalesDeliveryStatus {
    PENDING_SHIPMENT("PENDING_SHIPMENT", "未发货"),
    PARTIALLY_SHIPPED("PARTIALLY_SHIPPED", "部分发货"),
    SHIPPED("SHIPPED", "全部发货");

    @EnumValue @JsonValue private final String code;

    private final String description;

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static SalesDeliveryStatus fromCode(String code) {
        if (code == null) return null;
        for (SalesDeliveryStatus value : values()) {
            if (value.code.equals(code)) return value;
        }
        throw new IllegalArgumentException("未知的销售订单发货状态编码: " + code);
    }
}
