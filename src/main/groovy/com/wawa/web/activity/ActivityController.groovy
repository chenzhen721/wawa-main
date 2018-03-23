package com.wawa.web.activity

import com.mongodb.DBObject
import com.wawa.base.anno.RestWithSession
import com.wawa.base.BaseController
import com.wawa.web.api.UserController
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.ZSetOperations

import javax.servlet.http.HttpServletRequest
import java.text.SimpleDateFormat

/**
 * 活动页面
 */
@RestWithSession
class ActivityController extends BaseController {

    private Logger logger = LoggerFactory.getLogger(ActivityController.class)

    //活动时间
    private static final Long _begin = new SimpleDateFormat("yyyyMMdd").parse(System.getProperty("mfbegin", "20170801")).getTime()
    private static final Long _end = new SimpleDateFormat("yyyyMMdd").parse("20170829").getTime()
    private static final String ACTIVE_NAME = "active_name"

    protected static Boolean isPeriod(){
        Long now = System.currentTimeMillis()  ;
        if (now < _begin || now > _end)
            return false
        return true
    }

    def rank(HttpServletRequest req){
        if(!isPeriod())
            return [code: 0, msg: "活动已经结束!"]
        Integer size = req['size'] as Integer ?: 10
        def star_user_rank_key = "active:${ACTIVE_NAME}:user:rank".toString()
        List<DBObject> rank = getUserList(star_user_rank_key, size);
        [code: 1, data: rank]
    }


    private List getUserList(String redis_key, Integer size){
        def zset = mainRedis.opsForZSet().reverseRangeWithScores(redis_key, 0, size-1)
        def result = new ArrayList(size)
        int index = 1;
        for (ZSetOperations.TypedTuple<String> tt : zset) {
            Integer user_id = Integer.valueOf(tt.getValue())
            def count = tt.getScore()
            def user = users().findOne(user_id, UserController.user_info_field)
            if (null != user) {
                user.put("rank", index++)
                user.put("cost", count)
                result.add(user)
            }
        }
        return  result
    }

}
