package com.wawa.api.user;

import com.wawa.api.Web;
import com.wawa.base.StaticSpring;
import com.wawa.common.util.BusiExecutor;
import com.wawa.common.util.KeyUtils;
import com.wawa.common.util.SecurityUtils;
import com.wawa.model.PlatformType;
import com.wawa.service.shum.ShumengUtils;
import groovy.transform.CompileStatic;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.TimeUnit;


/**
 * 用户合法性检测
 */
@CompileStatic
public final class UserValidation {

    public final static Logger logger = LoggerFactory.getLogger(UserValidation.class) ;

    public static final StringRedisTemplate liveRedis = (StringRedisTemplate) StaticSpring.get("liveRedis");
    public static final MongoTemplate mainMongo = (MongoTemplate) StaticSpring.get("mainMongo");
    public static final StringRedisTemplate mainRedis = (StringRedisTemplate) StaticSpring.get("mainRedis");

    public static final Long DAY_MILLIS_TIME = 24 * 3600 * 1000L;

    private final static Long MINUTES_MILLS = 60 * 1000l;
    private final static Long MINUTES_MILLS_20 = 20 * 60 * 1000l;

    /**
     * 检查用户手机设备合法性
     * @param userId
     * @param platform
     * @param timestamp
     * @param smid 设备DNA字符串 使用RC4加密
     * @return
     */
    public static Boolean checkDevice(final String userId, Integer platform, String timestamp, final String smid){
        logger.debug("checkDevice : {}, {}, {}, {}", userId, platform, timestamp, smid);
        if(platform.equals(PlatformType.ios.ordinal())) return Boolean.TRUE;
        if(StringUtils.isEmpty(smid))
            return Boolean.FALSE;

        //判断60分钟内是否已经检查过设备合法性
        final String deviceCheckRediskey = KeyUtils.USER.deviceCheck(userId);
        String deviceChecked = mainRedis.opsForValue().get(deviceCheckRediskey);
        if(deviceChecked != null && deviceChecked.equals(smid)){
            logger.debug("checkDevice : deviceChecked is true");
            return Boolean.TRUE;
        }

        final String deviceCheckillegalListKey = KeyUtils.USER.deviceCheckillegalList();
        String decStr = SecurityUtils.RC4.decrypt(smid);
        logger.debug("checkDevice : decStr : {}", decStr);
        String[] strs = StringUtils.split(decStr, ',');
        if(strs == null || strs.length != 3){
            return Boolean.TRUE;
        }
        final String did = new String(Base64.decodeBase64(strs[0]));
        final String pkg = strs[1];
        if(StringUtils.isNotEmpty(timestamp)){
            String time = strs[2];
            if(!timestamp.equals(time)) return Boolean.FALSE;
        }
        logger.debug("checkDevice : did : {}, pkg : {}, time : {}", did, pkg);

        BusiExecutor.execute(new Runnable() {
            public void run() {
                try {
                    Boolean isOk = ShumengUtils.chkUser(did, pkg);
                    if (isOk) {//如果设备检查合法则60分钟内免检
                        Web.mainRedis.opsForValue().set(deviceCheckRediskey, smid, 30, TimeUnit.MINUTES);
                        Web.mainRedis.opsForSet().remove(deviceCheckillegalListKey, userId);
                    } else {
                        Web.mainRedis.opsForSet().add(deviceCheckillegalListKey, userId);
                    }
                } catch (Exception e) {
                    logger.error("checkDevice Exception  : {}", e);
                }
            }
        });
        //疑似虚拟机设备黑名单
        if(mainRedis.opsForSet().isMember(deviceCheckillegalListKey, userId)){
            logger.debug("checkDevice : deviceChecked is illegal");
            return Boolean.FALSE;
        }
        return Boolean.TRUE;
    }

    /**
     * 获取数盟合法设备ID
     * @param smid
     * @return
     */
    public static String getSid(final String smid){
        if(StringUtils.isEmpty(smid)) return null;

        String decStr = SecurityUtils.RC4.decrypt(smid);
        String[] strs = StringUtils.split(decStr, ',');
        if(strs == null || strs.length < 1){
            return null;
        }
        String did = new String(Base64.decodeBase64(strs[0]));
        String pkg = strs[1];
        if(ShumengUtils.chkUser(did, pkg)){
            return did;
        }
        return null;
    }

    static final Integer MAX_COUNT = 1;
    static final Long LIMIT_TIME = 60 * 1000l;

    /**
     * 验证同一时间IP限制
     * @param ip
     * @return
     */
    public static Boolean validateIp(String activity, String ip){
        String redisKey = KeyUtils.Actives.LimitIntervalPerIp(activity, ip);
        logger.debug("validateIp ip : {}", ip);
        if(liveRedis.opsForValue().setIfAbsent(redisKey, MAX_COUNT.toString())){
            liveRedis.expire(redisKey, LIMIT_TIME, TimeUnit.MILLISECONDS);
            return Boolean.TRUE;
        }
        return Boolean.FALSE;
    }

    /**
     * 获取礼物倒计时冷去时间
     * @param userId
     * @param ip
     * @return
     */
    public static Long getCooldownTime(String activity, Integer userId, String ip, Long defaultTime){
        Long coolDownTime = defaultTime;
        Long ipCoolDownTime = defaultTime;

        String redis_time_key = KeyUtils.Actives.cooldownOfUser(activity, userId);
        String coolDownTimeStr = mainRedis.opsForValue().get(redis_time_key);
        if(coolDownTimeStr != null){
            coolDownTime = Long.valueOf(coolDownTimeStr);
        }else{
            mainRedis.opsForValue().set(redis_time_key, defaultTime.toString());
        }
        String ip_redis_time_key = KeyUtils.Actives.cooldownOfIp(activity, ip);
        String ipCoolDownTimeStr = mainRedis.opsForValue().get(ip_redis_time_key);
        if(ipCoolDownTimeStr != null){
            ipCoolDownTime = Long.valueOf(ipCoolDownTimeStr);
        }else{
            mainRedis.opsForValue().set(ip_redis_time_key, defaultTime.toString());
        }
        logger.debug("getCooldownTime ip : {} ipCoolDownTime:{}, coolDownTime:{}", ip, ipCoolDownTime, coolDownTime);
        return coolDownTime >= ipCoolDownTime ? coolDownTime : ipCoolDownTime;
    }


}
