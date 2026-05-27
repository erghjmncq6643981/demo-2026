package com.chandler.motivation.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chandler.motivation.common.exception.MotivationException;
import com.chandler.motivation.domain.dataobject.MotivationUserPreference;
import com.chandler.motivation.domain.mapper.MotivationUserPreferenceMapper;
import com.chandler.motivation.support.MotivationEnums;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 管理用户侧偏好配置，避免前端刷新后丢失当前操作习惯。
 */
@Service
@RequiredArgsConstructor
public class MotivationUserPreferenceService extends ServiceImpl<MotivationUserPreferenceMapper, MotivationUserPreference> {

    private static final String KEY_SELECTED_CHILD_ID = "selectedChildId";
    private static final String KEY_CALENDAR_VIEW_MODE = "calendarViewMode";
    private static final String KEY_TASK_CALENDAR_VIEW_MODE = "taskCalendarViewMode";
    private static final String KEY_REWARD_CALENDAR_VIEW_MODE = "rewardCalendarViewMode";
    private static final String KEY_CALENDAR_DATE_SIZE = "calendarDateSize";
    private static final String KEY_CALENDAR_DATE_COLOR = "calendarDateColor";
    private static final String KEY_CALENDAR_TODAY_COLOR = "calendarTodayColor";
    private static final Set<String> ALLOWED_KEYS = Set.of(
            KEY_SELECTED_CHILD_ID,
            KEY_CALENDAR_VIEW_MODE,
            KEY_TASK_CALENDAR_VIEW_MODE,
            KEY_REWARD_CALENDAR_VIEW_MODE,
            KEY_CALENDAR_DATE_SIZE,
            KEY_CALENDAR_DATE_COLOR,
            KEY_CALENDAR_TODAY_COLOR);

    private final MotivationFamilyMemberService familyMemberService;
    private final MotivationSystemLogService systemLogService;

    /**
     * 查询当前用户全部可识别的偏好配置。
     */
    public Map<String, String> listByUser(Long userId) {
        Map<String, String> preferences = new LinkedHashMap<>();
        if (userId == null) {
            return preferences;
        }
        list(new LambdaQueryWrapper<MotivationUserPreference>()
                .eq(MotivationUserPreference::getUserId, userId)
                .in(MotivationUserPreference::getPreferenceKey, ALLOWED_KEYS)
                .orderByAsc(MotivationUserPreference::getPreferenceKey))
                .forEach(item -> preferences.put(item.getPreferenceKey(), item.getPreferenceValue()));
        return preferences;
    }

    /**
     * 批量保存用户偏好。空值表示删除该项偏好。
     */
    @Transactional
    public Map<String, String> savePreferences(Long userId, Map<String, ?> request) {
        if (userId == null) {
            throw new MotivationException("AUTH_REQUIRED", "请先登录");
        }
        if (request == null || request.isEmpty()) {
            return listByUser(userId);
        }
        Map<String, String> changed = new LinkedHashMap<>();
        request.forEach((key, rawValue) -> {
            String normalizedKey = normalizeKey(key);
            String value = normalizeValue(userId, normalizedKey, rawValue);
            if (!StringUtils.hasText(value)) {
                remove(new LambdaQueryWrapper<MotivationUserPreference>()
                        .eq(MotivationUserPreference::getUserId, userId)
                        .eq(MotivationUserPreference::getPreferenceKey, normalizedKey));
            } else {
                upsert(userId, normalizedKey, value);
            }
            changed.put(normalizedKey, value);
        });
        if (!changed.isEmpty()) {
            systemLogService.recordBusiness(userId, null, MotivationEnums.LogType.SYSTEM,
                    "更新系统配置", "用户更新了系统配置：" + describePreferences(changed));
        }
        return listByUser(userId);
    }

    private void upsert(Long userId, String key, String value) {
        MotivationUserPreference preference = getOne(new LambdaQueryWrapper<MotivationUserPreference>()
                .eq(MotivationUserPreference::getUserId, userId)
                .eq(MotivationUserPreference::getPreferenceKey, key)
                .last("limit 1"));
        if (preference == null) {
            preference = new MotivationUserPreference();
            preference.setUserId(userId);
            preference.setPreferenceKey(key);
            preference.setPreferenceValue(value);
            save(preference);
            return;
        }
        preference.setPreferenceValue(value);
        updateById(preference);
    }

    private String normalizeKey(String key) {
        String normalizedKey = key == null ? "" : key.trim();
        if (!ALLOWED_KEYS.contains(normalizedKey)) {
            throw new MotivationException("PREFERENCE_KEY_INVALID", "不支持的系统配置项：" + normalizedKey);
        }
        return normalizedKey;
    }

    private String normalizeValue(Long userId, String key, Object rawValue) {
        if (rawValue == null) {
            return "";
        }
        String value = String.valueOf(rawValue).trim();
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return switch (key) {
            case KEY_SELECTED_CHILD_ID -> normalizeSelectedChildId(userId, value);
            case KEY_CALENDAR_VIEW_MODE, KEY_TASK_CALENDAR_VIEW_MODE, KEY_REWARD_CALENDAR_VIEW_MODE -> normalizeCalendarViewMode(value);
            case KEY_CALENDAR_DATE_SIZE -> normalizeCalendarDateSize(value);
            case KEY_CALENDAR_DATE_COLOR, KEY_CALENDAR_TODAY_COLOR -> normalizeHexColor(value);
            default -> value;
        };
    }

    private String normalizeSelectedChildId(Long userId, String value) {
        long childId;
        try {
            childId = Long.parseLong(value);
        } catch (NumberFormatException ex) {
            throw new MotivationException("PREFERENCE_VALUE_INVALID", "当前宝贝配置无效");
        }
        if (!familyMemberService.canView(childId, userId)) {
            throw new MotivationException("CHILD_ACCESS_DENIED", "无权查看该宝贝档案");
        }
        return String.valueOf(childId);
    }

    private String normalizeCalendarViewMode(String value) {
        String normalized = value.toLowerCase();
        if (!"month".equals(normalized) && !"week".equals(normalized)) {
            throw new MotivationException("PREFERENCE_VALUE_INVALID", "日历视图只能是月视图或周视图");
        }
        return normalized;
    }

    private String normalizeCalendarDateSize(String value) {
        int size;
        try {
            size = Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            throw new MotivationException("PREFERENCE_VALUE_INVALID", "日历日期大小必须是数字");
        }
        if (size < 14 || size > 28) {
            throw new MotivationException("PREFERENCE_VALUE_INVALID", "日历日期大小必须在 14 到 28 之间");
        }
        return String.valueOf(size);
    }

    private String normalizeHexColor(String value) {
        if (!value.matches("^#[0-9a-fA-F]{6}$")) {
            throw new MotivationException("PREFERENCE_VALUE_INVALID", "日历颜色必须是 6 位十六进制颜色");
        }
        return value;
    }

    private String describePreferences(Map<String, String> preferences) {
        return preferences.entrySet().stream()
                .map(entry -> preferenceName(entry.getKey()) + "=" + preferenceValueName(entry.getKey(), entry.getValue()))
                .toList()
                .toString();
    }

    private String preferenceName(String key) {
        return switch (key) {
            case KEY_SELECTED_CHILD_ID -> "当前宝贝";
            case KEY_CALENDAR_VIEW_MODE -> "日历视图";
            case KEY_TASK_CALENDAR_VIEW_MODE -> "任务日历视图";
            case KEY_REWARD_CALENDAR_VIEW_MODE -> "奖励日历视图";
            case KEY_CALENDAR_DATE_SIZE -> "日期大小";
            case KEY_CALENDAR_DATE_COLOR -> "日期颜色";
            case KEY_CALENDAR_TODAY_COLOR -> "今天颜色";
            default -> key;
        };
    }

    private String preferenceValueName(String key, String value) {
        if (KEY_CALENDAR_VIEW_MODE.equals(key)
                || KEY_TASK_CALENDAR_VIEW_MODE.equals(key)
                || KEY_REWARD_CALENDAR_VIEW_MODE.equals(key)) {
            return "week".equals(value) ? "周视图" : "月视图";
        }
        return StringUtils.hasText(value) ? value : "已清空";
    }
}
