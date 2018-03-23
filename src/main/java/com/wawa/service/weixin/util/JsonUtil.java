package com.wawa.service.weixin.util;


import com.wawa.common.util.JSONUtil;

import java.util.Map;

/**
 * 使用jackson处理json，非微信demo
 */
public class JsonUtil {

//	public static String getJsonValue(String rescontent, String key) {
//		JSONObject jsonObject;
//		String v = null;
//		try {
//			jsonObject = new JSONObject(rescontent);
//			v = jsonObject.getString(key);
//		} catch (Exception e1) {
//			// TODO Auto-generated catch block
//			e1.printStackTrace();
//		}
//		return v;
//	}

    public static String getJsonValue(String rescontent, String key) {
        String v = null;
        try {
            Map<String, Object> map = JSONUtil.jsonToMap(rescontent);
            if (map != null) {
                v = (String) map.get(key);
            }
        } catch (Exception e1) {
            // TODO Auto-generated catch block
            e1.printStackTrace();
        }
        return v;
    }
}
