/*
 * chandler26-jdk17-dingtalk
 * 2026/7/23 10:41
 *
 * Please contact chandler
 * if you need additional information or have any questions.
 * Please contact chandler Corporation or visit:
 * https://www.jianshu.com/u/117796446366
 * @author 钱丁君-chandler
 * @version 1.0
 */
package com.chandler.dingtalk.example.util;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.dingtalk.open.app.api.OpenDingTalkClient;
import com.dingtalk.open.app.api.OpenDingTalkStreamClientBuilder;
import com.dingtalk.open.app.api.callback.OpenDingTalkCallbackListener;
import com.dingtalk.open.app.api.security.AuthClientCredential;
import lombok.extern.slf4j.Slf4j;

/**
 * 类功能描述
 *
 * @author 钱丁君-chandler 2026/7/23 10:41
 * @version 1.0.0
 * @since 1.8
 */
@Slf4j
public class BotExample {
    public static void main(String[] args) throws Exception {
        OpenDingTalkClient client = OpenDingTalkStreamClientBuilder
                .custom()
                .credential(new AuthClientCredential("${ClientId}", "${ClientSecret}"))
                .registerCallbackListener("/v1.0/im/bot/messages/get", new RobotMsgCallbackConsumer())
                .build();
        client.start();
    }


    public static class RobotMsgCallbackConsumer implements OpenDingTalkCallbackListener<JSONObject, JSONObject> {

        /*
         * @param request
         * @return
         */
        @Override
        public JSONObject execute(JSONObject request) {
            System.out.println(JSON.toJSONString(request));
            try {
                JSONObject text = request.getJSONObject("text");
                log.info("收到来自钉钉的消息:{}",text);
                if (text != null) {
                    //机器人接收消息内容
                    String msg = text.getString("content").trim();
                    String openConversationId = request.getString("conversationId");
                }
            } catch (Exception e) {
                log.error("receive group message by robot error:" +e.getMessage(), e);
            }
            return new JSONObject();
        }
    }
}