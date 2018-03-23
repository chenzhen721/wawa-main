package com.wawa.service.alipay.config;

public class AlipayConfig {
    // 商户appid
    public static String APPID = "2018010801699579";
    // 私钥 pkcs8格式的
    public static String RSA_PRIVATE_KEY = "MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQCHbK02743OflG721xjSky1xYxDPMc1tYNpJ4SMc7EArMkgKB4tEP4SOMB2SqW1owZEcJtX++5iOZrYc5nj7U8cwj0TcwVsWMHGFh7DLnPExO8fm8CHn/pZrgoGOcttFmCdkq2Tg89Eh5UmaC/muX1lPJmqCZrtO41NfAnql/eTdEL2aWAomYZGRStPm3hezb1LYzo+Cd5lqiWGPMUB3zk6cxY7CezyjnfyJLkqeIuzYCEAXDj5WhLRhRuQATbc+au4mXC9Tm5hIMSEPh6AcYjX8iBSEg3ry+TiDZOkZiGhehLuQTLxYpft2TqY9b9myy77N5HK+X9/RQ2ZRKxrdyotAgMBAAECggEAZqXGSUycdPckZrrntU7dBC5/mXtZF+nEeJD+lCMg9/z/2gLulIQ7wH4Jy9/a6Olv17Ju3OrVjthQ+V3uOGhovciw2KwnYS+JeObNXG46S9xlz9STw3lMHmufp4ZpHf+HLgi+zoBrO2t1bw4ruLvCQ1kCtHLhXo30FdY+AfY8LyQi9plLpkK8seZeqn1tLl+b3THPkpCoVhgsbbaMhop1KZo/XKkktDhrGKfDIwSsfSdBUO2H/zWFElhIEz0WqhWI1BzgPlSV6eBd8ZwwfxGaVUHAz7ZiF6+x/Is+86Kpj2Kq0z3xN32oIegEFknDNeiVMMepo3Z/3vyaX/PKfp63dQKBgQDd+alzX1pKXhvF6NpT0dOddB9mlylwwXq6vV5sXEoTyVxfAfICFdi9c0lP0iIX86PLFWDsd0nGXF45RpuyPLKuJOJWKXwpaapnvRf05K83Qr6PRsdEMqNFezUF7IKBFq6U3HMaoIMM6P912b/YwQuYaZzKdQWkahbCne1aLCV/cwKBgQCcLsC0/dItCSdwpFKkpvebthvosmIchXuzn/CIICIDdEGKnWSykD8uUz5mpVEEiuNMfb+26eYZskVC+qZ31Y7sau1FOLf9jrIJIwO/NpCAvkJHiuL3dH6cRwmOPp0oPRl+pa+Ve1lXvnAKFz+ff/1iHASb1Z1ChW/hLXk1WCsH3wKBgQCVuSnX3TA8yz92fbqOIRDYupPXkgxstsR+ou7GrnV05TQ+DUTcrTm1h8aiZX2DpF4NxIzM0yVa+8C+Z2brkU+AcPaN4YuTL2e2c3BuSbX50zZP1BDiajmAyzsXlNDuG4uEczHcevoKQjrVlht2K7gThOEyGj7FCJ3zhaysTuy4CwKBgE21dWyYXdrKkzUYjYYfDVQMaBZ/qxFF2SamjactVb8iOPofFdtCqi0CfinUV1tLP4zvhFQeTL68VHne/LTjLRm0mhm2/tTKCwtAwLyDCaMFBzVRhVxaOiTHs5lyzf8XZ3f8OEDH/swJlPVwW9egdxI5npFq17QhcGk5lvVTyRVBAoGAOVlUQfJK65RohnTAt3P0updrGk+cfQ3iE6CR8GveUf7JpVrKfVW1ZgwTmMB6ps/tiXiI7748M6O7z4uiQR6wKWVrFC8Kh37c3zdrX1gGaHkHC6ukcvmPBiWvfV7e8n/KdC7BDyVlRyX/YXEFdTrucr9qYO+8mx5Qja7tIHN80Dw=";
    // 服务器异步通知页面路径 需http://或者https://格式的完整路径，不能加?id=123这类自定义参数，必须外网可以正常访问
    public static String notify_url = "http://商户网关地址/alipay.trade.wap.pay-JAVA-UTF-8/wap_notify.jsp";
    // 页面跳转同步通知页面路径 需http://或者https://格式的完整路径，不能加?id=123这类自定义参数，必须外网可以正常访问 商户可以自定义同步跳转地址
    public static String return_url = "http://商户网关地址/alipay.trade.wap.pay-JAVA-UTF-8/return_url.jsp";
    // 请求网关地址
    public static String URL = "https://openapi.alipay.com/gateway.do";
    // 编码
    public static String CHARSET = "UTF-8";
    // 返回格式
    public static String FORMAT = "json";
    // 支付宝公钥
    //public static String ALIPAY_PUBLIC_KEY = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAh2ytNu+Nzn5Ru9tcY0pMtcWMQzzHNbWDaSeEjHOxAKzJICgeLRD+EjjAdkqltaMGRHCbV/vuYjma2HOZ4+1PHMI9E3MFbFjBxhYewy5zxMTvH5vAh5/6Wa4KBjnLbRZgnZKtk4PPRIeVJmgv5rl9ZTyZqgma7TuNTXwJ6pf3k3RC9mlgKJmGRkUrT5t4Xs29S2M6PgneZaolhjzFAd85OnMWOwns8o538iS5KniLs2AhAFw4+VoS0YUbkAE23PmruJlwvU5uYSDEhD4egHGI1/IgUhIN68vk4g2TpGYhoXoS7kEy8WKX7dk6mPW/Zssu+zeRyvl/f0UNmUSsa3cqLQIDAQAB";
    public static String ALIPAY_PUBLIC_KEY = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEApj5pltmkU7awWckw4FuXM6uMikd0NyIBoF81j6Vr5GfhE4ew9E/vBvlvCMPQDYCVa3k4Ue+NT77D5siyyywHcCnme+7dvIPUFjaYhsayOQJdwgnsxcoBkj2I/iKMJJp6AR1vhxICM81Ho+IJ4A3Q2ry9H4YqP3KbtzPOiS8kAHf2ECekr7Y+d53gmwYUYJkfKBLkZ58w+z2EFVEDunGS4afCI12pGaCeEmvtgt4SILqKspxpTqQHVNq1rUAvbKE6tJaflUp+YUa97gDaZ+QNNjltqxA24vGfK9ujXepOTRRmK7Ng66GUVlE27jY59Zerb88o+2WlMbhql73QPbrE4wIDAQAB";
    // 日志记录目录
    public static String log_path = "/log";
    // RSA2
    public static String SIGNTYPE = "RSA2";
}
