package com.wawa.service.shum;

import com.wawa.common.util.HttpClientUtils;
import com.wawa.common.util.JSONUtil;
import com.wawa.common.util.SecurityUtils;
import com.wawa.common.util.http.HttpClientUtil;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * 数盟设备防作弊检测
 * Date: 2016/6/23 10:06
 */
public class ShumengUtils {

    public  static final Logger logger = LoggerFactory.getLogger(ShumengUtils.class);

    private final static String API_URL = "http://diq.shuzilm.cn/q";

    private final static String VAIL_CODE = "100";

    private final static String ERROR_CODE1 = "601"; //601 - 虚拟机
    private final static String ERROR_CODE2 = "600";//600 - MAC 异常指目标设备的重要数据MAC地址里非正常状态，如缺失、不全、黑名单等，需要注意的是MAC异常对渠道而言是正常现象，但比例比较低，导致正常设备的MAC异常的可能原因是：1设备在运营商网络环境下，并且WIFI开关是关闭的状态的时候有可能会获取不到MAC信息，这时我们会优先记录为MAC异常。2同一批手机在出厂的时候会使用相同的芯片和相同的MAC，这时我们也会优先记录为MAC异常。MAC异常不作为快速渠道质量判别的标准和依据，但出现明显偏差时，需要核对数据
    private final static String ERROR_CODE3 = "-1"; //-1 - 设备状态异常如：查询的设备没有集成成功或者请求被劫持

    public static Boolean chkUser(String did, String pkg){
        Map<String, String> params = new HashMap<>();
        params.put("did", did);
        params.put("pkg", pkg);
        try{
            Long begin = System.currentTimeMillis();
            //String resp = HttpUtils.sentPost(API_URL, JSONUtil.beanToJson(params));
            String resp = HttpClientUtils.get(API_URL+"?did="+did+"&pkg="+pkg, null);

            if(StringUtils.isNotEmpty(resp)){
                Map result = JSONUtil.jsonToMap(resp);
                String code = result.get("result").toString();
                if(code.equals(VAIL_CODE)){
                    return Boolean.TRUE;
                }else if(code.equals(ERROR_CODE1) || code.equals(ERROR_CODE3)){
                    return Boolean.FALSE;
                }
            }
            logger.error("chkUser debug params : {} resp : {}", params, resp);
            HttpClientUtil.get(API_URL+"?did="+did+"?pkg="+pkg, null);
        }catch (Exception e){
            logger.error("chkUser Exception : {}", e);
            return Boolean.FALSE;
        }
        return Boolean.FALSE;
    }



    public static void main(String args[]){
        String decStr = SecurityUtils.RC4.decrypt("7ae08309abdbb46e37bcb0dfec153d0f677bbf3e42491495586ae5f8dd030008f7c86576f38b237e822cfe520e8328f29969b62bfaacf107903215f54849480068709c05c71c9c99fa0b7453499df593c43fa7a787d795b8dced84050a48369c2dbd065a8b03d5a9bd7599c2f8782deeb9d582cead08f1426f8c92425afb6400213b9bd760d3e946f905965ac67ad04fcb51ed1649c7aea32ed59174");

        String[] strs = StringUtils.split(decStr, ',');
        //String did = new String(Base64.decodeBase64(strs[0]));
        String did = new String("DuEW2aT1mgQoY6w9QdKiom+Ki4KQslTRNVIljVyU+XRB/YSxnnfzm9sZ5FgPEqreM41oWXyqh/S6ccPeCX1wgzHg");
        //String pkg = strs[1];
        String pkg = strs[1];
        String time = strs[2];
        System.out.println(did + "  " + pkg);
        chkUser(did, pkg);
    }

}
