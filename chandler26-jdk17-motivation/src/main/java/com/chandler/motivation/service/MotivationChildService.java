package com.chandler.motivation.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chandler.motivation.common.exception.MotivationException;
import com.chandler.motivation.domain.dataobject.MotivationChild;
import com.chandler.motivation.domain.dto.child.ChildSaveRequest;
import com.chandler.motivation.domain.mapper.MotivationChildMapper;
import com.chandler.motivation.support.MotivationConstants;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class MotivationChildService extends ServiceImpl<MotivationChildMapper, MotivationChild> {

    private final MotivationFamilyMemberService familyMemberService;
    private final MotivationSystemLogService systemLogService;

    @Transactional
    public MotivationChild create(ChildSaveRequest request, Long userId) {
        if (request == null || !StringUtils.hasText(request.getNickname())) {
            throw new MotivationException("CHILD_NAME_REQUIRED", "孩子昵称不能为空");
        }
        MotivationChild child = new MotivationChild();
        child.setNickname(request.getNickname().trim());
        child.setAvatarUrl(request.getAvatarUrl());
        child.setBirthday(request.getBirthday());
        child.setGender(StringUtils.hasText(request.getGender()) ? request.getGender() : "UNKNOWN");
        child.setRemark(request.getRemark());
        child.setStatus(MotivationConstants.ChildStatus.ACTIVE);
        child.setDeleted(0);
        child.setCreatedByUserId(userId);
        save(child);
        familyMemberService.createPrimaryParent(child.getId(), userId);
        systemLogService.record(userId, child.getId(), MotivationConstants.LogType.SYSTEM,
                "创建孩子档案", "为孩子「" + child.getNickname() + "」创建激励档案");
        return child;
    }

    public List<MotivationChild> listByUser(Long userId) {
        return list(new LambdaQueryWrapper<MotivationChild>()
                .inSql(MotivationChild::getId,
                        "select child_id from motivation_family_member where user_id = " + userId
                                + " and status = '" + MotivationConstants.ChildStatus.ACTIVE + "'")
                .eq(MotivationChild::getDeleted, 0)
                .orderByDesc(MotivationChild::getUpdateTime));
    }

    public MotivationChild update(Long childId, ChildSaveRequest request, Long userId) {
        MotivationChild child = getById(childId);
        if (child == null || Integer.valueOf(1).equals(child.getDeleted())) {
            throw new MotivationException("CHILD_NOT_FOUND", "孩子档案不存在");
        }
        requireManageAccess(childId, userId);
        if (request == null || !StringUtils.hasText(request.getNickname())) {
            throw new MotivationException("CHILD_NAME_REQUIRED", "孩子昵称不能为空");
        }
        child.setNickname(request.getNickname().trim());
        child.setAvatarUrl(request.getAvatarUrl());
        child.setBirthday(request.getBirthday());
        child.setGender(StringUtils.hasText(request.getGender()) ? request.getGender() : "UNKNOWN");
        child.setRemark(request.getRemark());
        updateById(child);
        systemLogService.record(userId, child.getId(), MotivationConstants.LogType.SYSTEM,
                "修改孩子档案", "修改孩子「" + child.getNickname() + "」的档案");
        return child;
    }

    public void delete(Long childId, Long userId) {
        MotivationChild child = getById(childId);
        if (child == null || Integer.valueOf(1).equals(child.getDeleted())) {
            throw new MotivationException("CHILD_NOT_FOUND", "孩子档案不存在");
        }
        requireManageAccess(childId, userId);
        child.setDeleted(1);
        child.setStatus(MotivationConstants.ChildStatus.INACTIVE);
        updateById(child);
        systemLogService.record(userId, child.getId(), MotivationConstants.LogType.SYSTEM,
                "删除孩子档案", "删除孩子「" + child.getNickname() + "」的档案");
    }

    public void requireManageAccess(Long childId, Long userId) {
        if (!familyMemberService.canManage(childId, userId)) {
            throw new MotivationException("CHILD_ACCESS_DENIED", "无权管理该孩子档案");
        }
    }

    public void requireViewAccess(Long childId, Long userId) {
        if (!familyMemberService.canView(childId, userId)) {
            throw new MotivationException("CHILD_ACCESS_DENIED", "无权查看该孩子档案");
        }
    }
}
