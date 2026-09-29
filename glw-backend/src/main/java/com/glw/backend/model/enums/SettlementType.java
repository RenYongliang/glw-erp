package com.glw.backend.model.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 销售结算类型，编码与 glw.sql 保持一致。 */
@Getter
@RequiredArgsConstructor
public enum SettlementType {
    CASH("CASH", "现金"),
    MONTHLY("MONTHLY", "月结"),
    PREPAID("PREPAID", "预付"),
    COD("COD", "货到付款");

    @EnumValue @JsonValue private final String code;

    private final String description;

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static SettlementType fromCode(String code) {
        if (code == null) return null;
        for (SettlementType value : values()) {
            if (value.code.equals(code)) return value;
        }
        throw new IllegalArgumentException("未知的销售结算类型编码: " + code);
    }
}
