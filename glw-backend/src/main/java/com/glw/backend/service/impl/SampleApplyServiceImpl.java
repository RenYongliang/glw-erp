package com.glw.backend.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.glw.backend.mapper.SampleApplyMapper;
import com.glw.backend.model.entity.SampleApply;
import com.glw.backend.service.SampleApplyService;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/** 样品申请表基础服务实现。 */
@Service
@Profile("!mock")
public class SampleApplyServiceImpl extends ServiceImpl<SampleApplyMapper, SampleApply>
        implements SampleApplyService {}
