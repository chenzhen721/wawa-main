package com.wawa.common.util;

import org.apache.commons.lang.math.RandomUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * @author: jiao.li@ttpod.com
 * Date: 2017/5/31 11:31
 */
public class RandomExtUtils {

    public static final Integer PER_RATE_BASE = 1000;
    /**
     * 范围值内随机
     * @param min
     * @param max
     * @return
     */
    public static Integer randomBetweenMinAndMax(Integer min, Integer max){
        return RandomUtils.nextInt(max)%(max-min+1) + min;
    }

    /**
     * 是否命中 千分之几的概率
     * @param num 0.025
     * @return
     */
    public static Boolean isBingoPerThousand (Integer num){
        return RandomUtils.nextInt(PER_RATE_BASE) < num;
    }


    static final Logger logger = LoggerFactory.getLogger(AuthCode.class) ;
    static final Color COLORS[] = {new Color(113, 31, 71), new Color(37, 0, 37), new Color(111, 33, 36),
            new Color(116, 86, 88),new Color(14, 51, 16),new Color(1, 1, 1), new Color(0, 0, 112),
            new Color(72, 14, 73),new Color(65, 67, 29),new Color(41, 75, 71)};

    static  final char[] SEEDS = "abcdefghjklmnpqrstuvwxyz123456789".toCharArray();

    static  final char[] NUM_SEEDS = "1234567890".toCharArray();

    public static String random(int size){
        if(size<=0){
            throw new IllegalArgumentException("size must > 0");
        }
        char[] result = new char[size];
        Random random = ThreadLocalRandom.current();
        int len = SEEDS.length;
        for(int i =0 ;i<size ; i++){
            result[i] = SEEDS[random.nextInt(len)];
        }
        return new String(result);
    }
    public static String randomNumber(int size){
        if(size<=0){
            throw new IllegalArgumentException("size must > 0");
        }
        char[] result = new char[size];
        Random random = ThreadLocalRandom.current();
        int len = NUM_SEEDS.length;
        for(int i =0 ;i<size ; i++){
            result[i] = NUM_SEEDS[random.nextInt(len)];
        }
        return new String(result);
    }
}
