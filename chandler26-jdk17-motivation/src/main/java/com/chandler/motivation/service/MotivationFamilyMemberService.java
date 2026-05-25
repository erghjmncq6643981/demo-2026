package com.chandler.motivation.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chandler.motivation.domain.dataobject.MotivationFamilyMember;
import com.chandler.motivation.domain.mapper.MotivationFamilyMemberMapper;
import com.chandler.motivation.support.MotivationConstants;
import org.springframework.stereotype.Service;

@Service
public class MotivationFamilyMemberService extends ServiceImpl<MotivationFamilyMemberMapper, MotivationFamilyMember> {

    public void createPrimaryParent(Long childId, Long userId) {
        MotivationFamilyMember member = new MotivationFamilyMember();
        member.setChildId(childId);
        member.setUserId(userId);
        member.setRelationRole(MotivationConstants.FamilyRole.PARENT);
        member.setIsPrimary(1);
        member.setCanManage(1);
        member.setStatus(MotivationConstants.ChildStatus.ACTIVE);
        save(member);
    }

    public boolean canManage(Long childId, Long userId) {
        if (childId == null || userId == null) {
            return false;
        }
        return count(new LambdaQueryWrapper<MotivationFamilyMember>()
                .eq(MotivationFamilyMember::getChildId, childId)
                .eq(MotivationFamilyMember::getUserId, userId)
                .eq(MotivationFamilyMember::getStatus, MotivationConstants.ChildStatus.ACTIVE)
                .eq(MotivationFamilyMember::getCanManage, 1)) > 0;
    }
}
