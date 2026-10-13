package com.chandler.fcc.admin.monitoring.application;

import cn.dev33.satoken.stp.StpUtil;
import com.chandler.fcc.admin.monitoring.controller.req.MonitoringReq;
import com.chandler.fcc.admin.monitoring.controller.resp.MonitoringBucketResp;
import com.chandler.fcc.admin.monitoring.infrastructure.MonitoringMapper;
import java.time.DayOfWeek;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** 固定北京时间业务口径，数据库使用 UTC 半开区间，最大周期一个月。 */
@Service
@RequiredArgsConstructor
public class MonitoringService {
    private final MonitoringMapper mapper;

    /** @param request 统计周期与日期 @return 完整紧凑统计桶 */
    public List<MonitoringBucketResp> aggregate(MonitoringReq request) {
        StpUtil.checkPermission("cdr:view");
        if(request==null || request.getDate()==null || request.getPeriod()==null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"统计周期和日期不能为空");
        var start=request.getDate();
        var end=start.plusDays(1);
        switch(request.getPeriod()) {
            case "day" -> { }
            case "week" -> { start=start.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)); end=start.plusWeeks(1); }
            case "month" -> { start=start.withDayOfMonth(1);end=start.plusMonths(1); }
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"不支持的统计周期");
        }
        var zone=ZoneId.of("Asia/Shanghai");
        return mapper.aggregate(start.atStartOfDay(zone).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime(),
            end.atStartOfDay(zone).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime(),"day".equals(request.getPeriod()));
    }
}
