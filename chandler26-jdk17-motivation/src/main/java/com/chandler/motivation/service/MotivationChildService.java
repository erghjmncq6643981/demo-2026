package com.chandler.motivation.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chandler.motivation.common.exception.MotivationException;
import com.chandler.motivation.domain.dataobject.MotivationChild;
import com.chandler.motivation.domain.dataobject.MotivationUser;
import com.chandler.motivation.domain.dto.child.ChildSaveRequest;
import com.chandler.motivation.domain.dto.common.AvatarResource;
import com.chandler.motivation.domain.dto.common.AvatarUploadResponse;
import com.chandler.motivation.domain.mapper.MotivationChildMapper;
import com.chandler.motivation.support.MotivationConstants;
import com.chandler.motivation.support.MotivationEnums;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class MotivationChildService extends ServiceImpl<MotivationChildMapper, MotivationChild> {

    private final MotivationFamilyMemberService familyMemberService;
    private final MotivationSystemLogService systemLogService;
    private final AuthService authService;
    private final AvatarImageService avatarImageService;

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
        List<MotivationChild> children = list(new LambdaQueryWrapper<MotivationChild>()
                .inSql(MotivationChild::getId,
                        "select child_id from motivation_family_member where user_id = " + userId
                                + " and status = '" + MotivationEnums.ChildStatus.ACTIVE.code() + "'")
                .eq(MotivationChild::getDeleted, MotivationConstants.Flag.NO)
                .orderByDesc(MotivationChild::getUpdateTime)
                .orderByDesc(MotivationChild::getId));
        attachChildAccounts(children);
        return children;
    }

    /**
     * 修改孩子档案，并按需补建孩子登录账号。
     */
    @Transactional
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
        child.setBirthday(request.getBirthday());
        child.setGender(StringUtils.hasText(request.getGender()) ? request.getGender() : "UNKNOWN");
        child.setRemark(request.getRemark());
        updateById(child);
        createChildAccountIfNeeded(child, request);
        updateChildAccountPasswordIfNeeded(child, request, userId);
        systemLogService.recordBusiness(userId, child.getId(), MotivationEnums.LogType.SYSTEM,
                "修改孩子档案", "修改了孩子「" + child.getNickname() + "」的档案");
        attachChildAccounts(List.of(child));
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
     * 上传并压缩孩子头像，图片字节直接入库。
     */
    @Transactional
    public AvatarUploadResponse updateAvatar(Long childId, Long userId, MultipartFile file) {
        MotivationChild child = getById(childId);
        if (child == null || Integer.valueOf(MotivationConstants.Flag.YES).equals(child.getDeleted())) {
            throw new MotivationException("CHILD_NOT_FOUND", "孩子档案不存在");
        }
        requireManageAccess(childId, userId);
        AvatarImageService.CompressedAvatar compressedAvatar = avatarImageService.compress(file);
        String avatarUrl = "/children/" + childId + "/avatar?t=" + System.currentTimeMillis();
        child.setAvatarData(compressedAvatar.data());
        child.setAvatarContentType(compressedAvatar.contentType());
        child.setAvatarUrl(avatarUrl);
        updateById(child);
        systemLogService.recordBusiness(userId, child.getId(), MotivationEnums.LogType.SYSTEM,
                "更新孩子头像", "用户更新了孩子「" + child.getNickname() + "」的头像");
        AvatarUploadResponse response = new AvatarUploadResponse();
        response.setAvatarUrl(avatarUrl);
        return response;
    }

    /**
     * 读取孩子头像二进制。
     */
    public AvatarResource readAvatar(Long childId, Long userId) {
        requireViewAccess(childId, userId);
        MotivationChild child = baseMapper.selectAvatarById(childId);
        if (child == null || child.getAvatarData() == null || child.getAvatarData().length == 0) {
            throw new MotivationException("AVATAR_NOT_FOUND", "头像不存在");
        }
        String contentType = StringUtils.hasText(child.getAvatarContentType())
                ? child.getAvatarContentType()
                : MotivationConstants.Avatar.CONTENT_TYPE_JPEG;
        return new AvatarResource(child.getAvatarData(), contentType);
    }

    /**
     * 家长查看孩子子账户密码。仅返回已加密保存过的可查看副本。
     */
    public String readChildAccountPassword(Long childId, Long userId) {
        MotivationChild child = getChildForManage(childId, userId);
        attachChildAccounts(List.of(child));
        if (child.getChildAccountUserId() == null) {
            throw new MotivationException("CHILD_ACCOUNT_REQUIRED", "孩子账号不存在");
        }
        systemLogService.recordBusiness(userId, childId, MotivationEnums.LogType.SYSTEM,
                "查看孩子账号密码", "查看了孩子「" + child.getNickname() + "」的子账户密码");
        return authService.readChildPassword(child.getChildAccountUserId());
    }

    /**
     * 家长重置孩子子账户密码，不要求孩子当前密码。
     */
    @Transactional
    public void updateChildAccountPassword(Long childId, Long userId, String password) {
        MotivationChild child = getChildForManage(childId, userId);
        attachChildAccounts(List.of(child));
        if (child.getChildAccountUserId() == null) {
            throw new MotivationException("CHILD_ACCOUNT_REQUIRED", "孩子账号不存在");
        }
        authService.resetChildPassword(child.getChildAccountUserId(), password);
        systemLogService.recordBusiness(userId, childId, MotivationEnums.LogType.SYSTEM,
                "修改孩子账号密码", "修改了孩子「" + child.getNickname() + "」的子账户密码");
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

    private MotivationChild getChildForManage(Long childId, Long userId) {
        MotivationChild child = getById(childId);
        if (child == null || Integer.valueOf(MotivationConstants.Flag.YES).equals(child.getDeleted())) {
            throw new MotivationException("CHILD_NOT_FOUND", "孩子档案不存在");
        }
        requireManageAccess(childId, userId);
        return child;
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
        child.setChildAccountUserId(childUser.getId());
        child.setChildUsername(childUser.getUsername());
    }

    private void updateChildAccountPasswordIfNeeded(MotivationChild child, ChildSaveRequest request, Long userId) {
        if (request == null || !StringUtils.hasText(request.getChildPasswordToUpdate())) {
            return;
        }
        attachChildAccounts(List.of(child));
        if (child.getChildAccountUserId() == null) {
            throw new MotivationException("CHILD_ACCOUNT_REQUIRED", "孩子账号不存在，请先创建子账户");
        }
        authService.resetChildPassword(child.getChildAccountUserId(), request.getChildPasswordToUpdate());
        systemLogService.recordBusiness(userId, child.getId(), MotivationEnums.LogType.SYSTEM,
                "修改孩子账号密码", "修改了孩子「" + child.getNickname() + "」的子账户密码");
    }

    private void attachChildAccounts(List<MotivationChild> children) {
        if (children == null || children.isEmpty()) {
            return;
        }
        List<Long> childIds = children.stream()
                .map(MotivationChild::getId)
                .filter(id -> id != null)
                .toList();
        if (childIds.isEmpty()) {
            return;
        }
        Map<Long, MotivationUser> accountMap = familyMemberService.listChildAccounts(childIds).stream()
                .collect(Collectors.toMap(
                        MotivationFamilyMemberService.ChildAccount::childId,
                        MotivationFamilyMemberService.ChildAccount::user,
                        (left, right) -> left));
        children.forEach(child -> {
            MotivationUser user = accountMap.get(child.getId());
            if (user != null) {
                child.setChildAccountUserId(user.getId());
                child.setChildUsername(user.getUsername());
            }
        });
    }
}
