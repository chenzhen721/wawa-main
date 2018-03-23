package com.wawa.web.friend

import com.mongodb.BasicDBObject
import com.mongodb.DBObject
import com.wawa.api.Web
import com.wawa.api.notify.SysMsgPushUtil
import com.wawa.base.BaseController
import com.wawa.base.anno.RestWithSession
import com.wawa.common.doc.Result
import com.wawa.common.util.KeyUtils
import com.wawa.common.util.Pinyin4jUtil
import com.wawa.model.*
import org.apache.commons.lang.StringUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import javax.servlet.http.HttpServletRequest

import static com.wawa.common.util.WebUtils.$$
import static com.wawa.common.doc.MongoKey.*

/**
 * 好友相关接口
 */
@RestWithSession
class FriendController extends BaseController{

    static final  Logger logger = LoggerFactory.getLogger(FriendController.class)
    static final DBObject user_info_field = $$("nick_name",1)
                                                        .append("pic",1)
                                                        .append("priv",1)
                                                        .append("signature",1)
                                                        .append("level",1);

    /**
     * @apiVersion 0.0.1
     * @apiGroup Friend
     * @apiName Friend_search
     * @api {post} friend/search/:uid  搜索用户
     * @apiDescription
     * 搜索用户
     *
     * @apiUse USER_COMMEN_PARAM
     * @apiParam {Integer} uid    用户ID
     *
     * @apiExample { curl } Example usage:
     *     curl -i http://test-aiapi.memeyule.com/friend/search/9e9a0d008ff62e6a5230f2eed9cb8299/1207893
     *
     *@apiSuccessExample {json} Success-Response:
     *   {
             "data": {
                     "_id": 1214781, 用户ID
                     "pic": "https://aiimg.sumeme.com/45/5/1487645253037.png", 头像
                     "nick_name": "haha", 昵称
                     "signature": "oh yeah" 用户签名,
                     "is_friend" : true,是否已经是好友
                     "level": 0, 用户等级
             }
             "code": 1
         }
     */
    def search(HttpServletRequest req){
        Integer currentId =  Web.getCurrentUserId()
        Integer user_id = Web.firstNumber(req)
        if(user_id <= 0){
            return Result.丢失必需参数
        }
        def user = users().findOne($$(_id,user_id),user_info_field)
        if (user ==  null){
            user = users().findOne($$('mm_no',user_id),user_info_field)
        }
        if (user ==  null){
            return Result.只能加普通用户为好友
        }
        user_id = user.get(_id) as Integer
        if (user_id  == currentId){
            return Result.不能添加自己为好友
        }
        user.put("is_friend", isFriend(currentId, user_id))
        [code: Result.success.code, data:user]
    }

    /**
     * @apiVersion 0.0.1
     * @apiGroup Friend
     * @apiName Friend_list
     * @api {get} friend/list  好友列表
     * @apiDescription
     * 用户好友列表
     *
     * @apiUse USER_COMMEN_PARAM
     *
     *
     * @apiExample { curl } Example usage:
     *     curl -i http://test-aiapi.memeyule.com/friend/list/9e9a0d008ff62e6a5230f2eed9cb8299
     *
     *
     * @apiSuccessExample {json} Success-Response:
     *  {
         "data": [
                     {
                         "_id": 1214781, 用户ID
                         "pic": "https://aiimg.sumeme.com/45/5/1487645253037.png", 头像
                         "nick_name": "haha", 昵称
                         "signature": "oh yeah" 用户签名,
                         "level": 0, 用户等级
                         "priv": 3,
                         "pinyin_name": "haha",
                         "online":true/false 是否在线
                     }
                ]
         ,
         "code": 1
     }
     */
    def list(){
        Set<String> sets =  mainRedis.opsForSet().members(KeyUtils.USER.friends(Web.getCurrentUserId()));
        if(sets==null){
            return [code:1,data:[]];
        }
        List<Integer> ids = new ArrayList<Integer>(sets.size());
        for(String id : sets){
            ids.add(Integer.valueOf(id));
        }
        if ( ids == null || ids.isEmpty() ){
            return [code:1,data:[]]
        }
        def userList = users().find($$(_id:new BasicDBObject($in,ids)),
                user_info_field).limit(200).toArray()
        userList.each { DBObject user ->
            if(user['nick_name'])
                user['pinyin_name'] = Pinyin4jUtil.getPinYin(user['nick_name'] as String)

            user.put("online", Web.isOnline(user[_id] as Integer))
        }
        [code: 1, data: userList]
    }

    /**
     * @apiVersion 0.0.1
     * @apiGroup Friend
     * @apiName is_friend
     * @api {get} friend/is_friend/:token/:uid  是否为好友
     * @apiDescription
     * 是否为好友
     *
     * @apiUse USER_COMMEN_PARAM
     * @apiParam {Integer} uid    用户ID
     * @apiExample { curl } Example usage:
     *     curl -i http://test-aiapi.memeyule.com/friend/is_friend/9e9a0d008ff62e6a5230f2eed9cb8299/1239999
     */
    def is_friend(HttpServletRequest req){
        Integer user_id = Web.firstNumber(req)
        Integer currentId =  Web.getCurrentUserId()
        //logger.debug("is_friend  user_id: {}, currentId : {}", user_id, currentId)
        [ code : 1, data:[friend:isFriend(currentId, user_id)]]
    }

    private final static String APPLY_CONTENT = "我是";

    /**
     * @apiVersion 0.0.1
     * @apiGroup Friend
     * @apiName friend_apply
     * @api {post} friend/apply/:token/:uid  申请好友
     * @apiDescription
     * 申请好友
     *
     * @apiUse USER_COMMEN_PARAM
     * @apiParam {Integer} uid    用户ID
     *
     * @apiExample { curl } Example usage:
     *     curl -i http://test-aiapi.memeyule.com/friend/apply/9e9a0d008ff62e6a5230f2eed9cb8299/1239999
     */
    def apply(HttpServletRequest req){
        Integer user_id = Web.firstNumber(req)
        Integer currentId = Web.getCurrentUserId()

        String content = APPLY_CONTENT + Web.currentUserNick();
        if (user_id  == currentId){
            return Result.不能添加自己为好友
        }
        if(isFriend(currentId, user_id))
            return Result.已经为好友

        def friendUser = users().findOne($$(_id: user_id), $$(friend_setting: 1))
        if(!friendUser) {
            return Result.丢失必需参数
        }
        Integer friendSetting = friendUser.get("friend_setting") as Integer ?: 0

        //是否设置拒绝加入任何人为好友
        if (friendSetting == FriendSetting.NOBODY.ordinal()) {
            return Result.对方设置不被任何人添加为好友
        }
        //是否在好友黑名单中
        def friends = friendMongo.getCollection('friends')
        if(friends.count($$(_id:user_id, 'blacklist': currentId)) > 0){
            return Result.对方已加入你到黑名单
        }
        //好友申请限制
        if(!apply_limit(currentId)){
            return Result.每天好友申请次数超过限制
        }
        addApply(user_id, currentId, content)
        return Result.success
    }

    /**
     * 添加好友申请
     * @param toId，对方id
     * @param fromId, 申请人id
     * @param content，附加消息
     */
    public addApply(int toId, int fromId, String content) {
        String apply_id = "${toId}_${fromId}".toString()
        def applys = friendMongo.getCollection('applys')
        if((applys.count($$(_id:apply_id,status:FriApplyStatus.未处理.ordinal())) == 0)
                && applys.update($$(_id:apply_id),
                $$($set:$$(uid:fromId,fid:toId,content:content,
                        status:FriApplyStatus.未处理.ordinal(),
                        timestamp:System.currentTimeMillis()
                )),true,false,writeConcern).getN()
                == 1){
            mainRedis.opsForValue().setIfAbsent(KeyUtils.USER.friends_apply_flag(toId.toString()), "1")
            publish(toId, "${Web.currentUserNick()}请求添加你为好友".toString(), fromId)
        }
    }

    /**
     * 1天内超过50条申请
     * @param user_id
     * @return
     */
    private Boolean apply_limit(Integer user_id){
        def redisUidKey = KeyUtils.USER.friendApplyLimit(user_id)//Web.currentUserId())//
        def valueOp = mainRedis.opsForValue()
        if(valueOp.setIfAbsent(redisUidKey,"50")){
            mainRedis.expireAt(redisUidKey, (new Date()+1).clearTime() )
        }
        return valueOp.increment(redisUidKey,-1)>=0;
    }

    /**
     * @apiVersion 0.0.1
     * @apiGroup Friend
     * @apiName apply_list
     * @api {get} friend/apply_list  申请列表
     * @apiDescription
     * 好友申请列表
     *
     * @apiUse USER_COMMEN_PARAM
     *
     * @apiExample { curl } Example usage:
     *     curl -i http://test-aiapi.memeyule.com/friend/apply_list/9e9a0d008ff62e6a5230f2eed9cb8299
     *
     *
     @apiSuccessExample {json} Success-Response:
      *
      {
          "data": [
                  {
                  "_id": 1206921,
                  "uid": 1206921,
                  "fid": 1214781,
                  "content": "我是木子李哈哈qwert",
                  "status": 1, (1:未处理, 2:通过, 3:拒绝)
                  "timestamp": 1495000737848,
                  "pic": "https://aiimg.sumeme.com/45/5/1487645253037.png",
                  "nick_name": "木子李哈哈qwert",
                  "priv": 3
                  }
            ],
          "code": 1
      }
      *
     */
    def apply_list(HttpServletRequest req){
        Integer currentId =  Web.getCurrentUserId()
        //,status:FriApplyStatus.未处理.ordinal()
        List applys = friendMongo.getCollection('applys').find($$(fid:currentId)).toArray()
        applys.each { DBObject apply ->
            def user = users().findOne(apply['uid'] as Integer, user_info_field)
            apply.putAll(user)
        }
        return [code: Result.success.getCode(), data:applys]
    }

    /**
     * @apiVersion 0.0.1
     * @apiGroup Friend
     * @apiName refuse
     * @api {post} friend/refuse/:token/:uid  拒绝好友申请
     * @apiDescription
     * 拒绝好友申请
     *
     * @apiUse USER_COMMEN_PARAM
     * @apiParam {Integer} uid    用户ID
     *
     * @apiExample { curl } Example usage:
     *     curl -i http://test-aiapi.memeyule.com/friend/refuse/9e9a0d008ff62e6a5230f2eed9cb8299/12012333
     */
    def refuse(HttpServletRequest req){
        if(handle_apply(req, FriApplyStatus.拒绝.ordinal())){
            return Result.success
        }
        return Result.error
    }

    /**
     * @apiVersion 0.0.1
     * @apiGroup Friend
     * @apiName agree
     * @api {post} friend/agree/:token/:uid   同意好友申请
     * @apiDescription
     * 同意好友申请
     *
     * @apiUse USER_COMMEN_PARAM
     * @apiParam {Integer} uid    用户ID
     *
     * @apiExample { curl } Example usage:
     *     curl -i http://test-aiapi.memeyule.com/friend/agree/9e9a0d008ff62e6a5230f2eed9cb8299/12012333
     */
    def agree(HttpServletRequest req){
        //是否在好友黑名单中
        def friends = friendMongo.getCollection('friends')
        Integer user_id = Web.firstNumber(req)
        if(friends.count($$(_id:Web.getCurrentUserId(), 'blacklist': user_id)) > 0){
            return Result.对方已加入你到黑名单
        }
        if(handle_apply(req, FriApplyStatus.通过.ordinal())){
            return doFriends(req,true)
        }
        return Result.error

    }

    private Boolean handle_apply(HttpServletRequest req, Integer status){
        Integer currentId =  Web.getCurrentUserId()
        Integer user_id = Web.firstNumber(req)
        String apply_id = "${currentId}_${user_id}".toString()
        logger.debug("handle_apply apply_id {}", apply_id)
        def applys = friendMongo.getCollection('applys')
        return applys.update($$(_id:apply_id),
                $$($set:$$(status:status,
                        timestamp:System.currentTimeMillis()
                )),false,false,writeConcern).getN() == 1
    }

    /**
     * @apiVersion 0.0.1
     * @apiGroup Friend
     * @apiName del_apply
     * @api {post} friend/del_apply/:token/:uid   删除好友申请
     * @apiDescription
     * 删除好友申请
     *
     * @apiUse USER_COMMEN_PARAM
     * @apiParam {Integer} uid    用户ID
     * @apiExample { curl } Example usage:
     *     curl -i http://test-aiapi.memeyule.com/friend/del_apply/9e9a0d008ff62e6a5230f2eed9cb8299/12012333
     */
    def del_apply(HttpServletRequest req){
        Integer currentId =  Web.getCurrentUserId()
        def uid = Web.firstParam(req)
        if(StringUtils.isNotEmpty(uid)){
            friendMongo.getCollection('applys').remove($$(uid: uid as Integer, fid : currentId))
        }else{
            friendMongo.getCollection('applys').remove($$(fid:currentId))
        }

        return Result.success
    }

    /**
     * @apiVersion 0.0.1
     * @apiGroup Friend
     * @apiName delete
     * @api {post} friend/delete/:token/:uid  删除好友
     * @apiDescription
     * 删除好友
     *
     * @apiUse USER_COMMEN_PARAM
     * @apiParam {Integer} uid    好友用户ID
     * @apiExample { curl } Example usage:
     *     curl -i http://test-aiapi.memeyule.com/friend/delete/9e9a0d008ff62e6a5230f2eed9cb8299/12031112
     */
    def delete(HttpServletRequest req){
        doFriends(req,false)
    }

    //好友上限
    private final static Long FRIEND_LIMIT = 200

    private doFriends(HttpServletRequest req ,boolean add){
        Integer user_id = Web.firstNumber(req)
        Integer currentId = Web.getCurrentUserId()
        friends(currentId, user_id, add)
    }

    private Boolean isFriend(Integer currentId, Integer user_id){
        return  mainRedis.opsForSet().isMember(KeyUtils.USER.friends(currentId),user_id.toString())
    }

    /**
     * 添加好友
     * @param currentId
     * @param user_id
     * @param add
     * @return
     */
    public friends(Integer currentId, Integer user_id, boolean add) {
        if (user_id  == 0 || currentId == 0){
            return Result.丢失必需参数
        }
        if (user_id  == currentId){
            return [code: Result.不能添加自己为好友, msg: "不能添加/删除自己"]
        }
        def setsOp =  mainRedis.opsForSet()
        def friends = friendMongo.getCollection('friends')
        if (add){
            def users = mainMongo.getCollection("users")
            def user = users.findOne(new BasicDBObject(_id,user_id),user_info_field)
            if (user == null){
                return Result.丢失必需参数
            }
            //好友上限
            if(setsOp.size(KeyUtils.USER.friends(currentId.toString())) >= FRIEND_LIMIT ||
                    setsOp.size(KeyUtils.USER.friends(user_id.toString())) >= FRIEND_LIMIT){
                return [code: Result.好友已超过上限, msg: "好友已超过上限"]
            }
            setsOp.add(KeyUtils.USER.friends(currentId),user_id.toString())
            setsOp.add(KeyUtils.USER.friends(user_id),currentId.toString())

            friends.update($$([_id:user_id]), $$($addToSet, $$("friends", currentId)), true, false)
            friends.update($$([_id:currentId]), $$($addToSet, $$("friends", user_id)), true, false)

            //发送好友通过消息
            //friendMsgController.send_msg(currentId,user_id, "${user['nick_name']}已通过了你的好友申请，你们可以愉快的聊天啦".toString())
            //publish(user_id, currentId,'system.friend.accept')
        }else{
            setsOp.remove(KeyUtils.USER.friends(currentId),user_id.toString())
            setsOp.remove(KeyUtils.USER.friends(user_id),currentId.toString())

            friends.update($$([_id:user_id]), $$($pull, $$("friends", currentId)), true, false)
            friends.update($$([_id:currentId]), $$($pull, $$("friends", user_id)), true, false)
            //publish(user_id, currentId,'system.friend.del')
        }
        Result.success
    }

    /**
     * @apiVersion 0.0.1
     * @apiGroup Friend
     * @apiName blacklist
     * @api {post} friend/blacklist/:token  黑名单列表
     * @apiDescription
     * 黑名单列表
     *
     * @apiUse USER_COMMEN_PARAM
     *
     * @apiExample { curl } Example usage:
     *     curl -i http://test-aiapi.memeyule.com/friend/blacklist/9e9a0d008ff62e6a5230f2eed9cb8299
     */
    def blacklist(HttpServletRequest req) {
        Integer user_id = Web.getCurrentUserId()
        def friends = friendMongo.getCollection('friends')
        def blacklist = friends.findOne(user_id, $$("blacklist", 1))?.get("blacklist") as List

        if (blacklist == null || blacklist.isEmpty() ){
            return [code:1,msg:'ok',data:[:]]
        }
        def userList = users().find($$(_id:new BasicDBObject($in,blacklist)),
                user_info_field).limit(200).toArray()

        [code: 1,data: [users:userList]]
    }

    /**
     * @apiVersion 0.0.1
     * @apiGroup Friend
     * @apiName add_blacklist
     * @api {post} friend/add_blacklist/:token/:uid  添加黑名单
     * @apiDescription
     * 添加黑名单
     *
     * @apiUse USER_COMMEN_PARAM
     * @apiParam {Integer} uid    用户ID
     * @apiExample { curl } Example usage:
     *     curl -i http://test-aiapi.memeyule.com/friend/add_blacklist/9e9a0d008ff62e6a5230f2eed9cb8299/12031112
     */
    def add_blacklist(HttpServletRequest req) {
        doBlacklist(req, true)
        //doFriends(req,false)
    }

    /**
     * @apiVersion 0.0.1
     * @apiGroup Friend
     * @apiName del_blacklist
     * @api {post} friend/del_blacklist/:token/:uid  删除黑名单
     * @apiDescription
     * 删除黑名单
     *
     * @apiUse USER_COMMEN_PARAM
     * @apiParam {Integer} uid    用户ID
     * @apiExample { curl } Example usage:
     *     curl -i http://test-aiapi.memeyule.com/friend/del_blacklist/9e9a0d008ff62e6a5230f2eed9cb8299/12031112
     */
    def del_blacklist(HttpServletRequest req) {
        doBlacklist(req, false)
    }

    private doBlacklist(HttpServletRequest req, boolean add) {
        Integer user_id = Web.firstNumber(req)
        Integer currentId = Web.getCurrentUserId()
        String nick_name = users().findOne(user_id, $$("nick_name", 1))?.get("nick_name")
        if (null == nick_name) {
            return Result.丢失必需参数
        }
        def friends = friendMongo.getCollection('friends')
        if (add) {
            if(friends.count($$(_id : currentId,'blacklist':user_id)) > 0){
                return Result.已加入黑名单
            }
            if (friends.update($$(_id, currentId), $$($addToSet, $$("blacklist", user_id)),true, false, writeConcern).getN() > 0) {
                //publish(user_id, 'friend.add_blacklist')
            } else {
                return Result.error
            }
        } else if (friends.update($$(_id, currentId), new BasicDBObject($pull, new BasicDBObject("blacklist", user_id))).getN() > 0) {

        }
        Result.success
    }

    /**
     * 推送IM通知
     * @param to_id 推送给谁
     * @param from_id 谁发起的
     * @param action
     */
    public void publish(Integer to_id, String content, Integer from_id){
        SysMsgPushUtil.sendToUser(to_id, content, Boolean.TRUE, SysMsgType.好友);
    }


}
