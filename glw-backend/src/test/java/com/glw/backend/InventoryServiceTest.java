package com.glw.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.glw.backend.mapper.InventoryMapper;
import com.glw.backend.model.dto.InventoryPageDTO;
import com.glw.backend.model.vo.InventoryBatchVO;
import com.glw.backend.model.vo.InventoryProductVO;
import com.glw.backend.service.impl.InventoryServiceImpl;
import java.util.List;
import org.junit.jupiter.api.Test;

class InventoryServiceTest {
    private static class TestService extends InventoryServiceImpl {
        TestService(InventoryMapper mapper) { baseMapper = mapper; }
    }

    @Test
    void groupsBatchesForOnlyTheCurrentPageAndRetainsProductsWithoutStock() {
        InventoryMapper mapper = mock(InventoryMapper.class);
        var service = new TestService(mapper);
        var query = new InventoryPageDTO();
        query.setKeyword("  ET  ");
        query.setBrand("  Brand  ");
        query.setPartNo("  Part  ");
        query.setPackageType("  DFN  ");
        query.setInStockOnly(false);
        var first = product(4L);
        var second = product(7L);
        var empty = product(9L);
        var page = new Page<InventoryProductVO>(2, 3, 9);
        page.setRecords(List.of(first, second, empty));
        when(mapper.selectProductStock(any(), eq(query))).thenReturn(page);
        var a = batch(4L, 10L);
        var b = batch(7L, 11L);
        var c = batch(4L, 12L);
        when(mapper.selectAvailableBatches(List.of(4L, 7L, 9L))).thenReturn(List.of(a, b, c));

        var result = service.pageProducts(query);

        assertThat(result.getTotal()).isEqualTo(9);
        assertThat(result.getCurrent()).isEqualTo(2);
        assertThat(first.getBatches()).containsExactly(a, c);
        assertThat(second.getBatches()).containsExactly(b);
        assertThat(empty.getBatches()).isEmpty();
        assertThat(query.getKeyword()).isEqualTo("ET");
        assertThat(query.getBrand()).isEqualTo("Brand");
        assertThat(query.getPartNo()).isEqualTo("Part");
        assertThat(query.getPackageType()).isEqualTo("DFN");
        verify(mapper, times(1)).selectAvailableBatches(any());
    }

    @Test
    void emptyPageDoesNotIssueAnUnboundedBatchQuery() {
        InventoryMapper mapper = mock(InventoryMapper.class);
        var query = new InventoryPageDTO();
        when(mapper.selectProductStock(any(), eq(query))).thenReturn(new Page<InventoryProductVO>());
        assertThat(new TestService(mapper).pageProducts(query).getRecords()).isEmpty();
        verify(mapper, never()).selectAvailableBatches(any());
    }

    private InventoryProductVO product(Long id) {
        var product = new InventoryProductVO();
        product.setProductId(id);
        return product;
    }

    private InventoryBatchVO batch(Long productId, Long inventoryId) {
        var batch = new InventoryBatchVO();
        batch.setProductId(productId);
        batch.setInventoryId(inventoryId);
        return batch;
    }
}
