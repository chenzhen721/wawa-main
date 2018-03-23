package com.wawa.model;


public enum IMType {
    系统消息("msg.system","msg_system"),
    全局消息("global.notify","global_notify"),
    开奖通知("global.marquee","global_marquee"),
    家族事件("event.status","event_status"), //寻宝
    加入事件("event.join","event_join"), //寻宝加入
    好友消息("user.chat", "user_chat");

    private String action;

    private String event;

    IMType(String action, String event) {
        this.action = action;
        this.event = event;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getEvent() {
        return event;
    }

    public void setEvent(String event) {
        this.event = event;
    }
}
