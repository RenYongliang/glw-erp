package com.glw.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.glw.backend.model.entity.FinAllocation;
import com.glw.backend.finance.FinanceModels.Allocation;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface FinAllocationMapper extends BaseMapper<FinAllocation> {
    int insertAllocation(Allocation allocation);
    Allocation allocation(Long id);
    Allocation allocationByKey(String key);
    List<Allocation> allocations(@Param("paymentId") Long paymentId, @Param("statementId") Long statementId);
    int reverse(Long id);
}
