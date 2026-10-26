package com.glw.backend.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.glw.backend.common.ApiResponse;
import com.glw.backend.model.dto.PageDTO;
import com.glw.backend.model.entity.Customer;
import com.glw.backend.model.vo.CustomerOptionVO;
import com.glw.backend.service.CustomerService;
import com.glw.backend.service.CrmManagementService;
import com.glw.backend.model.dto.CustomerSaveDTO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import lombok.AllArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/customer")
@Profile("!mock")
@AllArgsConstructor
public class CustomerController {
    private final CustomerService customerService;
    private final CrmManagementService management;

    @PostMapping("/list")
    public ApiResponse<IPage<CustomerOptionVO>> list(@RequestBody PageDTO pageDTO) {
        IPage<Customer> page = customerService.lambdaQuery().orderByAsc(Customer::getId)
                .page(pageDTO.toPage());
        return ApiResponse.ok(page.convert(customer -> new CustomerOptionVO(customer.getId(),
                customer.getName(), customer.getShortName(), customer.getAddress(),
                customer.getContact(), customer.getContactPhone(), customer.getSettleType(), customer.getVersion(), customer.getCategory())));
    }

    @PostMapping
    public ApiResponse<Customer> create(@Valid @RequestBody CustomerSaveDTO input) {
        return ApiResponse.ok(management.saveCustomer(null, input));
    }
    @PutMapping("/{id}")
    public ApiResponse<Customer> update(@PathVariable Long id, @Valid @RequestBody CustomerSaveDTO input) {
        return ApiResponse.ok(management.saveCustomer(id, input));
    }
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id, @RequestParam Integer version) {
        management.deleteCustomer(id, version);
        return ApiResponse.ok(null);
    }
}
