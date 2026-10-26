package com.glw.backend.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.glw.backend.common.ApiResponse;
import com.glw.backend.model.dto.PageDTO;
import com.glw.backend.model.entity.Customer;
import com.glw.backend.model.vo.CustomerOptionVO;
import com.glw.backend.service.CustomerService;
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

    @PostMapping("/list")
    public ApiResponse<IPage<CustomerOptionVO>> list(@RequestBody PageDTO pageDTO) {
        IPage<Customer> page = customerService.lambdaQuery().orderByAsc(Customer::getId)
                .page(pageDTO.toPage());
        return ApiResponse.ok(page.convert(customer -> new CustomerOptionVO(customer.getId(),
                customer.getName(), customer.getShortName(), customer.getAddress(),
                customer.getContact(), customer.getContactPhone(), customer.getSettleType())));
    }
}
