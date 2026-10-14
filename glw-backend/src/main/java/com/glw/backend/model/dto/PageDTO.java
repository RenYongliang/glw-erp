package com.glw.backend.model.dto;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PageDTO {

    private Long pageNo;

    private Long pageSize;

    public <T> Page<T> toPage() {
        return new Page<>(pageNo, pageSize);
    }
}
