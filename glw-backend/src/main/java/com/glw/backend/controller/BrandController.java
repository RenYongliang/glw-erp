package com.glw.backend.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.glw.backend.common.ApiResponse;
import com.glw.backend.model.dto.BrandSaveDTO;
import com.glw.backend.model.dto.PageDTO;
import com.glw.backend.model.vo.BrandOptionVO;
import com.glw.backend.model.vo.BrandVO;
import com.glw.backend.service.BrandManagementService;
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
@RequestMapping("/api/brand")
@Profile("!mock")
@AllArgsConstructor
public class BrandController {
    private final BrandManagementService management;

    @PostMapping("/list")
    public ApiResponse<IPage<BrandVO>> list(@RequestBody PageDTO query) {
        return ApiResponse.ok(management.page(query));
    }

    @GetMapping("/options")
    public ApiResponse<List<BrandOptionVO>> options() {
        return ApiResponse.ok(management.options());
    }

    @PostMapping
    public ApiResponse<BrandVO> create(@Valid @RequestBody BrandSaveDTO input) {
        return ApiResponse.ok(management.saveBrand(null, input));
    }

    @PutMapping("/{id}")
    public ApiResponse<BrandVO> update(@PathVariable Long id,
                                       @Valid @RequestBody BrandSaveDTO input) {
        return ApiResponse.ok(management.saveBrand(id, input));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id, @RequestParam Integer version) {
        management.deleteBrand(id, version);
        return ApiResponse.ok(null);
    }
}
