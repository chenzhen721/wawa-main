package com.wawa.web.union

import com.wawa.api.interceptor.OAuth2SimpleInterceptor
import com.wawa.api.interceptor.UserFrom
import com.wawa.base.BaseController
import com.wawa.base.anno.Rest
import com.wawa.common.util.JSONUtil
import com.wawa.common.util.MsgDigestUtil
import com.wawa.common.util.http.HttpClientUtil
import com.wawa.common.doc.Result
import com.wawa.common.util.KeyUtils
import com.wawa.web.api.ItemController
import org.apache.commons.lang.StringUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import javax.annotation.Resource
import javax.servlet.http.HttpServletRequest
import java.util.concurrent.TimeUnit

import static com.wawa.common.util.WebUtils.$$

/**
 * 全民彩联运对接
 * Created by Administrator on 2017/11/15.
 */
@Rest
class QmcController extends BaseController{

    static final Logger logger = LoggerFactory.getLogger(QmcController.class)

    static final String RAN_TOKEN_SEED = "#@#kx${new Date().format("yMMdd")}}%xi>YY".toString()
    static final String NAMESPACE = ""
//    static final String NAMESPACE = UserFrom.全民彩.namespace
    static final String USER_API = isTest ? "http://219.143.144.194:1024/lotserver/third/getInfoByToken" : "http://s.qmcai.com/lotserver/third/getInfoByToken"

    @Resource
    ItemController itemController

    /**
     * @apiVersion 0.0.1
     * @apiGroup Union
     * @apiName qmc_login
     * @api {post} qmc/login?&token=:token 全民彩联运登录
     * @apiDescription
     * 么么联运登录
     *
     * @apiParam {String} token 用户标识
     *
     * @apiExample { curl } Example usage:
     *     curl -i http://test-17laihou.com/qmc/login?alpha=1&version=123&channel=123&token=123&platform=123
     *
     * @apiSuccessExample {json} Success-Response:
     *
     * {
     *     "data": {
     *         "access_token": "1509348125"
     *     },
     *     "exec": 75010,
     *     "code": 1
     * }
     *
     */
    def login(HttpServletRequest req) {
        def token = req.getParameter('token') as String
        if (token == null) {
            logger.error("Quanmingcai login invalid param:" + req.getParameterMap())
            return [code: 0]
        }
        /*def md5Str = MD5.digest2HEX(APP_ID + APP_SECRET + deviceId + userId)
        if (md5Str != sign) {
            logger.error("Meme login invalid sign: ${sign}, local sign: ${md5Str}".toString())
        }*/
        def access_token = NAMESPACE + MsgDigestUtil.MD5.digest2HEX(RAN_TOKEN_SEED + token + Calendar.getInstance().get(Calendar.DAY_OF_YEAR))
        mainRedis.opsForValue().set(KeyUtils.USER.onlyToken2id(access_token), token, OAuth2SimpleInterceptor.THREE_DAY_SECONDS, TimeUnit.SECONDS)
        //更新第三方登录
        def params = [token: token] as Map
        String resp = HttpClientUtil.post(USER_API, params, null)
        logger.debug('qmc user info: ' + resp)
        if (StringUtils.isNotBlank(resp)) {
            Map json = JSONUtil.jsonToMap(resp)
            if (json.get("errorCode") == '0000') {
                Map data = (Map) json.get("result")
                def tuid = NAMESPACE + (String) data.get("userNo")
                def set = $$(third_token: access_token)
                users().update($$(tuid: tuid), $$($set: set), false, false)
                return [code: 1, data: [access_token: access_token]]
            }
        }
        return Result.error

    }

}
