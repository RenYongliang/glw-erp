package com.glw.backend.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.glw.backend.common.ApiResponse;
import com.glw.backend.model.dto.OutboundOrderCreateDTO;
import com.glw.backend.model.dto.OutboundOrderPageDTO;
import com.glw.backend.model.dto.OutboundOrderSignDTO;
import com.glw.backend.model.dto.OutboundShippingDTO;
import com.glw.backend.model.vo.OutboundOrderVO;

import java.util.List;
import com.glw.backend.service.OutboundOrderService;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
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

    /** 登记客户签收日期；应收记录据此进入对应账单月。 */
    @PostMapping("/sign")
    public ApiResponse<List<OutboundOrderVO>> sign(@Valid @RequestBody OutboundOrderSignDTO signDTO) {
        return ApiResponse.ok(outboundOrderService.signOutboundOrders(signDTO));
    }

    /** 取消客户签收；已进入对账单或已核销的应收不允许取消。 */
    @PostMapping("/{id}/unsign")
    public ApiResponse<Void> unsign(@PathVariable Long id) {
        outboundOrderService.unsignOutboundOrder(id);
        return ApiResponse.ok(null);
    }

    /** 撤销出库单，回补库存并作废未生效应收。 */
    @PostMapping("/{id}/reverse")
    public ApiResponse<Void> reverse(@PathVariable Long id) {
        outboundOrderService.reverseOutboundOrder(id);
        return ApiResponse.ok(null);
    }

    /** 修改收货与物流信息；不影响库存和应收。 */
    @PutMapping("/{id}/shipping")
    public ApiResponse<Void> updateShipping(
            @PathVariable Long id, @Valid @RequestBody OutboundShippingDTO shipping) {
        outboundOrderService.updateShipping(id, shipping);
        return ApiResponse.ok(null);
    }
}
