package com.wawa.api.play.dto;

/**
 * 娃娃机设备列表信息
 * Created by Administrator on 2017/11/10.
 */
public class WWRoomDTO {

    private String _id; //设备id
    private String name; //设备名称
    private String pull_stream; //视频流socket地址
    private String online_status; //on off
    private int device_status; //机器状态 0-空闲 1-游戏中 2-维护中

    public String get_id() {
        return _id;
    }

    public void set_id(String _id) {
        this._id = _id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPull_stream() {
        return pull_stream;
    }

    public void setPull_stream(String pull_stream) {
        this.pull_stream = pull_stream;
    }

    public String getOnline_status() {
        return online_status;
    }

    public void setOnline_status(String online_status) {
        this.online_status = online_status;
    }

    public int getDevice_status() {
        return device_status;
    }

    public void setDevice_status(int device_status) {
        this.device_status = device_status;
    }
}
