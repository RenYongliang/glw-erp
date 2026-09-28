package com.glw.backend.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.glw.backend.model.entity.Customer;

/** 客户表基础服务。业务状态流转及关联表事务在此基础上扩展。 */
public interface CustomerService extends IService<Customer> {}
