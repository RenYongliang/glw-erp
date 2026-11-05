package com.glw.backend.controller;

import com.glw.backend.common.ApiResponse;
import com.glw.backend.model.dto.DepartmentInput;
import com.glw.backend.model.vo.DepartmentVO;
import com.glw.backend.service.SysDepartmentService;

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

/** 部门管理接口。 */
@RestController
@RequestMapping("/api/department")
@AllArgsConstructor
@Profile("!mock")
public class SysDepartmentController {
    private final SysDepartmentService departmentService;

    /** 部门树；选择器传 enabledOnly=true 只返回启用部门。 */
    @GetMapping("/tree")
    public ApiResponse<List<DepartmentVO>> tree(
            @RequestParam(defaultValue = "false") boolean enabledOnly) {
        return ApiResponse.ok(departmentService.tree(enabledOnly));
    }

    @PostMapping
    public ApiResponse<DepartmentVO> create(@Valid @RequestBody DepartmentInput input) {
        return ApiResponse.ok(departmentService.create(input));
    }

    @PutMapping("/{id}")
    public ApiResponse<DepartmentVO> update(
            @PathVariable Long id, @Valid @RequestBody DepartmentInput input) {
        return ApiResponse.ok(departmentService.update(id, input));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        departmentService.deleteDepartment(id);
        return ApiResponse.ok(null);
    }
}
