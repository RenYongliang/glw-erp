package com.glw.backend.model.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 出库类型，编码与 glw.sql 保持一致。 */
@Getter
@RequiredArgsConstructor
public enum OutboundType {
    SALE("SALE", "销售出库");

    @EnumValue @JsonValue private final String code;

    private final String description;

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static OutboundType fromCode(String code) {
        if (code == null) return null;
        for (OutboundType value : values()) {
            if (value.code.equals(code)) return value;
        }
        throw new IllegalArgumentException("未知的出库类型编码: " + code);
    }
}
