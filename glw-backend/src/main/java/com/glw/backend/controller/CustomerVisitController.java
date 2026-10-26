package com.glw.backend.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.glw.backend.common.ApiResponse;
import com.glw.backend.model.dto.*;
import com.glw.backend.model.entity.CustomerVisit;
import com.glw.backend.model.vo.CustomerVisitVO;
import com.glw.backend.service.CrmManagementService;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/customer-visit")
@Profile("!mock")
@AllArgsConstructor
public class CustomerVisitController {
    private final CrmManagementService management;

    @PostMapping("/list")
    public ApiResponse<IPage<CustomerVisitVO>> list(@RequestBody PageDTO query) {
        return ApiResponse.ok(management.visits(query));
    }
    @PostMapping
    public ApiResponse<CustomerVisit> create(@Valid @RequestBody CustomerVisitSaveDTO input) {
        return ApiResponse.ok(management.saveVisit(null, input));
    }
    @PutMapping("/{id}")
    public ApiResponse<CustomerVisit> update(@PathVariable Long id, @Valid @RequestBody CustomerVisitSaveDTO input) {
        return ApiResponse.ok(management.saveVisit(id, input));
    }
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id, @RequestParam Integer version) {
        management.deleteVisit(id, version);
        return ApiResponse.ok(null);
    }
}
