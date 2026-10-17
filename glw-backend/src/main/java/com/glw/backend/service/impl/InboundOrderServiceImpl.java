package com.glw.backend.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.glw.backend.converter.InboundOrderConverter;
import com.glw.backend.mapper.InboundOrderMapper;
import com.glw.backend.model.dto.InboundOrderPageDTO;
import com.glw.backend.model.entity.InboundOrder;
import com.glw.backend.model.entity.InboundOrderItem;
import com.glw.backend.model.vo.InboundOrderVO;
import com.glw.backend.service.InboundOrderItemService;
import com.glw.backend.service.InboundOrderService;

import lombok.AllArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 入库表基础服务实现。 */
@Service
@Profile("!mock")
@AllArgsConstructor
public class InboundOrderServiceImpl extends ServiceImpl<InboundOrderMapper, InboundOrder>
        implements InboundOrderService {

    private final InboundOrderItemService inboundOrderItemService;
    private final InboundOrderConverter inboundOrderConverter;

    @Override
    public IPage<InboundOrderVO> pageInboundOrder(InboundOrderPageDTO pageDTO) {
        Page<InboundOrder> page = lambdaQuery()
                .eq(pageDTO.getId() != null, InboundOrder::getId, pageDTO.getId())
                .eq(pageDTO.getPurchaseOrderId() != null, InboundOrder::getPurchaseOrderId, pageDTO.getPurchaseOrderId())
                .eq(StringUtils.hasText(pageDTO.getPurchaseOrderNo()), InboundOrder::getPurchaseOrderNo, pageDTO.getPurchaseOrderNo())
                .eq(pageDTO.getSupplierId() != null, InboundOrder::getSupplierId, pageDTO.getSupplierId())
                .like(StringUtils.hasText(pageDTO.getSupplierName()), InboundOrder::getSupplierName, pageDTO.getSupplierName())
                .eq(pageDTO.getInboundType() != null, InboundOrder::getInboundType, pageDTO.getInboundType())
                .eq(pageDTO.getStatus() != null, InboundOrder::getStatus, pageDTO.getStatus())
                .ge(StringUtils.hasText(pageDTO.getInboundTimeStart()), InboundOrder::getInboundTimeStart, pageDTO.getInboundTimeStart())
                .le(StringUtils.hasText(pageDTO.getInboundTimeEnd()), InboundOrder::getInboundTimeEnd, pageDTO.getInboundTimeEnd())
                .orderByDesc(InboundOrder::getInboundTime)
                .orderByDesc(InboundOrder::getId)
                .page(pageDTO.toPage());

        IPage<InboundOrderVO> result = page.convert(inboundOrderConverter::toVO);
        fillItems(result.getRecords());
        return result;
    }

    private void fillItems(List<InboundOrderVO> records) {
        if (CollectionUtils.isEmpty(records)) return;

        List<Long> orderIds = records.stream().map(InboundOrderVO::getId).toList();
        Map<Long, List<InboundOrderItem>> items = inboundOrderItemService.lambdaQuery()
                .in(InboundOrderItem::getInboundOrderId, orderIds)
                .orderByAsc(InboundOrderItem::getId)
                .list()
                .stream()
                .collect(Collectors.groupingBy(InboundOrderItem::getInboundOrderId));
        records.forEach(order -> order.setItems(
                items.getOrDefault(order.getId(), List.of()).stream()
                        .map(inboundOrderConverter::toItemVO)
                        .toList()));
    }
}
