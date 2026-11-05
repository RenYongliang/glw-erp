package com.glw.backend.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.glw.backend.common.BusinessException;
import com.glw.backend.common.ErrorCode;
import com.glw.backend.mapper.SysDepartmentMapper;
import com.glw.backend.mapper.UserMapper;
import com.glw.backend.model.dto.DepartmentInput;
import com.glw.backend.model.entity.SysDepartment;
import com.glw.backend.model.entity.SysUser;
import com.glw.backend.model.vo.DepartmentVO;
import com.glw.backend.service.SysDepartmentService;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** 部门服务实现：树结构由 parent_id 维护，level 随上级变化同步到所有下级。 */
@Service
@Profile("!mock")
public class SysDepartmentServiceImpl extends ServiceImpl<SysDepartmentMapper, SysDepartment>
        implements SysDepartmentService {
    private final UserMapper userMapper;

    public SysDepartmentServiceImpl(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Override
    public List<DepartmentVO> tree(boolean enabledOnly) {
        var rows =
                list(
                        Wrappers.<SysDepartment>lambdaQuery()
                                .eq(enabledOnly, SysDepartment::getEnabled, true)
                                .orderByAsc(SysDepartment::getSortNo)
                                .orderByAsc(SysDepartment::getId));
        var leaderNames = leaderNames(rows);
        Map<Long, DepartmentVO> nodes = new LinkedHashMap<>();
        rows.forEach(row -> nodes.put(row.getId(), toVO(row, leaderNames)));
        List<DepartmentVO> roots = new ArrayList<>();
        for (var node : nodes.values()) {
            var parent = node.getParentId() == null ? null : nodes.get(node.getParentId());
            // 上级被停用过滤掉时，下级按顶级展示，避免整棵子树消失。
            if (node.getParentId() == null || node.getParentId() == 0 || parent == null) {
                roots.add(node);
            } else {
                parent.getChildren().add(node);
            }
        }
        return roots;
    }

    @Override
    @Transactional
    public DepartmentVO create(DepartmentInput input) {
        long parentId = input.getParentId() == null ? 0 : input.getParentId();
        int level = parentId == 0 ? 1 : parentLevel(parentId);
        String name = input.getName().trim();
        ensureNameAvailable(parentId, name, null);
        validateLeader(input.getLeaderId());

        var department = new SysDepartment();
        department.setName(name);
        department.setParentId(parentId);
        department.setLevel(level);
        department.setLeaderId(input.getLeaderId());
        department.setSortNo(input.getSortNo() == null ? 0 : input.getSortNo());
        department.setEnabled(input.getEnabled() == null || input.getEnabled());
        department.setRemark(input.getRemark());
        save(department);
        return toVO(getById(department.getId()), leaderNames(List.of(department)));
    }

    @Override
    @Transactional
    public DepartmentVO update(Long id, DepartmentInput input) {
        var department = getById(id);
        if (department == null) throw new BusinessException(ErrorCode.NOT_FOUND, "部门不存在");
        long parentId = input.getParentId() == null ? 0 : input.getParentId();
        if (parentId != 0) {
            ensureParentChain(parentId, id);
        }
        String name = input.getName().trim();
        ensureNameAvailable(parentId, name, id);
        validateLeader(input.getLeaderId());

        int level = parentId == 0 ? 1 : parentLevel(parentId);
        int delta = level - department.getLevel();
        department.setName(name);
        department.setParentId(parentId);
        department.setLevel(level);
        department.setLeaderId(input.getLeaderId());
        department.setSortNo(
                input.getSortNo() == null ? department.getSortNo() : input.getSortNo());
        department.setEnabled(
                input.getEnabled() == null ? department.getEnabled() : input.getEnabled());
        department.setRemark(input.getRemark());
        updateById(department);
        if (delta != 0) shiftDescendantLevels(department.getId(), delta);
        return toVO(getById(id), leaderNames(List.of(department)));
    }

    @Override
    @Transactional
    public void deleteDepartment(Long id) {
        var department = getById(id);
        if (department == null) throw new BusinessException(ErrorCode.NOT_FOUND, "部门不存在");
        long children =
                count(Wrappers.<SysDepartment>lambdaQuery().eq(SysDepartment::getParentId, id));
        if (children > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "请先处理下级部门");
        }
        long users =
                userMapper.selectCount(
                        Wrappers.<SysUser>lambdaQuery().eq(SysUser::getDepartmentId, id));
        if (users > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "部门下还有用户，不能删除");
        }
        removeById(id);
    }

    private int parentLevel(long parentId) {
        var parent = getById(parentId);
        if (parent == null) throw new BusinessException(ErrorCode.BAD_REQUEST, "上级部门不存在");
        if (parent.getLevel() == null) return 1;
        return parent.getLevel() + 1;
    }

    /** 上级不能是自己或自己的下级，否则会形成环。 */
    private void ensureParentChain(long parentId, Long selfId) {
        Long cursor = parentId;
        while (cursor != null && cursor != 0) {
            if (cursor.equals(selfId)) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "上级部门不能是自己或自己的下级");
            }
            var parent = getById(cursor);
            if (parent == null) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "上级部门不存在");
            }
            cursor = parent.getParentId();
        }
    }

    private void ensureNameAvailable(long parentId, String name, Long selfId) {
        var query =
                Wrappers.<SysDepartment>lambdaQuery()
                        .eq(SysDepartment::getParentId, parentId)
                        .eq(SysDepartment::getName, name);
        if (selfId != null) query.ne(SysDepartment::getId, selfId);
        if (count(query) > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "同一上级下已存在同名部门");
        }
    }

    private void validateLeader(Long leaderId) {
        if (leaderId == null) return;
        var leader = userMapper.selectById(leaderId);
        if (leader == null || !Boolean.TRUE.equals(leader.getEnabled())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "部门负责人不存在或已停用");
        }
    }

    /** 部门整体移动后，所有下级的 level 一起平移。 */
    private void shiftDescendantLevels(Long rootId, int delta) {
        var all = list();
        Map<Long, List<SysDepartment>> byParent = new HashMap<>();
        for (var row : all) {
            byParent.computeIfAbsent(row.getParentId(), key -> new ArrayList<>()).add(row);
        }
        var queue = new ArrayDeque<>(byParent.getOrDefault(rootId, List.of()));
        while (!queue.isEmpty()) {
            var child = queue.removeFirst();
            child.setLevel(child.getLevel() == null ? 1 : child.getLevel() + delta);
            updateById(child);
            queue.addAll(byParent.getOrDefault(child.getId(), List.of()));
        }
    }

    private DepartmentVO toVO(SysDepartment row, Map<Long, String> leaderNames) {
        var vo = new DepartmentVO();
        vo.setId(row.getId());
        vo.setName(row.getName());
        vo.setParentId(row.getParentId());
        vo.setLevel(row.getLevel());
        vo.setLeaderId(row.getLeaderId());
        vo.setLeaderName(row.getLeaderId() == null ? null : leaderNames.get(row.getLeaderId()));
        vo.setSortNo(row.getSortNo());
        vo.setEnabled(row.getEnabled());
        vo.setRemark(row.getRemark());
        return vo;
    }

    private Map<Long, String> leaderNames(List<SysDepartment> rows) {
        Set<Long> ids =
                rows.stream()
                        .map(SysDepartment::getLeaderId)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet());
        if (ids.isEmpty()) return Map.of();
        return userMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(SysUser::getId, SysUser::getName));
    }

}
