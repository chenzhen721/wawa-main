package com.wawa.common.util;

import com.wawa.base.StaticSpring;
import groovy.transform.CompileStatic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.TimeUnit;

/**
 *  redis锁
 *  Usage:
 *
     RedisLock redisLock = new RedisLock(lock_key);
     try{
        redisLock.lock(5)
     //TODO SOMETHING
     }finally {
        redisLock.unlock();
     }
 */
@CompileStatic
public class RedisLock {

    public static final Logger logger = LoggerFactory.getLogger(RedisLock.class);

    public static final StringRedisTemplate mainRedis = (StringRedisTemplate) StaticSpring.get("mainRedis");

    private final static String LOCK_PREFIX = "redis:lock:";
    private final String lock_key;
    private String lock_value = "";
    private final static Integer DEFALUT_SEC =  5;
    private final static Long LOCK_TIME_MILLIS =  1000l;

    public RedisLock(String lock_key) {
        this.lock_key = LOCK_PREFIX + lock_key;
    }

    public void lock() throws Exception {
        lock(DEFALUT_SEC);
    }
    /**
     * 获得锁
     */
    public void lock(Integer sec) throws Exception {
        Boolean lock = Boolean.FALSE;
        int index = 0;
        while(!lock){
            Long curr = System.currentTimeMillis();
            Long timeout = sec * LOCK_TIME_MILLIS;
            Long timestamp = curr + timeout;
            lock = mainRedis.opsForValue().setIfAbsent(lock_key, timestamp.toString());
            if(lock){
                mainRedis.expire(lock_key, timeout, TimeUnit.MILLISECONDS);
                lock_value = timestamp.toString();
                break;
            }else{
                Thread.sleep(sec);
                logger.debug("lock sleeping ... " + lock_key);
            }
            if(index++ >= LOCK_TIME_MILLIS){
                String lock_value = mainRedis.opsForValue().get(lock_key);
                if(lock_value != null && Long.valueOf(lock_value) < curr)
                    mainRedis.delete(lock_key);
            }
        }
    }

    /**
     * 解锁
     */
    public void unlock(){
        String alive_lock_value = mainRedis.opsForValue().get(lock_key);
        if (alive_lock_value != null && lock_value.equals(alive_lock_value))
            mainRedis.delete(lock_key);
    }

}
