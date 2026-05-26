package com.chandler.motivation.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chandler.motivation.common.exception.MotivationException;
import com.chandler.motivation.domain.dataobject.MotivationChildPointBalance;
import com.chandler.motivation.domain.dataobject.MotivationPointLedger;
import com.chandler.motivation.domain.dto.points.PointAdjustRequest;
import com.chandler.motivation.domain.mapper.MotivationPointLedgerMapper;
import com.chandler.motivation.support.MotivationConstants;
import com.chandler.motivation.support.MotivationEnums;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class MotivationPointLedgerService extends ServiceImpl<MotivationPointLedgerMapper, MotivationPointLedger> {

    private final MotivationChildPointBalanceService balanceService;
    private final MotivationSystemLogService systemLogService;

    /**
     * 按来源追踪最近一条积分流水，避免重复入账。
     */
    public MotivationPointLedger lastBySource(String sourceType, Long sourceId, Long childId, String pointType) {
        return getOne(new LambdaQueryWrapper<MotivationPointLedger>()
                .eq(MotivationPointLedger::getSourceType, sourceType)
                .eq(MotivationPointLedger::getSourceId, sourceId)
                .eq(MotivationPointLedger::getChildId, childId)
                .eq(MotivationPointLedger::getPointType, pointType)
                .last("limit 1"));
    }

    public List<MotivationPointLedger> listByChild(Long childId, String pointType, int limit) {
        LambdaQueryWrapper<MotivationPointLedger> wrapper = new LambdaQueryWrapper<MotivationPointLedger>()
                .eq(MotivationPointLedger::getChildId, childId)
                .orderByDesc(MotivationPointLedger::getUpdateTime)
                .orderByDesc(MotivationPointLedger::getId)
                .last("limit " + Math.max(MotivationConstants.Pagination.MIN_LIMIT,
                        Math.min(limit, MotivationConstants.Pagination.LEDGER_MAX_LIMIT)));
        if (StringUtils.hasText(pointType)) {
            wrapper.eq(MotivationPointLedger::getPointType, pointType.trim());
        }
        return list(wrapper);
    }

    /**
     * 统一处理积分变动、账本和余额同步。
     */
    @Transactional
    public MotivationPointLedger applyChange(Long childId,
                                             String pointType,
                                             int amount,
                                             String sourceType,
                                             Long sourceId,
                                             String sourceName,
                                             String reason,
                                             Long operatorUserId) {
        if (childId == null) {
            throw new MotivationException("CHILD_REQUIRED", "请选择孩子");
        }
        if (!StringUtils.hasText(pointType)) {
            throw new MotivationException("POINT_TYPE_REQUIRED", "请选择货币类型");
        }
        if (amount == 0) {
            throw new MotivationException("POINT_AMOUNT_REQUIRED", "积分变动不能为 0");
        }
        if (!StringUtils.hasText(sourceType) || sourceId == null) {
            throw new MotivationException("LEDGER_SOURCE_REQUIRED", "积分来源不能为空");
        }
        MotivationPointLedger existing = lastBySource(sourceType, sourceId, childId, pointType);
        if (existing != null) {
            return existing;
        }

        MotivationChildPointBalance balance = balanceService.getOrCreate(childId, pointType);
        int balanceAfter = balance.getBalance() + amount;
        if (balanceAfter < 0) {
            throw new MotivationException("POINT_BALANCE_NOT_ENOUGH", "积分余额不足");
        }
        balance.setBalance(balanceAfter);
        if (amount > 0) {
            balance.setEarnedTotal(balance.getEarnedTotal() + amount);
        } else {
            balance.setSpentTotal(balance.getSpentTotal() + Math.abs(amount));
        }
        balance.setVersion(balance.getVersion() == null ? 1 : balance.getVersion() + 1);
        balance.setUpdateTime(LocalDateTime.now());
        balanceService.updateById(balance);

        MotivationPointLedger ledger = new MotivationPointLedger();
        ledger.setChildId(childId);
        ledger.setPointType(pointType.trim());
        ledger.setChangeAmount(amount);
        ledger.setBalanceAfter(balanceAfter);
        ledger.setSourceType(sourceType.trim());
        ledger.setSourceId(sourceId);
        ledger.setSourceName(trimToNull(sourceName));
        ledger.setReason(trimToNull(reason));
        ledger.setOperatorUserId(operatorUserId);
        ledger.setEventTime(LocalDateTime.now());
        save(ledger);

        systemLogService.recordBusiness(operatorUserId, childId, MotivationEnums.LogType.POINT,
                amount > 0 ? "增加积分" : "扣减积分",
                "孩子「" + childId + "」的" + MotivationEnums.descriptionOf(MotivationEnums.PointType.class, pointType, MotivationEnums.PointType.STAR)
                        + "发生 " + (amount > 0 ? "增加" : "扣减") + " " + Math.abs(amount) + "，当前余额 " + balanceAfter);
        return ledger;
    }

    /**
     * 父母手动调整积分，必须填写原因。
     */
    @Transactional
    public MotivationPointLedger manualAdjust(Long childId, Long operatorUserId, PointAdjustRequest request) {
        if (request == null) {
            throw new MotivationException("POINT_REQUEST_REQUIRED", "请填写积分调整信息");
        }
        if (!StringUtils.hasText(request.getReason())) {
            throw new MotivationException("POINT_REASON_REQUIRED", "手动加减分必须填写原因");
        }
        if (request.getAmount() == null || request.getAmount() == 0) {
            throw new MotivationException("POINT_AMOUNT_REQUIRED", "积分变动不能为 0");
        }
        return applyChange(childId,
                request.getPointType(),
                request.getAmount(),
                MotivationConstants.LedgerSourceType.MANUAL_ADJUST,
                System.currentTimeMillis(),
                "手动调整",
                request.getReason(),
                operatorUserId);
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
