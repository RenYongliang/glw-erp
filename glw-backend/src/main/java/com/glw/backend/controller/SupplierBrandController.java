package com.glw.backend.controller;

import com.glw.backend.common.ApiResponse;
import com.glw.backend.model.dto.SupplierBrandSaveDTO;
import com.glw.backend.model.vo.SupplierBrandVO;
import com.glw.backend.service.SupplierManagementService;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/supplier-brand")
@Profile("!mock")
@AllArgsConstructor
public class SupplierBrandController {
    private final SupplierManagementService management;

    @GetMapping("/list")
    public ApiResponse<List<SupplierBrandVO>> list(
            @RequestParam(required = false) Long supplierId,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(management.brands(supplierId, keyword));
    }

    @PostMapping
    public ApiResponse<SupplierBrandVO> create(@Valid @RequestBody SupplierBrandSaveDTO input) {
        return ApiResponse.ok(management.saveBrand(null, input));
    }

    @PutMapping("/{id}")
    public ApiResponse<SupplierBrandVO> update(@PathVariable Long id,
                                               @Valid @RequestBody SupplierBrandSaveDTO input) {
        return ApiResponse.ok(management.saveBrand(id, input));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id, @RequestParam Integer version) {
        management.deleteBrand(id, version);
        return ApiResponse.ok(null);
    }
}
