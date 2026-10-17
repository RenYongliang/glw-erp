package com.glw.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.glw.backend.model.entity.Product;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.glw.backend.model.vo.ProductOptionVO;
import com.glw.backend.model.vo.ProductCostBatchVO;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/** 产品信息表数据访问。由非 mock 模式的 MapperScan 注册。 */
public interface ProductMapper extends BaseMapper<Product> {
    IPage<ProductOptionVO> selectOptions(Page<ProductOptionVO> page, @Param("keyword") String keyword);
    List<ProductCostBatchVO> selectCostBatches(@Param("productId") Long productId);
}
