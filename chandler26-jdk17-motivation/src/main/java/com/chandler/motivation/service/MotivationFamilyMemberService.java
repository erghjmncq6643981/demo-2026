package com.chandler.motivation.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chandler.motivation.domain.dataobject.MotivationUser;
import com.chandler.motivation.domain.dataobject.MotivationFamilyMember;
import com.chandler.motivation.domain.mapper.MotivationFamilyMemberMapper;
import com.chandler.motivation.domain.mapper.MotivationUserMapper;
import com.chandler.motivation.support.MotivationConstants;
import com.chandler.motivation.support.MotivationEnums;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MotivationFamilyMemberService extends ServiceImpl<MotivationFamilyMemberMapper, MotivationFamilyMember> {

    private final MotivationUserMapper userMapper;

    /**
     * 建立孩子档案的主家长管理关系。
     */
    public void createPrimaryParent(Long childId, Long userId) {
        MotivationFamilyMember member = new MotivationFamilyMember();
        member.setChildId(childId);
        member.setUserId(userId);
        member.setRelationRole(MotivationEnums.FamilyRole.PARENT.code());
        member.setIsPrimary(MotivationConstants.Flag.YES);
        member.setCanManage(MotivationConstants.Flag.YES);
        member.setStatus(MotivationEnums.ChildStatus.ACTIVE.code());
        save(member);
    }

    /**
     * 建立孩子账号和孩子档案之间的查看关系。
     */
    public void createChildMember(Long childId, Long userId) {
        MotivationFamilyMember existing = getOne(new LambdaQueryWrapper<MotivationFamilyMember>()
                .eq(MotivationFamilyMember::getChildId, childId)
                .eq(MotivationFamilyMember::getUserId, userId)
                .last("limit 1"));
        MotivationFamilyMember member = existing == null ? new MotivationFamilyMember() : existing;
        member.setChildId(childId);
        member.setUserId(userId);
        member.setRelationRole(MotivationEnums.FamilyRole.CHILD.code());
        member.setIsPrimary(MotivationConstants.Flag.NO);
        member.setCanManage(MotivationConstants.Flag.NO);
        member.setStatus(MotivationEnums.ChildStatus.ACTIVE.code());
        if (member.getId() == null) {
            save(member);
        } else {
            updateById(member);
        }
    }

    public boolean canManage(Long childId, Long userId) {
        if (childId == null || userId == null) {
            return false;
        }
        return count(new LambdaQueryWrapper<MotivationFamilyMember>()
                .eq(MotivationFamilyMember::getChildId, childId)
                .eq(MotivationFamilyMember::getUserId, userId)
                .eq(MotivationFamilyMember::getStatus, MotivationEnums.ChildStatus.ACTIVE.code())
                .eq(MotivationFamilyMember::getCanManage, MotivationConstants.Flag.YES)) > 0;
    }

    public boolean canView(Long childId, Long userId) {
        if (childId == null || userId == null) {
            return false;
        }
        return count(new LambdaQueryWrapper<MotivationFamilyMember>()
                .eq(MotivationFamilyMember::getChildId, childId)
                .eq(MotivationFamilyMember::getUserId, userId)
                .eq(MotivationFamilyMember::getStatus, MotivationEnums.ChildStatus.ACTIVE.code())) > 0;
    }

    public List<ChildAccount> listChildAccounts(List<Long> childIds) {
        if (childIds == null || childIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<MotivationFamilyMember> members = list(new LambdaQueryWrapper<MotivationFamilyMember>()
                .in(MotivationFamilyMember::getChildId, childIds)
                .eq(MotivationFamilyMember::getRelationRole, MotivationEnums.FamilyRole.CHILD.code())
                .eq(MotivationFamilyMember::getStatus, MotivationEnums.ChildStatus.ACTIVE.code())
                .orderByDesc(MotivationFamilyMember::getUpdateTime)
                .orderByDesc(MotivationFamilyMember::getId));
        if (members.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> userIds = members.stream()
                .map(MotivationFamilyMember::getUserId)
                .filter(id -> id != null)
                .distinct()
                .toList();
        Map<Long, MotivationUser> userMap = userIds.isEmpty()
                ? Collections.emptyMap()
                : userMapper.selectBatchIds(userIds).stream()
                .filter(user -> Integer.valueOf(MotivationConstants.Flag.NO).equals(user.getDeleted()))
                .collect(Collectors.toMap(MotivationUser::getId, Function.identity(), (left, right) -> left));
        return members.stream()
                .map(member -> new ChildAccount(member.getChildId(), userMap.get(member.getUserId())))
                .filter(account -> account.user() != null)
                .toList();
    }

    public record ChildAccount(Long childId, MotivationUser user) {
    }
}
