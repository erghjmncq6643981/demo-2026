package com.chandler.motivation.controller;

import com.chandler.motivation.common.result.ApiResponse;
import com.chandler.motivation.domain.dto.calendar.CalendarEventResponse;
import com.chandler.motivation.service.AuthService;
import com.chandler.motivation.service.CalendarService;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/calendar")
public class CalendarController {

    private final CalendarService calendarService;
    private final AuthService authService;

    @GetMapping
    public ApiResponse<List<CalendarEventResponse>> monthView(@RequestParam Long childId,
                                                              @RequestParam(required = false) Integer year,
                                                              @RequestParam(required = false) Integer month) {
        return ApiResponse.ok(calendarService.monthView(childId, year, month, authService.requireUser().getId()));
    }

    @GetMapping("/range")
    public ApiResponse<List<CalendarEventResponse>> rangeView(@RequestParam Long childId,
                                                              @RequestParam(required = false) LocalDate startDate,
                                                              @RequestParam(required = false) LocalDate endDate) {
        return ApiResponse.ok(calendarService.rangeView(childId, startDate, endDate, authService.requireUser().getId()));
    }
}
