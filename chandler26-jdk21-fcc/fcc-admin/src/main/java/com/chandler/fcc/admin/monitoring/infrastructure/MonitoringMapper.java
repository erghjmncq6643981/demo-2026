package com.chandler.fcc.admin.monitoring.infrastructure;

import com.chandler.fcc.admin.monitoring.controller.resp.MonitoringBucketResp;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 从 Call 事实全量聚合，不从分页 CDR 推断统计结果。 */
@Mapper
public interface MonitoringMapper {
    /** @param start UTC 区间起点含 @param end UTC 终点不含 @param hourly 是否按小时 @return 完整时间桶 */
    List<MonitoringBucketResp> aggregate(@Param("start") LocalDateTime start,
        @Param("end") LocalDateTime end,@Param("hourly") boolean hourly);
}
