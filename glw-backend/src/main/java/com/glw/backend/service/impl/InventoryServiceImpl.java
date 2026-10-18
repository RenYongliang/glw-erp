package com.glw.backend.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.glw.backend.mapper.InventoryMapper;
import com.glw.backend.model.entity.Inventory;
import com.glw.backend.service.InventoryService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.glw.backend.model.dto.InventoryPageDTO;
import com.glw.backend.model.vo.InventoryBatchVO;
import com.glw.backend.model.vo.InventoryProductVO;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/** 库存表基础服务实现。 */
@Service
@Profile("!mock")
public class InventoryServiceImpl extends ServiceImpl<InventoryMapper, Inventory>
        implements InventoryService {
    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public IPage<InventoryProductVO> pageProducts(InventoryPageDTO query) {
        query.setKeyword(trim(query.getKeyword()));
        query.setBrand(trim(query.getBrand()));
        query.setPartNo(trim(query.getPartNo()));
        query.setPackageType(trim(query.getPackageType()));
        IPage<InventoryProductVO> page = baseMapper.selectProductStock(query.toPage(), query);
        if (page.getRecords().isEmpty()) return page;
        List<Long> ids = page.getRecords().stream().map(InventoryProductVO::getProductId).toList();
        var batches = baseMapper.selectAvailableBatches(ids).stream()
                .collect(Collectors.groupingBy(InventoryBatchVO::getProductId));
        page.getRecords().forEach(product -> product.setBatches(
                batches.getOrDefault(product.getProductId(), List.of())));
        return page;
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }
}
