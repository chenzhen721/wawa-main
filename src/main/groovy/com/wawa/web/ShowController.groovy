package com.wawa.web

import com.mongodb.BasicDBObject
import com.wawa.base.BaseController
import com.wawa.base.anno.Rest
import com.wawa.common.util.JSONUtil
import com.wawa.common.util.KeyUtils
import org.apache.commons.lang.StringUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.dao.DataAccessException
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.redis.connection.RedisConnection
import org.springframework.data.redis.core.RedisCallback

import javax.annotation.Resource
import javax.servlet.http.HttpServletRequest

import static com.wawa.common.util.WebUtils.$$

/**
 * 部分数据接口
 */
@Rest
class ShowController extends BaseController {

    @Resource
    MongoTemplate adminMongo

    Logger logger = LoggerFactory.getLogger(ShowController.class)

    /**
     * 爱玩直播 礼物类型全是手机
     * @param req
     * @return
     */
    def gift_list(HttpServletRequest req) {
        //手机直播礼物
        String all_gift_key = KeyUtils.all_gifts()
        String result = mainRedis.opsForValue().get(all_gift_key)
        if(StringUtils.isBlank(result)){
            logger.debug("gift_list cache renew...")
            mainRedis.opsForValue().set(KeyUtils.local_gifts_flag(), '' + System.currentTimeMillis())

            def query = $$('status': Boolean.TRUE,'sale':Boolean.TRUE)
             result =renewCacheGiftList(all_gift_key, query)
        }
        return JSONUtil.jsonToMap(result)
    }

    private String renewCacheGiftList(String gift_key, BasicDBObject giftStatus){
        def gifts = adminMongo.getCollection("gifts").find(giftStatus).sort($$(['order': 1, 'coin_price': 1])).toArray()
        def result = [code: 1, data: [
                categories: adminMongo.getCollection("gift_categories").find($$('status', Boolean.TRUE)).sort($$('order', 1)).toArray()
                , gifts  : gifts
        ]]
        String json = JSONUtil.beanToJson(result)
        mainRedis.opsForValue().set(gift_key, json)
        return json;
    }


    private forbiddenList(boolean shutUp, final Integer roomId) {
        String setKey = shutUp ? KeyUtils.ROOM.shutupSet(roomId) : KeyUtils.ROOM.kickSet(roomId)
        Set<String> uids = userRedis.opsForSet().members(setKey)
        if (uids.isEmpty()) {
            return [code: 1, data: [:]]
        }
        final List<byte[]> ttls = uids.collect {
            KeyUtils.serializer(
                    shutUp ? KeyUtils.ROOM.shutup(roomId, it) : KeyUtils.ROOM.kick(roomId, it)
            )
        }

        List<Map<String, Object>> users = new ArrayList(uids.size())
        def ids = (List) mainRedis.execute(new RedisCallback() {
            Object doInRedis(RedisConnection connection) throws DataAccessException {
                connection.openPipeline()
                for (byte[] key : ttls) {
                    connection.ttl(key)
                }
                return connection.closePipeline()
            }
        })
        int i = 0;
        for (String id : uids) {
            users.add([xy_user_id: id, ttl: ids.get(i++)])
        }
        [code: 1, data: users]
    }


}
