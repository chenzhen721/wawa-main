package com.wawa.service.weixin.entity;

/**
 * Created by monkey on 16/9/14.
 */
public class Article {

    private String title;

    private String description;

    private String picurl;

    private String url;

    public Article(String title, String description, String picurl, String url) {
        this.title = title;
        this.description = description;
        this.picurl = picurl;
        this.url = url;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getPicurl() {
        return picurl;
    }

    public void setPicurl(String picurl) {
        this.picurl = picurl;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }
}
