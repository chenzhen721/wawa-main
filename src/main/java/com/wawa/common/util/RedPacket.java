package com.wawa.common.util;

import org.apache.commons.lang.math.RandomUtils;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 *小窝红包
 */
public class RedPacket {

    //每个人最少能收到1个
    private final static Double min = 10d;

    public static void main(String[] args) {
        Integer total = 20;//红包总额
        int num = 1;// 分成N个红包，支持n人随机领取
        List<Integer> ar = distribute(total,num);
        for (int i = 0; i < ar.size(); i++) {
            System.out.println("第" + (i+1) + "个红包：" + ar.get(i));
        }
        //test();
    }

    public static void test(){
        for (int i = 4; i < 1000000000; i++) {
            for (int j = i; j > 2 ; j--) {
                List<Integer> ar = distribute(i, j);
                if(!isEqule(ar, i)){
                    System.out.println("total：" + i + " amount : " + j);
                }
            }
        }
        System.out.println("everything is ok !!");
    }

    static Boolean isEqule(List<Integer> arr, Integer total){
        Integer sum = 0;
        for (Integer v : arr){
            sum += v;
        }
        return sum.equals(total);
    }

    public static List<Integer> distribute(Integer total, int num){
        List<Integer> ar = new LinkedList<>();
        for (int i = 1; i < num; i++) {
            int remian = num - i;
            Double safe_total =  Math.ceil((total - remian * min) / remian) ;//随机安全上限
            Double money = min + RandomUtils.nextInt(safe_total.intValue());
            total = total - money.intValue();
            ar.add(money.intValue());
        }
        ar.add(total);
        Collections.shuffle(ar);
        return ar;
    }


}
