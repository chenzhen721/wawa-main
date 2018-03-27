package com.wawa.web.api

import com.mongodb.DBCollection
import com.wawa.api.Web
import com.wawa.base.anno.RestWithSession
import com.wawa.common.doc.MongoKey
import com.wawa.base.Crud
import com.wawa.common.doc.Result
import com.wawa.base.BaseController
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.util.CollectionUtils

import javax.annotation.Resource
import javax.servlet.http.HttpServletRequest

import static com.wawa.common.util.WebUtils.$$
import static com.wawa.common.doc.MongoKey.*

/**
 * 用户个人信息
 */
@RestWithSession
class HomeController extends BaseController {

    @Resource
    MongoTemplate rankMongo
    @Resource
    MongoTemplate adminMongo

    @Resource
    CatchuController catchuController
    @Resource
    MissionController missionController

    static final Logger logger = LoggerFactory.getLogger(HomeController.class)

    DBCollection category(){return adminMongo.getCollection('category')}

    /**
     * @apiVersion 0.0.1
     * @apiGroup Home
     * @apiName index_event
     * @api {get} home/index_event/:access_token  首页事件
     * @apiDescription
     * 首页事件
     *
     * @apiUse USER_COMMEN_PARAM
     *
     * @apiExample { curl } Example usage:
     *     curl -i http://test-api.17laihou.com/home/index_event/11f69035f0fdb1d11381407b2b0ed1df

     * @apiSuccessExample {json} Success-Response:
     *
     * {
     *     "data": {
     *         "observe_notify": [{
     *                  "_id": '',
     *                  "user": {
     *                      "nick_name": ,
     *                      "pic":,
     *                  },
     *                  "award": {
     *                      "type": , //奖励类型 0补娃娃 1补币 2充值/签到奖励 3邀请奖励 4注册奖励 5抓必中开启通知 6抓必中领取通知
     *                      "desc": 26钻石,
     *                  }
     *              }]
     *     },
     *     "exec": 293,
     *     "code": 1
     * }
     *
     * @apiError UserNotFound The <code>0</code> of the User was not found.
     */
    def index_event(HttpServletRequest req) {
        def result = [:] as Map
        //查询申述通知
        def logs = catchuController.record_observ_notify(Web.getCurrentUserId()) as List
        if (logs == null) {
            logs = [] as List
        }
        //充值、签到
        def sign_daily = missionController.sign_daily(req) as Map
        if (sign_daily['data'] != null) {
            logs.add(sign_daily['data'])
        }
        //邀请奖励
        def invite_notice = missionController.invite_notice(req) as Map
        if (invite_notice['data'] != null) {
            logs.add(invite_notice['data'])
        }
        //注册奖励
        def charge_notice = missionController.charge_notice(req)
        if (charge_notice['data'] != null) {
            logs.add(charge_notice['data'])
        }
        //抓必中特权提示
        /*def follow_notice = missionController.unlimit_notify_notice(req)
        if (follow_notice['data'] != null) {
            logs.add(follow_notice['data'])
        }*/
        //抓必中特权开启
        /*def unlimit_notice = missionController.unlimit_open_notice(req)
        if (unlimit_notice['data'] != null) {
            logs.add(unlimit_notice['data'])
        }*/
        if (!CollectionUtils.isEmpty(logs)) {
            result.put('observe_notify', logs)
        }
        if (result.isEmpty()) {
            return Result.success
        }
        return [code: 1, data: result]
    }

    /**
     * @apiVersion 0.0.1
     * @apiGroup Home
     * @apiName category_list
     * @api {get} home/category_list/:access_token  首页类目标签
     * @apiDescription
     * 首页类目标签
     *
     * @apiUse USER_COMMEN_PARAM
     *
     * @apiExample { curl } Example usage:
     *     curl -i http://test-api.17laihou.com/home/category_list/11f69035f0fdb1d11381407b2b0ed1df

     * @apiSuccessExample {json} Success-Response:
     *
     * {
     *     "data": {
     *         "_id": "",
     *         "name": "",
     *     },
     *     "exec": 293,
     *     "code": 1
     * }
     *
     * @apiError UserNotFound The <code>0</code> of the User was not found.
     */
    def category_list(HttpServletRequest req) {
        def time = System.currentTimeMillis()
        def query = $$(stime: [$lte: time], etime: [$lte: time], status: 0, onshow: true, type: 0)
        Crud.list(req, category(), query, $$(name: 1), SJ_DESC)
    }


}
