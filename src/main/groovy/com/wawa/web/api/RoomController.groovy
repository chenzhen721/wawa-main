package com.wawa.web.api

import com.mongodb.DBCollection
import com.wawa.api.Web
import com.wawa.base.BaseController
import com.wawa.base.anno.RestWithSession
import com.wawa.common.doc.Result
import com.wawa.common.util.BusiExecutor
import com.wawa.common.util.KeyUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.web.bind.ServletRequestUtils

import javax.servlet.http.HttpServletRequest
import java.util.concurrent.TimeUnit

import static com.wawa.common.util.WebUtils.$$

/**
 * 用户个人信息
 */
@RestWithSession
class RoomController extends BaseController {


    static final Logger logger = LoggerFactory.getLogger(RoomController.class)


    DBCollection catch_rooms() {
        return catchMongo.getCollection('catch_room')
    }

    DBCollection catch_repair_logs() {
        return logMongo.getCollection('catch_repair_logs')
    }


    /**
     * @apiVersion 0.0.1
     * @apiGroup Room
     * @apiName apply_repair
     * @api {get} room/apply_repair/:access_token/:_id?type=:type  房间报修
     * @apiDescription
     * 房间报修
     *
     * @apiUse USER_COMMEN_PARAM
     * @apiParam {Integer} [_id] 房间ID
     * @apiParam {Integer} [type] 报修类型 0：失灵， 1：延迟， 2： 其它
     *
     * @apiExample { curl } Example usage:
     *     curl -i http://test-api.17laihou.com/Room/apply_repair/11f69035f0fdb1d11381407b2b0ed1df/123

     * @apiSuccessExample {json} Success-Response:
     *
     * {
     *     "exec": 293,
     *     "code": 1
     * }
     *
     * @apiError UserNotFound The <code>0</code> of the User was not found.
     */
    def apply_repair (HttpServletRequest req) {
        final def redis = mainRedis
        final Integer room_id = Web.firstNumber(req)
        final Integer type = ServletRequestUtils.getIntParameter(req, 'type')
        if (type == null || room_id == null) {
            return Result.丢失必需参数
        }
        //status 0 新报 1 处理中 2 完成
        BusiExecutor.execute(new Runnable() {
            void run() {
                def ops = redis.opsForValue()
                def key = KeyUtils.ROOM.room_repair(room_id)
                if (redis.hasKey(key)) {
                    def _id = ops.get(key)
                    catch_repair_logs().update($$(_id: _id), $$($inc: $$("problem.${type}".toString(), 1)), false, false)
                }
                def _id = "${room_id}_${System.currentTimeMillis()}".toString()
                if (ops.setIfAbsent(key, _id)) {
                    redis.expire(key, 30 * 60L, TimeUnit.SECONDS)
                    def map = ["0": 0, "1": 0, "2": 0]
                    map.put('' + type, 1)
                    def room = catch_rooms().findOne($$(_id: room_id), $$(toy_id: 1, fid: 1))
                    catch_repair_logs().save($$(_id: _id, status: 0, room_id: room_id, fid: room['fid'], toy_id: room['toy_id'], problem: map, timestamp: System.currentTimeMillis()), writeConcern)
                }
            }
        })
        return Result.success
    }




}
