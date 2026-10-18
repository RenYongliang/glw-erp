package com.glw.backend.model.dto;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class InventoryPageDTO {
    @NotNull @Min(1)
    private Long pageNo = 1L;
    @NotNull @Min(1) @Max(200)
    private Long pageSize = 20L;
    @Size(max = 128)
    private String keyword;
    @Size(max = 128)
    private String brand;
    @Size(max = 128)
    private String partNo;
    @Size(max = 128)
    private String packageType;
    private boolean inStockOnly = true;

    public <T> Page<T> toPage() {
        return new Page<>(pageNo, pageSize);
    }
}
