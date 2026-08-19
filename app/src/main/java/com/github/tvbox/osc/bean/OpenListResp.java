package com.github.tvbox.osc.bean;

import com.google.gson.annotations.SerializedName;

/**
 * OpenList API 通用响应包装 {"code":200,"message":"success","data":{...}}
 */
public class OpenListResp<T> {
    @SerializedName("code")
    public int code;
    @SerializedName("message")
    public String message = "";
    @SerializedName("data")
    public T data;

    public boolean isSuccess() {
        return code == 200;
    }
}
