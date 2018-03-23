package com.wawa.web.api

import com.mongodb.BasicDBObject
import com.wawa.api.Web
import com.wawa.base.anno.RestWithSession
import com.wawa.common.doc.MsgAction
import com.wawa.common.doc.Result
import com.wawa.common.util.KeyUtils
import com.wawa.model.FamilyType
import com.wawa.model.ManageOpers
import com.wawa.model.UserType
import com.wawa.base.BaseController
import com.wawa.api.notify.RoomMsgPublish
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.web.bind.ServletRequestUtils

import javax.servlet.http.HttpServletRequest
import java.util.concurrent.TimeUnit

import static com.wawa.common.util.WebUtils.$$
import static com.wawa.common.doc.MongoKey.*

/**
 * 房间管理相关权限
 */
@RestWithSession
class ManageController extends BaseController{

    static final Integer DEFAULT_MINUTE = 5

    static Logger logger = LoggerFactory.getLogger(ManageController.class)

    /**
     * @apiVersion 0.0.1
     * @apiGroup Manage
     * @apiName shutup
     * @api {post} manage/shutup/:access_token/:room_id/:user_id?minute=5  家族房间禁言
     * @apiDescription
     * 家族房间禁言

     * @apiUse USER_COMMEN_PARAM
     *
     * @apiParam {Number} room_id 家族房间ID
     * @apiParam {Number} user_id 用户ID
     * @apiParam {Number=5分钟,30分钟,12*60分钟 } minute 时间(分钟)

     * @apiExample {curl} Example usage:
     *     curl -i http://test-aiapi.memeyule.com/manage/shutup/1b29cd74980ebe1f415f406bbd174450/1282223/13022222?minute=5
     *
     */
    def shutup(HttpServletRequest req){
        doInAdmin(req, 1){ Integer room_id, Integer uid, Integer minute->
            forbiddenLittleWhile(true,room_id,uid,minute)
        }
    }

    /**
     * @apiVersion 0.0.1
     * @apiGroup Manage
     * @apiName kick
     * @api {post} manage/kick/:access_token/:room_id/:user_id?minute=5 家族房间踢人
     * @apiDescription
     * 家族房间踢人
     *
     * @apiUse USER_COMMEN_PARAM
     *
     * @apiParam {Number} room_id 家族房间ID
     * @apiParam {Number} user_id 被踢用户ID
     * @apiParam {Number=5分钟,60分钟,12小时} minute 时间(分钟)
     *
     * @apiExample {curl} Example usage:
     *     curl -i http://test-aiapi.memeyule.com/manage/kick/1b29cd74980ebe1f415f406bbd174450/1282223/13022222?minute=5
     *
     */
    def kick(HttpServletRequest req){
        doInAdmin(req, 2){ Integer room_id, Integer uid, Integer minute->
            forbiddenLittleWhile(false,room_id,uid,minute)
        }
    }

    /**
     * 恢复禁言
     * @param req
     * @return
     */
/*    def recover(HttpServletRequest req){
        Integer room_id = roomId(req)
        Integer uid = Web.userId(req)
        Integer cid = Web.getCurrentUserId()

        def room = rooms().findOne(room_id,new BasicDBObject(xy_star_id:1,admin:1))
        if(room == null){//房间不存在
            return Web.notAllowed()
        }

        //用户不存在
        def hander = users().findOne(new BasicDBObject(_id:cid,status:true),$$("priv":1,"family":1,"finance":1))
        if(null == hander){
            return Web.notAllowed()
        }

        //获取禁言用户身份\等级信息
        Integer handlerPriv  = hander?.get("priv") as Integer
        Map finance = hander?.get(Finance.finance) as Map
        def hander_rank = (finance.get(Finance.coin_spend_total) ?: 0) as Long

        //直播间管理员
        List<Integer> admins = (List<Integer>) (room.get('admin') ?: Collections.emptyList())
        //可以恢复禁言
        if(handlerPriv == UserType.运营人员.ordinal() || handlerPriv == UserType.客服人员.ordinal()
                || handlerPriv == UserType.主播.ordinal()
                || Web.isGuarder(room_id, cid)
                || admins.contains(cid)
                || hander_rank >= Constant.USER_LEVEL_26
                || Web.isVip(cid, User.VIP.HIGH_LEVEL)
        ){
            userRedis.delete(KeyUtils.ROOM.shutup(room_id, uid))
            //通知用户 恢复发言权
            MessageSend.publishRecoverShutUpEvent(uid)
//            publish(KeyUtils.CHANNEL.user(uid),"{\"action\":\"manage.recover\",\"data_d\":{\"room_id\":${room_id}}}")
            return [code:1]
        }
        return Web.notAllowed()
    }*/



    //禁言权限
    private ManageOpers canAdmin(Integer room_id, Integer uid){
        Integer cid = Web.getCurrentUserId()
        logger.info("room_id : {}, shutuper : {},  target : {}",room_id, cid, uid)

        def room = rooms().findOne(room_id,new BasicDBObject(family_id:1))
        if(room == null){//房间不存在
            logger.info("bFlag: false room is null")
            return ManageOpers.Shutuper.other
        }
        Integer xy_star_id = room.get('xy_star_id') as Integer
        Integer familyId = room.get("family_id") as Integer
        //禁言者不存在或者不属于此房间家族
        def hander = users().findOne($$(_id:cid, status:true, 'family.family_id':familyId),$$("priv":1,"family":1,"level":1))
        if(null == hander){
            logger.info("bFlag:false handler is null")
            return ManageOpers.Shutuper.other
        }
        //获取禁言用户身份等级信息
        Object handlerPriv  = hander?.get("priv")
        Map family = hander?.get("family") as Map
        Integer handlerFamilyPriv = (family.get("family_priv") ?: 0) as Integer
        Integer handlerFamilyId = (family.get("family_id") ?: 0) as Integer

        //目标用户不存在
        Object target = users().findOne(uid, $$("priv":1,"family":1,"level":1))
        if (target ==null){
            logger.info("bFlag:false target user is null")
            return ManageOpers.Shutuper.other
        }
        //目标用户身份、等级
        Integer targetUserType = target?.get("priv") as Integer
        Map targetFamily = target?.get("family") as Map
        Integer targetFamilyPriv = (targetFamily?.get("family_priv") ?: FamilyType.成员.ordinal()) as Long
        Integer targetFamilyId = (targetFamily?.get("family_id") ?: 0) as Long

        //不能禁言运营人员 、客服人员
        if(targetUserType == UserType.运营人员.ordinal()
                || targetUserType == UserType.客服人员.ordinal()){
            logger.info("bFlag:false target user is yunyin kefu")
            return ManageOpers.Shutuper.other
        }

        //运营和客服可以禁言任何人
        int iHanderType = Integer.parseInt(handlerPriv.toString())
        if(iHanderType == UserType.运营人员.ordinal()
                || iHanderType == UserType.客服人员.ordinal()){
            logger.info("bFlag: true hander is yunying  kefu")
            return ManageOpers.Shutuper.运营
        }

        Boolean flag = Boolean.TRUE
        //族长
        if(cid.equals(xy_star_id)){
            return ManageOpers.Shutuper.族长
        }

        //家族其他管理职位
        if(!handlerFamilyPriv.equals(FamilyType.成员.ordinal())){
            //相同家族 不能经验职位高的
            if(handlerFamilyId.equals(targetFamilyId) && handlerFamilyPriv >= targetFamilyPriv){
                logger.info("bFlag: false target is higher than hander")
                flag = Boolean.FALSE
            }
            if(flag)
                return ManageOpers.Shutuper.副族长
        }

        logger.info("bFlag: false cannot match any rule...")
        return ManageOpers.Shutuper.other
    }

    //踢人
    private ManageOpers canAdminKick(Integer room_id, Integer uid){
        Integer cid = Web.getCurrentUserId()
        logger.info("room_id : {}, Kicker : {},  target : {}",room_id, cid, uid)

        def room = rooms().findOne(room_id,new BasicDBObject(family_id:1, xy_star_id: 1))
        if(room == null){//房间不存在
            logger.info("bFlag: false room is null")
            return ManageOpers.Kicker.other
        }
        Integer xy_star_id = room.get('xy_star_id') as Integer
        Integer familyId = room.get("family_id") as Integer
        //踢人者不存在或者不属于此房间家族
        def hander = users().findOne($$(_id:cid, status:true, 'family.family_id':familyId),$$("priv":1,"family":1,"level":1))
        if(null == hander){
            logger.info("bFlag:false handler is null")
            return ManageOpers.Kicker.other
        }
        //获取踢人用户身份等级信息
        Object handlerPriv  = hander?.get("priv")
        Map family = hander?.get("family") as Map
        Integer handlerFamilyPriv = (family.get("family_priv") ?: 0) as Integer
        Integer handlerFamilyId = (family.get("family_id") ?: 0) as Integer

        //目标用户不存在
        Object target = users().findOne(uid, $$("priv":1,"family":1,"level":1))
        if (target ==null){
            logger.info("bFlag:false target user is null")
            return ManageOpers.Kicker.other
        }
        //目标用户身份、等级
        Integer targetUserType = target?.get("priv") as Integer
        Map targetFamily = target?.get("family") as Map
        Integer targetFamilyPriv = (targetFamily?.get("family_priv") ?: FamilyType.成员.ordinal()) as Integer
        Integer targetFamilyId = (targetFamily?.get("family_id") ?: 0) as Integer

        //不能踢运营人员 、客服人员
        if(targetUserType == UserType.运营人员.ordinal()
                || targetUserType == UserType.客服人员.ordinal()){
            logger.info("bFlag:false target user is yunyin kefu")
            return ManageOpers.Kicker.other
        }

        //运营和客服可以踢任何人
        int iHanderType = Integer.parseInt(handlerPriv.toString())
        if(iHanderType == UserType.运营人员.ordinal()
                || iHanderType == UserType.客服人员.ordinal()){
            logger.info("bFlag: true hander is yunying  kefu")
            return ManageOpers.Kicker.运营
        }

        Boolean flag = Boolean.TRUE
        //族长
        if(cid.equals(xy_star_id)){
            return ManageOpers.Kicker.族长
        }

        //家族其他管理职位
        if(!handlerFamilyPriv.equals(FamilyType.成员.ordinal())){
            //相同家族 不能踢经验职位高与自己的
            if(handlerFamilyId.equals(targetFamilyId) && handlerFamilyPriv >= targetFamilyPriv){
                logger.info("bFlag: false target is higher than hander")
                flag = Boolean.FALSE
            }
            if(flag) {
                if (handlerFamilyPriv == FamilyType.副族长.ordinal()) {
                    return ManageOpers.Kicker.副族长
                }
                if (handlerFamilyPriv == FamilyType.执事.ordinal()) {
                    return ManageOpers.Kicker.执事
                }
            }
        }

        logger.info("bFlag: false cannot match any rule...")
        return ManageOpers.Kicker.other
    }



    private forbiddenLittleWhile(boolean shutUp, Integer room_id, Integer uid, long minutes){
        String forbiddenKey = shutUp ? KeyUtils.ROOM.shutup(room_id,uid):KeyUtils.ROOM.kick(room_id,uid)
        String action =  shutUp ? "manage.shutup" : "manage.kick"
        def fbd_users = users().findOne(uid,new BasicDBObject('nick_name',1).append('pic', 1))
        String nickName = fbd_users?.get('nick_name')
        String pic = fbd_users?.get('pic')
        if (nickName != null){
            Long secs = Math.max(1, minutes * 60)
            logger.debug("forbiddenLittleWhile minutes: {} secs: {}", minutes, secs)
            userRedis.opsForValue().set(forbiddenKey,KeyUtils.MARK_VAL, secs,TimeUnit.SECONDS)
            def admin_userId = Web.getCurrentUserId()
            def admin_nicke_name = Web.currentUserNick()
            def user = users().findOne($$('_id':uid,'status':Boolean.TRUE),$$('level':1,'nick_name':1, 'family.family_id': 1, 'family.family_priv': 1))
            def from = users().findOne($$('_id':admin_userId,'status':Boolean.TRUE),$$('pic': 1, 'level':1,'nick_name':1, 'family.family_id': 1, 'family.family_priv': 1))
            RoomMsgPublish.publish2Room(room_id, shutUp ? MsgAction.禁言 : MsgAction.踢人, [
                    room_id      :room_id, xy_user_id:Web.getCurrentUserId(), nick_name:Web.currentUserNick()
                    , fbd_user_id: uid, fbd_nick_name: nickName, fbd_pic: pic, minutes: minutes, from: from, to: user
            ], Boolean.FALSE)

        }
    }

    private doInAdmin(HttpServletRequest req, Integer type, Closure closure){
        Integer room_id = Web.roomId(req)
        Integer uid = Web.userId(req)
        Integer minute = limitMinute(req, DEFAULT_MINUTE)
        ManageOpers manageOper = null;
        if(type == 1){
            manageOper = canAdmin(room_id,uid)
        }
        else{
            manageOper = canAdminKick(room_id,uid)
        }

        logger.debug("doInAdmin req minute: {}", minute)
        if(manageOper != null && manageOper.isAllowed(minute)){
            return [code:Boolean.FALSE.is(closure.call(room_id, uid, minute)) ? 0 : 1]
        }
        logger.debug("doInAdmin is not allowed")
        return  Result.权限不足;

    }

    private static Integer limitMinute(HttpServletRequest req, Integer defaultValue){
        Integer value = ServletRequestUtils.getIntParameter(req,"minute",defaultValue)
        return Math.min(value,10080)// 最长一周
    }

}
