package com.glw.backend.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.glw.backend.mapper.InventoryMapper;
import com.glw.backend.model.entity.Inventory;
import com.glw.backend.service.InventoryService;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/** 库存表基础服务实现。 */
@Service
@Profile("!mock")
public class InventoryServiceImpl extends ServiceImpl<InventoryMapper, Inventory>
        implements InventoryService {}
