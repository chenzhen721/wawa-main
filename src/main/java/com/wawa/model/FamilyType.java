package com.wawa.model;

/**
 *
 *  family_priv // 1：族长 2：副族长  3:执事 4：成员
 */
public enum FamilyType {
    UnKnown(null,null),族长("leader", 1),副族长("vp", 8),执事("manage", 200),成员("member", 200),;

    private String id;
    private Integer limitCount;

    FamilyType(String id, Integer limitCount){
        this.id = id;
        this.limitCount = limitCount;
    }

    public String getId(){
        return this.id;
    }

    public Integer getLimitCount(){
        return this.limitCount;
    }
}
