package com.jonychen.util;

import jakarta.servlet.http.HttpServletRequest;

/** IP 地址工具类 */
public final class IpUtils {

    /** IPv6 localhost */
    private static final String IPV6_LOCALHOST = "0:0:0:0:0:0:0:1";

    /** IPv6 localhost 简写形式 */
    private static final String IPV6_LOCALHOST_SHORT = "::1";

    /** IPv4 localhost */
    private static final String IPV4_LOCALHOST = "127.0.0.1";

    private IpUtils() {}

    /**
     * 获取客户端真实 IP 地址
     *
     * <p>优先级：X-Forwarded-For > X-Real-IP > RemoteAddr
     *
     * <p>X-Forwarded-For 格式：client1, proxy1, proxy2，取第一个非 unknown 的 IP
     */
    public static String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");

        if (isValidIp(ip)) {
            // X-Forwarded-For 可能包含多个 IP，取第一个
            int index = ip.indexOf(',');
            if (index > 0) {
                ip = ip.substring(0, index).trim();
            }
            return normalizeIp(ip);
        }

        ip = request.getHeader("X-Real-IP");
        if (isValidIp(ip)) {
            return normalizeIp(ip);
        }

        ip = request.getRemoteAddr();
        return normalizeIp(ip);
    }

    /** 判断 IP 是否有效（非空、非 unknown） */
    private static boolean isValidIp(String ip) {
        return ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip);
    }

    /** 标准化 IP 地址，将 IPv6 localhost 转换为 IPv4 格式 */
    private static String normalizeIp(String ip) {
        if (ip == null || ip.isEmpty()) {
            return "unknown";
        }
        // 将 IPv6 localhost 转换为 IPv4 格式，更友好
        if (IPV6_LOCALHOST.equals(ip) || IPV6_LOCALHOST_SHORT.equals(ip)) {
            return IPV4_LOCALHOST;
        }
        return ip;
    }
}
