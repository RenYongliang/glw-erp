package com.glw.backend.model.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 是否送样，编码与 glw.sql 保持一致。 */
@Getter
@RequiredArgsConstructor
public enum SampleDeliveryStatus {
    NOT_DELIVERED(0, "未送样"),
    DELIVERED(1, "已送样");

    @EnumValue @JsonValue private final Integer code;

    private final String description;

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static SampleDeliveryStatus fromCode(Integer code) {
        if (code == null) return null;
        for (SampleDeliveryStatus value : values()) {
            if (value.code.equals(code)) return value;
        }
        throw new IllegalArgumentException("未知的是否送样编码: " + code);
    }
}
