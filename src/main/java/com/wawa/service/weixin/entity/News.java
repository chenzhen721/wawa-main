package com.wawa.service.weixin.entity;

import java.util.Map;

/**
 * Created by monkey on 16/9/14.
 */
public class News {
    private String msgtype;

    private String touser;

    private Map news;

    public News(String msgtype, String touser, Map news) {
        this.msgtype = msgtype;
        this.touser = touser;
        this.news = news;
    }

    public String getMsgtype() {
        return msgtype;
    }

    public void setMsgtype(String msgtype) {
        this.msgtype = msgtype;
    }

    public String getTouser() {
        return touser;
    }

    public void setTouser(String touser) {
        this.touser = touser;
    }

    public Map getNews() {
        return news;
    }

    public void setNews(Map news) {
        this.news = news;
    }
}
