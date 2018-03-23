package com.wawa.api.trade;

import com.wawa.service.weixin.RequestHandler;
import com.wawa.service.weixin.client.TenpayHttpClient;
import com.wawa.service.weixin.util.WXUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;

/**
 * 微信查询订单
 */
public class WeixinOrderQuery extends RequestHandler {

    final static Logger logger = LoggerFactory.getLogger(WeixinOrderQuery.class) ;

    private static final String MCH_ID = "1495650632";
    private static final String APP_ID = "wxf64f0972d4922815";
    private static final String APP_KEY = "fbf4fd32c00a82d5cbe5161c5e699a0e";

    public WeixinOrderQuery(HttpServletRequest request, HttpServletResponse response) {
        super(request, response);
        super.setGateUrl("https://api.mch.weixin.qq.com/pay/orderquery");
    }

    public String sendQuery() throws Exception {
        //创建签名
        this.createSign();
        String xmlBody = WXUtil.mapToXml(this.getAllParameters());
        TenpayHttpClient httpClient = new TenpayHttpClient();
        httpClient.setCharset("utf-8");
        httpClient.setTimeOut(3);
        String resContent = null;
        if (httpClient.callHttpPost(this.getGateUrl(), xmlBody)) {
            resContent = httpClient.getResContent();
        }
        return resContent;
    }

    public static Map<String, String> query(String out_trade_no){
        try{
            WeixinOrderQuery reqHandler = new WeixinOrderQuery(null, null);//生成package的请求类
            reqHandler.setKey(APP_KEY);
            reqHandler.setParameter("appid", APP_ID);
            reqHandler.setParameter("mch_id", MCH_ID);
            reqHandler.setParameter("out_trade_no", out_trade_no);
            reqHandler.setParameter("nonce_str", WXUtil.getNonceStr());
            String resContent = reqHandler.sendQuery();
            Map<String, String> resMap = WXUtil.parseXml(resContent);
            return resMap;
        }catch (Exception e){
            logger.error("query Exception : {}", e);
        }
        return null;
    }

    public static void main(String[] args) throws Exception {
        System.out.println(query("1371913_1371913_1515689362968"));

    }
}
