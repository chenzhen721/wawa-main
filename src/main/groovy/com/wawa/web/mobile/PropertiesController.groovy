package com.wawa.web.mobile

import com.wawa.base.BaseController
import com.wawa.base.anno.Rest
import com.wawa.common.util.JSONUtil
import com.wawa.common.util.KeyUtils
import com.wawa.model.AppPropType
import org.apache.commons.lang.StringUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import static com.wawa.common.util.WebUtils.$$

/**
 * 手机APP资源列表
 * date: 13-2-28 下午4:04
 */
@Rest
class PropertiesController extends BaseController {

    Logger logger = LoggerFactory.getLogger(PropertiesController.class)

    /**
     * 安卓资源配置列表
     */
    def list(){
        String props_key = KeyUtils.all_props(AppPropType.android.ordinal())
        String json =  mainRedis.opsForValue().get(props_key)
        if(StringUtils.isBlank(json))
        {
            def lst = adminMongo.getCollection("properties").find($$(type:AppPropType.android.ordinal()),$$(timestamp:0,desc:0,type:0)).toArray()
            def result = [code: 1, data:lst]
            json = JSONUtil.beanToJson(result)
            mainRedis.opsForValue().set(props_key,json)
            return  result
        }
        return  JSONUtil.jsonToMap(json)
    }

    /**
     * ios资源配置列表
     * @return
     */
    def ios_list(){
        String props_key = KeyUtils.all_props(AppPropType.ios.ordinal())
        String json =  mainRedis.opsForValue().get(props_key)
        if(StringUtils.isBlank(json))
        {
            def lst =adminMongo.getCollection("properties").find($$(type:AppPropType.ios.ordinal()),$$(timestamp:0,desc:0,type:0)).toArray()
            def result = [code: 1, data:lst]
            json = JSONUtil.beanToJson(result)
            mainRedis.opsForValue().set(props_key,json)
            return  result
        }
        return  JSONUtil.jsonToMap(json)
    }

    /**
     * Ios列表
     * 118元8260柠檬 com.xingai.meme.coin8260   118   
     6元420柠檬 com.xingai.meme.coin420 6   
     223元15610柠檬 com.xingai.meme.coin15610  223   
     12元840柠檬 com.xingai.meme.coin840   12   
     308元21560柠檬 com.xingai.meme.coin21560  308   
     46元4760柠檬 com.xingai.meme.coin4760 46   
     30元2100柠檬 com.xingai.meme.coin2100  30   
     648元45360柠檬 com.xingai.meme.coin45360   648
     *
     */
    def ios(){
        List<Map<Object, Object>> price_list = new ArrayList<Map<Object, Object>>(10)
        price_list.add([desc:'8260柠檬',key:'com.xingai.meme.coin8260',price:118] as Map)
        price_list.add([desc:'420柠檬',key:'com.xingai.meme.coin420',price:6] as Map)
        price_list.add([desc:'15610柠檬',key:'com.xingai.meme.coin15610',price:223] as Map)
        price_list.add([desc:'840柠檬',key:'com.xingai.meme.coin840',price:12] as Map)
        price_list.add([desc:'21560柠檬',key:'com.xingai.meme.coin21560',price:308] as Map)
        price_list.add([desc:'4760柠檬',key:'com.xingai.meme.coin4760',price:46] as Map)
        price_list.add([desc:'2100柠檬',key:'com.xingai.meme.coin2100',price:30] as Map)
        price_list.add([desc:'45360柠檬',key:'com.xingai.meme.coin45360',price:648] as Map)
        return [code : 1, data:[price_list:price_list]]
    }


}
