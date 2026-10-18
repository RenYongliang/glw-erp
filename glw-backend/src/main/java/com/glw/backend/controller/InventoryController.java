package com.glw.backend.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.glw.backend.common.ApiResponse;
import com.glw.backend.model.dto.InventoryPageDTO;
import com.glw.backend.model.vo.InventoryProductVO;
import com.glw.backend.service.InventoryService;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/inventory")
@Profile("!mock")
@AllArgsConstructor
public class InventoryController {
    private final InventoryService inventoryService;

    @PostMapping("/list")
    public ApiResponse<IPage<InventoryProductVO>> list(@Valid @RequestBody InventoryPageDTO query) {
        return ApiResponse.ok(inventoryService.pageProducts(query));
    }
}
