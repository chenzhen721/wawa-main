package com.wawa.service.weixin.util;

import org.apache.commons.codec.binary.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;


/**
 * Created by monkey on 2016/11/21.
 */
public class AesUtil {

    private String sessionKey;

    public AesUtil(String sessionKey) {
        super();
        this.sessionKey = sessionKey;
    }

    public String decryptData(String content, String iv) {
        String res = "";
        try {
            // 设置解密模式为AES的CBC模式
            Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
            SecretKeySpec key_spec = new SecretKeySpec(Base64.decodeBase64(this.sessionKey), "AES");
            IvParameterSpec wiv = new IvParameterSpec(Base64.decodeBase64(iv));
            // 用于微信小程序
            cipher.init(Cipher.DECRYPT_MODE, key_spec, wiv);
            // 使用BASE64对密文进行解码ß
            byte[] encrypted = Base64.decodeBase64(content);
            System.out.println("encrypted is " + new String(encrypted));
            // 解密
            byte[] original = cipher.doFinal(encrypted);
            byte[] bytes = PKCS7Encoder.decode(original);
            res = new String(bytes, "utf-8");
        } catch (Exception e) {
            e.printStackTrace();
        }
        return res;
    }

    /**
     * 代码中的测试数据和微信小程序官方提供的一致
     *
     * @param args
     */
    public static void main(String[] args) {
//        String sessionKey = "XpLOcJG2NDahnQG+nkwhSQ==";
//        AesUtil util = new AesUtil(sessionKey);
//        String iv = "TDZ885OovRLV/f3EmNEF8g==";
//        String e = util.decryptData("bNE+rYA26KEXtD3XjqLEmaRSVOGshn5/Oc1zu88sRuNWCZhbuoHJjopaQ4JvOcDNmA2jljlZ6CcIxjIT8QiQiwqfwsOc9WGJDbE3zsiw5iaoMc2FoNP0aEdeXm0yI/tYEnJNP0Nr5mKcR6qu8wzilQ07WbP8A6Nk75T9tSSAG4G9Orfk7lZWlDHBaCB2cwpHt+AXX3GizcSqk77/loFI1JT1GFu2aixz9Fe2hdl1oIx078v4nn+Z9iU7aiOIgEbm2CCXpX37kPnb/5LcR41uejPlRqbDfqDvsdhT7C4jDd/ZnTYD9Ztr5KlYV/6ra+6vUPTzmkaHtNdJsWb8x0BA/LjvxI3ULzXfRnmhwipzmqJ9gGiZT2rBUR1uaiHcrCw4qfD6ovdLBGV71TiS/2KuzHmod9pWhJCc4Bxz8mLJNY7uKRNIeAZrHT1Lim0+9KoNLifepfn8ueZYrskxFmNyVRFt69HtIjeAoqdRN8zo5YQ=", iv);
//        System.out.println(e);
        String a= "";
        Long z = 0L;
        Boolean flag = paramsEmpty(a,z);
        System.out.println("flag is " + flag);
        /**
         * {"openId":"oGZUI0egBJY1zhBYw2KhdUfwVJJE","nickName":"Band","gender":1,"language":"zh_CN",
         * "city":"Guangzhou","province":"Guangdong","country":"CN",
         * "avatarUrl":"http://wx.qlogo.cn/mmopen/vi_32/aSKcBBPpibyKNicHNTMM0qJVh8Kjgiak2AHWr8MHM4WgMEm7GFhsf8OYrySdbvAMvTsw3mo8ibKicsnfN5pRjl1p8HQ/0",
         * "unionId":"ocMvos6NjeKLIBqg5Mr9QjxrP1FA","watermark":{"timestamp":1477314187,"appid":"wx4f4bc4dec97d474b"}}
         *
         */
    }

    public static Boolean paramsEmpty(Object... params) {
        System.out.println("params length is " + params.length);
//        for (int i = 0; i < params.length; i++) {
//            Object tmp = params[i];
//            if (tmp == null) {
//                return true;
//            }
//            if (tmp instanceof String) {
//                return StringUtils.isBlank(tmp.toString());
//            }
//            if (tmp instanceof Long) {
//                return tmp == 0L;
//            }
//            if (tmp instanceof Integer) {
//                return tmp == 0;
//            }
//            if (tmp instanceof Double) {
//                return tmp == 0D;
//            }
//        }
        return false;
    }
}
