package com.wawa.service.weixin.entity;

/**
 * Created by monkey on 16/9/18.
 */
public class WeXinMenu {

    private String name;

    private String type;

    private String url;

    public WeXinMenu(String name, String type, String url) {
        this.name = name;
        this.type = type;
        this.url = url;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
