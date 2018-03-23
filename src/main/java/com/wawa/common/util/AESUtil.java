package com.wawa.common.util;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang.ArrayUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 *  AES加密解密
 */
public class AESUtil {

    static final Logger logger = LoggerFactory.getLogger(AESUtil.class);

    public static void main(String[] args) {
        // TODO Auto-generated method stub
        // MTIzNDU2Nzg5MDEyMzQ1NlOQ6NWrqwfRO2DdheZia2M=
        String content="helloworld";
        String key="12345678901234561234567890123456";
        String iv="1234567890123456";

        System.out.println("加密前："+content);
        byte[] encrypted = encrypt(content.getBytes(), key.getBytes(), iv.getBytes());
        byte[] buf  = ArrayUtils.addAll(iv.getBytes(), encrypted);
        System.out.println("加密后 base64："+ Base64.encodeBase64String(buf) +"compare:"+ Base64.encodeBase64String(buf).equals("MTIzNDU2Nzg5MDEyMzQ1NlOQ6NWrqwfRO2DdheZia2M="));

        //System.out.println("加密后："+byteToHexString(encrypted));
        byte[ ] decrypted = decrypt(encrypted, key.getBytes(), iv.getBytes());
        System.out.println("解密后："+new String(decrypted));
    }

    /**
     * 加密
     * @param content
     * @param keyBytes
     * @param iv
     * @return
     */
    public static byte[] encrypt(byte[] content, byte[] keyBytes, byte[] iv){

        try{
            /*KeyGenerator keyGenerator=KeyGenerator.getInstance("AES");
            keyGenerator.init(256, new SecureRandom(keyBytes));
            SecretKey key=keyGenerator.generateKey();*/
            SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "AES");
            Cipher cipher= Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, new IvParameterSpec(iv));
            byte[] result=cipher.doFinal(content);
            return result;
        }catch (Exception e) {
            logger.error("Exception : {}", e);
        }
        return null;
    }

    /**
     * 解密
     * @param content
     * @param keyBytes
     * @param iv
     * @return
     */
    public static byte[] decrypt(byte[] content, byte[] keyBytes, byte[] iv){

        try{
            /*KeyGenerator keyGenerator=KeyGenerator.getInstance("AES");
            keyGenerator.init(256, new SecureRandom(keyBytes));//key长可设为128，192，256位，这里只能设为128
            SecretKey key=keyGenerator.generateKey();*/
            SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "AES");
            Cipher cipher= Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, keySpec, new IvParameterSpec(iv));
            byte[] result=cipher.doFinal(content);
            return result;
        }catch (Exception e) {
            logger.error("Exception : {}", e);
        }
        return null;
    }

    /**将二进制转换成16进制
     * @param bytes
     * @return
     */
    public static String byteToHexString(byte[] bytes) {
        StringBuffer sb = new StringBuffer(bytes.length);
        String sTemp;
        for (int i = 0; i < bytes.length; i++) {
            sTemp = Integer.toHexString(0xFF & bytes[i]);
            if (sTemp.length() < 2)
                sb.append(0);
            sb.append(sTemp.toUpperCase());
        }
        return sb.toString();
    }

    /**将16进制转换为二进制
     * @param hexStr
     * @return
     */
    public static byte[] hexStrToByte(String hexStr) {
        if (hexStr.length() < 1)
            return null;
        byte[] result = new byte[hexStr.length()/2];
        for (int i = 0;i< hexStr.length()/2; i++) {
            int high = Integer.parseInt(hexStr.substring(i*2, i*2+1), 16);
            int low = Integer.parseInt(hexStr.substring(i*2+1, i*2+2), 16);
            result[i] = (byte) (high * 16 + low);
        }
        return result;
    }
}