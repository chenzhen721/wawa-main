package com.wawa.web.api

import com.mongodb.BasicDBObject
import com.mongodb.DBCollection
import com.mongodb.DBObject
import com.wawa.base.anno.RestWithSession
import com.wawa.base.Crud
import com.wawa.common.doc.Result
import com.wawa.common.util.RedisLock
import com.wawa.model.Finance
import com.wawa.model.RedPacketStatus
import com.wawa.model.UserAwardType
import com.wawa.base.BaseController
import com.wawa.api.Web
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import javax.servlet.http.HttpServletRequest

import static com.wawa.common.util.WebUtils.$$
import static com.wawa.common.doc.MongoKey.*

/**
 * 家族房红包
 */
@RestWithSession
class RedPacketController extends BaseController {

    private Logger logger = LoggerFactory.getLogger(RedPacketController.class)

    DBCollection award_logs() {logMongo.getCollection("user_award_logs")}


    /**
     * @apiVersion 0.0.1
     * @apiGroup RedPacket
     * @apiName red_draw
     * @api {post} redpacket/draw/:token/:packet_id 抽取红包
     * @apiDescription
     * 用户抽取红包
     *
     * @apiUse USER_COMMEN_PARAM

     * @apiParam {String} packet_id     红包ID
     *
     * @apiExample { curl } Example usage:
     *     curl -i http://test-aiapi.memeyule.com/redpacket/sendd01f29c8c22e40850af613ef9708c34f/1201183_1203285_1504255294090

     *
     */
    def draw(HttpServletRequest req) {
        Integer userId = Web.getCurrentUserId()
        String packet_id = Web.firstParam(req) //红包ID
        def packet = red_packets().findOne($$(redpacket_id: packet_id), $$(user_id : 1,friends:1, timestamp: 1,  draw_uids:1,expires:1))
        if (packet == null) {
            return Result.丢失必需参数;
        }
        //def user = users().findOne($$(_id:userId), $$('finance': 1,  level:1))
        RedisLock lock = new RedisLock("draw:redpacket:"+packet_id);
        try{
            lock.lock();
            //是否已经抽过
            List<Integer> draw_uids = packet['draw_uids'] as List
            if(draw_uids != null && draw_uids.contains(userId)){
                return Result.已领取过
            }
            //是否为好友
            List<Long> friends = packet['friends'] as List
            if(friends == null || !friends.contains(userId)){
                return Result.权限不足
            }
            if ((packet['expires'] as Long) < System.currentTimeMillis()) {
                return Result.红包已过期
            }
            def query = $$(redpacket_id: packet_id, status: RedPacketStatus.有效.ordinal(), draw_uids: [$ne: userId], count: [$gt: 0]).append('$where', 'this.packets.length > 0')
            def update = $$('$pop': [packets: -1], $addToSet: [draw_uids: userId], $inc:[count: -1])
            def red_packet = red_packets().findAndModify(query, update)
            //红包抽完
            if (red_packet == null) {
                return Result.红包已抢光
            }
            List<Integer> packets = red_packet.get('packets') as List
            if (packets.size() > 0) {
                //用户抽得钻石
                Long diamond = packets.remove(0) as Long;
                def log = Web.awardLog(userId, UserAwardType.钻石红包, [diamond: diamond])
                //添加钻石成功
                if (addDiamond(userId, diamond, log)) {
                    def time = System.currentTimeMillis()
                    def draw_user = Web.getUserInfo(userId)
                    //记录抽取的用户日志
                    draw_user.put('timestamp', time)
                    draw_user.put('diamond', diamond)
                    def updateDrawUser = $$($addToSet: [draw_logs: draw_user])
                    red_packets().update($$(redpacket_id: packet_id), updateDrawUser, false, false, writeConcern);
                    return [code: Result.success.code, data: [diamond:diamond]]
                }
            }
            return Result.红包已抢光
        }catch (Exception e){
            logger.error("draw Exception : {}", e)
        }
        finally {
            lock.unlock();
        }
        return Result.error;
    }

    /**
     * @apiVersion 0.0.1
     * @apiGroup RedPacket
     * @apiName red_info
     * @api {get} redpacket/info/:token/:packet_id 查询红包信息
     * @apiDescription
     * 查询红包信息
     *
     * @apiUse USER_COMMEN_PARAM
     * @apiParam {String} packet_id     红包ID
     *
     * @apiExample { curl } Example usage:
     *     curl -i http://test-aiapi.memeyule.com/redpacket/info/d01f29c8c22e40850af613ef9708c34f/1201183_1203285_1504255294090

     */
    def info(HttpServletRequest req) {
        String packet_id = Web.firstParam(req) //红包ID
        def packet = red_packets().findOne($$(redpacket_id: packet_id), $$(user_id:1, draw_logs: 1, count:1, award_diamond:1,toy:1))
        if (packet == null) {
            return Result.丢失必需参数;
        }
        def uid = packet['user_id'] as Integer
        packet['user'] = Web.getUserInfo(uid);
        return [code: Result.success.code, data:packet]
    }


    /**
     * @apiVersion 0.0.1
     * @apiGroup RedPacket
     * @apiName rec_logs
     * @api {get} redpacket/rec_logs/51ba66570cbeed8764d27d05b5c52820?page=1&size=20 收红包流水
     * @apiDescription
     * 用户收取红包流水日志
     *
     * @apiExample { curl } Example usage:
     *     curl -i http://test-aiapi.memeyule.com/redpacket/rec_logs/:token?page=1&size=20&category=1

     * @apiSuccessExample {json} Success-Response:
     *
     *   "count": 1,
     "   "data": [
     {
     "user_id": 1204523,
     "award": {
     diamond:10 钻石数量
     }，
     "timestamp": 1504253415143
     }
     ]
     "code": 1,
     "all_page": 1
     *
     */
    def rec_logs(HttpServletRequest req){
        Crud.list(req, award_logs(), $$(user_id: Web.getCurrentUserId(),type:  UserAwardType.钻石红包.getId()), ALL_FIELD, SJ_DESC){
            List<BasicDBObject> data ->
                for (BasicDBObject obj : data) {
                    /*Integer cash = (obj.remove('award') as Map).get(key) as Integer
                    obj[key] = cash;*/
                }
        }
    }

    /**
     * 定时任务，超时退款
     * @param req
     */
    def refund(HttpServletRequest req) {
        red_packets().find($$(category: 2, count: [$gt: 0], end_at: [$lt: System.currentTimeMillis()])).toArray().each { DBObject obj->
            def refund = (obj['packets'] as List).sum()
            //更改红包状态，
            def set = $$(status: RedPacketStatus.超时.ordinal(), refund: refund)
            if (red_packets().update($$(_id: obj['_id']), $$($set: set), false, false, writeConcern).getN() == 1) {
                def familyId = obj['family_id'] as Integer
                def userId = obj['user_id'] as Integer
                if (1 == users().update($$(_id, userId), $$($inc, $$(Finance.finance$diamond_count, refund)), false, false, writeConcern).getN()) {
                    //记录用户奖励日志
                    def award = [diamond: refund] as Map
                    Web.saveAwardLog(familyId, userId, UserAwardType.钻石红包退款, award)
                }
            }
        }
        return Result.success
    }
}
