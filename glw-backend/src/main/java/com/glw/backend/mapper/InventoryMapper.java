package com.glw.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.glw.backend.model.entity.Inventory;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.glw.backend.model.dto.InventoryPageDTO;
import com.glw.backend.model.vo.InventoryBatchVO;
import com.glw.backend.model.vo.InventoryProductVO;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/** 库存表数据访问。由非 mock 模式的 MapperScan 注册。 */
public interface InventoryMapper extends BaseMapper<Inventory> {
    IPage<InventoryProductVO> selectProductStock(Page<InventoryProductVO> page,
            @Param("query") InventoryPageDTO query);
    List<InventoryBatchVO> selectAvailableBatches(@Param("productIds") List<Long> productIds);
}
