package com.wawa.model;

/**
 * 支付订单类型
 */
public enum OrderVia {
    支付宝手机("ali_m"),
    支付宝PC("ali_pc"),
    支付宝WAP("ali_wap"),
    微信手机("weixin_m"),
    爱微游("aiweiyou_wap"),
    微游("weiyou_wap"),
    微信PC星启天("weixin_pc"),
    微信H5("weixin_h5"),
    微信WAP("weixin_wap"),
    么么H5("meme_h5"),
    银联("unionpay");

    private String id;
    OrderVia(String id){
        this.id = id;
    }

    public String getId(){
        return this.id;
    }
}
