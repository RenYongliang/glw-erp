package com.glw.backend.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.glw.backend.common.ApiResponse;
import com.glw.backend.model.dto.PurchaseOrderCreateDTO;
import com.glw.backend.model.dto.PurchaseOrderPageDTO;
import com.glw.backend.model.vo.PurchaseOrderVO;
import com.glw.backend.service.PurchaseOrderService;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/purchase-order")
@AllArgsConstructor
@Profile("!mock")
public class PurchaseOrderController {
    private final PurchaseOrderService purchaseOrderService;

    @PostMapping("/list")
    public ApiResponse<IPage<PurchaseOrderVO>> list(@RequestBody PurchaseOrderPageDTO pageDTO) {
        return ApiResponse.ok(purchaseOrderService.pagePurchaseOrder(pageDTO));
    }

    @PostMapping
    public ApiResponse<PurchaseOrderVO> create(@Valid @RequestBody PurchaseOrderCreateDTO createDTO) {
        return ApiResponse.ok(purchaseOrderService.createPurchaseOrder(createDTO));
    }

    @PutMapping("/{id}")
    public ApiResponse<PurchaseOrderVO> update(
            @PathVariable Long id, @Valid @RequestBody PurchaseOrderCreateDTO createDTO) {
        return ApiResponse.ok(purchaseOrderService.updatePurchaseOrder(id, createDTO));
    }
}
