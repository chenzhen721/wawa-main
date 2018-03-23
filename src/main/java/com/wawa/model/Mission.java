package com.wawa.model;

/**
 * date: 13-3-3 上午11:06
 *
 * @author: yangyang.cong@ttpod.com
 */
public enum Mission {

    首充100("money100"), //奖励邀请名额
    注册奖励("register"),
    关注奖励("weixinfollow"), //关注奖励
    ;

    Mission(String id){
        this.id = id;
        this.mongoKey = "mission." + id;
    }
    public final String id;
    public final String mongoKey;

    public String getId() {
        return id;
    }

    public static enum Status{
        完成未领取奖金,奖金已领取,不再显示;
    }
}
