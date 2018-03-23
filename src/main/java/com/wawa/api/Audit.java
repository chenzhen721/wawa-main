package com.wawa.api;

import com.wawa.base.StaticSpring;
import com.wawa.common.util.HttpClientUtils;
import com.wawa.common.util.JSONUtil;
import com.wawa.model.PhotoStatusType;
import com.wawa.model.PicType;
import groovy.transform.CompileStatic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;

import static com.wawa.common.util.WebUtils.$$;


/**
 * 图片审核
 */
@CompileStatic
public final class Audit {

    final static  Logger logger = LoggerFactory.getLogger(Audit.class) ;

    public static final StringRedisTemplate liveRedis = (StringRedisTemplate) StaticSpring.get("liveRedis");
    public static final StringRedisTemplate mainRedis = (StringRedisTemplate) StaticSpring.get("mainRedis");
    public static final MongoTemplate mainMongo = (MongoTemplate) StaticSpring.get("mainMongo");
    public static final MongoTemplate logMongo = (MongoTemplate) StaticSpring.get("logMongo");
    public static final MongoTemplate adminMongo = (MongoTemplate) StaticSpring.get("adminMongo");
    private static final String IMG_API_URL =  "http://mon.memeyule.com/monapi/image/check";
    private static final String TXT_API_URL =  "http://mon.memeyule.com/monapi/shumei/check";
    private static final String ILLAGE_CODE = "1";
    private static final Integer ILLAGE_SCORE = 700;
    /**
     * 图片审核
     * @param pic_url 图片地址
     * @param user_id  图片所属业务id(用户id)
     * @param picType 图片类型
     */
    public static void picToAudit(String pic_url, Integer user_id, PicType picType){
        try{
            Map<String,Object> data = new HashMap<>();
            data.put("_id", user_id+"_"+ System.currentTimeMillis());
            data.put("user_id", user_id);
            data.put("pic_url", pic_url);
            data.put("type", picType.ordinal());
            data.put("status", PhotoStatusType.未处理.ordinal());
            data.put("timestamp", System.currentTimeMillis());
            adminMongo.getCollection("audit_pic").save($$(data));
        }catch (Exception e){
            logger.error("saveToAudit Exception : {}", e);
        }
    }

    /**
     * 是否为非法图片
     * @param pic_url
     * @return
     */
    public static Boolean identifyIsIllegalPic(String pic_url){
        try{
            String resp = HttpClientUtils.get(IMG_API_URL+"?url="+pic_url, null);
            Map json = JSONUtil.jsonToMap(resp);
            String data = json.get("data").toString();
            if(data.equals(ILLAGE_CODE)){
                return Boolean.TRUE;
            }
        }catch (Exception e){
            logger.error("identifyIsIllegalPic Exception : {}", e);
        }
        return Boolean.FALSE;
    }


    /**
     * 昵称审核
     * @param nickName
     * @param user_id
     */
    public static Boolean IsIllegalNickName(String nickName, Integer user_id){
        try{
            if(!isContainIllegalTxt(nickName, user_id)){
                Map<String,Object> info = new HashMap<>();
                info.put("_id", user_id+"_"+ System.currentTimeMillis());
                info.put("user_id", user_id);
                info.put("nickName", nickName);
                info.put("status", PhotoStatusType.未处理.ordinal());
                info.put("timestamp", System.currentTimeMillis());
                adminMongo.getCollection("audit_nickname").save($$(info));
            }
        }catch (Exception e){
            logger.error("nickNameToAudit Exception : {}", e);
        }
        return Boolean.FALSE;
    }

    /**
     * 是否包含非法文字
     * @param txt
     * @param user_id
     * @return
     */
    public static Boolean isContainIllegalTxt(String txt, Integer user_id) {
        try{
            String resp = HttpClientUtils.get(TXT_API_URL+"?nickname="+ URLEncoder.encode(txt, "utf-8")+"&userId="+user_id, null);
            Map json = JSONUtil.jsonToMap(resp);
            Integer code = (Integer)json.get("code");
            logger.debug("audit text json : {}", json);
            if(code.equals(1)){
                Map data = (Map)json.get("data");
                Integer score = Integer.valueOf(data.get("score").toString());
                //非法用户名
                if(score >= ILLAGE_SCORE){
                    return Boolean.TRUE;
                }
            }
        }catch (Exception e){
            logger.error("nickNameToAudit Exception : {}", e);
        }
        return Boolean.FALSE;
    }

    public static void  main(String args[]){
        System.out.println(identifyIsIllegalPic("http://test.img.2339.com/59/3/1305403.jpg?v=1417160802093"));
    }
}
