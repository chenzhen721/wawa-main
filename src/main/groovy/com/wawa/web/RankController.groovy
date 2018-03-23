package com.wawa.web

import com.mongodb.DBCollection
import com.mongodb.DBObject
import com.wawa.base.BaseController
import com.wawa.base.anno.Rest
import com.wawa.common.doc.Result
import com.wawa.common.util.KeyUtils
import com.wawa.web.api.UserController
import com.wawa.api.Web
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.ZSetOperations
import org.springframework.web.bind.ServletRequestUtils

import javax.servlet.http.HttpServletRequest

import static com.wawa.common.util.WebUtils.$$

/**
 * 排名相关
 */
@Rest
class RankController extends BaseController {

    Logger logger = LoggerFactory.getLogger(RankController.class)

    DBCollection family_user_rank() { return rankMongo.getCollection("family_user") }

    //使用LRU缓存
    //private final static Integer EXPIRE_SEC = 30 * 60
    //LRUCache<Integer, Map> userMonth = new LRUCache<Integer, Map>(EXPIRE_SEC, 1000);

    /**
     * @apiVersion 0.0.1
     * @apiGroup Rank
     * @apiName family_rank
     * @api {get} rank/family_rank?size=5  家族排行榜
     * @apiDescription
     * 家族排行榜
     *
     * @apiUse USER_COMMEN_PARAM
     *
     * @apiExample { curl } Example usage:
     *     curl -i http://test-aiapi.memeyule.com/rank/family_rank?size=5

     * @apiSuccessExample {json} Success-Response:
     *
     *     {
     *       "code":1,
     *       "data": [
     *            {
                     "_id": 1206921,
                     "name": "家族名称",
                     "badge": "https://aiimg.sumeme.com/24/0/1495008843352.png",
                     "pic": "http://test-aiimg.sumeme.com/1111.jpg",
                     "prestige": 168028, 威望
                     "level": 3, 等级
                     "rank": 1 排名
                 }
     *       ]
     *     }
     *
     */
    def family_rank(HttpServletRequest req) {
        String redisKey = KeyUtils.FAMILIES.ranks();
        Integer size = Web.getPageSize(req);
        def rankSet = mainRedis.opsForZSet().reverseRangeWithScores(redisKey, 0, size-1)
        def ranks = new ArrayList(size)
        int rank = 1;
        for (ZSetOperations.TypedTuple<String> tt : rankSet) {
            Integer familyId = Integer.valueOf(tt.getValue())
            //def count = tt.getScore()
            def family = Web.getFamilyinfo(familyId);
            if (null != family) {
                family.put("rank", rank++)
                ranks.add(family)
            }
        }
        [code: Result.success.code, data: ranks]
    }

    /**
     * @apiVersion 0.0.1
     * @apiGroup Rank
     * @apiName family_user_rank_yesterday
     * @api {get} rank/family_user_rank_yesterday/:family_id?size=5  家族用户昨日贡献榜
     * @apiDescription
     * 家族用户昨日贡献榜
     *
     * @apiUse USER_COMMEN_PARAM

     * @apiParam {Number} family_id 家族ID
     *
     * @apiExample { curl } Example usage:
     *     curl -i http://test-aiapi.memeyule.com/rank/family_user_rank_yesterday/1206921?size=5

     * @apiSuccessExample {json} Success-Response:
     *
     *     {
     *       "code":1,
     *       "data": [
     *          {
 *               "rank": 1,排名
                 "num": 10000,贡献金币数量
                 "family_id": 1206921,
                 "user_id": 1206921,
                 "user": { } 用户相关信息
     *          }
     *       ]
     *     }
     *
     */
    def family_user_rank_yesterday(HttpServletRequest req) {
        Integer familyId = Web.firstNumber(req)
        Integer size = Web.getPageSize(req)
        def ranks = family_user_rank().find($$(family_id: familyId)).sort($$(rank: 1)).limit(size).toArray()
        ranks.each { DBObject rank ->
            def user = Web.getUserInfo(rank['user_id'] as Integer)
            user.removeField("family")
            rank.put("user",user)
        }
        [code: Result.success.code, data: ranks]
    }

    /**
     * @apiVersion 0.0.1
     * @apiGroup Rank
     * @apiName family_user_rank
     * @api {get} rank/family_user_rank/:family_id?type=1&page=1&size=5  家族用户日/周贡献榜
     * @apiDescription
     * 家族用户日/周贡献榜
     *
     * @apiUse USER_COMMEN_PARAM

     * @apiParam {Number} family_id 家族ID
     * @apiParam {Number} type 榜单类型： 0或不传-日榜; 1-周榜
     *
     * @apiExample { curl } Example usage:
     *     curl -i http://test-aiapi.memeyule.com/rank/family_user_rank/1206921?type=1&page=1&size=5

     * @apiSuccessExample {json} Success-Response:
     *
     *     {
     *       "code":1,
     *       "data": [
     *          {
     *               "rank": 1,排名
                     "num": 10000,贡献金币数量
                     "family_id": 1206921,
                     "user_id": 1206921,
                     "user": { } 用户相关信息
     *          }
     *       ],
     *       "all_page": 10,
     *       "count": 50
     *     }
     *
     */
    def family_user_rank(HttpServletRequest req) {
        Integer familyId = Web.firstNumber(req)
        Integer size = Web.getPageSize(req)
        Integer type = ServletRequestUtils.getIntParameter(req, 'type', 0)
        int page = ServletRequestUtils.getIntParameter(req, 'page', 1)
        if(page < 0) page = 1
        long count = getUserDonateSize(familyId, type)
        long all_page = (long)((count + size - 1) / size)
        int end = page * size > count ? count.intValue() : page * size
        def ranks = getUserDonateRank(familyId, (page - 1) * size, end, type)
        [code: Result.success.code, data: ranks, all_page: all_page, count: count]
    }

    /**
     * @apiVersion 0.0.1
     * @apiGroup Rank
     * @apiName current_user_rank
     * @api {get} rank/current_user_rank/:token/:family_id?type=1&page=1&size=5  当前用户日/周贡献榜排名
     * @apiDescription
     * 当前用户用户日/周贡献榜
     *
     * @apiUse USER_COMMEN_PARAM

     * @apiParam {Number} family_id 家族ID
     * @apiParam {Number} type 榜单类型： 0或不传-日榜; 1-周榜
     *
     * @apiExample { curl } Example usage:
     *     curl -i http://test-aiapi.memeyule.com/rank/current_user_rank/43b0d2b64a8b12350d2f0158f88b1aa2/1206921?type=1&page=1&size=5

     * @apiSuccessExample {json} Success-Response:
     *
     *     {
     *       "code":1,
     *       "data":
     *          {
     *               "rank": 1,排名 //如果没有排名不返回该字段
                     "num": 10000,贡献金币数量 //默认为0
                     "family_id": 1206921,
                     "user_id": 1206921,
                     "user": { } 用户相关信息
     *          }
     *     }
     *
     */
    def current_user_rank(HttpServletRequest req) {
        Integer familyId = Web.firstNumber(req)
        def currentUser = Web.getUserByAccessToken(req)
        if (familyId != null && currentUser != null && currentUser['_id'] != null) {
            def user = users().findOne(currentUser['_id'] as Integer, UserController.user_info_core_field)
            if (user != null) {
                def result = [family_id: familyId, user_id: currentUser['_id'], user: user, num: 0]
                Integer type = ServletRequestUtils.getIntParameter(req, 'type', 0)
                String redisKey = KeyUtils.FAMILIES.family_donate_day_ranks(familyId)
                if (type == 1) {
                    redisKey = KeyUtils.FAMILIES.family_donate_week_ranks(familyId)
                }
                def index = Web.mainRedis.opsForZSet().reverseRank(redisKey, currentUser['_id']) as Integer
                if (index != null && index >= 0) {
                    result.put('rank', index + 1)
                }
                def count = Web.mainRedis.opsForZSet().score(redisKey, currentUser['_id'])
                if (count != null && count > 0) {
                    result.put('num', count)
                }
                return [code: 1, data: result]
            }
        }
        Result.error
    }

    List getUserDonateRank(Integer familyId, Integer start, Integer size, Integer type) {
        String redisKey = KeyUtils.FAMILIES.family_donate_day_ranks(familyId)
        if (type == 1) {
            redisKey = KeyUtils.FAMILIES.family_donate_week_ranks(familyId)
        }
        if (size == null) {
            size = Web.mainRedis.opsForZSet().size(redisKey)
        }
        def zset = Web.mainRedis.opsForZSet().reverseRangeWithScores(redisKey, start?:0, size - 1)
        def result = new ArrayList()
        int index = 1
        for (ZSetOperations.TypedTuple<String> tt : zset) {
            Integer user_id = Integer.valueOf(tt.getValue())
            def count = tt.getScore()
            def rank = $$([rank: index++, num: count, family_id: familyId, user_id: user_id])
            def user = users().findOne(user_id, UserController.user_info_core_field)
            if (null != user) {
                rank.put('user', user)
            }
            result.add(rank)
        }
        return  result
    }

    private Long getUserDonateSize(Integer familyId, Integer type) {
        String redisKey = KeyUtils.FAMILIES.family_donate_day_ranks(familyId)
        if (type == 1) {
            redisKey = KeyUtils.FAMILIES.family_donate_week_ranks(familyId)
        }
        return Web.mainRedis.opsForZSet().size(redisKey)
    }

    /**
     * 获取家族威望排名
     * @param size
     * @return [{_id: 1, name: 1, pic: 1, leader_id: 1}]
     */
    List getRankList(Long start, Long end){
        String redisKey = KeyUtils.FAMILIES.ranks()
        if (end == null) {
            end = getRankCount()
        }
        def zset = Web.mainRedis.opsForZSet().reverseRangeWithScores(redisKey, start?:0, end - 1)
        def result = new ArrayList()
        int index = 1
        for (ZSetOperations.TypedTuple<String> tt : zset) {
            Integer family_id = Integer.valueOf(tt.getValue())
            def count = tt.getScore()
            def family = rooms().findOne(family_id, PublicController.ROOM_LIST_FIELD)
            if (null != family) {
                family.put("rank", start + index)
                family.put("total", count)
                result.add(family)
                index++
            }
        }
        return  result
    }

    Long getRankCount() {
        String redisKey = KeyUtils.FAMILIES.ranks()
        return mainRedis.opsForZSet().size(redisKey)
    }

}
