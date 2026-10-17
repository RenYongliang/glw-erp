package com.glw.backend.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.glw.backend.model.entity.Product;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.glw.backend.model.dto.ProductPageDTO;
import com.glw.backend.model.vo.ProductOptionVO;
import com.glw.backend.model.vo.ProductCostBatchVO;
import java.util.List;

/** 产品信息表基础服务。业务状态流转及关联表事务在此基础上扩展。 */
public interface ProductService extends IService<Product> {
    IPage<ProductOptionVO> pageOptions(ProductPageDTO pageDTO);
    List<ProductCostBatchVO> costBatches(Long productId);
}
