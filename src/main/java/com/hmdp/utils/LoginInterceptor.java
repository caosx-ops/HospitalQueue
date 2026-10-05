package com.hmdp.utils;

import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.springframework.util.AntPathMatcher;

import java.util.Arrays;
import java.util.List;

public class LoginInterceptor implements HandlerInterceptor {

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();
    private static final List<String> PUBLIC_GET_PATHS = Arrays.asList(
            "/department/**", "/doctor/**", "/schedule/doctor/**", "/upload/**",
            "/review/hot", "/review/*", "/review/of/doctor", "/review/of/user",
            "/review/likes/*", "/review/reply/of/review/*", "/user/*",
            "/user/uv", "/actuator/health", "/actuator/health/**", "/actuator/prometheus"
    );

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (isPublic(request)) {
            return true;
        }
        // 1.判断是否需要拦截（ThreadLocal中是否有用户）
        if (UserHolder.getUser() == null) {
            // 没有，需要拦截，设置状态码
            response.setStatus(401);
            // 拦截
            return false;
        }
        // 有用户，则放行
        return true;
    }

    private boolean isPublic(HttpServletRequest request) {
        String method = request.getMethod();
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if ("POST".equals(method) && ("/user/code".equals(path) || "/user/login".equals(path))) {
            return true;
        }
        if (!"GET".equals(method)) {
            return false;
        }
        return PUBLIC_GET_PATHS.stream().anyMatch(pattern -> PATH_MATCHER.match(pattern, path));
    }
}
