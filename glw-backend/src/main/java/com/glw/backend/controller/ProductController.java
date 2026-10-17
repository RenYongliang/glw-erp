package com.glw.backend.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.glw.backend.common.ApiResponse;
import com.glw.backend.model.dto.ProductPageDTO;
import com.glw.backend.model.vo.ProductOptionVO;
import com.glw.backend.model.vo.ProductCostBatchVO;
import java.util.List;
import com.glw.backend.service.ProductService;
import lombok.AllArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/product")
@Profile("!mock")
@AllArgsConstructor
public class ProductController {
    private final ProductService productService;

    @PostMapping("/list")
    public ApiResponse<IPage<ProductOptionVO>> list(@RequestBody ProductPageDTO pageDTO) {
        return ApiResponse.ok(productService.pageOptions(pageDTO));
    }

    @GetMapping("/{productId}/cost-batches")
    public ApiResponse<List<ProductCostBatchVO>> costBatches(@PathVariable Long productId) {
        return ApiResponse.ok(productService.costBatches(productId));
    }
}
