/*
 * chandler26-jdk17-dingtalk
 * 2026/7/23 10:48
 *
 * Please contact chandler
 * if you need additional information or have any questions.
 * Please contact chandler Corporation or visit:
 * https://www.jianshu.com/u/117796446366
 * @author 钱丁君-chandler
 * @version 1.0
 */
package com.chandler.dingtalk.example.util;

import com.dingtalk.api.DefaultDingTalkClient;
import com.dingtalk.api.DingTalkClient;
import com.dingtalk.api.request.OapiRobotSendRequest;
import com.dingtalk.api.response.OapiRobotSendResponse;
import com.taobao.api.ApiException;
import org.apache.commons.codec.binary.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.List;

/**
 * 类功能描述
 *
 * @author 钱丁君-chandler 2026/7/23 10:48
 * @version 1.0.0
 * @since 1.8
 */
public class DingTalkWebhookExample {
    public static final String SECRET = "SEC3bdbd424f1860309b0470d4bff6c33d20a03e5498af825a6c634815c5b24d637";

    public static void main(String[] args) throws ApiException, NoSuchAlgorithmException, UnsupportedEncodingException, InvalidKeyException {
        Long timestamp = System.currentTimeMillis();
        System.out.println(timestamp);
        String secret = SECRET;
        String stringToSign = timestamp + "\n" + secret;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes("UTF-8"), "HmacSHA256"));
        byte[] signData = mac.doFinal(stringToSign.getBytes("UTF-8"));
        String sign = URLEncoder.encode(new String(Base64.encodeBase64(signData)), "UTF-8");
        System.out.println(sign);

        String serverUrl = "https://oapi.dingtalk.com/robot/send?access_token=053763504b2c1f3f98fcea5742e00a2ed7045a85f68f65ce605e7b673995ed89&sign=" + sign + "&timestamp=" + timestamp;
        DingTalkClient client = new DefaultDingTalkClient(serverUrl);
        OapiRobotSendRequest request = new OapiRobotSendRequest();
        request.setMsgtype("text");
        OapiRobotSendRequest.Text text = new OapiRobotSendRequest.Text();
        text.setContent("测试文本消2");
        request.setText(text);
        OapiRobotSendRequest.At at = new OapiRobotSendRequest.At();
//        at.setAtUserIds(Arrays.asList("4525xxxxxxxxx7041"));
        at.setAtMobiles(List.of("18762696252"));
        at.setIsAtAll(false);
        request.setAt(at);
        OapiRobotSendResponse response = client.execute(request);
        System.out.println(response.getBody());
    }
}