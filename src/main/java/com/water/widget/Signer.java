package com.water.widget;

import java.security.MessageDigest;

/**
 * 签名工具类。
 *
 * 开源仓库中仅提供占位实现。本地构建时需在 secrets.properties 中配置 SIGN_SALT，
 * 构建系统会将其注入 BuildConfig，本类再通过 IlifeApi.sign() 间接使用。
 *
 * 如需完整的签名功能，请自行实现签名逻辑并配置必要的参数。
 */
public class Signer {

    /**
     * 生成签名。
     * 当 salt 为空时返回空字符串，表示签名功能未配置。
     * 本地开发者请参考项目文档实现签名算法。
     */
    static String sign(String adId, String token, String uid, String salt) {
        if (salt == null || salt.isEmpty()) {
            return "";
        }
        try {
            long now = System.currentTimeMillis();
            long n = 10 * (now / 10000);
            String e = token.length() >= 8 ? token.substring(token.length() - 8) : token;
            String t = uid.length() >= 8 ? uid.substring(uid.length() - 8) : uid;
            String raw = adId + n + e + t + salt;
            return md5(raw);
        } catch (Exception ex) {
            return "";
        }
    }

    private static String md5(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] d = md.digest(s.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format("%02x", b & 0xff));
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }
}
