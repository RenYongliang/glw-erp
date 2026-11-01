package com.glw.backend.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.glw.backend.common.ApiResponse;
import com.glw.backend.model.dto.PageDTO;
import com.glw.backend.model.dto.SupplierSaveDTO;
import com.glw.backend.model.vo.SupplierOptionVO;
import com.glw.backend.model.vo.SupplierVO;
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
@RequestMapping("/api/supplier")
@Profile("!mock")
@AllArgsConstructor
public class SupplierController {
    private final SupplierManagementService management;

    @PostMapping("/list")
    public ApiResponse<IPage<SupplierVO>> list(@RequestBody PageDTO query) {
        return ApiResponse.ok(management.page(query));
    }

    @GetMapping("/options")
    public ApiResponse<List<SupplierOptionVO>> options() {
        return ApiResponse.ok(management.options());
    }

    @PostMapping
    public ApiResponse<SupplierVO> create(@Valid @RequestBody SupplierSaveDTO input) {
        return ApiResponse.ok(management.saveSupplier(null, input));
    }

    @PutMapping("/{id}")
    public ApiResponse<SupplierVO> update(@PathVariable Long id,
                                          @Valid @RequestBody SupplierSaveDTO input) {
        return ApiResponse.ok(management.saveSupplier(id, input));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id, @RequestParam Integer version) {
        management.deleteSupplier(id, version);
        return ApiResponse.ok(null);
    }
}
