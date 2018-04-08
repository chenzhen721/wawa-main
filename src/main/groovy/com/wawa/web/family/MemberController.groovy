package com.wawa.web.family

import com.mongodb.BasicDBObject
import com.mongodb.DBCollection
import com.mongodb.DBObject
import com.wawa.api.Web
import com.wawa.api.notify.RoomMsgPublish
import com.wawa.api.notify.SysMsgPushUtil
import com.wawa.base.BaseController
import com.wawa.base.Crud
import com.wawa.base.anno.RestWithSession
import com.wawa.common.doc.MsgAction
import com.wawa.common.doc.Result
import com.wawa.model.*
import com.wawa.web.api.UserController
import org.apache.commons.lang.StringUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.web.bind.ServletRequestUtils

import javax.servlet.http.HttpServletRequest

import static com.wawa.common.util.WebUtils.$$
import static com.wawa.common.doc.MongoKey.*

/**
 * 家族成员
 */

@RestWithSession
class MemberController extends BaseController {

    DBCollection table() {familyMongo.getCollection('member_applys')}

    DBCollection invites() {familyMongo.getCollection('member_invites')}

    static final Logger logger = LoggerFactory.getLogger(MemberController.class)

    static final Integer FAMILY_MEMBER_LIMITS = isTest ? 10 :200;
    /**
     * @apiVersion 0.0.1
     * @apiGroup Family
     * @apiName family_apply
     * @api {post} member/apply  用户申请家族
     * @apiDescription
     * 详细描述
     *
     * @apiUse USER_COMMEN_PARAM
     * @apiParam {Number} id    家族ID
     *
     * @apiExample { curl } Example usage:
     *     curl -i http://test-aiapi.memeyule.com/member/apply/9e9a0d008ff62e6a5230f2eed9cb8299/:id
     *
     */
    def apply(HttpServletRequest req) {
        def userId = Web.getCurrentUserId()
        def users = users()

        if(Web.currentUserType() == UserType.客服人员.ordinal() ||
                Web.currentUserType() == UserType.运营人员.ordinal())
            return Result.权限不足
        Integer family_id = Web.firstNumber(req)
        def family =  familys().findOne($$(_id, family_id).append("status", StatusType.通过.ordinal()))
        if(null == family)
            return Result.家族不存在

        //用户已经有家族
        def user =  users.findOne($$(_id:userId,'family':[$ne:null]))
        if(null != user)
            return Result.用户已经加入家族

        Integer member_count = (family["member_count"] ?:0)as Integer
        logger.debug("member_count : {}", member_count)
        if(member_count >= FAMILY_MEMBER_LIMITS){
            return Result.数量超过上限;
        }
        def member_apply = $$('xy_user_id': userId, family_id:family_id)
        //"status"状态 1-未通过 ; 2-通过  ; 3-待处理  ； 4-解散
        def status = table().findOne(member_apply, $$('status', 1), SJ_DESC)?.get('status') as Integer
        if (status && (status == FamilyApplyStatus.未处理.ordinal()))
            return Result.用户已经提交申请   //msg: '待处理'

        def tmp = System.currentTimeMillis()
        member_apply.put(_id, "${member_apply.get('xy_user_id')}_${System.currentTimeMillis()}".toString())
        member_apply.put("family_id", family_id)
        member_apply.put("name", family.get('name'))
        member_apply.put("badge", family.get('badge'))
        member_apply.put("pic", family.get('pic'))
        member_apply.put("timestamp", tmp)
        member_apply.put("lastmodif", tmp)
        member_apply.put("user_priv", Web.currentUserType())
        member_apply.put("status", FamilyApplyStatus.未处理.ordinal())
        table().save(member_apply)
        //推送消息
        List<Integer> family_members = []
            users.find($$('family.family_id':family_id, 'family.family_priv':[$ne:FamilyType.成员.ordinal()]),$$(_id, 1)).toArray().each { DBObject obj ->
                family_members.add(obj.get('_id') as Integer)
            }
        SysMsgPushUtil.sendToUsers(family_members,"${Web.currentUserNick()}申请加入家族".toString(), Boolean.FALSE, SysMsgType.家族申请)

        return Result.success
    }


    /**
     * @apiVersion 0.0.1
     * @apiGroup Family
     * @apiName family_apply_list
     * @api {get} member/applys  我的申请记录
     * @apiDescription
     * 用户查看自己的家族申请记录
     *
     * @apiUse USER_COMMEN_PARAM
     *
     * @apiExample { curl } Example usage:
     *     curl -i http://test-aiapi.memeyule.com/member/applys/9e9a0d008ff62e6a5230f2eed9cb8299
     */
    def applys(HttpServletRequest req){
        def userId = Web.getCurrentUserId()
        def record = logMongo.getCollection('member_applys').find($$(xy_user_id:userId)).toArray()
        [code: 1, data:record]
    }

    /**
     * @apiVersion 0.0.1
     * @apiGroup Family
     * @apiName family_apply_cancel
     * @api {post} member/cancel  取消申请
     * @apiDescription
     * 用户取消申请家族
     *
     * @apiUse USER_COMMEN_PARAM
     * @apiParam {String} id    申请记录ID
     *
     * @apiExample { curl } Example usage:
     *     curl -i http://test-aiapi.memeyule.com/member/cancel/9e9a0d008ff62e6a5230f2eed9cb8299/:id
     */
    def cancel(HttpServletRequest req){
        Long time = System.currentTimeMillis()
        //"status"状态 1-未通过 ; 2-通过  ; 3-待处理  ； 4-解散
       def userId = Web.getCurrentUserId()
       String sId = Web.firstParam(req)
       table().findAndModify(new BasicDBObject(_id: sId, xy_user_id:userId,status: FamilyApplyStatus.未处理.ordinal()),
                    new BasicDBObject('$set': [status:  FamilyApplyStatus.用户取消.ordinal(), lastmodif: time]))
        return Result.success

    }

    /**
     * @apiVersion 0.0.1
     * @apiGroup Family
     * @apiName family_exit
     * @api {post} member/exit  退出家族
     * @apiDescription
     * 用户退出自己所在家族
     *
     * @apiUse USER_COMMEN_PARAM
     *
     * @apiExample { curl } Example usage:
     *     curl -i http://test-aiapi.memeyule.com/member/exit/9e9a0d008ff62e6a5230f2eed9cb8299
     */
    def exit(HttpServletRequest req){
        def userId = Web.getCurrentUserId()
        def users = users();
        def member =  users.findOne($$(_id,userId),$$("family":1,priv:1))
        if(null == member)
            return Result.权限不足

        def family =(Map)member?.get("family")
        Integer family_id = family?.get("family_id") as Integer
        def family_priv = family?.get("family_priv") as Integer

        def myFamily =  familyMongo.getCollection('familys').findOne($$(_id,family_id));
        if(null == myFamily)
            return Result.权限不足

        //族长不能退出家族
        if(FamilyType.族长.ordinal() == family_priv.intValue())
            return Result.族长不能退出家族


        if(1==users.update($$(_id,userId),$$($unset, $$("family", 1))).getN()){
            familyMongo.getCollection("members").remove($$(_id,"${family_id}_${userId}".toString()),writeConcern)
            def tmp = System.currentTimeMillis()
            if(FamilyType.成员.ordinal() == family_priv.intValue()){
                familys().update($$(_id, family_id), $$($set: [lastmodif: tmp], $inc: ['member_count': -1]))
                rooms().update($$(_id, family_id), $$($inc, $$('member_count', -1)))
            }
            if(FamilyType.副族长.ordinal() == family_priv.intValue()){
                familys().update($$(_id,family_id),$$($set:[lastmodif:tmp],$inc:['leaders.count':-1]))
            }
            refreshMemberCount(family_id);
            Web.refreshUserInfoOfSession(req, userId);
            RoomMsgPublish.publish2Room(family_id, MsgAction.退出家族, [user:Web.getUserInfo(userId), user_id:userId], Boolean.FALSE)
            return Result.success
        }
        return Result.error
    }


    /**
     * @apiVersion 0.0.1
     * @apiGroup Family
     * @apiName member_apply_list
     * @api {get} member/apply_list  族长查看申请记录
     * @apiDescription
     * 族长查看成员申请记录
     *
     * @apiExample { curl } Example usage:
     *     curl -i http://test-aiapi.memeyule.com/member/apply_list/9e9a0d008ff62e6a5230f2eed9cb8299?page=1&size=20

     * @apiSuccessExample {json} Success-Response:
     *
     *   "count": 1,
         "data": [
             {
             "_id": "1214781_1494814862096" 申请ID,
             "xy_user_id": 1214781 用户id,
             "family_id": 1206921 家族id,
             "name": "家族名称",
             "badge": "http://img.sumeme.com/50/2/1406172066034.jpg",
             "timestamp": 申请时间,
             "user_priv": 3,
             "status": 3 [1:未通过,2:通过, 3:未处理, 4:用户取消,5:失效],
             "nick_name": "用户昵称",
             "pic": "https://aiimg.sumeme.com/45/5/1487645253037.png" 用户头像
             }
         ],
         "code": 1,
         "all_page": 1
     *
     */
    def apply_list(HttpServletRequest req) {
        def users =  users()
        def leader = users.findOne($$(_id, Web.getCurrentUserId()), $$("family", 1))
        def family = (Map) leader?.get("family")
        def family_id = family?.get("family_id")

        def myFamily = familyMongo.getCollection('familys').findOne($$(_id, family_id));
        if (null == myFamily)
            return Result.权限不足

        if (!this.check(req))
            return Result.权限不足

        def query = Web.fillTimeBetween(req)
        query.and("family_id").is(family_id)

        if (StringUtils.isNotBlank(req.getParameter('user_id') as String))
            query.and("xy_user_id").is(Integer.parseInt(req.getParameter('user_id') as String))

        def status = FamilyApplyStatus.未处理.ordinal()
        if (StringUtils.isNotBlank(req.getParameter('status') as String))
            status = Integer.parseInt(req.getParameter('status') as String)
        query.and("status").is(status)

        Crud.list(req, table(), query.get(), ALL_FIELD, SJ_DESC) { List<BasicDBObject> list ->
            for (BasicDBObject apply : list) {
                def user = users.findOne(apply.get("xy_user_id") as Integer, $$(pic:1,nick_name: 1, _id: 0))
                apply.putAll(user)
            }
        }
    }

    /**
     * @apiVersion 0.0.1
     * @apiGroup Family
     * @apiName family_handle
     * @api {post} member/handle 族长处理申请
     * @apiDescription
     * 族长处理申请记录(通过或未通过)
     *
     * @apiUse USER_COMMEN_PARAM
     *
     * @apiParam {String} _id  申请流水ID
     * @apiParam {Interge=1:未通过, 2:通过} status  状态
     *
     * @apiExample { curl } Example usage:
     *     curl -i http://test-aiapi.memeyule.com/member/handle/9e9a0d008ff62e6a5230f2eed9cb8299/1214781_1494814862096?status=2
     */
    def handle(HttpServletRequest req) {
        boolean bFlag = this.check(req)
        if (!bFlag)
            return Result.权限不足

        Long time = System.currentTimeMillis()

        //"status"状态 1-未通过 ; 2-通过  ; 3-待处理  ； 4-解散
        String sId = Web.firstParam(req)
        def status = ServletRequestUtils.getIntParameter(req, 'status')
        if(StringUtils.isEmpty(sId)){
            return Result.丢失必需参数;
        }
        if (status != FamilyApplyStatus.通过.ordinal() && status != FamilyApplyStatus.未通过.ordinal()) {
            return Result.丢失必需参数;
        }
        Integer user_id =  sId.split("_")[0] as Integer
        //用户是否已经加入其它家族
        if(users().count($$(_id:user_id,'family':[$ne:null])) != 0){
            table().findAndModify($$(_id: sId, status: FamilyApplyStatus.未处理.ordinal()),
                        $$('$set': [status: FamilyApplyStatus.失效.ordinal(), lastmodif: time]))
            return Result.用户已经加入家族;
        }

        def apply = table().findAndModify($$(_id: sId, status: StatusType.未处理.ordinal()), $$('$set': [status: status, lastmodif: time]))

        if (apply) {
            Integer fid = apply.get("family_id") as Integer

            if (status == StatusType.通过.ordinal() ){
                def family =  familys().findOne($$(_id, fid).append("status", StatusType.通过.ordinal()))
                if(null == family)
                    return Result.家族不存在
                Integer member_count = (family["member_count"] ?:0)as Integer
                if(member_count >= FAMILY_MEMBER_LIMITS){
                    return Result.数量超过上限;
                }
                if(addMember(user_id, fid)) {
                    table().updateMulti($$('xy_user_id': user_id, status: FamilyApplyStatus.未处理.ordinal()),
                            $$($set, $$(status: FamilyApplyStatus.失效.ordinal(), lastmodif: time)))
                    refreshMemberCount(fid);
                    //发送消息
                    publish(user_id, Web.getCurrentUserId(), "恭喜，你申请的家族已接纳了你".toString(), SysMsgType.系统)
                    RoomMsgPublish.publish2Room(user_id, MsgAction.加入家族, [user:Web.getUserInfo(user_id)], Boolean.TRUE)

                }
            }

            else if (status == StatusType.未通过.ordinal()) {
                //发送消息
                publish(user_id, Web.getCurrentUserId(), "抱歉，你申请的家族已拒绝了你".toString(), SysMsgType.系统)
            }
            return Result.success
        }
        return Result.error
    }

    //添加成员
    private Boolean addMember(Integer user_id, Integer familyId){
        def family =  familyMongo.getCollection('familys').findOne($$(_id, familyId))
        Long time = System.currentTimeMillis();
        if (1 == users().update($$(_id : user_id, family : null),
                $$($set: [family: [family_id: familyId, family_priv: FamilyType.成员.ordinal(), timestamp: time,
                                                                       name     : family.get('name'),
                                                                       badge    : family.get('badge'),
                                                                       pic      : family.get('pic')]]),
                false, false, writeConcern).getN()){
            familyMongo.getCollection('familys').update($$(_id, familyId), $$($inc, $$('member_count', 1)))
            rooms().update($$(_id, familyId), $$($inc, $$('member_count', 1)));
            familyMongo.getCollection("members").save($$(
                    _id: "${familyId}_${user_id}".toString(), fid: familyId, uid:user_id, timestamp: time), writeConcern);
            Web.refreshUserInfoOfSession(user_id)
            return Boolean.TRUE
        }
        return Boolean.TRUE;
    }

    /**
     * @apiVersion 0.0.1
     * @apiGroup Family
     * @apiName family_set_priv
     * @api {post} member/set_priv/:token/:id/:priv  家族职位设置
     * @apiDescription
     * 家族职位设置
     *
     * @apiUse USER_COMMEN_PARAM

     * @apiParam {Number} id  被踢用户ID
     * @apiParam {Number=1:族长,2:副族长,3:执事,4:成员} priv  家族职位
     *
     * @apiExample { curl } Example usage:
     *     curl -i http://test-aiapi.memeyule.com/MemberController/set_priv/9e9a0d008ff62e6a5230f2eed9cb8299/1205871/2
     *
     */
    def set_priv(HttpServletRequest req) {
        Integer userId = Web.firstNumber(req)
        Integer priv_type = Web.secondNumber(req)
        if(priv_type <= 0 || priv_type > FamilyType.values().length){
            return Result.丢失必需参数
        }
        //user
        def member = users().findOne($$(_id, userId), $$("family": 1,priv:1))
        if (null == member)
            return Result.丢失必需参数

        //leader
        def leader = users().findOne($$(_id, Web.getCurrentUserId()), UserController.user_info_core_field)
        if (null == leader)
            return Result.丢失必需参数

        def leader_family = (Map) leader?.get("family")
        def leader_family_id = leader_family?.get("family_id") as Integer
        def myFamily = familys().findOne($$(_id, leader_family_id).append("status", StatusType.通过.ordinal()));
        if (null == myFamily)
            return Result.丢失必需参数

        Integer leader_priv = leader_family?.get("family_priv") as Integer
        if (FamilyType.族长.ordinal() == FamilyType.成员)
            return Result.权限不足
        //不允许设置职位比自己的职位还高或者相等
        if(leader_priv >= priv_type){
            return Result.权限不足
        }
        //member
        def family = (Map) member?.get("family")
        def family_id = family?.get("family_id") as Integer
        if (leader_family_id != family_id)
            return Result.权限不足
        //设置用户的当前家族职位
        def familyPriv = family?.get("family_priv") as Integer
        FamilyType familyType = FamilyType.values()[familyPriv]
        //只能操作比自己家族职位低的
        if(leader_priv >= familyPriv)
            return Result.权限不足
        //需要新设置的家族职位
        FamilyType newFamilyPriv = FamilyType.values()[priv_type]
        //是否超过数量上限
        String privFiled = newFamilyPriv.getId()+"_count";
        Integer limitCount = newFamilyPriv.getLimitCount()
        if((myFamily.get(privFiled) as Integer) >= limitCount){
            return Result.数量超过上限;
        }
        def tmp = System.currentTimeMillis()

        if (FamilyType.成员.ordinal() != familyPriv) {
            if(familys().update($$(_id: leader_family_id).append(privFiled, $$($ne:[$gte:limitCount])),
                                    $$($set: [lastmodif: tmp], $inc: $$(privFiled, 1))).getN() == 0){
                return Result.数量超过上限;
            }
        }else{//管理降级
            familys().update($$(_id, leader_family_id), $$($set: [lastmodif: tmp], $inc: $$(familyType.getId()+"_count", -1)))
        }
        if (1 == users().update($$(_id, userId),
                $$($set, $$(['family.family_priv': priv_type, 'family.lastmodif': tmp])), false, false ,writeConcern).getN()){
            refreshMemberCount(family_id);
            Web.refreshUserInfoOfSession(userId)
            RoomMsgPublish.publish2Room(family_id, MsgAction.设置职位, [priv:priv_type, from_user:leader, user:Web.getUserInfo(userId), user_id:userId], Boolean.TRUE)
            return Result.success
        }
        return Result.error
    }


    /**
     * @apiVersion 0.0.1
     * @apiGroup Family
     * @apiName family_kick
     * @api {post} member/kick/:token/:id 族长/副族长踢出用户
     * @apiDescription
     * 族长和副族长可将用户从家族中踢除
     *
     * @apiUse USER_COMMEN_PARAM
     *
     * @apiParam {Number} id  被踢用户ID
     *
     * @apiExample { curl } Example usage:
     *     curl -i http://test-aiapi.memeyule.com/member/kick/9e9a0d008ff62e6a5230f2eed9cb8299/1214781
     */
    def kick(HttpServletRequest req) {
        //member
        def member_id = Web.firstNumber(req)

        def users = users();
        //leader
        def leader = users.findOne($$(_id, Web.getCurrentUserId()), $$("family", 1))
        if (null == leader)
            return Result.权限不足

        def leader_family = (Map) leader?.get("family")
        def leader_family_id = leader_family?.get("family_id") as Integer
        def myFamily = familyMongo.getCollection('familys').findOne($$(_id, leader_family_id).append("status", StatusType.通过.ordinal()));
        if (null == myFamily)
            return Result.权限不足

        def leader_family_priv = leader_family?.get("family_priv") as Integer
        if (FamilyType.族长.ordinal() != leader_family_priv && FamilyType.副族长.ordinal() != leader_family_priv)
            return Result.权限不足

        def member = users.findOne($$(_id, member_id), $$("family": 1, "pic": 1, "nick_name": 1))
        if (null == member)
            return Result.丢失必需参数
        //不能踢除自己
        if(Web.getCurrentUserId() == member_id){
            return Result.权限不足
        }
        def member_family = (Map) member?.get("family")
        def family_id = member_family?.get("family_id") as Integer
        def member_family_priv = member_family?.get("family_priv") as Integer

        if (family_id == null || leader_family_id != family_id)
            return Result.权限不足

        //副族长不能踢副族长
        if (leader_family_priv >= member_family_priv)
            return Result.权限不足

        if (1 == users.update($$(_id, member_id), $$($unset, $$("family", 1))).getN()) {
            familyMongo.getCollection("members").remove($$(_id,"${family_id}_${member_id}".toString()),writeConcern)

            def tmp = System.currentTimeMillis()
            table().update($$(_id, family_id), $$($set: [lastmodif: tmp], $inc: ['member_count': -1]))
            rooms().update($$(_id, family_id), $$($inc, $$('member_count', -1)))

            if (FamilyType.副族长.ordinal() == member_family_priv.intValue())
                table().update($$(_id, family_id), $$($set: [lastmodif: tmp], $inc: ['leaders.count': -1]))

            refreshMemberCount(family_id);
            //发送消息
            publish(member_id, Web.getCurrentUserId(), "由于你最近经常不在,被请出了家族。别气馁,加个新的家族再战!".toString(), SysMsgType.系统)
            def data = [
                    user_id:member_id,
                    family_id: family_id,
                    pic: member?.get('pic') ?: '',
                    nick_name: member?.get('nick_name') ?: ''
            ]
            RoomMsgPublish.publish2User(member_id, MsgAction.踢出家族, data, Boolean.FALSE)
            Web.refreshUserInfoOfSession(member_id);
            return Result.success
        }
        return Result.error
    }

    /**
     * @apiVersion 0.0.1
     * @apiGroup Family
     * @apiName member_invite
     * @api {post} member/invite/:token/:id  邀请用户
     * @apiDescription
     * 家族族长或者其他管理职位邀请用户加入家族
     *
     * @apiUse USER_COMMEN_PARAM

     * @apiParam {Number} id  邀请的用户id
     *
     * @apiExample { curl } Example usage:
     *     curl -i http://test-aiapi.memeyule.com/member/invite/031928b93e543825298982e06a00796c/1214781
     */
    def invite(HttpServletRequest req){

        def invited_user_id = Web.firstNumber(req)
        Integer userId = Web.getCurrentUserId()
        Integer familyId = Web.getCurrentFamilyId()
        //是否为族长和副族长和执事
        if (!this.check(req))
            return Result.权限不足
        //用户是否没有家族
        def user = users().findOne($$(_id, invited_user_id), UserController.user_info_core_field)
        def userFamily = (Map) user?.get("family")
        if(userFamily != null){
            return  Result.用户已经加入家族
        }
        def family =  familys().findOne($$(_id, familyId).append("status", StatusType.通过.ordinal()))
        if(null == family)
            return Result.家族不存在
        Integer member_count = (family["member_count"] ?:0)as Integer
        if(member_count >= FAMILY_MEMBER_LIMITS){
            return Result.数量超过上限;
        }
        String apply_id = "${userId}_${invited_user_id}_${familyId}".toString()
        if((invites().count($$(_id:apply_id,status:FriApplyStatus.未处理.ordinal())) == 0)
                && invites().update($$(_id:apply_id),
                                     $$($set:$$(user_id:invited_user_id,fid:familyId,inviter_user_id:userId,
                                             status:FriApplyStatus.未处理.ordinal(),timestamp:System.currentTimeMillis()
                                                    )),true,false,writeConcern).getN() == 1){
            //推送消息
            publish(invited_user_id, userId, "${Web.currentUserNick()}诚挚的邀请您加入他的家族".toString(), SysMsgType.家族邀请)
            return Result.success
        }
        return Result.已邀请过此用户
    }

    /**
     * @apiVersion 0.0.1
     * @apiGroup Family
     * @apiName invited_list
     * @api {get} member/invited_list/:token/  家族邀请列表
     * @apiDescription
     * 用户查看家族邀请列表
     *
     * @apiUse USER_COMMEN_PARAM
     *
     * @apiExample { curl } Example usage:
     *     curl -i http://test-aiapi.memeyule.com/member/invited_list/031928b93e543825298982e06a00796c

     * @apiSuccessExample {json} Success-Response:
     *
     *     {
             "data": [
                 {
                     "_id": "1206921_1214781_1206921" 邀请ID,
                     "user_id": 1214781 被邀请用户id,
                     "inviter_user_id": 1206921 发出邀请用户id,
                     "fid": 1206921 家族id,
                     "status": 1 状态 ( 1-未处理 ; 2-通过  ; 3-拒绝),
                     "timestamp": 1495525591116 邀请时间,
                     "name": "家族名",
                     "badge": "https://aiimg.sumeme.com/24/0/1495008843352.png" 家族徽章,
                     "pic": "http://test-aiimg.sumeme.com/1111.jpg" 家族头像
                 }
             ],
             "code": 1
             }
     *
     */
    def invited_list(){
        Integer userId =  Web.getCurrentUserId()
        //,status:FriApplyStatus.未处理.ordinal()
        List invites = invites().find($$(user_id:userId,status:FriApplyStatus.未处理.ordinal())).toArray()
        invites.each { DBObject invite ->
            def family = Web.getFamilyinfo(invite['fid'] as Integer)
            family.removeField('_id');
            invite.putAll(family)
        }
        return [code: Result.success.getCode(), data:invites]
    }

    /**
     * @apiVersion 0.0.1
     * @apiGroup Family
     * @apiName member_accept
     * @api {post} member/accept/:token/:invite_id  用户接受邀请
     * @apiDescription
     * 用户接受家族邀请加入家族
     *
     * @apiUse USER_COMMEN_PARAM

     * @apiParam {String} invite_id  邀请ID (见邀请列表的 _id 字段)
     *
     * @apiExample { curl } Example usage:
     *     curl -i http://test-aiapi.memeyule.com/member/accept/031928b93e543825298982e06a00796c/1206921_1214781_1206921
     */
    def accept(HttpServletRequest req){
        def invited_id = Web.firstParam(req)
        Web.getCurrentFamilyId()
        Integer userId =  Web.getCurrentUserId()
        if(invited_id == null)
            return Result.丢失必需参数
        def invite = invites().findAndModify($$(_id:invited_id,status:FriApplyStatus.未处理.ordinal()), $$(status:FriApplyStatus.通过.ordinal()))
        if(invite == null)
            return Result.丢失必需参数
        Integer familyId =invite['fid'] as Integer
        if(addMember(userId, familyId)){
            publish(invite['inviter_user_id'] as Integer, userId, "${Web.currentUserNick()}已接受家族邀请".toString(), SysMsgType.系统)
            return Result.success
        }
        return  Result.用户已经加入家族
    }

    /**
     * @apiVersion 0.0.1
     * @apiGroup Family
     * @apiName del_invitation
     * @api {post} member/del_invitation/:token/:invite_id  删除家族邀请
     * @apiDescription
     * 删除家族邀请 不传invite_id 则清空用户全部邀请
     *
     * @apiUse USER_COMMEN_PARAM
     * @apiParam {String} invite_id  邀请ID (见邀请列表)
     *
     * @apiExample { curl } Example usage:
     *     curl -i http://test-aiapi.memeyule.com/member/del_invitation/9e9a0d008ff62e6a5230f2eed9cb8299/1206921_1214781_1206921
     */
    def del_invitation(HttpServletRequest req){
        Integer currentId =  Web.getCurrentUserId()
        def invited_id = Web.firstParam(req)
        if(StringUtils.isNotEmpty(invited_id)){
            invites().remove($$(_id: invited_id ))
        }else{
            invites().remove($$(user_id:currentId))
        }
        return Result.success
    }

    private boolean refreshMemberCount(Integer familyId){
        FamilyType[] familyTypes = FamilyType.values()
        Map memberCounts = new HashMap();
        Long memberCount = 0;
        for (int i = 1; i < familyTypes.length ; i++) {
            Long count = users().count($$('family.family_id':familyId,'family.family_priv':familyTypes[i].ordinal()));
            memberCounts[familyTypes[i].getId()+'_count'] = count
            memberCount += count;
        }
        memberCounts[FamilyType.成员.getId()+'_count'] = memberCount
        familys().update($$(_id : familyId), $$($set, memberCounts));
        rooms().update($$(_id, familyId), $$($set, memberCounts));
    }

    private boolean check(HttpServletRequest req) {
        def user = users().findOne($$(_id, Web.getCurrentUserId()), $$("family", 1))
        def family = (Map) user?.get("family")
        if(family == null) return Boolean.FALSE

        def family_priv = family?.get("family_priv") as Integer
        if (FamilyType.族长.ordinal() != family_priv
                && FamilyType.副族长.ordinal() != family_priv
                    && FamilyType.执事.ordinal() != family_priv)
            return Boolean.FALSE

        return Boolean.TRUE
    }

    /**
     * 推送IM通知
     * @param to_id 推送给谁
     * @param from_id 谁发起的
     * @param action
     */
    public void publish(Integer to_id, Integer from_id, String text, SysMsgType sysMsgType){
        SysMsgPushUtil.sendToUser(to_id, text, Boolean.FALSE, sysMsgType);
    }

}
