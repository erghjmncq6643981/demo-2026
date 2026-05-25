package com.chandler.motivation.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chandler.motivation.common.exception.MotivationException;
import com.chandler.motivation.domain.dataobject.MotivationChild;
import com.chandler.motivation.domain.dataobject.MotivationUser;
import com.chandler.motivation.domain.dto.child.ChildSaveRequest;
import com.chandler.motivation.domain.mapper.MotivationChildMapper;
import com.chandler.motivation.support.MotivationConstants;
import com.chandler.motivation.support.MotivationEnums;
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
    private final AuthService authService;

    /**
     * 创建孩子档案，并按需创建孩子登录账号。
     */
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
        child.setStatus(MotivationEnums.ChildStatus.ACTIVE.code());
        child.setDeleted(MotivationConstants.Flag.NO);
        child.setCreatedByUserId(userId);
        save(child);
        familyMemberService.createPrimaryParent(child.getId(), userId);
        createChildAccountIfNeeded(child, request);
        systemLogService.recordBusiness(userId, child.getId(), MotivationEnums.LogType.SYSTEM,
                "创建孩子档案", "为孩子「" + child.getNickname() + "」创建了激励档案");
        return child;
    }

    public List<MotivationChild> listByUser(Long userId) {
        return list(new LambdaQueryWrapper<MotivationChild>()
                .inSql(MotivationChild::getId,
                        "select child_id from motivation_family_member where user_id = " + userId
                                + " and status = '" + MotivationEnums.ChildStatus.ACTIVE.code() + "'")
                .eq(MotivationChild::getDeleted, MotivationConstants.Flag.NO)
                .orderByDesc(MotivationChild::getUpdateTime));
    }

    /**
     * 修改孩子档案，并按需补建孩子登录账号。
     */
    public MotivationChild update(Long childId, ChildSaveRequest request, Long userId) {
        MotivationChild child = getById(childId);
        if (child == null || Integer.valueOf(MotivationConstants.Flag.YES).equals(child.getDeleted())) {
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
        createChildAccountIfNeeded(child, request);
        systemLogService.recordBusiness(userId, child.getId(), MotivationEnums.LogType.SYSTEM,
                "修改孩子档案", "修改了孩子「" + child.getNickname() + "」的档案");
        return child;
    }

    /**
     * 软删除孩子档案，保留历史业务记录。
     */
    public void delete(Long childId, Long userId) {
        MotivationChild child = getById(childId);
        if (child == null || Integer.valueOf(MotivationConstants.Flag.YES).equals(child.getDeleted())) {
            throw new MotivationException("CHILD_NOT_FOUND", "孩子档案不存在");
        }
        requireManageAccess(childId, userId);
        child.setDeleted(MotivationConstants.Flag.YES);
        child.setStatus(MotivationEnums.ChildStatus.INACTIVE.code());
        updateById(child);
        systemLogService.recordBusiness(userId, child.getId(), MotivationEnums.LogType.SYSTEM,
                "删除孩子档案", "删除了孩子「" + child.getNickname() + "」的档案");
    }

    /**
     * 校验当前用户是否有孩子档案管理权限。
     */
    public void requireManageAccess(Long childId, Long userId) {
        if (!familyMemberService.canManage(childId, userId)) {
            throw new MotivationException("CHILD_ACCESS_DENIED", "无权管理该孩子档案");
        }
    }

    /**
     * 校验当前用户是否有孩子档案查看权限。
     */
    public void requireViewAccess(Long childId, Long userId) {
        if (!familyMemberService.canView(childId, userId)) {
            throw new MotivationException("CHILD_ACCESS_DENIED", "无权查看该孩子档案");
        }
    }

    private void createChildAccountIfNeeded(MotivationChild child, ChildSaveRequest request) {
        if (!Boolean.TRUE.equals(request.getCreateChildAccount())) {
            return;
        }
        if (!StringUtils.hasText(request.getChildUsername())) {
            throw new MotivationException("CHILD_USERNAME_REQUIRED", "请填写孩子账号");
        }
        if (!StringUtils.hasText(request.getChildPassword())) {
            throw new MotivationException("CHILD_PASSWORD_REQUIRED", "请填写孩子密码");
        }
        MotivationUser childUser = authService.createChildAccount(
                request.getChildUsername(),
                request.getChildPassword(),
                child.getNickname());
        familyMemberService.createChildMember(child.getId(), childUser.getId());
    }
}
