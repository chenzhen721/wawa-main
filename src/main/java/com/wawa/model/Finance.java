package com.wawa.model;

/**
 * user finance
 */
public interface Finance {


    String finance = "finance";
    String coin_count = "coin_count";//金币
    String diamond_count = "diamond_count";//钻石
    String cash_count = "cash_count";//现金
    String bean_count = "bean_count";
    String feather_send_total = "feather_send_total";
    String sign_daily_total = "sign_daily_total";
    String sign_total = "sign_total";

    String bean_count_total = "bean_count_total";
    String coin_spend_total = "coin_spend_total";

    String finance$coin_count = finance +"." +coin_count;
    String finance$diamond_count = finance +"." +diamond_count;
    String finance$bean_count = finance +"." +bean_count;
    String finance$cash_count = finance +"." +cash_count;
    String finance$coin_spend_total = finance +"." +coin_spend_total;
    String finance$bean_count_total = finance +"." +bean_count_total;
    String finance$feather_send_total = finance +"." +feather_send_total;
    String finance$sign_daily_total = finance +"." +sign_daily_total;
    String finance$sign_total = finance +"." +sign_total;

    /**
     * 最近7天所消费星币
     */
    String week_spend  =  "week_spend";

}
