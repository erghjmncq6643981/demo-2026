package com.chandler.fengteng.mock;

import static org.hamcrest.Matchers.emptyString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** 验证 PDA 协议的连接检查、持续扫码、打垛和参数错误响应。 */
@SpringBootTest
@AutoConfigureMockMvc
class EventTaskControllerTest {

    @Autowired
    private MockMvc mockMvc;

    /** 连接检查无需任务字段，也不生成箱号或垛码。 */
    @Test
    void connectReturnsSuccessWithoutTaskData() throws Exception {
        mockMvc.perform(post("/privare-protocol/fengteng/event-task")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version":"1.0","method":"scan-connect","payload":{"variables":{"device":"PDA"}}}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.successful").value(true))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.method").value("scan-connect"))
                .andExpect(jsonPath("$.data.shortCode").value(emptyString()))
                .andExpect(jsonPath("$.data.shortId").value(emptyString()))
                .andExpect(jsonPath("$.data.assetsCodeList", hasSize(0)))
                .andExpect(jsonPath("$.data.variables.device").value("PDA"));
    }

    /** 扫码返回箱号，透传 method 和 variables。 */
    @Test
    void scanReturnsAssetCodes() throws Exception {
        mockMvc.perform(post("/privare-protocol/fengteng/event-task")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version":"1.0","method":"scan-start","payload":{"taskType":0,
                                "taskNo":"YD20260101001","variables":{"source":"PDA扫码"}}}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.successful").value(true))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.method").value("scan-start"))
                .andExpect(jsonPath("$.data.shortCode").value(emptyString()))
                .andExpect(jsonPath("$.data.shortId").value(emptyString()))
                .andExpect(jsonPath("$.data.assetsCodeList", hasSize(3)))
                .andExpect(jsonPath("$.data.assetsCodeList[0]").value(matchesPattern("FC\\.\\d{8}")))
                .andExpect(jsonPath("$.data.variables.source").value("PDA扫码"));
    }

    /** 查询时累计箱号，停止后不再允许查询该任务。 */
    @Test
    void scanInfoReturnsAccumulatedAssetsUntilEnd() throws Exception {
        String path = "/privare-protocol/fengteng/event-task";
        mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content("""
                {"version":"1.0","method":"scan-start","payload":{"taskType":0,"taskNo":"YD-SCAN-INFO"}}
                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.assetsCodeList", hasSize(3)));

        String infoRequest = """
                {"version":"1.0","method":"scan-info","payload":{"taskNo":"YD-SCAN-INFO"}}
                """;
        mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(infoRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.method").value("scan-info"))
                .andExpect(jsonPath("$.data.assetsCodeList", hasSize(6)))
                .andExpect(jsonPath("$.data.shortCode").value(emptyString()));

        mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content("""
                {"version":"1.0","method":"scan-end","payload":{"taskNo":"YD-SCAN-INFO"}}
                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.assetsCodeList", hasSize(6)));

        mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(infoRequest))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.successful").value(false));
    }

    /** 打垛返回非空垛码及箱号。 */
    @Test
    void stackingReturnsShortCode() throws Exception {
        mockMvc.perform(post("/privare-protocol/fengteng/event-task")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version":"1.0","method":"scan-start","payload":{"taskType":1,
                                "taskNo":"YD20260101001"}}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.method").value("scan-start"))
                .andExpect(jsonPath("$.data.shortCode").value(not(emptyString())))
                .andExpect(jsonPath("$.data.shortId").value(not(emptyString())))
                .andExpect(jsonPath("$.data.assetsCodeList", hasSize(3)))
                .andExpect(jsonPath("$.data.variables").isMap());
    }

    /** 无效任务类型返回明确错误。 */
    @Test
    void invalidTaskTypeReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/privare-protocol/fengteng/event-task")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version":"1.0","payload":{"taskType":2,"taskNo":"YD20260101001"}}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.successful").value(false))
                .andExpect(jsonPath("$.code").value(400));
    }
}
