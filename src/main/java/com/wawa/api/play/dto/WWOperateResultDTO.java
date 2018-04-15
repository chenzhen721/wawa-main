package com.wawa.api.play.dto;

/**
 * Created by Administrator on 2017/11/10.
 */
public class WWOperateResultDTO {

    private String user_id;
    private String device_id;
    private Integer operate_result;
    private String platform;
    private String add_time;

    public String getUser_id() {
        return user_id;
    }

    public void setUser_id(String user_id) {
        this.user_id = user_id;
    }

    public String getDevice_id() {
        return device_id;
    }

    public void setDevice_id(String device_id) {
        this.device_id = device_id;
    }

    public Integer getOperate_result() {
        return operate_result;
    }

    public void setOperate_result(Integer operate_result) {
        this.operate_result = operate_result;
    }

    public String getPlatform() {
        return platform;
    }

    public void setPlatform(String platform) {
        this.platform = platform;
    }

    public String getAdd_time() {
        return add_time;
    }

    public void setAdd_time(String add_time) {
        this.add_time = add_time;
    }
}
