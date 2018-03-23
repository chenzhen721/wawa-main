package com.wawa.web.api

import com.wawa.api.Web
import com.wawa.base.anno.RestWithSession
import com.wawa.common.doc.Result
import com.wawa.base.BaseController
import org.apache.commons.lang.StringUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.web.bind.ServletRequestUtils

import javax.servlet.http.HttpServletRequest

import static com.wawa.common.util.WebUtils.$$

/**
 * 用户现金相关
 */
@RestWithSession
class CashController extends BaseController {

    static final Logger logger = LoggerFactory.getLogger(CashController.class)

    /**
     * @apiVersion 0.0.1
     * @apiGroup Cash
     * @apiName is_bind_weixin
     * @api {get} cash/is_bind_weixin  是否绑定微信账户
     * @apiDescription
     * 是否绑定微信账户
     *
     * @apiUse USER_COMMEN_PARAM
     *
     * @apiExample { curl } Example usage:
     *     curl -i http://test-aiapi.memeyule.com/cash/is_bind_weixin/54b6f011cfb0b160b9323c51979835af
     *
     * @apiSuccessExample {json} Success-Response:
     * {
     *   code:1,
     *   data: {'is_bind': true}
     * }
     */
    def is_bind_weixin(HttpServletRequest req) {

        Integer userId = Web.getCurrentUserId()
        def user = users().findOne($$('_id': userId), $$('account': 1))
        def account = user.containsField('account') ? user['account'] as Map : [:]
        return [code: Result.success.code, data: ['is_bind': account.containsKey('open_id')]]
    }

    /**
     * @apiVersion 0.0.1
     * @apiGroup Cash
     * @apiName bind_weixin
     * @api {post} cash/bind_weixin/:token?open_id=  绑定微信openID
     * @apiDescription
     * 绑定微信openID
     *
     * @apiUse USER_COMMEN_PARAM

     * @apiParam {String} open_id    微信用户openID
     *
     * @apiExample { curl } Example usage:
     *     curl -i http://test-aiapi.memeyule.com/cash/bind_weixin/54b6f011cfb0b160b9323c51979835af?open_id=asdasdasd9a88qweqwe123
     */
    def bind_weixin(HttpServletRequest req) {

        def open_id = ServletRequestUtils.getStringParameter(req, 'open_id', '')
        if (StringUtils.is(open_id)) {
            return Result.丢失必需参数
        }
        Integer userId = Web.getCurrentUserId()
        def query = $$('_id': userId, 'status': Boolean.TRUE)
        def user = users().findOne(query, $$('account': 1))
        def account = user.containsField('account') ? user['account'] as Map : [:]
        def user_open_id = account.containsKey('open_id') ? account['open_id'] as String : ''
        // 如果该用户已经绑定过 或者 绑定的openId相同
        if (StringUtils.isNotBlank(user_open_id) || user_open_id == open_id) {
            return [code: Result.error]
        }
        if (users().count($$('account.open_id', open_id)) > 0) {
            return Result.微信号已绑定其它账户
        }
        account.put('open_id', open_id)
        def update = $$('$set': $$('account': account))
        if (users().update(query, update, false, false, writeConcern).getN() == 1) {
            return Result.success
        }
        return Result.error
    }

}
