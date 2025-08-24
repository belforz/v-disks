package com.v_disk.utils;


public class ResponseJSON<T> {
    private String status;
    private T data;

    public ResponseJSON(String status,T data) {
        this.status = status;
        this.data = data;
    }

    public String getStatus() {
        return status;
    }

    

    public T getData() {
        return data;
    }

    public void setStatus(String status) {
        this.status = status;
    }


    public void setData(T data) {
        this.data = data;
    }
}
