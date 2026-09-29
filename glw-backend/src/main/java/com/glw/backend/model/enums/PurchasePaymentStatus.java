package com.glw.backend.model.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 付款状态，编码与 glw.sql 保持一致。 */
@Getter
@RequiredArgsConstructor
public enum PurchasePaymentStatus {
    UNPAID("UNPAID", "待付款"),
    PARTIALLY_PAID("PARTIALLY_PAID", "部分付款"),
    PAID("PAID", "已付款");

    @EnumValue @JsonValue private final String code;

    private final String description;

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static PurchasePaymentStatus fromCode(String code) {
        if (code == null) return null;
        for (PurchasePaymentStatus value : values()) {
            if (value.code.equals(code)) return value;
        }
        throw new IllegalArgumentException("未知的付款状态编码: " + code);
    }
}
