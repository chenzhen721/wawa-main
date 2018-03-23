package com.wawa.common.doc;

/**
 * 接口请求返回状态码
 */
public interface Result {
    public static final IMessageCode success = ResultCode.build(1, "success");
    public static final IMessageCode error = ResultCode.build(0, "error");
    public static final IMessageCode 余额不足 = ResultCode.build(30412, "余额不足");
    public static final IMessageCode 权限不足 = ResultCode.build(30413, "权限不足");
    public static final IMessageCode 丢失必需参数 = ResultCode.build(30406, "丢失必需参数");
    public static final IMessageCode TOKEN无效 = ResultCode.build(30405, "ACCESS_TOKEN无效");
    public static final IMessageCode 用户经验不足 = ResultCode.build(30420, "用户经验不足");
    public static final IMessageCode 用户未绑定手机号 = ResultCode.build(30414, "用户未绑定手机号");
    public static final IMessageCode 生成支付订单失败 = ResultCode.build(30415, "生成支付订单失败");
    public static final IMessageCode 非法图片 = ResultCode.build(30510, "非法图片");
    public static final IMessageCode 邀请码无效 = ResultCode.build(30610, "邀请码无效");
    public static final IMessageCode 已添加邀请码 = ResultCode.build(30611, "已添加邀请码");
    public static final IMessageCode 管理员禁用 = ResultCode.build(30612, "该功能已被管理员禁用");
    public static final IMessageCode 活动未开始 = ResultCode.build(30415, "活动未开始");
    public static final IMessageCode 活动已结束 = ResultCode.build(30416, "活动已结束");

    public static final IMessageCode 设备维护中 = ResultCode.build(34001, "设备维护中");
    public static final IMessageCode 其他人正在游戏中 = ResultCode.build(34002, "其他人正在游戏中");
    public static final IMessageCode 您正在其它房间游戏中 = ResultCode.build(34013, "您正在其它房间游戏中");
    public static final IMessageCode 未填写地址 = ResultCode.build(34003, "未填写地址");
    public static final IMessageCode 小于最少邮寄数量 = ResultCode.build(34004, "小于最少邮寄数量");
    public static final IMessageCode 未选择默认地址 = ResultCode.build(34005, "未选择默认地址");
    public static final IMessageCode 刷新用户信息失败 = ResultCode.build(34006, "刷新用户信息失败");
    public static final IMessageCode 创建用户失败 = ResultCode.build(34007, "创建用户失败");
    public static final IMessageCode 用户信息获取失败 = ResultCode.build(34008, "用户信息获取失败");
    public static final IMessageCode 游戏记录创建失败 = ResultCode.build(34009, "游戏记录创建失败");
    public static final IMessageCode 状态切换冷却中 = ResultCode.build(34010, "状态切换冷却中");
    public static final IMessageCode 用户已在其他房间排队 = ResultCode.build(34011, "用户已在其他房间排队");
    public static final IMessageCode 房间排队人数已满 = ResultCode.build(34012, "房间排队人数已满");

    //好友相关
    public static final IMessageCode 不能添加自己为好友 = ResultCode.build(31001, "不能添加自己为好友");
    public static final IMessageCode 只能加普通用户为好友 = ResultCode.build(31002, "只能加普通用户为好友");
    public static final IMessageCode 已经为好友 = ResultCode.build(31003, "已经为好友");
    public static final IMessageCode 好友已超过上限 = ResultCode.build(31005, "好友已超过上限");
    public static final IMessageCode 对方已加入你到黑名单 = ResultCode.build(31006, "对方已加入你到黑名单");
    public static final IMessageCode 已加入黑名单 = ResultCode.build(31007, "已加入黑名单");
    public static final IMessageCode 对方设置不被任何人添加为好友 = ResultCode.build(31008, "对方设置不被任何人添加为好友");
    public static final IMessageCode 每天好友申请次数超过限制 = ResultCode.build(31009, "每天好友申请次数超过限制");

    //家族相关
    public static final IMessageCode 家族不存在 = ResultCode.build(32000, "家族不存在");
    public static final IMessageCode 用户已经加入家族 = ResultCode.build(32002, "用户已经加入家族");
    public static final IMessageCode 用户已经提交申请 = ResultCode.build(32005, "用户已经提交申请");
    public static final IMessageCode 族长不能退出家族 = ResultCode.build(32006, "族长不能退出家族");
    public static final IMessageCode 数量超过上限 = ResultCode.build(32007, "数量超过上限");
    public static final IMessageCode 非本家族成员 = ResultCode.build(32008, "非本家族成员");
    public static final IMessageCode 已邀请过此用户 = ResultCode.build(32009, "已邀请过此用户");

    //现金
    public static final IMessageCode 微信号已绑定其它账户 = ResultCode.build(35003, "微信号已绑定其它账户");
    public static final IMessageCode 用户等级不足 = ResultCode.build(35005, "用户等级不足");

    //个人游戏
    public static final IMessageCode 不能对自己使用 = ResultCode.build(36001, "不能对自己使用");
    public static final IMessageCode 道具数量不足 = ResultCode.build(36002, "道具数量不足");

    //红包相关
    public static final IMessageCode 已领取过 = ResultCode.build(30101, "已领取过");
    public static final IMessageCode 已达领取上限 = ResultCode.build(30102, "已达领取上限");
    public static final IMessageCode 红包已抢光 = ResultCode.build(30103, "红包已抢光");
    public static final IMessageCode 抢的人太多了 = ResultCode.build(30104, "抢的人太多了，请重试");
    public static final IMessageCode 红包已过期 = ResultCode.build(30105, "红包过期，剩余钻石退回");

    //商城
    public static final IMessageCode 商品数量不足 = ResultCode.build(30201, "商品数量不足");
    public static final IMessageCode 暂未开放购买 = ResultCode.build(30202, "暂未开放购买");

    //邮寄相关
    public static final IMessageCode 暂无快递信息 = ResultCode.build(30416, "暂无快递信息");
    public static final IMessageCode 无法下单 = ResultCode.build(30417, "暂无法下单，请联系管理员");
}

