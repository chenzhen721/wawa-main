package com.wawa.api.trade;

import java.math.BigDecimal;

/**
 * 延迟订单信息
 */
public final class DelayOrdeInfo {

    private String orderId;
    private Double cny;
    private Long coin;
    private String tradeNo;
    private String buyerEmail;
    private String feeType;
    private String attach;
    private String via;
    private Integer rate = 100;
    public DelayOrdeInfo(){

    }
    public  DelayOrdeInfo(Integer rate){
        this.rate = rate;
    }
    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public Double getCny() {
        return cny;
    }

    public void setCny(Double cny) {
        this.cny = cny;
    }

    public String getTradeNo() {
        return tradeNo;
    }

    public void setTradeNo(String tradeNo) {
        this.tradeNo = tradeNo;
    }

    public String getBuyerEmail() {
        return buyerEmail;
    }

    public void setBuyerEmail(String buyerEmail) {
        this.buyerEmail = buyerEmail;
    }

    public String getFeeType() {
        return feeType;
    }

    public void setFeeType(String feeType) {
        this.feeType = feeType;
    }

    public String getAttach() {
        return attach;
    }

    public void setAttach(String attach) {
        this.attach = attach;
    }

    public String getVia() {
        return via;
    }

    public void setVia(String via) {
        this.via = via;
    }

    public Integer getRate() {
        return rate;
    }

    public void setRate(Integer rate) {
        this.rate = rate;
    }

    public Long getCoin() {
        return (new BigDecimal(cny.toString()).multiply(new BigDecimal(rate.toString()))).longValue();
    }

}
