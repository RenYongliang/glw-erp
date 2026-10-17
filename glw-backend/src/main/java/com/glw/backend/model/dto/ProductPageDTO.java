package com.glw.backend.model.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ProductPageDTO extends PageDTO {
    private String keyword;
}
