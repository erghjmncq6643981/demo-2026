package com.chandler.fcc.admin.monitoring.controller.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

/** 以北京时间的日、周或月请求完整话单聚合。 */
@Data
@Schema(description="话务监控聚合查询")
public class MonitoringReq {
    @NotNull @Pattern(regexp="day|week|month")
    @Schema(description="统计周期：day 日、week 周、month 月",example="day")
    private String period;
    @NotNull @DateTimeFormat(iso=DateTimeFormat.ISO.DATE)
    @Schema(description="北京时间的周期定位日期",example="2026-10-04")
    private LocalDate date;
}
