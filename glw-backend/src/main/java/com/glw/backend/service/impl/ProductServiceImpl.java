package com.glw.backend.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.glw.backend.mapper.ProductMapper;
import com.glw.backend.model.entity.Product;
import com.glw.backend.service.ProductService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.glw.backend.model.dto.ProductPageDTO;
import com.glw.backend.model.vo.ProductOptionVO;
import com.glw.backend.model.vo.ProductCostBatchVO;
import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/** 产品信息表基础服务实现。 */
@Service
@Profile("!mock")
public class ProductServiceImpl extends ServiceImpl<ProductMapper, Product>
        implements ProductService {
    @Override
    public IPage<ProductOptionVO> pageOptions(ProductPageDTO pageDTO) {
        String keyword = pageDTO.getKeyword();
        return baseMapper.selectOptions(pageDTO.toPage(), keyword == null ? null : keyword.trim());
    }

    @Override
    public List<ProductCostBatchVO> costBatches(Long productId) {
        return baseMapper.selectCostBatches(productId);
    }
}
