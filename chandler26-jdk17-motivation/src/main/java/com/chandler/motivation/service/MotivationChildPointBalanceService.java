package com.chandler.motivation.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chandler.motivation.domain.dataobject.MotivationChildPointBalance;
import com.chandler.motivation.domain.dto.points.PointBalanceResponse;
import com.chandler.motivation.domain.mapper.MotivationChildPointBalanceMapper;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class MotivationChildPointBalanceService extends ServiceImpl<MotivationChildPointBalanceMapper, MotivationChildPointBalance> {

    public MotivationChildPointBalance getOrCreate(Long childId, String pointType) {
        MotivationChildPointBalance balance = getOne(new LambdaQueryWrapper<MotivationChildPointBalance>()
                .eq(MotivationChildPointBalance::getChildId, childId)
                .eq(MotivationChildPointBalance::getPointType, pointType)
                .last("limit 1"));
        if (balance != null) {
            return balance;
        }
        balance = new MotivationChildPointBalance();
        balance.setChildId(childId);
        balance.setPointType(pointType);
        balance.setBalance(0);
        balance.setEarnedTotal(0);
        balance.setSpentTotal(0);
        balance.setVersion(0);
        save(balance);
        return balance;
    }

    public List<PointBalanceResponse> listSummary(Long childId) {
        return list(new LambdaQueryWrapper<MotivationChildPointBalance>()
                .eq(MotivationChildPointBalance::getChildId, childId)
                .orderByDesc(MotivationChildPointBalance::getUpdateTime)
                .orderByDesc(MotivationChildPointBalance::getId))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private PointBalanceResponse toResponse(MotivationChildPointBalance balance) {
        PointBalanceResponse response = new PointBalanceResponse();
        response.setPointType(balance.getPointType());
        response.setBalance(balance.getBalance());
        response.setEarnedTotal(balance.getEarnedTotal());
        response.setSpentTotal(balance.getSpentTotal());
        return response;
    }
}
