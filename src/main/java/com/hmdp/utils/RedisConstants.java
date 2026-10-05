package com.hmdp.utils;

public class RedisConstants {
    public static final String LOGIN_CODE_KEY = "login:code:";
    public static final Long LOGIN_CODE_TTL = 2L;
    public static final String LOGIN_USER_KEY = "login:token:";
    public static final Long LOGIN_USER_TTL = 30L;

    public static final Long CACHE_NULL_TTL = 2L;

    public static final String FEED_KEY = "feed:";
    public static final String USER_SIGN_KEY = "sign:";

    // 医院排队系统相关常量
    public static final Long CACHE_DOCTOR_TTL = 30L;
    public static final String CACHE_DOCTOR_KEY = "cache:doctor:";
    public static final String CACHE_DEPARTMENT_KEY = "cache:department:";

    public static final String LOCK_DOCTOR_KEY = "lock:doctor:";
    public static final Long LOCK_DOCTOR_TTL = 10L;
    /** 通用缓存重建锁，供 CacheClient 的旧接口兼容使用。 */
    public static final String LOCK_SHOP_KEY = "lock:cache:";

    public static final String SCHEDULE_STOCK_KEY = "schedule:stock:";
    public static final String REVIEW_LIKED_KEY = "review:liked:";
    public static final String DOCTOR_GEO_KEY = "doctor:geo:";
    public static final String PATIENT_SIGN_KEY = "sign:patient:";
    public static final String UV_KEY = "uv:global";
}
