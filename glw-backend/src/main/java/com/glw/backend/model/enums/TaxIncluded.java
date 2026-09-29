package com.glw.backend.model.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 是否含税，编码与 glw.sql 保持一致。 */
@Getter
@RequiredArgsConstructor
public enum TaxIncluded {
    EXCLUDED(0, "未税"),
    INCLUDED(1, "含税");

    @EnumValue @JsonValue private final Integer code;

    private final String description;

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static TaxIncluded fromCode(Integer code) {
        if (code == null) return null;
        for (TaxIncluded value : values()) {
            if (value.code.equals(code)) return value;
        }
        throw new IllegalArgumentException("未知的是否含税编码: " + code);
    }
}
