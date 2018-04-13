package com.wawa.api.play;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.wawa.common.util.JSONUtil;
import com.wawa.common.util.MsgDigestUtil;
import com.wawa.common.util.HttpClientUtils;
import com.wawa.common.util.HttpsClientUtils;
import com.wawa.api.play.dto.QiygAssignDTO;
import com.wawa.api.play.dto.QiygListDTO;
import com.wawa.api.play.dto.QiygOperateResultDTO;
import com.wawa.api.play.dto.QiygOrderResultDTO;
import com.wawa.api.play.dto.QiygRespDTO;
import com.wawa.api.play.dto.QiygResultDTO;
import com.wawa.api.play.dto.QiygRoomDTO;
import com.wawa.api.play.model.OperateType;
import com.wawa.base.BaseController;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * 奇异果 抓娃娃
 * Created by Administrator on 2017/11/10.
 */
public abstract class Qiyiguo {

    static final Logger logger = LoggerFactory.getLogger(Qiyiguo.class);

    public static final String HOST = BaseController.isTest ? "https://testdoll.artqiyi.com" : "http://doll.artqiyi.com";
    public static final String PLATFORM = "meme";
    public static final String APP_ID = BaseController.isTest ? "984069e5f8edd8ca4411e81863371f16" : "984069e5f8edd8ca4411e81863371f16";
    public static final List<String> KEY_WITHOUT_SIGN = new ArrayList<String>(){{this.add("sign"); this.add("app"); this.add("act");}};
    public static final MsgDigestUtil md5 = MsgDigestUtil.MD5;
    public static final TypeFactory typeFactory = TypeFactory.defaultInstance();

    public static String creatSign(SortedMap<String, Object> params) {
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
    }

    /**
     * 1.娃娃机列表
     */
    public static List<QiygRoomDTO> room_list() {
        String url = HOST + "/api/index.php";
        SortedMap<String, Object> params = new TreeMap<>();
        params.put("app", "doll");
        params.put("act", "doll_list");
        params.put("platform", PLATFORM);
        params.put("per_page", 200);
        params.put("ts", System.currentTimeMillis());
        String value = doGet(url, params);
        QiygListDTO qiygListDTO = toBean(value, QiygListDTO.class);
        if (qiygListDTO == null || qiygListDTO.getList() == null) {
            return null;
        }
        return qiygListDTO.getList();
    }

    /**
     *  2.娃娃机详情
     */
    public static QiygRoomDTO room_detail(String roomId) {
        if (roomId == null) {
            return null;
        }
        String url = HOST + "/api/index.php";
        SortedMap<String, Object> params = new TreeMap<>();
        params.put("app", "doll");
        params.put("act", "get_device_status");
        params.put("platform", PLATFORM);
        params.put("device_id", roomId);
        params.put("ts", System.currentTimeMillis());
        String value = doGet(url, params);
        return toBean(value, QiygRoomDTO.class);
    }

    /**
     * 3.申请分配娃娃机
     */
    public static QiygAssignDTO assign(String roomId, Integer userId) {
        if (roomId == null || userId == null) {
            return null;
        }
        String url = HOST + "/api/index.php";
        SortedMap<String, Object> params = new TreeMap<>();
        params.put("app", "doll");
        params.put("act", "assign");
        params.put("platform", PLATFORM);
        params.put("device_id", roomId);
        params.put("user_id", userId);
        params.put("ts", System.currentTimeMillis());
        String value = doGet(url, params);
        return toBean(value, QiygAssignDTO.class);
    }

    /**
     * 4.娃娃机操控接口
     */
    public static QiygRespDTO operate(OperateType type, String roomId, Integer userId) {
        if (roomId == null || userId == null) {
            return null;
        }
        String url = HOST + "/api/index.php";
        SortedMap<String, Object> params = new TreeMap<>();
        params.put("app", "doll");
        params.put("act", "operate");
        params.put("platform", PLATFORM);
        params.put("device_id", roomId);
        params.put("user_id", userId);
        params.put("action", type.ordinal());
        params.put("ts", System.currentTimeMillis());
        String value = doGet(url, params);
        return respBean(value, QiygRespDTO.class);
    }

    /**
     * 5.最新用户抓取结果
     */
    public static QiygResultDTO recentResult(String roomId, Integer userId) {
        if (roomId == null || userId == null) {
            return null;
        }
        String url = HOST + "/api/index.php";
        SortedMap<String, Object> params = new TreeMap<>();
        params.put("app", "buyer_order");
        params.put("act", "get_doll_result");
        params.put("platform", PLATFORM);
        params.put("device_id", roomId);
        params.put("user_id", userId);
        params.put("ts", System.currentTimeMillis());
        String value = doGet(url, params);
        return toBean(value, QiygResultDTO.class);
    }

    /**
     * 6.查询操作结果
     */
    public static QiygOperateResultDTO operateResult(String logId) {
        if (logId == null) {
            return null;
        }
        String url = HOST + "/api/index.php";
        SortedMap<String, Object> params = new TreeMap<>();
        params.put("app", "doll");
        params.put("act", "operate_result");
        params.put("platform", PLATFORM);
        params.put("log_id", logId);
        params.put("ts", System.currentTimeMillis());
        String value = doGet(url, params);
        return toBean(value, QiygOperateResultDTO.class);
    }

    /**
     * 7.	创建订单接口
     */
    public static QiygOrderResultDTO createOrder(Integer user_id, String username, String goods_list, String address,
                                                 String mobile, String consignee) {
        Long ts = System.currentTimeMillis();
        String url = HOST + "/api/index.php?app=buyer_order&act=create_order&ts="+ts+"&sign=";
        SortedMap<String, Object> params = new TreeMap<>();
        params.put("platform", PLATFORM);
        params.put("user_id", user_id);
        params.put("username", username);
        params.put("goods_list", goods_list);
        params.put("address", address);
        params.put("mobile", mobile);
        params.put("consignee", consignee);
        params.put("ts", System.currentTimeMillis());
        url = url + creatSign(params);
        String value = doPost(url, params);
        return toBean(value, QiygOrderResultDTO.class);
    }

    /**
     * 9.订单列表 //TODO
     */
    public static QiygOperateResultDTO orderList(Integer userId, Integer page) {
        if (userId == null) {
            return null;
        }
        String url = HOST + "/api/index.php";
        SortedMap<String, Object> params = new TreeMap<>();
        params.put("app", "buyer_order");
        params.put("act", "order_list");
        params.put("platform", PLATFORM);
        params.put("user_id", userId);
        params.put("page", page);
        params.put("ts", System.currentTimeMillis());
        String value = doGet(url, params);
        return toBean(value, QiygOperateResultDTO.class);
    }

    /**
     * 概率调整， 范围1-888
     * @param device_id
     * @param winning_probability
     * @return
     */
    public static QiygOperateResultDTO winning_rate(String device_id, String winning_probability) {
        String url = HOST + "/api/index.php";
        SortedMap<String, Object> params = new TreeMap<>();
        params.put("app", "doll");
        params.put("act", "set_winning_probability");
        params.put("platform", PLATFORM);
        params.put("device_id", device_id);
        params.put("winning_probability", winning_probability);
        params.put("ts", System.currentTimeMillis());
        String value = doGet(url, params);
        return toBean(value, QiygOperateResultDTO.class);
    }

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
            /*JavaType paramType = createJavaType(parameterClasses);
            JavaType javaType = typeFactory.constructParametricType(QiygRespDTO.class, paramType);
            QiygRespDTO<T> result = JSONUtil.jsonToBean(value, javaType);
            if (result != null && result.getDone() && result.getRetval() != null) {
                return result.getRetval();
            }*/
        } catch (Exception e) {
            e.printStackTrace();
            logger.error("translate json to bean error: " + value);
        }
        return null;
    }

    private static JavaType createJavaType(Class<?>... param) {
        /*if (param.length == 1) {
            return typeFactory.uncheckedSimpleType(param[0]);
        }else if (param.length == 2) {
            if (param[0].equals(List.class)) {
                return typeFactory.constructCollectionType(List.class, param[1]);
            } else {
                logger.error("unknown param type");
                return null;
            }
        }*/
        return null;
    }

    private static String doGet(String url, SortedMap<String, Object> params) {
        String value = null;
        url = url + "?" +buildParam(params);
        try {
            /*if (url.startsWith("http://")) {
                value = HttpClientUtils.get(url, null);
            } else if (url.startsWith("https://")) {
                value = HttpsClientUtils.get(url, null);
            }*/
        } catch (Exception e) {
            logger.error("Get " + url + " error.", e);
        }
        return value;
    }

    private static String doPost(String url, SortedMap<String, Object> params) {
        String value = null;

        try {
            /*if (url.startsWith("http://")) {
                value = HttpClientUtils.post(url, buildPostParam(params), null);
            } else if (url.startsWith("https://")) {
                value = HttpsClientUtils.post(url, buildPostParam(params),null);
            }*/
        } catch (Exception e) {
            logger.error("Post " + url + " error.", e);
        }
        return value;
    }

    private static String buildParam(SortedMap<String, Object> params) {
        String sign = creatSign(params);
        StringBuffer sb = new StringBuffer();
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            String k = entry.getKey();
            String v = String.valueOf(entry.getValue());
            sb.append(k).append("=").append(v).append("&");
        }
        sb.append("sign=").append(sign);
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
