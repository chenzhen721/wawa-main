package com.wawa.api.notify;

import com.mongodb.util.JSON;
import com.wawa.AppProperties;
import com.wawa.common.doc.MsgAction;
import com.wawa.common.util.HttpClientUtils;
import com.wawa.common.util.HttpsClientUtils;
import com.wawa.common.util.JSONUtil;
import com.wawa.common.util.MsgExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;


/**
 * ws 基于房间内消息推送
 */
public class RoomMsgPublish {

    static final Logger logger = LoggerFactory.getLogger(RoomMsgPublish.class);

    // ws服务器地址
    private static final String WS_SERVER_URL = AppProperties.get("ws.domain");
    //private static final String WS_VIDEO_SERVER_URL = AppProperties.get("wsvideo.domain");

    // 推送用户信息api
    private static final String PUBLIISH_USER_API = "/api/publish/user/ID";

    // 推送房间信息api
    private static final String PUBLIISH_ROOM_API = "/api/publish/room/ID";
    private static final String ROOM_VIDEO_RESTART_API = "/api/video/restart/ID/RECORDID";
    private static final String ROOM_VIDEO_PAUSE_API = "/api/video/pause/ID/RECORDID";
    //private static final String ROOM_DISPATCH_START_API = "/api/dispatch/start";
    //private static final String ROOM_DISPATCH_PAUSE_API = "/api/dispatch/pause";

    // 全局推送api
    private static final String PUBLIISH_GLOBAL_API = "/api/publish/global";

    // 全局推送action
    static final String GLOBAL_ACTION = "global.marquee";


    /**
     * 推送消息给用户
     */
    public static void publish2User(Object userId, MsgAction action, final Map data, Boolean isSaveLog) {
        publish(getUserPublishUrl(userId.toString()), action.getId(), data, isSaveLog);
    }

    /**
     * 推送消息到房间
     */
    public static void publish2Room(Object roomId, MsgAction action, final Map data, Boolean isSaveLog) {
        publish(getRoomPublishUrl(roomId.toString()), action.getId(), data,isSaveLog);
    }

    /**
     * 推流启动
     */
    public static void roomVideoRestart(Object roomId, Object recordId) {
        publish(roomVideoRestartUrl(roomId.toString(), recordId.toString()), "", new HashMap(), false);
    }

    /*public static void roomVideoDispatchRestart(Object roomId, Object recordId) {
        Map<String, Object> data = new HashMap<>();
        data.put("roomId", roomId);
        data.put("recordId", recordId);
        publish(roomVideoDispatchRestartUrl(), data);
    }*/

    /**
     * 视频流暂停
     * @param roomId
     */
    public static void roomVideoPause(Object roomId, Object recordId) {
        publish(roomVideoPauseUrl(roomId.toString(), recordId.toString()), "", new HashMap(), false);
    }

    /*public static void roomVideoDispatchPause(Object roomId, Object recordId) {
        Map<String, Object> data = new HashMap<>();
        data.put("roomId", roomId);
        data.put("recordId", recordId);
        publish(roomVideoDispatchPauseUrl(), data);
    }*/

    /**
     * 发送全局信息
     */
    public static void publish2GlobalEvent(Map data) {
        publish(getGlobalPublishUrl(), GLOBAL_ACTION, data, Boolean.FALSE);
    }

    private static void publish(String url, String action, Map data, Boolean isSaveLog) {
        Map<String, Object> params = new HashMap<>();
        params.put("action", action);
        params.put("data", data);
        params.put("is_log", isSaveLog ? 1:0);
        String postJson = JSONUtil.beanToJson(params);
        publish(url, postJson);
    }

    private static void publish(String url, Map data) {
        String postJson = JSONUtil.beanToJson(data);
        publish(url, postJson);
    }

    /**
     * 推送消息
     */
    private static void publish(final String url, final String body) {
        MsgExecutor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    String resp;
                    if (url.startsWith("http://")) {
                        resp = HttpClientUtils.postJson(url, body);
                    } else if (url.startsWith("https://")) {
                        resp = HttpsClientUtils.postJson(url, body);
                    } else {
                        logger.error("unknown schema: " + url);
                        return;
                    }
                    Map result = JSONUtil.jsonToMap(resp);
                    if (!result.get("code").toString().equals("1")) {
                        logger.error("publish error ,resp is {}", resp);
                    }
                } catch (Exception e) {
                    logger.error("publish Exception : {}", e);
                }
            }
        });
    }

    private static String getUserPublishUrl(String userId) {
        return String.format("%s%s", WS_SERVER_URL, PUBLIISH_USER_API.replace("ID", userId));
    }

    private static String getRoomPublishUrl(String roomId) {
        return String.format("%s%s", WS_SERVER_URL, PUBLIISH_ROOM_API.replace("ID", roomId));
    }

    private static String roomVideoRestartUrl(String roomId, String recordId) {
        return String.format("%s%s", WS_SERVER_URL, ROOM_VIDEO_RESTART_API.replace("RECORDID", recordId).replace("ID", roomId));
    }

    /*private static String roomVideoDispatchRestartUrl() {
        return String.format("%s%s", WS_VIDEO_SERVER_URL, ROOM_DISPATCH_START_API);
    }*/

    private static String roomVideoPauseUrl(String roomId, String recordId) {
        return String.format("%s%s", WS_SERVER_URL, ROOM_VIDEO_PAUSE_API.replace("RECORDID", recordId).replace("ID", roomId));
    }

    /*private static String roomVideoDispatchPauseUrl() {
        return String.format("%s%s", WS_VIDEO_SERVER_URL, ROOM_DISPATCH_PAUSE_API);
    }*/

    private static String getGlobalPublishUrl() {
        return String.format("%s%s", WS_SERVER_URL, PUBLIISH_GLOBAL_API);
    }


    public static void main(String[] args) throws IOException {
        String data = "{\"data\":{\"ranks\":[{\"_id\":\"1201263_2017_08_16_1502855037096\",\"name\":\"破骑V5\",\"pic\":\"http://test-aiimg.sumeme.com/57/1/1201273_1.jpg?v=1499425500091\",\"leader_id\":1201273,\"rank\":1,\"total\":385207.0,\"cash\":100,\"diamond\":10000,\"family_id\":1201263,\"type\":\"family_rank\",\"timestamp\":1502855037096,\"amount\":0,\"count\":0}]},\"action\":\"global.marquee\",\"is_log\":0}";
        publish2GlobalEvent((Map) JSON.parse(data));
    }
}


