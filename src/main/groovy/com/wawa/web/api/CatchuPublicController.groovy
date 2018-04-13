package com.wawa.web.api

import com.mongodb.DBCollection
import com.wawa.api.Web
import com.wawa.base.BaseController
import com.wawa.base.anno.Rest
import com.wawa.common.util.KeyUtils
import com.wawa.common.util.RandomExtUtils
import com.wawa.model.UserAwardType
import com.wawa.web.partner.QiyiguoPublicController
import com.wawa.web.partner.WawaPublicController
import groovy.json.JsonSlurper
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import javax.annotation.Resource
import javax.servlet.http.HttpServletRequest

import static com.wawa.common.util.WebUtils.$$

/**
 * 抓娃娃
 */
@Rest
class CatchuPublicController extends BaseController {

    static final Logger logger = LoggerFactory.getLogger(CatchuPublicController.class)

    public static final String APP_ID = isTest ? "984069e5f8edd8ca4411e81863371f16" : "984069e5f8edd8ca4411e81863371f16"
    public static final JsonSlurper jsonSlurper = new JsonSlurper()

    @Resource
    private CatchuController catchuController
    @Resource
    private WawaPublicController wawaPublicController

    DBCollection catch_rooms() {
        return catchMongo.getCollection('catch_room')
    }
    DBCollection catch_users() {
        return catchMongo.getCollection('catch_user')
    }
    DBCollection catch_records() {
        return catchMongo.getCollection('catch_record')
    }

    private static long semaphore = 0 //型号量
    private static final long delay = 5000L //型号量

    /**
     * @apiVersion 0.0.1
     * @apiGroup CatchuPublic
     * @apiName room_list
     * @api {get} catchupublic/room_list?partner=:partner&page=:page&size=:size&type=:type  获取房间列表
     * @apiDescription
     * 获取娃娃房详细信息
     *
     * @apiUse USER_COMMEN_PARAM
     * @apiParam {Integer} [partner]  合作商 默认0
     * @apiParam {Integer} [type]  获取列表类型，1代表收藏列表 2代表随机推荐
     * @apiParam {Integer} [page]  页号
     * @apiParam {Integer} [size]  个数
     *
     * @apiExample { curl } Example usage:
     *     curl -i http://test-aiapi.memeyule.com/catchupublic/room_list?partner=1
     *
     * @apiSuccessExample {json} Success-Response:
     *
     * {
     *     "count": 1,
     *     "data": [
     *         {
     *             "_id": 10000016,
     *             "fid": 1005,
     *             "name": "wawa1",
     *             "type": true,  //true-正常游戏 false-备货中
     *             "pic": "http://www.baidu.com",
     *             "price": 300,
     *             "desc": "描述",
     *             "status": 0, 0-空闲 1-游戏中 2-准备中
     *             "timestamp": 1508232023761,
     *             "pull_url_1": "rtmp://l.ws.sumeme.com/meme/10000016?k=bda3453af8f7bf3c3d69d327f6b31557&t=59e5d260_1",
     *             "pull_url_2": "rtmp://l.ws.sumeme.com/meme/10000016?k=bda3453af8f7bf3c3d69d327f6b31557&t=59e5d260_2",
     *             "toy_id": 123
     *         }
     *     ],
     *     "exec": 11,
     *     "code": 1,
     *     "all_page": 1
     * }
     *
     * @apiError UserNotFound The <code>0</code> of the User was not found.
     */
    def room_list(HttpServletRequest req) {
        //def partner = ServletRequestUtils.getIntParameter(req, 'partner', 1)
        return wawaPublicController.room_list(req)
    }

    public static final String domain = "api.memeyule.com/"

    def awardPoint(Integer user_id, Boolean status) {
        def points
        if (status) {
            points = RandomExtUtils.randomBetweenMinAndMax(10, 20)
        } else {
            points = RandomExtUtils.randomBetweenMinAndMax(20, 30)
        }
        if (1 == users().update($$(_id: user_id), $$($inc: $$('bag.points.count', points)), false, false).getN()) {
            Web.saveAwardLog(user_id, UserAwardType.抓娃娃送积分, [points: points])
            return points
        }
        return 0
    }

    public boolean isFirst(Object uid) {
        if (mainRedis.hasKey(KeyUtils.USER.first(uid)) && '1' == mainRedis.opsForValue().get(KeyUtils.USER.first(uid))) {
            mainRedis.delete(KeyUtils.USER.first(uid))
            return Boolean.TRUE
        }
        return Boolean.FALSE
    }
}
