package com.wawa.api;

import com.mongodb.BasicDBObject;

/**
 * 金币
 */
public interface DoCost {


    boolean costSuccess();


    BasicDBObject costLog();

}
