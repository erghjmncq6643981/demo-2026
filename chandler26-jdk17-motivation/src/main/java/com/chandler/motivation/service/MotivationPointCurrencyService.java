package com.chandler.motivation.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chandler.motivation.common.exception.MotivationException;
import com.chandler.motivation.domain.dataobject.MotivationPointCurrency;
import com.chandler.motivation.domain.dto.points.PointCurrencySaveRequest;
import com.chandler.motivation.domain.dto.points.PointExchangeRuleRequest;
import com.chandler.motivation.domain.mapper.MotivationPointCurrencyMapper;
import com.chandler.motivation.support.MotivationConstants;
import com.chandler.motivation.support.MotivationEnums;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class MotivationPointCurrencyService extends ServiceImpl<MotivationPointCurrencyMapper, MotivationPointCurrency> {

    private static final Map<String, CurrencyDefaults> DEFAULTS = Map.of(
            MotivationEnums.PointType.STAR.code(), new CurrencyDefaults("星星", "★", "#f59e0b", 1, 1),
            MotivationEnums.PointType.FLOWER.code(), new CurrencyDefaults("红花", "✿", "#ec4899", 10, 2),
            MotivationEnums.PointType.CROWN.code(), new CurrencyDefaults("皇冠", "♛", "#7c3aed", 100, 3));

    private final MotivationChildService childService;
    private final MotivationPointExchangeRuleService pointExchangeRuleService;
    private final MotivationSystemLogService systemLogService;

    /**
     * 获取孩子的币值配置，缺失时自动补默认值。
     */
    public List<MotivationPointCurrency> listByChild(Long childId, Long userId) {
        childService.requireViewAccess(childId, userId);
        return withDefaults(childId);
    }

    /**
     * 创建孩子币值配置。
     */
    @Transactional
    public MotivationPointCurrency create(PointCurrencySaveRequest request, Long userId) {
        if (request == null || request.getChildId() == null) {
            throw new MotivationException("CHILD_REQUIRED", "请选择孩子");
        }
        childService.requireManageAccess(request.getChildId(), userId);
        String pointType = normalizePointType(request.getPointType());
        MotivationPointCurrency existing = findActive(request.getChildId(), pointType);
        if (existing != null) {
            throw new MotivationException("POINT_CURRENCY_EXISTS", "该币值类型已存在，请直接修改");
        }
        MotivationPointCurrency currency = new MotivationPointCurrency();
        applyRequest(currency, request, pointType);
        currency.setChildId(request.getChildId());
        currency.setDeleted(MotivationConstants.Flag.NO);
        currency.setCreatedByUserId(userId);
        currency.setUpdatedByUserId(userId);
        save(currency);
        syncExchangeRule(request.getChildId(), userId);
        systemLogService.recordBusiness(userId, currency.getChildId(), MotivationEnums.LogType.POINT,
                "创建币值", "创建了币值「" + currency.getName() + "」，兑换比例为 1:" + currency.getExchangeWeight());
        return currency;
    }

    /**
     * 修改币值配置。
     */
    @Transactional
    public MotivationPointCurrency update(Long currencyId, PointCurrencySaveRequest request, Long userId) {
        MotivationPointCurrency currency = getById(currencyId);
        if (currency == null || Integer.valueOf(MotivationConstants.Flag.YES).equals(currency.getDeleted())) {
            throw new MotivationException("POINT_CURRENCY_NOT_FOUND", "币值不存在");
        }
        childService.requireManageAccess(currency.getChildId(), userId);
        String pointType = normalizePointType(request == null ? currency.getPointType() : request.getPointType());
        MotivationPointCurrency existing = findActive(currency.getChildId(), pointType);
        if (existing != null && !existing.getId().equals(currencyId)) {
            throw new MotivationException("POINT_CURRENCY_EXISTS", "该币值类型已存在，请直接修改");
        }
        PointCurrencySaveRequest merged = request == null ? new PointCurrencySaveRequest() : request;
        merged.setChildId(currency.getChildId());
        applyRequest(currency, merged, pointType);
        currency.setUpdatedByUserId(userId);
        updateById(currency);
        syncExchangeRule(currency.getChildId(), userId);
        systemLogService.recordBusiness(userId, currency.getChildId(), MotivationEnums.LogType.POINT,
                "修改币值", "修改了币值「" + currency.getName() + "」，兑换比例为 1:" + currency.getExchangeWeight());
        return currency;
    }

    /**
     * 软删除币值配置。
     */
    @Transactional
    public void delete(Long currencyId, Long userId) {
        MotivationPointCurrency currency = getById(currencyId);
        if (currency == null || Integer.valueOf(MotivationConstants.Flag.YES).equals(currency.getDeleted())) {
            throw new MotivationException("POINT_CURRENCY_NOT_FOUND", "币值不存在");
        }
        childService.requireManageAccess(currency.getChildId(), userId);
        currency.setDeleted(MotivationConstants.Flag.YES);
        currency.setStatus(MotivationEnums.ChildStatus.INACTIVE.code());
        currency.setUpdatedByUserId(userId);
        updateById(currency);
        syncExchangeRule(currency.getChildId(), userId);
        systemLogService.recordBusiness(userId, currency.getChildId(), MotivationEnums.LogType.POINT,
                "删除币值", "删除了币值「" + currency.getName() + "」");
    }

    public List<MotivationPointCurrency> withDefaults(Long childId) {
        List<MotivationPointCurrency> allCurrencies = list(new LambdaQueryWrapper<MotivationPointCurrency>()
                .eq(MotivationPointCurrency::getChildId, childId));
        List<MotivationPointCurrency> missingDefaults = DEFAULTS.entrySet().stream()
                .filter((entry) -> allCurrencies.stream().noneMatch((currency) -> entry.getKey().equals(currency.getPointType())))
                .map((entry) -> defaultCurrency(childId, entry.getKey(), entry.getValue()))
                .toList();
        if (!missingDefaults.isEmpty()) {
            saveBatch(missingDefaults);
        }
        List<MotivationPointCurrency> currencies = list(new LambdaQueryWrapper<MotivationPointCurrency>()
                .eq(MotivationPointCurrency::getChildId, childId)
                .eq(MotivationPointCurrency::getDeleted, MotivationConstants.Flag.NO));
        currencies.sort(Comparator
                .comparing((MotivationPointCurrency currency) -> currency.getSortNo() == null ? 0 : currency.getSortNo())
                .thenComparing(MotivationPointCurrency::getExchangeWeight)
                .thenComparing(MotivationPointCurrency::getPointType));
        return currencies;
    }

    private void applyRequest(MotivationPointCurrency currency, PointCurrencySaveRequest request, String pointType) {
        CurrencyDefaults defaults = DEFAULTS.get(pointType);
        currency.setPointType(pointType);
        currency.setName(StringUtils.hasText(request.getName()) ? request.getName().trim() : defaults.name());
        currency.setIcon(StringUtils.hasText(request.getIcon()) ? request.getIcon().trim() : defaults.icon());
        currency.setColor(StringUtils.hasText(request.getColor()) ? request.getColor().trim() : defaults.color());
        currency.setExchangeWeight(positiveWeight(request.getExchangeWeight(), defaults.exchangeWeight()));
        currency.setStatus(StringUtils.hasText(request.getStatus())
                ? request.getStatus().trim().toUpperCase()
                : MotivationEnums.ChildStatus.ACTIVE.code());
        currency.setSortNo(request.getSortNo() == null ? defaults.sortNo() : request.getSortNo());
    }

    private void syncExchangeRule(Long childId, Long userId) {
        Map<String, Integer> weights = withDefaults(childId).stream()
                .filter((currency) -> MotivationEnums.codeEquals(MotivationEnums.ChildStatus.ACTIVE, currency.getStatus()))
                .collect(java.util.stream.Collectors.toMap(
                        MotivationPointCurrency::getPointType,
                        MotivationPointCurrency::getExchangeWeight,
                        (left, right) -> left));
        PointExchangeRuleRequest request = new PointExchangeRuleRequest();
        request.setStarWeight(weights.getOrDefault(MotivationEnums.PointType.STAR.code(), DEFAULTS.get(MotivationEnums.PointType.STAR.code()).exchangeWeight()));
        request.setFlowerWeight(weights.getOrDefault(MotivationEnums.PointType.FLOWER.code(), DEFAULTS.get(MotivationEnums.PointType.FLOWER.code()).exchangeWeight()));
        request.setCrownWeight(weights.getOrDefault(MotivationEnums.PointType.CROWN.code(), DEFAULTS.get(MotivationEnums.PointType.CROWN.code()).exchangeWeight()));
        pointExchangeRuleService.saveRule(childId, userId, request);
    }

    private MotivationPointCurrency findActive(Long childId, String pointType) {
        return getOne(new LambdaQueryWrapper<MotivationPointCurrency>()
                .eq(MotivationPointCurrency::getChildId, childId)
                .eq(MotivationPointCurrency::getPointType, pointType)
                .eq(MotivationPointCurrency::getDeleted, MotivationConstants.Flag.NO)
                .last("limit 1"));
    }

    private String normalizePointType(String pointType) {
        String normalized = StringUtils.hasText(pointType) ? pointType.trim().toUpperCase() : MotivationEnums.PointType.STAR.code();
        if (!DEFAULTS.containsKey(normalized)) {
            throw new MotivationException("POINT_TYPE_INVALID", "积分类型不正确");
        }
        return normalized;
    }

    private int positiveWeight(Integer value, int fallback) {
        return value == null || value <= 0 ? fallback : value;
    }

    private MotivationPointCurrency defaultCurrency(Long childId, String pointType, CurrencyDefaults defaults) {
        MotivationPointCurrency currency = new MotivationPointCurrency();
        currency.setChildId(childId);
        currency.setPointType(pointType);
        currency.setName(defaults.name());
        currency.setIcon(defaults.icon());
        currency.setColor(defaults.color());
        currency.setExchangeWeight(defaults.exchangeWeight());
        currency.setStatus(MotivationEnums.ChildStatus.ACTIVE.code());
        currency.setDeleted(MotivationConstants.Flag.NO);
        currency.setSortNo(defaults.sortNo());
        return currency;
    }

    private record CurrencyDefaults(String name, String icon, String color, int exchangeWeight, int sortNo) {
    }
}
