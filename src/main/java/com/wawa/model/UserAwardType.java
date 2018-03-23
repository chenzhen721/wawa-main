package com.wawa.model;

/**
 *用户奖励类型
 */
public enum UserAwardType {
    UnKnown(null),
    新用户注册("new_user"),
    新手引导("user_guide"),
    钻石红包("diamondpacket_reward"),
    钻石红包退款("diamondpacket_refund"),
    抓娃娃送积分("catch_points"),
    邀请钻石("invite_diamond"),
    邀请积分("invite_points"),
    签到钻石("sign_diamond"),
    关注钻石("follow_diamond"),
    过期兑积分("expire_points"),
    兑吧扣积分("duiba_deduct_points"),
    ;

    private String id;

    UserAwardType(String id){
        this.id = id;
    }

    public String getId(){
        return this.id;
    }
}
