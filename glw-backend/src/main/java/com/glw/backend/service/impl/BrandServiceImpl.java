package com.glw.backend.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.glw.backend.mapper.BrandMapper;
import com.glw.backend.model.entity.Brand;
import com.glw.backend.service.BrandService;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/** 品牌主数据表基础服务实现。 */
@Service
@Profile("!mock")
public class BrandServiceImpl extends ServiceImpl<BrandMapper, Brand> implements BrandService {}
