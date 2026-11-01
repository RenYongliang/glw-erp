package com.glw.backend.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.glw.backend.mapper.SupplierBrandMapper;
import com.glw.backend.model.entity.SupplierBrand;
import com.glw.backend.service.SupplierBrandService;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/** 供应商品牌表基础服务实现。 */
@Service
@Profile("!mock")
public class SupplierBrandServiceImpl extends ServiceImpl<SupplierBrandMapper, SupplierBrand>
        implements SupplierBrandService {}
