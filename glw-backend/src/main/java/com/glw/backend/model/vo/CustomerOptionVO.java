package com.glw.backend.model.vo;

public record CustomerOptionVO(Long id, String name, String shortName, String address,
                               String contact, String contactPhone, String settleType, Integer version, String category) {}
