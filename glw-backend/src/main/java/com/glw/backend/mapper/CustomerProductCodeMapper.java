package com.glw.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.glw.backend.model.entity.CustomerProductCode;
import com.glw.backend.model.vo.CustomerProductCodeVO;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/** 客户物料编码对照表数据访问。 */
public interface CustomerProductCodeMapper extends BaseMapper<CustomerProductCode> {
    List<CustomerProductCodeVO> selectByCustomer(@Param("customerId") Long customerId);
}
