package com.glw.backend.model.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 收款状态，编码与 glw.sql 保持一致。 */
@Getter
@RequiredArgsConstructor
public enum SalesPaymentStatus {
    UNPAID("UNPAID", "待收款"),
    PARTIALLY_PAID("PARTIALLY_PAID", "部分收款"),
    PAID("PAID", "已收款");

    @EnumValue @JsonValue private final String code;

    private final String description;

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static SalesPaymentStatus fromCode(String code) {
        if (code == null) return null;
        for (SalesPaymentStatus value : values()) {
            if (value.code.equals(code)) return value;
        }
        throw new IllegalArgumentException("未知的收款状态编码: " + code);
    }
}
