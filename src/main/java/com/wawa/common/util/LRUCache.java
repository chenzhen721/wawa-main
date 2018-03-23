package com.wawa.common.util;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import groovy.transform.CompileStatic;

import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

/**
 * LRU 缓存
 * @author: jiao.li
 * Date: 2016/4/6 10:15
 */
@CompileStatic
public class LRUCache<K, V> {

    private Cache<K, V> cache;

    /**
     *
     * @param expireSecAfterAccess 最近一次访问后缓存失效时间
     * @param maxSize  最大缓存数量
     */
    public LRUCache(Integer expireSecAfterAccess, Integer maxSize){
        cache = CacheBuilder.newBuilder()
                .concurrencyLevel(4)
                .maximumSize(maxSize)
                .expireAfterAccess(expireSecAfterAccess, TimeUnit.SECONDS)
                .recordStats()
                .build();
    }

    public void put(K k, V v){
        cache.put(k, v);
    }

    public V get(K k){
        try{
            return cache.get(k, new Callable<V>() {
                @Override
                public V call() {
                    return null;
                }
            });
        }catch (Exception e){
            return null;
        }
    }

    public String stats(){
        return cache.stats().toString();
    }

    public Long size(){
        return cache.size();
    }
}
