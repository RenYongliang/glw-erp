package com.glw.backend.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.glw.backend.mapper.ProductMapper;
import com.glw.backend.model.entity.Product;
import com.glw.backend.service.ProductService;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/** 产品信息表基础服务实现。 */
@Service
@Profile("!mock")
public class ProductServiceImpl extends ServiceImpl<ProductMapper, Product>
        implements ProductService {}
