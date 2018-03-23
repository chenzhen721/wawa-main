package com.wawa.service.weixin;



import com.wawa.service.weixin.client.TenpayHttpClient;
import com.wawa.service.weixin.util.WXUtil;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;

/**
 * 预下单请求处理类
 */
public class WebRequestHandler extends RequestHandler {

    public WebRequestHandler(HttpServletRequest request, HttpServletResponse response) {
        super(request, response);
        super.setGateUrl("https://api.mch.weixin.qq.com/pay/unifiedorder");
    }

    public Map sendPrepay() throws Exception {
        //创建签名
        this.createSign();
        String xmlBody = WXUtil.mapToXml(this.getAllParameters());
        TenpayHttpClient httpClient = new TenpayHttpClient();
        httpClient.setCharset("utf-8");
        Map resMap = null;
        if (httpClient.callHttpPost(this.getGateUrl(), xmlBody)) {
            String resContent = httpClient.getResContent();
            resMap = WXUtil.parseXml(resContent);
        }
        return resMap;
    }

}