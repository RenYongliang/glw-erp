package com.glw.backend.controller;

import com.glw.backend.common.ApiResponse;
import com.glw.backend.model.dto.CustomerProductCodeSaveDTO;
import com.glw.backend.model.vo.CustomerProductCodeVO;
import com.glw.backend.service.CustomerProductCodeManagementService;
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

/** 客户物料编码对照维护。 */
@RestController
@RequestMapping("/api/customer-product-code")
@Profile("!mock")
@AllArgsConstructor
public class CustomerProductCodeController {
    private final CustomerProductCodeManagementService service;

    @GetMapping
    public ApiResponse<List<CustomerProductCodeVO>> list(@RequestParam Long customerId) {
        return ApiResponse.ok(service.list(customerId));
    }

    @PostMapping
    public ApiResponse<CustomerProductCodeVO> create(
            @Valid @RequestBody CustomerProductCodeSaveDTO input) {
        return ApiResponse.ok(service.save(null, input));
    }

    @PutMapping("/{id}")
    public ApiResponse<CustomerProductCodeVO> update(
            @PathVariable Long id, @Valid @RequestBody CustomerProductCodeSaveDTO input) {
        return ApiResponse.ok(service.save(id, input));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id, @RequestParam Integer version) {
        service.delete(id, version);
        return ApiResponse.ok(null);
    }
}
