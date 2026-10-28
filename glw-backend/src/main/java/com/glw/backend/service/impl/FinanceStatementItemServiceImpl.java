package com.glw.backend.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.glw.backend.mapper.FinStatementItemMapper;
import com.glw.backend.model.entity.FinStatementItem;
import com.glw.backend.service.FinanceStatementItemService;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/** Statement snapshot persistence. */
@Service
@Profile("!mock")
public class FinanceStatementItemServiceImpl extends ServiceImpl<FinStatementItemMapper, FinStatementItem>
        implements FinanceStatementItemService {}
