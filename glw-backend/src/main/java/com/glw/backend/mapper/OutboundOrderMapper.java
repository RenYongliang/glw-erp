package com.glw.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.glw.backend.model.entity.OutboundOrder;
import org.apache.ibatis.annotations.Param;

/** 出库表数据访问。由非 mock 模式的 MapperScan 注册。 */
public interface OutboundOrderMapper extends BaseMapper<OutboundOrder> {
    /** 清空客户签收时间，并递增乐观锁版本。 */
    int clearSign(@Param("id") Long id, @Param("version") Integer version);
}
