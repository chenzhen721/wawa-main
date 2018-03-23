package com.wawa.web.api

import com.mongodb.BasicDBObject
import com.wawa.api.Web
import com.wawa.base.anno.RestWithSession
import com.wawa.common.doc.TwoTableCommit
import com.wawa.base.Crud
import com.wawa.common.doc.Result
import com.wawa.base.BaseController
import com.wawa.model.Finance
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.web.bind.ServletRequestUtils

import javax.annotation.Resource
import javax.servlet.http.HttpServletRequest

import static com.wawa.common.util.WebUtils.$$
import static com.wawa.common.doc.MongoKey.*

/**
 * 背包
 */
@RestWithSession
class ItemController extends BaseController {
    static final Logger logger = LoggerFactory.getLogger(ItemController.class)

    @Resource
    MongoTemplate adminMongo

    @Resource
    MongoTemplate logMongo

    @Resource
    MongoTemplate mainMongo

    static final Map POINTS_AND_DIAMOND = [1000: 30, 2000: 60, 4000: 120, 8000: 240] //以分为单位

    /**
     * @apiVersion 0.0.1
     * @apiGroup Item
     * @apiName exchange
     * @api {get} item/exchange/:access_token/:item_id?count=&diamond=  兑换钻石
     * @apiDescription
     * 兑换钻石
     *
     * @apiUse USER_COMMEN_PARAM
     *
     * @apiParam {String} item 积分ID(points)
     * @apiParam {Interge} count 兑换数量
     * @apiParam {Interge} diamond 兑换数量
     *
     * @apiExample { curl } Example usage:
     *     curl -i http://test-aiapi.memeyule.com/item/exchange/0c77d9156ebf1fd71fa21d37357507de/points?count=10&diamond=1

     * @apiSuccessExample {json} Success-Response:
     * {
     *     code: 1,
     *     data: {
     *         _id: userId,
     *         finance: {
     *             diamond_count: 123
     *         },
     *         bag: {
     *             points: {
     *                 count: 123
     *             }
     *         }
     *     }
     * }
     *
     * @apiError UserNotFound The <code>0</code> of the User was not found.
     */
    def exchange(HttpServletRequest req) {
        def item_id = Web.firstParam(req)
        def diamond = ServletRequestUtils.getIntParameter(req, 'diamond', 0)
        def count = ServletRequestUtils.getIntParameter(req, 'count', 0)
        if (item_id == null || diamond <= 0 || count <= 0) {
            return Result.丢失必需参数
        }
        //校验count是否合理
        if (!POINTS_AND_DIAMOND.containsKey(count) || POINTS_AND_DIAMOND.get(count) != diamond) {
            return Result.丢失必需参数
        }
        def userId = Web.currentUserId
        def logWithId = Web.logCost('item_exchange_diamond', count, null)
        logWithId.put('_id', userId + "_" + System.nanoTime())
        logWithId.append('diamond', diamond).append('item_id', item_id).append('user_id', userId)
        logWithId.append('award', [diamond: diamond])

        boolean succ = bag_item_exchange(userId, item_id, count, diamond, logWithId)

        if (!succ) {
            return Result.道具数量不足
        }
        return [code: 1, data: users().findOne(userId, $$(Finance.finance$diamond_count, 1).append("bag.${item_id}.count".toString(), 1))]
    }

    public boolean bag_item_exchange(Integer user_id, String item_id, Integer count, Integer diamond, BasicDBObject logWithId) {
        def key = "bag.${item_id}.count".toString()
        boolean succ = Crud.doTwoTableCommit(logWithId, [
                main           : { users() },
                logColl        : { logMongo.getCollection(diamond_add) },
                queryWithId    : { $$('_id': user_id).append(key, $$($gte:count)) },
                update         : { $$($inc, $$(Finance.finance$diamond_count, diamond).append(key, -count))},
                successCallBack: {
                    return Boolean.TRUE
                },
                rollBack       : { $$($inc, $$(Finance.finance$diamond_count, -diamond).append(key, count))}
        ] as TwoTableCommit)
        return succ
    }

}
