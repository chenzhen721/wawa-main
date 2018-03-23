package com.wawa.model;

import java.util.HashSet;
import java.util.Set;

/**
 * 房间间管理用户权限
 */
public interface ManageOpers{

    public Boolean isAllowed(Integer min);
    /**
     *   禁言
     *   运营 	5分钟/30分钟/12小时 	可禁言所有人
         族长 	5分钟/30分钟/12小时 	可禁运营以外所有人
         副族长 	5分钟/30分钟/12小时 	可禁副族长以下（非本家族所有人）
         执事 	5分钟/30分钟/12小时 	可禁执事以下（非本家族所有人）
     */
    public enum Shutuper implements ManageOpers{
        运营(5,30,720),
        族长(5,30,720),
        副族长(5,30,720),
        执事(5,30,720),
        other(-1),
        ;

        Shutuper(Integer... mins){
            for (Integer min : mins) {
                allowedMins.add(min);
            }
        }
        private final Set<Integer> allowedMins = new HashSet<>();
        public Boolean isAllowed(Integer min) {
            return allowedMins.contains(min);
        }
    }

    /**
     *   踢人
         运营 	5分钟/60分钟/12小时 	可踢所有人
         族长 	5分钟/60分钟/12小时 	可踢运营以外所有人
         副族长 	5分钟/60分钟/12小时 	可踢副族长以下（非本家族所有人）
         执事 	即踢/5分钟 	        可踢执事以下（非本家族所有人）
     */
    public enum Kicker implements ManageOpers{
        运营(5,60,720),
        族长(5,60,720),
        副族长(5,60,720),
        执事(0,5),
        other(-1),
        ;

        Kicker(Integer... mins){
            for (Integer min : mins) {
                allowedMins.add(min);
            }
        }
        private final Set<Integer> allowedMins = new HashSet<>();
        public Boolean isAllowed(Integer min) {
            return allowedMins.contains(min);
        }
    }
}
