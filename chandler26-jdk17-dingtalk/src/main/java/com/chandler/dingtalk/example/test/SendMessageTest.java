/*
 * chandler26-jdk17-dingtalk
 * 2026/6/26 13:53
 *
 * Please contact chandler
 * if you need additional information or have any questions.
 * Please contact chandler Corporation or visit:
 * https://www.jianshu.com/u/117796446366
 * @author 钱丁君-chandler
 * @version 1.0
 */
package com.chandler.dingtalk.example.test;

import com.dingtalk.api.DefaultDingTalkClient;
import com.dingtalk.api.DingTalkClient;
import com.dingtalk.api.request.OapiGettokenRequest;
import com.dingtalk.api.request.OapiMessageCorpconversationAsyncsendV2Request;
import com.dingtalk.api.response.OapiGettokenResponse;
import com.dingtalk.api.response.OapiMessageCorpconversationAsyncsendV2Response;
import com.taobao.api.ApiException;
import org.apache.commons.lang3.StringUtils;

/**
 * 类功能描述
 *
 * @author 钱丁君-chandler 2026/6/26 13:53
 * @version 1.0.0
 * @since 1.8
 */
public class SendMessageTest {
    private static String APP_KEY = "dingva1c6prbsrbzkpjw";
    private static String APP_SECRET = "SEqPvGRVpIc6OPY4bL1ZjBfDUUHD2zTtbbHn2e0O_WzXyjg4Shch6zO3TTgfiOoZ";
    private static Long AGENT_ID = 4720089706L;


    private static String getAccessToken() throws ApiException {
        DefaultDingTalkClient client = new DefaultDingTalkClient("https://api.dingtalk.com/v1.0/oauth2/accessToken");
        OapiGettokenRequest request = new OapiGettokenRequest();
        //Appkey
        request.setAppkey(APP_KEY);
        //Appsecret
        request.setAppsecret(APP_SECRET);
        /*请求方式*/
        request.setHttpMethod("POST");
        OapiGettokenResponse response = client.execute(request);
        System.out.println(response.getAccessToken());
        return response.getAccessToken();
    }

    public static void main(String[] args) throws ApiException {
        String accessToken = getAccessToken();
//        System.out.println("accessToken: "+accessToken);
        if (StringUtils.isEmpty(accessToken)) {
            return;
        }
        DingTalkClient client = new DefaultDingTalkClient("https://oapi.dingtalk.com/topapi/message/corpconversation/asyncsend_v2");
        OapiMessageCorpconversationAsyncsendV2Request request = new OapiMessageCorpconversationAsyncsendV2Request();
        request.setAgentId(AGENT_ID);
        request.setUseridList("791-wtik7nidu");
        request.setToAllUser(false);
        OapiMessageCorpconversationAsyncsendV2Request.Msg msg = new OapiMessageCorpconversationAsyncsendV2Request.Msg();
        msg.setMsgtype("text");
        OapiMessageCorpconversationAsyncsendV2Request.Text text = new OapiMessageCorpconversationAsyncsendV2Request.Text();
        text.setContent("这是一个测试消息");
        msg.setText(text);
        request.setMsg(msg);
        OapiMessageCorpconversationAsyncsendV2Response rsp = client.execute(request, accessToken);
        System.out.println(rsp.getBody());
    }
}