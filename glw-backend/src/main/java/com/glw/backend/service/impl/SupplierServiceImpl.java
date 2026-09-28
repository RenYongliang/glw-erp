package com.glw.backend.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.glw.backend.mapper.SupplierMapper;
import com.glw.backend.model.entity.Supplier;
import com.glw.backend.service.SupplierService;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/** 供应商表基础服务实现。 */
@Service
@Profile("!mock")
public class SupplierServiceImpl extends ServiceImpl<SupplierMapper, Supplier>
        implements SupplierService {}
