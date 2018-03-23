package com.wawa.common.doc;

import com.mongodb.BasicDBObject;
import com.wawa.common.util.MsgExecutor;
import groovy.lang.Closure;

import java.util.concurrent.ConcurrentHashMap;

/**
 *
 */
public abstract class DelayMessage {

    static final BasicDBObject user_field = new BasicDBObject();
    static {
        user_field.put("nick_name", 1);
        user_field.put("finance", 1);
        user_field.put("pic", 1);
        user_field.put("priv", 1);
        user_field.put("mm_no", 1);
    }

    private static final ConcurrentHashMap<String,Boolean> delayCached = new ConcurrentHashMap<>(4096);


    public static void delayFire(final String key, final long millis, final Closure closure) {
        if (null == delayCached.putIfAbsent(key, Boolean.TRUE)) {
            MsgExecutor.execute(new Runnable() {
                public void run() {
                    try {
                        Thread.sleep(millis);
                        closure.call();
                    } catch (InterruptedException ignored) {
                    } finally {
                        delayCached.remove(key);
                    }
                }
            });
        }
    }

}
