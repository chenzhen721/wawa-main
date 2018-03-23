package com.wawa.service.alipay.client.base;


import com.wawa.AppProperties;

public interface PartnerConfig {
    //合作商户ID。用签约支付宝账号登录ms.alipay.com后，在账户信息页面获取。
    String PARTNER = "2088712526419625";
    // 商户收款的支付宝账号
    String SELLER =  "memezhibo@qq.com";
    // 商户（MD5）KEY
    String KEY= "aonb0vq59msqrvjj82zohq868s4vc9mz";
    // 支付成功跳转链接
    String call_back_url = AppProperties.get("h5.domain") + "pay/success";
    // 未完成支付，用户点击链接返回商户url
    //String merchantUrl = AppProperties.get("site.domain") + "alipay.html";
    String merchantUrl = AppProperties.get("h5.domain") + "pay/success";

    String notifyUrl = AppProperties.get("api.domain")+"pay/ali_wap_notify";

}
