package com.glw.backend.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.glw.backend.common.ApiResponse;
import com.glw.backend.model.dto.OutboundOrderCreateDTO;
import com.glw.backend.model.dto.OutboundOrderPageDTO;
import com.glw.backend.model.vo.OutboundOrderVO;
import com.glw.backend.service.OutboundOrderService;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/outbound-order")
@Profile("!mock")
@AllArgsConstructor
public class OutboundOrderController {
    private final OutboundOrderService outboundOrderService;

    @PostMapping("/list")
    public ApiResponse<IPage<OutboundOrderVO>> list(@RequestBody OutboundOrderPageDTO pageDTO) {
        return ApiResponse.ok(outboundOrderService.pageOutboundOrder(pageDTO));
    }

    @PostMapping
    public ApiResponse<OutboundOrderVO> create(@Valid @RequestBody OutboundOrderCreateDTO createDTO) {
        return ApiResponse.ok(outboundOrderService.createOutboundOrder(createDTO));
    }
}
