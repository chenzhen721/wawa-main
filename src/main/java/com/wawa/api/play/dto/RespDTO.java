package com.wawa.api.play.dto;

/**
 * Created by Administrator on 2017/11/10.
 */
public class RespDTO<T> {

    private Integer code;
    private T data;

    public Integer getCode() {
        return code;
    }

    public void setCode(Integer code) {
        this.code = code;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }
}
