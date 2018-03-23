package com.wawa.service.msg;

import com.wawa.common.util.HttpClientUtils;
import org.apache.commons.lang.StringUtils;

import java.net.URLEncoder;

/**
 * 创蓝发送验证码接口
 */
public class HttpSender {

    private static final String ACCOUNT = "N1208146";

    private static final String PSWD = "Ps86329b";

    private static final String API_URL = "http://222.73.117.169/msg/HttpBatchSendSM" + "?account=" + ACCOUNT + "&pswd=" + PSWD;

    private static final String MSG_SIGN = "【来吼】";

    // needstatus 是否需要短信状态报告
    private static final boolean NEED_STATUS = false;

    // 短信发送成功状态
    private static final String SUCCESS_STATUS = "0";

    // 短信发送失败状态
    private static final String FAILURE_STATUS = "1";

    /**
     * @param mobileArr
     * @param msg
     * @return
     * @throws Exception
     */
    public static String batchSend(String[] mobileArr, String msg) throws Exception {
        String mobile = "";
        for (String tmp : mobileArr) {
            mobile += tmp + ",";
        }

        if (StringUtils.isNotBlank(mobile)) {
            msg = MSG_SIGN + msg;
            String encode = URLEncoder.encode(msg, "UTF-8");
            String url = API_URL + "&mobile=" + mobile + "&needstatus=" + NEED_STATUS + "&msg=" + encode;
            String resp = HttpClientUtils.get(url, null, HttpClientUtils.UTF8);
            if (StringUtils.isNotBlank(resp)) {
                String[] resps = StringUtils.split(resp, ",");
                String status = resps[1];
                if (status.startsWith(SUCCESS_STATUS)) {
                    return SUCCESS_STATUS;
                }
            }
        }
        return FAILURE_STATUS;
    }


    public static void main(String[] args) throws Exception {
        String[] mobile = {"15618040084"};
//        String msg = "主人,您有一个新的订单.NICK_NAME要您在CALL_TIME叫醒ta.打开甜心叫醒查看详情-甜心叫醒";
        String msg = "【甜心叫早】主人,您有一个新的订单.有理想的人妖要您在2016-12-09 05:35:00叫醒ta.打开甜心叫醒查看详情";
        System.out.println(batchSend(mobile, msg));
    }
}