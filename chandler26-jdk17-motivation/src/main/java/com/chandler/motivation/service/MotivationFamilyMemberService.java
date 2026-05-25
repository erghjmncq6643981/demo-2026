package com.chandler.motivation.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chandler.motivation.domain.dataobject.MotivationFamilyMember;
import com.chandler.motivation.domain.mapper.MotivationFamilyMemberMapper;
import com.chandler.motivation.support.MotivationConstants;
import com.chandler.motivation.support.MotivationEnums;
import org.springframework.stereotype.Service;

@Service
public class MotivationFamilyMemberService extends ServiceImpl<MotivationFamilyMemberMapper, MotivationFamilyMember> {

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
}
