/**
 * Alipay.com Inc.
 * Copyright (c) 2005-2006 All Rights Reserved.
 */
package com.wawa.service.alipay.client.base;

import lombok.Data;

/**
 * 调用支付宝服务返回的结果
 * 
 * @author 3y
 * @version $Id: ResponseResult.java, v 0.1  2011-08-28 1:37:15 PM 3y Exp $
 */
@Data
public class ResponseResult {

	/**
	 * 是否调用成功 默认为false 所以在每次调用都必须设置这个值为true；
	 */
	private boolean success = false;

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    /**
	 * 调用的业务成功结果 如果调用失败 这个将是空值
	 */
	private String businessResult;

    public String getBusinessResult() {
        return businessResult;
    }

    public void setBusinessResult(String businessResult) {
        this.businessResult = businessResult;
    }
}
