package com.glw.backend.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.glw.backend.model.dto.DepartmentInput;
import com.glw.backend.model.entity.SysDepartment;
import com.glw.backend.model.vo.DepartmentVO;

import java.util.List;

/** 部门服务：维护部门树、同级名称唯一和上下级关系。 */
public interface SysDepartmentService extends IService<SysDepartment> {
    /** 查询部门树；enabledOnly 为 true 时只返回启用部门。 */
    List<DepartmentVO> tree(boolean enabledOnly);

    DepartmentVO create(DepartmentInput input);

    DepartmentVO update(Long id, DepartmentInput input);

    /** 有下级部门或有用户时不允许删除。 */
    void deleteDepartment(Long id);
}
