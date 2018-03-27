/**
 * Alipay.com Inc.
 * Copyright (c) 2005-2008 All Rights Reserved.
 */
package com.wawa.service.alipay.client.trade;

//import com.alipay.api.AlipayApiException;
//import com.alipay.api.AlipayClient;
//import com.alipay.api.DefaultAlipayClient;
//import com.alipay.api.domain.AlipayTradeWapPayModel;
//import com.alipay.api.request.AlipayTradeWapPayRequest;
//import com.alipay.api.response.AlipayTradeWapPayResponse;
import com.wawa.AppProperties;
import com.wawa.service.alipay.config.AlipayConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * alipay.trade.pay(统一收单交易支付接口).
 */
public class WapTrade2 {

    static final Logger log = LoggerFactory.getLogger(WapTrade2.class);

    /*AlipayClient alipayClient = new DefaultAlipayClient(AlipayConfig.URL,
            AlipayConfig.APPID,
            AlipayConfig.RSA_PRIVATE_KEY,
            AlipayConfig.FORMAT,
            AlipayConfig.CHARSET,
            AlipayConfig.ALIPAY_PUBLIC_KEY,
            AlipayConfig.SIGNTYPE);*/

    static String notifyUrl = AppProperties.get("api.domain") + "ali/wap_notify";

    /*public AlipayTradeWapPayResponse pay(String subject, String outTradeNo, String amount, String returnUrl) throws AlipayApiException {
        AlipayTradeWapPayModel model = new AlipayTradeWapPayModel();
        model.setOutTradeNo(outTradeNo);
        model.setSubject(subject);
        model.setTotalAmount(amount);

        AlipayTradeWapPayRequest req = new AlipayTradeWapPayRequest();
        req.setBizModel(model);
        req.setNotifyUrl(notifyUrl);
        req.setReturnUrl(returnUrl);
        return alipayClient.pageExecute(req);
    }*/
}
