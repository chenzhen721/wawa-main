package com.wawa.web.doc

import com.wawa.base.anno.Rest
import com.wawa.common.doc.MsgAction
import com.wawa.common.doc.Result
import com.wawa.common.doc.ResultCode
import com.wawa.base.BaseController

/**
 * COMMEN PARAMS ONLY FOR API DOC
 */
@Rest
class ApiDocController extends BaseController {
/**
 * 用户token
 * @apiDefine USER_COMMEN_PARAM
 * @apiParam { String } access_token  Users unique access-key.
 */

/**
 * 家族相关信息
 * @apiDefine FAMILY_COMMEN_RESPONSE
 *  {
     "_id": 家族id,
     "timestamp": 创建时间,
     "lastmodif": 最久修改时间,
     "status": 状态,
     "name": 家族名称,
     "badge": 家族徽章,
     "pic": 家族头像,
     "member_count": 成员数量,
     "leader_id": 1206921 族长id
    }
 */
    /**
     * @apiVersion 0.0.1
     * @apiGroup zResult
     * @apiName result_code
     * @api {get} apidoc/codes  CODE及描述
     * @apiDescription
     * 点击 右下方 -=[发送]=-按钮查看所有CODE以及对应描述
     *
     * @apiExample { curl } Example usage:
     *     curl -i http://test-aiapi.memeyule.com/apidoc/codes/
     */
    def codes(){
        return [code: Result.success.getCode(), data: ResultCode.messageCodes]
    }

    /**
     * @apiVersion 0.0.1
     * @apiGroup zResult
     * @apiName room_msg_actions
     * @api {get} apidoc/room_msg_actions  Action及描述
     * @apiDescription
     * 点击 右下方 -=[发送]=-按钮查看所有Action以及对应描述
     *
     * @apiExample { curl } Example usage:
     *     curl -i http://test-aiapi.memeyule.com/apidoc/room_msg_actions/
     */
    def room_msg_actions(){
        Map actions = new HashMap();
        MsgAction.values().each { MsgAction a ->
            if(a.getId() != null)
                actions[a.getId()] = a.name();
        }
        return [code: Result.success.getCode(), actions: actions]
    }
}