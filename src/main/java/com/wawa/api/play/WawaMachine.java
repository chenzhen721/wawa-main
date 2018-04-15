package com.wawa.api.play;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.wawa.api.play.dto.RespDTO;
import com.wawa.api.play.dto.WWAssignDTO;
import com.wawa.api.play.dto.WWListDTO;
import com.wawa.api.play.dto.WWOperateResultDTO;
import com.wawa.api.play.dto.WWRoomDTO;
import com.wawa.base.BaseController;
import com.wawa.common.util.HttpClientUtils;
import com.wawa.common.util.HttpsClientUtils;
import com.wawa.common.util.JSONUtil;
import com.wawa.common.util.MsgDigestUtil;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * 奇异果 抓娃娃
 * Created by Administrator on 2017/11/10.
 */
public abstract class WawaMachine {

    static final Logger logger = LoggerFactory.getLogger(WawaMachine.class);

    public static final String HOST = BaseController.isTest ? "http://test-server.doll520.com" : "http://test-server.doll520.com";
    public static final String APP_ID = BaseController.isTest ? "wawa_default" : "wawa_default";
    public static final String APP_SECRET = BaseController.isTest ? "ab75e7a2de882107d3bc89948a1baa9e" : "ab75e7a2de882107d3bc89948a1baa9e";
    public static final MsgDigestUtil md5 = MsgDigestUtil.MD5;
    public static final TypeFactory typeFactory = TypeFactory.defaultInstance();

    /*public static String creatSign(SortedMap<String, Object> params) {
        StringBuffer sb = new StringBuffer();
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            String k = entry.getKey();
            if (KEY_WITHOUT_SIGN.contains(k) || entry.getValue() == null) {
                continue;
            }
            String v = String.valueOf(entry.getValue());
            sb.append(k).append(v);
        }
        return md5.digest2HEX(md5.digest2HEX(sb.toString()) + APP_ID);
    }*/

    /**
     * 1.娃娃机列表
     */
    public static List<WWRoomDTO> room_list() {
        String url = HOST + "/public/list";
        SortedMap<String, Object> params = new TreeMap<>();
        params.put("app_id", APP_ID);
        params.put("page", 1);
        params.put("size", 100);
        String value = doGet(url, params);
        WWListDTO WWListDTO = toBean(value, WWListDTO.class);
        if (WWListDTO == null || WWListDTO.getList() == null) {
            return null;
        }
        return WWListDTO.getList();
    }

    /**
     *  2.娃娃机详情
     */
    public static WWRoomDTO room_detail(String roomId) {
        if (roomId == null) {
            return null;
        }
        String url = HOST + "/public/info";
        SortedMap<String, Object> params = new TreeMap<>();
        params.put("app_id", APP_ID);
        params.put("device_id", roomId);
        params.put("ts", System.currentTimeMillis());
        String value = doGet(url, params);
        return toBean(value, WWRoomDTO.class);
    }

//    /**
//     *  //todo 预留接口，更新机器读取信息等
//     */
//    public static WawaRoomDTO update(String roomId) {
//        if (roomId == null) {
//            return null;
//        }
//        String url = HOST + "/public/update";
//        SortedMap<String, Object> params = new TreeMap<>();
//        params.put("app_id", APP_ID);
//        params.put("device_id", roomId);
//        params.put("ts", System.currentTimeMillis());
//        String value = doPost(url, params);
//        return toBean(value, WawaRoomDTO.class);
//    }

    /**
     * 3.申请分配娃娃机
     */
    public static WWAssignDTO assign(String roomId, String record_id, Integer userId, Integer lw, Integer hw, Integer htl) {
        if (roomId == null || userId == null) {
            return null;
        }
        String url = HOST + "/public/assign";
        SortedMap<String, Object> params = new TreeMap<>();
        //分配娃娃机需要预分配机器抓力，这个地方重点处理
        params.put("app_id", APP_ID);
        params.put("device_id", roomId);
        params.put("record_id", record_id); //本地生成的唯一ID
        params.put("user_id", userId);
        params.put("lw", lw); //弱抓力
        params.put("hw", hw); //强抓力
        params.put("htl", htl); //强转弱
        params.put("ts", System.currentTimeMillis()); //暂时没用上 给签名使用的
        String value = doGet(url, params);
        return toBean(value, WWAssignDTO.class);
    }

    /**
     * 6.查询操作结果 //todo
     */
    public static WWOperateResultDTO operateResult(String logId) {
        if (logId == null) {
            return null;
        }
        String url = HOST + "/api/index.php";
        SortedMap<String, Object> params = new TreeMap<>();
        params.put("app", "doll");
        params.put("act", "operate_result");
        params.put("log_id", logId);
        params.put("ts", System.currentTimeMillis());
        String value = doGet(url, params);
        return toBean(value, WWOperateResultDTO.class);
    }

//    /**
//     * 默认概率调整， 范围1-888
//     * @param device_id
//     * @param winning_probability
//     * @return
//     */
//    public static QiygOperateResultDTO winning_rate(String device_id, String winning_probability) {
//        String url = HOST + "/api/index.php";
//        SortedMap<String, Object> params = new TreeMap<>();
//        params.put("app", "doll");
//        params.put("act", "set_winning_probability");
//        params.put("platform", PLATFORM);
//        params.put("device_id", device_id);
//        params.put("winning_probability", winning_probability);
//        params.put("ts", System.currentTimeMillis());
//        String value = doGet(url, params);
//        return toBean(value, QiygOperateResultDTO.class);
//    }

    private static <T> T respBean(String value, Class<T> parametrized) {
        if (StringUtils.isBlank(value) ) {
            return null;
        }
        try {
            return JSONUtil.jsonToBean(value, parametrized);
        } catch (Exception e) {
            e.printStackTrace();
            logger.error("translate json to bean error: " + value);
        }
        return null;
    }

    private static <T> T toBean(String value, Class<?>... parameterClasses) {
        if (StringUtils.isBlank(value) || parameterClasses == null || parameterClasses.length <= 0) {
            return null;
        }
        try {
            JavaType paramType = createJavaType(parameterClasses);
            JavaType javaType = typeFactory.constructParametricType(RespDTO.class, paramType);
            RespDTO<T> result = JSONUtil.jsonToBean(value, javaType);
            if (result != null && result.getCode() == 1 && result.getData() != null) {
                return result.getData();
            }
        } catch (Exception e) {
            e.printStackTrace();
            logger.error("translate json to bean error: " + value);
        }
        return null;
    }

    private static JavaType createJavaType(Class<?>... param) {
        if (param.length == 1) {
            return typeFactory.uncheckedSimpleType(param[0]);
        }else if (param.length == 2) {
            if (param[0].equals(List.class)) {
                return typeFactory.constructCollectionType(List.class, param[1]);
            } else {
                logger.error("unknown param type");
                return null;
            }
        }
        return null;
    }

    private static String doGet(String url, SortedMap<String, Object> params) {
        String value = null;
        url = url + "?" +buildParam(params);
        try {
            if (url.startsWith("http://")) {
                value = HttpClientUtils.get(url, null);
            } else if (url.startsWith("https://")) {
                value = HttpsClientUtils.get(url, null);
            }
        } catch (Exception e) {
            logger.error("Get " + url + " error.", e);
        }
        return value;
    }

    private static String doPost(String url, SortedMap<String, Object> params) {
        String value = null;

        try {
            if (url.startsWith("http://")) {
                value = HttpClientUtils.post(url, buildPostParam(params), null);
            } else if (url.startsWith("https://")) {
                value = HttpsClientUtils.post(url, buildPostParam(params),null);
            }
        } catch (Exception e) {
            logger.error("Post " + url + " error.", e);
        }
        return value;
    }

    private static String buildParam(SortedMap<String, Object> params) {
        //todo String sign = creatSign(params);
        StringBuffer sb = new StringBuffer();
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            String k = entry.getKey();
            String v = String.valueOf(entry.getValue());
            sb.append(k).append("=").append(v).append("&");
        }
        //sb.append("sign=").append(sign);
        return sb.toString();
    }

    private static Map<String, String> buildPostParam(SortedMap<String, Object> params) {
        Map<String, String> map = new HashMap<>();
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            String k = entry.getKey();
            String v = String.valueOf(entry.getValue());
            map.put(k, v);
        }
        return map;
    }

}
