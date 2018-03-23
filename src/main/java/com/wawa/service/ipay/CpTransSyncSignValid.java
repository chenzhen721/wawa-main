package com.wawa.service.ipay;


import com.wawa.common.util.MsgDigestUtil;

import java.math.BigInteger;

/**
 * Desc:cp交易同步签名验证 date:2012/12/14
 */
public final class CpTransSyncSignValid {
    static final String IPAY_KEY = "Njc0ODg4NzVDRERENTQwM0E0QTUyRkVBOTk5RTFFQTVCNkU5NjgzMU1UYzFNVEExTkRjMk9EYzVNemMxTWpjNU5qRXJNalEwTURFNE9UUTBOamt5TURnMU56QTROekEzTVRJek5URXhOemt5TlRVMU5USTVOemd4";

    /**
     * desc:生成密钥
     *
     * @param transdata
     *            需要加密的数据，如{"appid":"1","exorderno":"2"}
     * @param key
     *            应用的密钥(商户可从商户自服务系统获取)
     * @return
     */
    public static String genSign(String transdata, String key) {
        String sign = "";
        try {
            // 获取privatekey和modkey
            String decodeBaseStr = Base64.decode(key);

            String[] decodeBaseVec = decodeBaseStr.replace('+', '#').split("#");

            String privateKey = decodeBaseVec[0];
            String modkey = decodeBaseVec[1];

            // 生成sign的规则是先md5,再rsa
            String md5Str = MD5.md5Digest(transdata);

            sign = RSAUtil.encrypt(md5Str, new BigInteger(privateKey),
                    new BigInteger(modkey));

        } catch (Exception e) {
            e.printStackTrace();
        }
        return sign;

    }

    /**
     *
     * @param transdata
     *            同步过来的transdata数据
     * @param sign
     *            同步过来的sign数据
     *            应用的密钥(商户可从商户自服务系统获取)
     * @return 验证签名结果 true:验证通过 false:验证失败
     */
    public static boolean validSign(String transdata, String sign) {
        try {
            String md5Str = MD5.md5Digest(transdata);

            String decodeBaseStr = Base64.decode(IPAY_KEY);

            String[] decodeBaseVec = decodeBaseStr.replace('+', '#').split("#");

            String privateKey = decodeBaseVec[0];
            String modkey = decodeBaseVec[1];

            String reqMd5 = RSAUtil.decrypt(sign, new BigInteger(privateKey),
                    new BigInteger(modkey));

            if (md5Str.equals(reqMd5)) {
                return true;
            } else {
                return false;
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return false;

    }


    /**
     *
     * @param transdata
     *            同步过来的transdata数据
     * @param sign
     *            同步过来的sign数据
     * @param key
     *            应用的密钥(商户可从商户自服务系统获取)
     * @return 验证签名结果 true:验证通过 false:验证失败
     */
    public static boolean validSign(String transdata, String sign, String key) {
        try {
            String md5Str = MD5.md5Digest(transdata);

            String decodeBaseStr = Base64.decode(key);

            String[] decodeBaseVec = decodeBaseStr.replace('+', '#').split("#");

            String privateKey = decodeBaseVec[0];
            String modkey = decodeBaseVec[1];

            String reqMd5 = RSAUtil.decrypt(sign, new BigInteger(privateKey),
                    new BigInteger(modkey));

            if (md5Str.equals(reqMd5)) {
                return true;
            } else {
                return false;
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return false;

    }

    public static void main(String[] args) {
//		// String reqJson =
//		// "{\"exorderno\":\"1\",\"transid\":\"2\",\"waresid\":\"3\",\"chargepoint\":31,\"feetype\":4,\"money\":5,\"count\":6,\"result\":0,\"transtype\":0,\"transtime\":\"2012-12-12 12:11:10\",\"cpprivate\":\"7\",\"sign\":\"64a04bc23987c621264a6295b8c61191 9c9ccd91cbc584316b9d99919921a9be 89c38dfa9329001a521bf4c904bb83cd \"}";
//		// boolean b = CpTransSyncSignValid.validSign(reqJson,
//		// "MjdFN0ExMURCM0JDMDc0QTQ3OTY1NzEwNDEzODMzMjhERkFDRDA5MU1UVTRNalkyTXpNek1ESTFNREUxT1RjME16RXJNakk0TnpjeE56ZzBNVEEyTlRJME16TTNORE00TkRBM09EY3hNemcxTkRrMU1UTXhPVEl4");
//		String md5= MD5.md5Digest("{\"exorderno\":\"test00001\",\"transid\":\"00012122916363200005\",\"waresid\":\"20000100000001200001\",\"appid\":\"1\",\"feetype\":2,\"money\":1,\"count\":1,\"result\":0,\"transtype\":0,\"transtime\":\"2012-12-29 16:36:33\",\"cpprivate\":\"123456\"}");
//		String sign = null;
//		try {
//			sign = RSAUtil.encrypt(md5, new BigInteger("57771314293114350820943284589604085519"), new BigInteger("13945683305049607291"));
//		} catch (Exception e) {
//		}
//		System.out.println(sign);
		//3ae5508e339425e9d0d89c1bf6755183 5777a6fb3489bc48a8902636adc2e7bc 8404d152e2a891a488145c8c85f7f4ad 

        String t="{\"exorderno\":\"10011310958\",\"transid\":\"03111122816103650001\",\"appid\":\"10021200000001100212\",\"waresid\":1111,\"feetype\":0,\"money\":5,\"count\":3,\"result\":1,\"transtype\":0,\"transtime\":\"2013-03-31 13:07:56\",\"cpprivate\":\"123456\"}";
        String md5= MD5.md5Digest(t);
        String sign = null;
		try {
			sign = RSAUtil.encrypt(md5, new BigInteger("57771314293114350820943284589604085519"), new BigInteger("13945683305049607291"));
		} catch (Exception e) {
		}
		System.out.println(sign);


//        System.out.println(
//                genSign(t,IPAY_KEY)
//        );
        //String sign="42089644a764591d78d0782b1b51eb39 8c7fd02773bffa8453c59e0dfc72b1ed 39b105f497781711552f2da0e242c0cd";
        System.out.println(
                validSign(t,sign)
        );
        System.out.println(
                MsgDigestUtil.MD5.digest2HEX("866959017994856_NzcxQzU5MzM2MTJCOEIyNk1UWXlNak0xT1")
        );
	}

}
