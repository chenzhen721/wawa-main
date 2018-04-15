package com.wawa.api.play.dto;

/**
 * 娃娃机设备列表信息
 * Created by Administrator on 2017/11/10.
 */
public class WWRoomDTO {

    private String _id; //设备id
    private String name; //设备名称
    private String server_uri; //控制socket
    private String stream_uri; //视频流socket地址
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

    public String getServer_uri() {
        return server_uri;
    }

    public void setServer_uri(String server_uri) {
        this.server_uri = server_uri;
    }

    public String getStream_uri() {
        return stream_uri;
    }

    public void setStream_uri(String stream_uri) {
        this.stream_uri = stream_uri;
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
