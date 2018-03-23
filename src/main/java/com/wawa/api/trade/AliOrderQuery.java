package com.wawa.api.trade;

import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import com.alipay.api.request.AlipayTradeQueryRequest;
import com.alipay.api.response.AlipayTradeQueryResponse;
import com.wawa.service.alipay.config.AlipayConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * 支付宝检查订单
 */
public class AliOrderQuery {

    final static Logger logger = LoggerFactory.getLogger(AliOrderQuery.class) ;
    static final String API_URL = "https://openapi.alipay.com/gateway.do";

    public static Map<String, String> query(String out_trade_no) {
        Map<String,String> resMap = new HashMap<>();
       try{
           AlipayClient alipayClient = new DefaultAlipayClient(API_URL, AlipayConfig.APPID,AlipayConfig.RSA_PRIVATE_KEY,AlipayConfig.FORMAT,
                                                                    AlipayConfig.CHARSET,AlipayConfig.ALIPAY_PUBLIC_KEY,AlipayConfig.SIGNTYPE);
           AlipayTradeQueryRequest request = new AlipayTradeQueryRequest();
           request.setBizContent("{" + "\"out_trade_no\":\""+out_trade_no+"\"" +"}");
           AlipayTradeQueryResponse response = alipayClient.execute(request);

           if(response.isSuccess() && response.getTradeStatus().equals("TRADE_SUCCESS")){
               resMap.put("out_trade_no", response.getOutTradeNo());
               resMap.put("trade_status", response.getTradeStatus());
               resMap.put("total_fee", response.getTotalAmount());
               resMap.put("trade_no", response.getTradeNo());
               resMap.put("buyer_email", response.getBuyerUserId());
           }
        }catch (Exception e){
            logger.error("query Exception : {}", e);
        }
        return resMap;
    }

    public static void main(String[] args) throws Exception {
        System.out.println(query("1217924_1217924_1516007104317"));
        System.out.println(query("1217924_1217924_1516007040630"));

    }
}
