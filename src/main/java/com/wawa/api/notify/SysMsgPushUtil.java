package com.wawa.api.notify;

import com.wawa.AppProperties;
import com.wawa.common.util.HttpClientUtils;
import com.wawa.common.util.JSONUtil;
import com.wawa.common.util.MsgExecutor;
import com.wawa.model.IMType;
import com.wawa.model.SysMsgType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 系统消息和通知推送
 */
public class SysMsgPushUtil {

    static final Logger logger = LoggerFactory.getLogger(SysMsgPushUtil.class);


    private static final String IM_DOMAIN = AppProperties.get("im.domain", "http://test-aiim.memeyule.com:6070");

    static final Long MESSAGE_EXPIRE = 60 * 60 * 1000L;

    /**
     * 推送给单个用户
     * @param userId
     * @param content
     * @param is_notify
     */
    public static void sendToUser(Integer userId, Object content, Boolean is_notify) {
        send(userId.toString(), buildBody(content, IMType.系统消息, is_notify, SysMsgType.系统));
    }

    /**
     * 推送给单个用户
     * @param userId
     * @param content
     * @param is_notify
     */
    public static void sendToUser(Integer userId, Object content, Boolean is_notify, Long expire) {
        send(userId.toString(), buildBody(content, IMType.系统消息, is_notify, SysMsgType.系统, expire));
    }

    /**
     *  推送给单个用户
     * @param userId
     * @param content
     * @param is_notify
     * @param sysMsgType 消息分类
     */
    public static void sendToUser(Integer userId, Object content, Boolean is_notify, SysMsgType sysMsgType) {
        send(userId.toString(), buildBody(content, IMType.系统消息, is_notify, sysMsgType));
    }

    public static void sendToUserChat(Integer userId, Map from, Object content, Boolean is_notify, SysMsgType sysMsgType) {
        send(userId.toString(), buildChatBody(userId, from, content, IMType.好友消息, is_notify, sysMsgType));
    }

    /**
     * 推送消息给客户端
     */
    public static void sendToUsers(List<Integer> userIds, Object content, IMType imType, Boolean is_notify, SysMsgType sysMsgType) {
        Map<String, Object> body = buildGlobeBody(content, imType, is_notify, sysMsgType);
        body.put("user_ids", userIds);
        send("batch", body);
    }

    /**
     * 推送消息给客户端
     */
    public static void sendToUser(Integer userId, Object content, IMType imType, Boolean is_notify, SysMsgType sysMsgType) {
        send(userId.toString(), buildGlobeBody(content, imType, is_notify, sysMsgType));
    }

    /**
     * 推送给多个用户
     * @param userIds
     * @param content
     * @param is_notify
     */
    public static void sendToUsers(List<Integer> userIds, Object content, Boolean is_notify, SysMsgType sysMsgType) {
        Map<String, Object> body =buildBody(content, IMType.系统消息, is_notify, sysMsgType);
        body.put("user_ids", userIds);
        send("batch", body);
    }

    /**
     * 世界广播  推送个全部用户
     * @param content  推送内容
     */
    public static void sendToAll(Object content) {
        Map<String, Object> body =buildBody(content, IMType.全局消息, Boolean.FALSE, SysMsgType.系统);
        send("all", body);
    }

    public static void sendToAll(IMType imType, Object content) {
        Map<String, Object> body =buildBody(content, imType, Boolean.FALSE, SysMsgType.系统);
        send("all", body);
    }

    private static Map<String, Object> buildBody(Object content, IMType imType, Boolean is_notify, SysMsgType sysMsgType){
        return buildBody(content, imType, is_notify, sysMsgType, null);
    }

    private static Map<String, Object> buildBody(Object content, IMType imType, Boolean is_notify, SysMsgType sysMsgType, Long expire){
        Map<String, Object> data = new HashMap<>();
        data.put("type", sysMsgType.ordinal());
        data.put("text", content);
        Long now = System.currentTimeMillis();
        Long expireTime = now + MESSAGE_EXPIRE;
        data.put("expire_time", expire != null ? expire : expireTime);
        Map<String, Object> message = new HashMap<>();
        message.put("action", imType.getAction());
        message.put("data", data);

        Map<String, Object> body = new HashMap<>();
        if(is_notify != null && is_notify == Boolean.TRUE){
            body.put("umeng", pushInfo(content.toString(), content.toString()));
        }
        body.put("message", message);
        return body;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> buildChatBody(Integer to_id, Map from, Object content, IMType imType, Boolean is_notify, SysMsgType sysMsgType) {
        Map<String, Object> body = buildBody(content, imType, is_notify, sysMsgType);
        Map<String, Object> message = (Map<String, Object>)body.get("message");
        Map<String, Object> data = (Map<String, Object>)message.get("data");
        data.put("from", from);
        data.put("to_id", to_id);
        data.put("content", content);
        data.remove("text");
        return body;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> buildGlobeBody(Object content, IMType imType, Boolean is_notify, SysMsgType sysMsgType){
        Map<String, Object> data = new HashMap<>();
        data.put("type", sysMsgType.ordinal());
        Long now = System.currentTimeMillis();
        Long expireTime = now + MESSAGE_EXPIRE;
        data.put("expire_time", expireTime);
        data.putAll((Map<String, Object>)content);
        Map<String, Object> message = new HashMap<>();
        message.put("action", imType.getAction());
        message.put("data", data);

        Map<String, Object> body = new HashMap<>();
        if(is_notify != null && is_notify == Boolean.TRUE){
            body.put("umeng", pushInfo(content.toString(), content.toString()));
        }
        body.put("message", message);
        return body;
    }

    /**
     * 推送data信息
     */
    private static Map<String, Object> pushInfo(String title, String text){
        Map<String, Object> params = new HashMap<>();
        Map<String, Object> data = new HashMap<>();
        Long now = System.currentTimeMillis();
        Long expireTime = now + MESSAGE_EXPIRE;
        params.put("title", title);
        params.put("text", text);
        params.put("expire_time", expireTime);
        Map<String, Object> extra = new HashMap<>();
        extra.put("event", "redirect_app");
        data.put("params", params);
        data.put("extra", extra);
        return data;
    }

    private static void send(final String path, final Object body) {
        MsgExecutor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    String api_url = IM_DOMAIN + "/api/publish/" + path;
                    SysMsgPushUtil.logger.debug("push info: " + JSONUtil.beanToJson(body));
                    String result = HttpClientUtils.postJson(api_url, JSONUtil.beanToJson(body));
                    SysMsgPushUtil.logger.debug("result: " + result);
                    if (result != null && (Integer)JSONUtil.jsonToMap(result).get("code") != 1) {
                        SysMsgPushUtil.logger.error("push error" + result);
                    }
                } catch (Exception e) {
                    SysMsgPushUtil.logger.error("push Exception : {}" , e.getMessage());
                }
            }
        });
    }

    public static void main(String[] args) throws Exception {
        //sendToUser(1207022,"111", true);
        //List<Integer> userids = new ArrayList<>();
        //userids.add(123123123);
        //sendToUsers(userids,"22", false);
        //sendToAll("1111");
        /*Map<String, Object> data = new HashMap<>();
        data.put("to_id", 1201085);
        data.put("content", "通过好友分享成功加为好友啦，我已经给你带来了一个好友红包哦");*/
        Map<String, Object> from = new HashMap<>();
        from.put("_id", 1201077);
        from.put("nick_name", "陈真");
        from.put("pic", "http://img.sumeme.com/22/6/1403510731734.jpg");
        from.put("priv", "2");
        //data.put("from", from);

        /*Map<String, Object> map = new HashMap<>();
        map.put("action", "user.chat");
        map.put("data", data);*/

        //String result = HttpClientUtils.postJson("http://test-aiim.memeyule.com:6070?accessToken=43b0d2b64a8b12350d2f0158f88b1aa2&platform=1", JSONUtil.beanToJson(map));


        Map<String, Object> body = buildBody("通过好友分享成功加为好友啦，我已经给你带来了一个好友红包哦", IMType.好友消息, false, SysMsgType.好友);
        Map<String, Object> message = (Map<String, Object>)body.get("message");
        Map<String, Object> data = (Map<String, Object>)message.get("data");
        data.put("from", from);
        data.put("to_id", 1201066);

        send("1201066", body);

        //sendToUserChat(1201085, "通过好友分享成功加为好友啦，我已经给你带来了一个好友红包哦", false, SysMsgType.好友);
    }
}
