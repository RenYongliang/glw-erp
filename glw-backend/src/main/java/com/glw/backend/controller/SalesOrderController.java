package com.glw.backend.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.glw.backend.common.ApiResponse;
import com.glw.backend.model.dto.SalesOrderPageDTO;
import com.glw.backend.model.vo.SalesOrderVO;
import com.glw.backend.service.SalesOrderService;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sales-order")
@AllArgsConstructor
public class SalesOrderController {


    private final SalesOrderService salesOrderService;

    @PostMapping("/list")
    public ApiResponse<IPage<SalesOrderVO>> list(@RequestBody SalesOrderPageDTO pageDTO) {
        IPage<SalesOrderVO> page = salesOrderService.pageSalesOrder(pageDTO);
        return ApiResponse.ok(page);
    }



}
