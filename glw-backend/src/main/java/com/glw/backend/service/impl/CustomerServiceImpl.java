package com.glw.backend.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.glw.backend.mapper.CustomerMapper;
import com.glw.backend.model.entity.Customer;
import com.glw.backend.service.CustomerService;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/** 客户表基础服务实现。 */
@Service
@Profile("!mock")
public class CustomerServiceImpl extends ServiceImpl<CustomerMapper, Customer>
        implements CustomerService {}
