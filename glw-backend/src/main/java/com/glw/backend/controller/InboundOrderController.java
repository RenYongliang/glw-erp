package com.glw.backend.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.glw.backend.common.ApiResponse;
import com.glw.backend.model.dto.InboundOrderCreateDTO;
import com.glw.backend.model.dto.InboundOrderPageDTO;
import com.glw.backend.model.vo.InboundOrderVO;
import com.glw.backend.service.InboundOrderService;
import lombok.AllArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@RestController
@RequestMapping("/api/inbound-order")
@AllArgsConstructor
@Profile("!mock")
public class InboundOrderController {
    private final InboundOrderService inboundOrderService;

    @GetMapping("/{id}")
    public ApiResponse<InboundOrderVO> detail(@PathVariable Long id) {
        return ApiResponse.ok(inboundOrderService.detail(id, null));
    }

    @GetMapping("/{id}/purchase-orders/{purchaseOrderId}")
    public ApiResponse<InboundOrderVO> allocation(@PathVariable Long id, @PathVariable Long purchaseOrderId) {
        return ApiResponse.ok(inboundOrderService.detail(id, purchaseOrderId));
    }

    @PostMapping("/list")
    public ApiResponse<IPage<InboundOrderVO>> list(@RequestBody InboundOrderPageDTO pageDTO) {
        return ApiResponse.ok(inboundOrderService.pageInboundOrder(pageDTO));
    }

    @PostMapping
    public ApiResponse<InboundOrderVO> create(@Valid @RequestBody InboundOrderCreateDTO createDTO) {
        return ApiResponse.ok(inboundOrderService.createInboundOrder(createDTO));
    }

    /** 撤销已入库单，回补库存并作废未生效应付。 */
    @PostMapping("/{id}/reverse")
    public ApiResponse<Void> reverse(@PathVariable Long id) {
        inboundOrderService.reverseInboundOrder(id);
        return ApiResponse.ok(null);
    }
}
