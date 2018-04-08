package com.wawa.web.api

import com.mongodb.BasicDBObject
import com.mongodb.DBCollection
import com.wawa.api.Web
import com.wawa.base.BaseController
import com.wawa.base.Crud
import com.wawa.base.anno.RestWithSession
import com.wawa.common.doc.TwoTableCommit
import com.wawa.common.doc.Result
import com.wawa.model.Finance
import org.apache.commons.lang.StringUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.data.mongodb.core.MongoTemplate

import javax.annotation.Resource
import javax.servlet.http.HttpServletRequest

import static com.wawa.common.util.WebUtils.$$
import static com.wawa.common.doc.MongoKey.*

/**
 * 商品信息
 */
@RestWithSession
class ShopController extends BaseController {
    static final Logger logger = LoggerFactory.getLogger(ShopController.class)

    @Resource
    MongoTemplate rankMongo
    @Resource
    MongoTemplate adminMongo

    DBCollection shop(){
        adminMongo.getCollection('shop')
    }

    DBCollection sign_logs() { logMongo.getCollection('sign_logs')}

    /**
     * @apiVersion 0.0.1
     * @apiGroup Shop
     * @apiName list
     * @api {get} shop/list/:access_token?page=:page&size=:size&group=  获取商品列表
     * @apiDescription
     * 获取商品列表
     *
     * @apiUse USER_COMMEN_PARAM
     *
     * @apiParam {String} group 商品聚类
     * @apiParam {Interge} page 页号
     * @apiParam {Interge} size 页码
     *
     * @apiExample { curl } Example usage:
     *     curl -i http://test-aiapi.memeyule.com/shop/list/0c77d9156ebf1fd71fa21d37357507de?page=1&size=20

     * @apiSuccessExample {json} Success-Response:
     * {
     *     code: 1,
     *     data: [{
     *          _id: 123,
     *          name: 'vip',
     *          item_id: 'vip',
     *          pic: 'http://',
     *          cost: 50,
     *          count: 1,
     *          unit: '个月',
     *          limit: 10,
     *          tag: '10%off',
     *          timestamp: 12123,
     *          status: true //false-不可购买，true-可以买
     *          cd: 123 //到期倒计时， unit:second
     *          desc: 描述
     *     }],
     *     all_page: 1,
     *     count: 10
     * }
     *
     * @apiError UserNotFound The <code>0</code> of the User was not found.
     */
    def list(HttpServletRequest req) {
        def group = req.getParameter('group') as String
        def query = $$(status: true, stime: [$lte: System.currentTimeMillis()])
        if (StringUtils.isNotBlank(group)) {
            query.put('group', group)
        }
        def order = $$(order: 1, timestamp: -1)
        Crud.list(req, shop(), query, ALL_FIELD, order) { List<BasicDBObject> list ->
            for (BasicDBObject obj : list) {
                if (obj['group'] == 'card') {//周卡、月卡
                    def item_id = obj['_id'] //项目ID
                    // 未购买 已购买
                    if (sign_logs().count($$(user_id: Web.currentUserId, is_award: false, item_id: item_id)) <= 0) {
                        obj['is_buy'] = false
                    } else {
                        obj['is_buy'] = true
                    }
                }
            }
        }
    }

    /**
     * @apiVersion 0.0.1
     * @apiGroup Shop
     * @apiName do_buy
     * @api {get} shop/do_buy/:access_token/:item_id  购买商品
     * @apiDescription
     * 购买商城商品
     *
     * @apiUse USER_COMMEN_PARAM
     *
     * @apiParam {String} item_id 商品号
     *
     * @apiExample { curl } Example usage:
     *     curl -i http://test-aiapi.memeyule.com/shop/do_buy/0c77d9156ebf1fd71fa21d37357507de/vip

     * @apiSuccessExample {json} Success-Response:
     * { code: 1 }
     *
     * @apiError UserNotFound The <code>0</code> of the User was not found.
     */
    def do_buy(HttpServletRequest req) {
        def item_id = Web.firstParam(req) as String
        def item = shop().findOne($$(item_id: item_id, $or: [[remaining: [$exists: false]], [remaining: [$gt: 0]]])) as Map
        if (item == null) {
            return Result.商品数量不足
        }
        def result = buy_item(req, item)
        if (result == null) {
            return Result.error
        }
        if (result['code'] == 1) {
            //不支持秒杀活动
            shop().update($$(_id: item['_id'], remaining: [$gt: 0]), $$($inc: [remaining: -1]), false, false, writeConcern)
        }
        return result
    }

    private buy_item(HttpServletRequest req, Map item) {
        def userId = Web.currentUserId
        Integer diamond = item['cost_diamond'] as Integer
        Integer count = item['count'] as Integer
        def field = "bag.${item['item_id']}.count".toString()
        def logWithId = Web.logCost('diamond_buy_item', diamond, null)
        logWithId.put('_id', userId + "_" + System.nanoTime())
        logWithId.append('count', count).append('item_id', item['item_id'])

        boolean succ = Crud.doTwoTableCommit(logWithId, [
                main           : { users() },
                logColl        : { logMongo.getCollection(diamond_cost) },
                queryWithId    : { $$('_id': userId).append(Finance.finance$diamond_count, $$($gte:diamond)) },
                update         : { $$($inc, $$(Finance.finance$diamond_count, -diamond).append(field, count))},
                successCallBack: {
                    return Boolean.TRUE
                },
                rollBack       : {}
        ] as TwoTableCommit)

        if (!succ) {
            return Result.余额不足
        }
        return Result.success
    }



}
