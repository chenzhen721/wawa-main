package com.wawa.common.doc;

import groovy.transform.CompileStatic;

/**
 * 用户等级
 * date: 13-2-28 下午5:30
 */
@CompileStatic
public class Level {

    private static final long[] family_level = {0,
            20000,
            80000,
            180000,
            320000,
            500000,
            720000,
            980000,
            1280000,
            1620000,
            2000000,
            2420000,
            2880000,
            3380000,
            3920000,
            4500000,
            5120000,
            5780000,
            6480000,
            7220000,
            8000000,
            8820000,
            9680000,
            10580000,
            11520000,
            12500000,
            13520000,
            14580000,
            15680000,
            16820000,
            18000000,
            19220000,
            20480000,
            21780000,
            23120000,
            24500000,
            25920000,
            27380000,
            28880000,
            30420000,
            32000000,
            33620000,
            35280000,
            36980000,
            38720000,
            40500000,
            42320000,
            44180000,
            46080000,
            48020000,
            50000000
    };

    //用户等级对应经验
    private static final long[] user_level = {0,
            100,800,2000,4000,8000,15000,20000,24000,26000,28000,30000,32000,34000,36000,42000,48000,54000,60000,70000,80000,90000,120000,150000,280000,320000,330000,350000,380000,400000,700000,750000,800000,820000,840000,1200000,1300000,1400000,1500000,1550000,1600000,2000000,2300000,2500000,2800000,3000000,3500000,4000000,5000000,7000000,10000000
    };
    //升级所获星尘
    private static final long[] user_dust = {0,
            50,60,70,80,90,100,110,120,130,140,150,160,170,180,190,200,210,220,230,240,250,260,270,280,290,300,310,320,330,340,350,360,370,380,390,400,410,420,430,440,450,460,470,480,490,500,510,520,530,540
    };
    private static final String[] family_level_pic = new String[family_level.length];

    private static final String[] family_pics = {
                "https://aiimg.sumeme.com/39/7/1498622495847.png",
                "https://aiimg.sumeme.com/53/5/1498622507317.png",
                "https://aiimg.sumeme.com/42/2/1498622519146.png",
                "https://aiimg.sumeme.com/31/7/1498622533663.png",
                "https://aiimg.sumeme.com/6/6/1498622543430.png",
                "https://aiimg.sumeme.com/31/7/1498622551967.png",
                "https://aiimg.sumeme.com/14/6/1498622561550.png",
                "https://aiimg.sumeme.com/55/7/1498622571255.png",
                "https://aiimg.sumeme.com/38/6/1498622581286.png",
                "https://aiimg.sumeme.com/22/6/1498622592150.png"
            };

    private static final int max_user_level = user_level.length;

    private static final int max_family_level = family_level.length;

    static {
        //初始化家族等级对于城堡图片
        for (int i = 0; i < max_family_level-1; i++) {
            int index = (int) i / 5;
            family_level_pic[i] = family_pics[index];
        }
    }
    /**
     * 获取威望对应的家族等级
     * @param prestige 威望
     * @return
     */
    public static int familyLevel(long prestige) {
        for (int i = 1; i < max_family_level; i++) {
            if (prestige < (family_level[i])) {
                return i;
            }
        }
        return max_family_level - 1;
    }

    /**
     * 获取等级对应的家族威望
     * @param level
     * @return
     */
    public static long familyPrestige(int level) {
        if(level > max_family_level){
            return user_dust[max_family_level-1];
        }
        return family_level[level];
    }

    /**
     * 获取家族等级对应城堡图片
     * @param level
     * @return
     */
    public static String familyPicOfLevel(int level) {
        if(level > max_family_level){
            return family_level_pic[family_level_pic.length-1];
        }
        return family_level_pic[level -1];
    }

    /**
     * 当前等级需要的经验
     * @param level
     * @return
     */
    public static Long userLevelUpExp(int level){
        if(level > max_user_level){
            return 0l;
        }
        return user_level[level-1];
    }

    /**
     * 升级奖励星尘
     * @param level
     * @return
     */
    public static Long userLevelUpAwardDust(int level){
        if(level > max_user_level){
            return user_dust[max_user_level-1];
        }
        return user_dust[level-1];
    }



    public static void main(String[] args) {
//        System.out.println(getCoinAwardByLevel(90000));
//        System.out.println(getCoinAwardByLevel(88000));
//        System.out.println(userCoin(6));
//        System.out.println(userLevel(0));
      /*  System.out.println(starLevel(898988));*/

//        System.out.println(starLevel(1000));
//        System.out.println(starLevel(250000));
//        System.out.println(starLevel(5400000));
//        System.out.println(starLevel(268000000));
//        System.out.println(starLevel(268000001));
//
//        System.out.println("user================");
//        assert userLevel(0)!=1;
//        assert userLevel(999)!=0;
//        System.out.println(userLevel(0));
//        System.out.println(userLevel(999));
//        System.out.println(userLevel(1000));
//        System.out.println(userLevel(700000));
//        System.out.println(userLevel(700001));
//        System.out.println(userLevel(5000000));
//        System.out.println(userLevel(5000001));
//        System.out.println(userLevel(258000000));
//        System.out.println(userLevel(258000001));
//        System.out.println(userLevel(458000000));
//        System.out.println(userLevel(458000001));
//
//        System.out.println("weekstar================");
//        System.out.println(userWeekStarLevel(0));
//        System.out.println(userWeekStarLevel(20000));
//        System.out.println(userWeekStarLevel(50000));
//        System.out.println(userWeekStarLevel(100000));
//        System.out.println(userWeekStarLevel(200000));
//        System.out.println(userWeekStarLevel(500000));
//        System.out.println(userWeekStarLevel(500001));
//
//
//        for (int i = 0; i < starLevels.length; i++) {
//            Long level_coin = starLevels[i];
//            System.out.println((level_coin-1) +":"+ starLevel(level_coin-1));
//            System.out.println(level_coin +":"+ starLevel(level_coin));
//            System.out.println((level_coin+1) +":"+ starLevel(level_coin+1));
//        }
//
//
//        for (int i = 0; i < userLevels.length; i++) {
//            Long level_coin = userLevels[i];
//            System.out.println((level_coin-1) +":"+ userLevel(level_coin-1));
//            System.out.println(level_coin +":"+ userLevel(level_coin));
//            System.out.println((level_coin+1) +":"+ userLevel(level_coin+1));
//        }
/*        System.out.println("familyLevel :"+ familyLevel(0));
        System.out.println("familyLevel :"+ familyLevel(20000-1));
        System.out.println("familyLevel :"+ familyLevel(20000));
        System.out.println("familyLevel :"+ familyLevel(20001));
        System.out.println("familyLevel :"+ familyLevel(50000000));
        System.out.println("familyLevel :"+ familyLevel(50000001));
        System.out.println("userLevelUpExp :"+ userLevelUpExp(2));
        System.out.println("userLevelUpAwardDust :"+ userLevelUpAwardDust(2));*/
        System.out.println(familyPicOfLevel(1));
        System.out.println(familyPicOfLevel(5));
        System.out.println(familyPicOfLevel(10));
        System.out.println(familyPicOfLevel(20));
        System.out.println(familyPicOfLevel(50));
    }

}
