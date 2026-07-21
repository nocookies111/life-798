package com.water.widget;

import android.content.Context;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Random;

/**
 * 服务端 API 封装。
 *
 * 统一请求头：
 *   Content-Type: application/json
 *   ApplicationType: 1,5 或 1,1
 *   Authorization: <token>
 */
public class IlifeApi {
    static final String GATEWAY = BuildConfig.API_GATEWAY;
    static final String CID = BuildConfig.API_CID;
    private static final String SIGN_SALT = BuildConfig.SIGN_SALT;
    private static final String UA = "WaterWidget/5.0.0 (Android)";

    public interface ImgCallback {
        void onResult(byte[] bytes, String err);
    }

    public interface JsonCallback {
        void onResult(JSONObject json, String err);
    }

    public interface TextCallback {
        void onResult(String text, String err);
    }

    // ====== 签名 ======

    /**
     * 积分任务签名。
     * 实际算法通过 BuildConfig 注入的参数完成，源码中不含具体实现。
     * 本地构建时需在项目根目录创建 secrets.properties 并填入：
     *   API_GATEWAY=https://example.com/api/v1
     *   SIGN_SALT=your_salt_here
     *   API_CID=your_cid_here
     */
    public static String sign(String adId, String token, String uid) {
        if (SIGN_SALT == null || SIGN_SALT.isEmpty()) {
            return "";
        }
        return Signer.sign(adId, token, uid, SIGN_SALT);
    }

    // ====== 登录流程 ======

    public static String newCaptchaKey() {
        String chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder sb = new StringBuilder();
        Random r = new Random();
        for (int i = 0; i < 10; i++) sb.append(chars.charAt(r.nextInt(chars.length())));
        return sb.toString();
    }

    public static void captcha(final String s, final ImgCallback cb) {
        new Thread(() -> {
            HttpURLConnection c = null;
            try {
                String u = GATEWAY + "/captcha/?s=" + URLEncoder.encode(s, "UTF-8")
                        + "&r=" + System.currentTimeMillis();
                c = (HttpURLConnection) new URL(u).openConnection();
                c.setRequestMethod("GET");
                c.setRequestProperty("User-Agent", UA);
                c.setConnectTimeout(15000);
                c.setReadTimeout(15000);
                InputStream is = c.getInputStream();
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[4096];
                int n;
                while ((n = is.read(buf)) > 0) out.write(buf, 0, n);
                cb.onResult(out.toByteArray(), null);
            } catch (Exception e) {
                cb.onResult(null, e.getMessage());
            } finally {
                if (c != null) c.disconnect();
            }
        }).start();
    }

    public static void sendSms(final String phone, final String graphCode, final String s,
                               final JsonCallback cb) {
        JSONObject body = new JSONObject();
        try {
            body.put("un", phone);
            body.put("authCode", graphCode);
            body.put("s", s);
        } catch (Exception ignored) {}
        post("/acc/login/code", body, null, cb);
    }

    public static void login(final String phone, final String smsCode, final JsonCallback cb) {
        login(phone, smsCode, false, cb);
    }

    /**
     * 登录。
     * @param asApp true=用设备控制方式登录（ApplicationType=1,1）
     *              false=用账户服务方式登录（ApplicationType=1,5）
     */
    public static void login(final String phone, final String smsCode,
                             final boolean asApp, final JsonCallback cb) {
        JSONObject body = new JSONObject();
        try {
            body.put("openCode", "");
            body.put("authCode", smsCode);
            body.put("un", phone);
            body.put("cid", CID);
        } catch (Exception ignored) {}
        final String appType = asApp ? "1,1" : "1,5";
        new Thread(() -> {
            try {
                String resp = httpRawApp("POST", GATEWAY + "/acc/login",
                        body.toString(), null, appType);
                cb.onResult(new JSONObject(resp), null);
            } catch (Exception e) {
                cb.onResult(null, e.getMessage());
            }
        }).start();
    }

    // ====== 账户信息 ======

    /** 取账户信息，含 uid（id 字段）。用于补全 Account.uid。 */
    public static void viewInfo(Context ctx, final JsonCallback cb) {
        get(ctx, "/acc/view-info", cb);
    }

    // ====== 积分任务 ======

    public static void missionLst(Context ctx, final JsonCallback cb) {
        get(ctx, "/acc/score/mission-lst", cb);
    }

    /** 用指定 token 获取任务列表（用于双平台合并）。 */
    public static void missionLstWithToken(final String token, final JsonCallback cb) {
        new Thread(() -> {
            try {
                String body = httpRaw("GET", GATEWAY + "/acc/score/mission-lst", null, token);
                cb.onResult(new JSONObject(body), null);
            } catch (Exception e) {
                cb.onResult(null, e.getMessage());
            }
        }).start();
    }

    /** 获取积分明细（用于校验任务是否已完成）。 */
    public static void scoreLst(final Context ctx, final JsonCallback cb) {
        get(ctx, "/acc/score/score-lst?page=0&size=200&hasCount=1", cb);
    }

    /** 用指定 token 获取账户信息（uid/eid/手机号）。 */
    public static void viewInfoWithToken(final String token, final JsonCallback cb) {
        new Thread(() -> {
            try {
                String body = httpRaw("GET", GATEWAY + "/acc/view-info", null, token);
                cb.onResult(new JSONObject(body), null);
            } catch (Exception e) {
                cb.onResult(null, e.getMessage());
            }
        }).start();
    }

    /** 用指定 token 获取积分明细。 */
    public static void scoreLstWithToken(final String token, final JsonCallback cb) {
        new Thread(() -> {
            try {
                String body = httpRaw("GET", GATEWAY + "/acc/score/score-lst?page=0&size=200&hasCount=1", null, token);
                cb.onResult(new JSONObject(body), null);
            } catch (Exception e) {
                cb.onResult(null, e.getMessage());
            }
        }).start();
    }

    /** 用指定 token 执行积分任务（用于双平台合并）。 */
    public static void scoreSendWithToken(final String token, final String uid,
                                          final String adId, final JsonCallback cb) {
        new Thread(() -> {
            try {
                String effectiveUid = uid;
                if (effectiveUid == null || effectiveUid.isEmpty()) {
                    effectiveUid = fetchUidSync(token);
                }
                final String sg = sign(adId, token, effectiveUid != null ? effectiveUid : "");
                String url = GATEWAY + "/acc/score/score-send?sign=" + sg + "&s=true";
                JSONObject body = new JSONObject();
                body.put("adId", adId);
                body.put("type", 101);
                String resp = httpRaw("POST", url, body.toString(), token);
                cb.onResult(new JSONObject(resp), null);
            } catch (Exception e) {
                cb.onResult(null, e.getMessage());
            }
        }).start();
    }

    /** 每日签到。weekDay: 1=周一...7=周日, signAdId: 签到adId(通常为"DAILY_CHECK_IN")。 */
    public static void scoreSendSignIn(final String token, final String uid,
                                       final int weekDay, final String signAdId,
                                       final JsonCallback cb) {
        new Thread(() -> {
            try {
                String effectiveUid = uid;
                if (effectiveUid == null || effectiveUid.isEmpty()) {
                    effectiveUid = fetchUidSync(token);
                }
                final String sg = sign(signAdId, token, effectiveUid != null ? effectiveUid : "");
                String url = GATEWAY + "/acc/score/score-send?sign=" + sg + "&s=true";
                JSONObject body = new JSONObject();
                body.put("weekDay", weekDay);
                body.put("adId", signAdId);
                String resp = httpRaw("POST", url, body.toString(), token);
                cb.onResult(new JSONObject(resp), null);
            } catch (Exception e) {
                cb.onResult(null, e.getMessage());
            }
        }).start();
    }

    /**
     * 完成任务（带签名）。
     * POST /acc/score/score-send?sign=<sign>&s=true  body={adId, type:101}
     */
    public static void scoreSend(final Context ctx, final String adId, final JsonCallback cb) {
        new Thread(() -> {
            Account acc = AccountStore.getCurrent(ctx);
            if (acc == null || !acc.hasToken()) {
                cb.onResult(null, "未登录");
                return;
            }
            // 确保 uid 存在
            String uid = acc.uid;
            if (uid == null || uid.isEmpty()) {
                // 同步取 uid
                uid = fetchUidSync(acc.token);
                if (uid != null && !uid.isEmpty()) {
                    acc.uid = uid;
                    AccountStore.updateCurrent(ctx, acc);
                }
            }
            final String sg = sign(adId, acc.token, uid != null ? uid : "");
            String url = GATEWAY + "/acc/score/score-send?sign=" + sg + "&s=true";
            JSONObject body = new JSONObject();
            try {
                body.put("adId", adId);
                body.put("type", 101);
            } catch (Exception ignored) {}
            try {
                String resp = httpRaw("POST", url, body.toString(), acc.token);
                cb.onResult(new JSONObject(resp), null);
            } catch (Exception e) {
                cb.onResult(null, e.getMessage());
            }
        }).start();
    }

    /** 同步获取 uid（从 view-info）。 */
    private static String fetchUidSync(String token) {
        try {
            String resp = httpRaw("GET", GATEWAY + "/acc/view-info", null, token);
            JSONObject json = new JSONObject(resp);
            if (json.optInt("code", -999) == 0) {
                JSONObject data = json.optJSONObject("data");
                if (data != null) return data.optString("id", "");
            }
        } catch (Exception ignored) {}
        return "";
    }

    // ====== 设备 ======

    public static void master(Context ctx, final JsonCallback cb) {
        get(ctx, "/ui/app/master", cb);
    }

    public static void devStart(Context ctx, final String did, final TextCallback cb) {
        new Thread(() -> {
            Account acc = AccountStore.getCurrent(ctx);
            if (acc == null || (!acc.hasToken() && !acc.hasAppToken())) {
                cb.onResult(null, "未登录");
                return;
            }
            // 设备控制需要使用设备控制登录信息（ApplicationType=1,1）。
            // 账户服务登录信息（1,5）无法用于设备控制。
            boolean useApp = acc.hasAppToken();
            String token = useApp ? acc.appToken : acc.token;
            String appType = useApp ? "1,1" : "1,5";
            String url = GATEWAY + "/dev/start?did=" + enc(did)
                    + "&upgrade=true&ptype=91&rcp=false";
            try {
                String body = httpRawApp("GET", url, null, token, appType);
                JSONObject json = new JSONObject(body);
                int code = json.optInt("code", -999);
                String msg = json.optString("msg", "");
                if (code == 0) {
                    cb.onResult("成功 ✓", null);
                } else if (code == -99) {
                    cb.onResult(null, "TOKEN_EXPIRED");
                } else if (code == -21) {
                    cb.onResult(null, useApp ? msg : "需要设备控制登录信息，请先在账户中添加");
                } else if (code == -88) {
                    cb.onResult(null, "未签约代扣协议，请在服务端完成签约后再使用");
                } else if (code == -87) {
                    cb.onResult(null, "签约已过期，请重新签约");
                } else if (code == -52) {
                    cb.onResult(null, "账户欠费，请充值后使用");
                } else if (code == -20) {
                    cb.onResult(null, "未绑定一卡通账号，请先完成账户绑定");
                } else if (code == -19) {
                    cb.onResult(null, "设备准备中，请稍后");
                } else if (code == -82) {
                    cb.onResult(null, "需要支付");
                } else {
                    cb.onResult(null, msg.isEmpty() ? "code=" + code : msg);
                }
            } catch (Exception e) {
                cb.onResult(null, e.getMessage());
            }
        }).start();
    }

    /** 结束出水。 */
    public static void devEnd(Context ctx, final String did, final TextCallback cb) {
        new Thread(() -> {
            Account acc = AccountStore.getCurrent(ctx);
            if (acc == null || (!acc.hasToken() && !acc.hasAppToken())) {
                cb.onResult(null, "未登录");
                return;
            }
            boolean useApp = acc.hasAppToken();
            String token = useApp ? acc.appToken : acc.token;
            String appType = useApp ? "1,1" : "1,5";
            String url = GATEWAY + "/dev/end?did=" + enc(did);
            try {
                String body = httpRawApp("GET", url, null, token, appType);
                JSONObject json = new JSONObject(body);
                int code = json.optInt("code", -999);
                if (code == 0) {
                    cb.onResult("已停止 ✓", null);
                } else if (code == -99) {
                    cb.onResult(null, "TOKEN_EXPIRED");
                } else {
                    cb.onResult(null, "code=" + code);
                }
            } catch (Exception e) {
                cb.onResult(null, e.getMessage());
            }
        }).start();
    }

    // ====== 通用请求 ======

    private static void get(Context ctx, final String path, final JsonCallback cb) {
        new Thread(() -> {
            Account acc = AccountStore.getCurrent(ctx);
            String token = acc != null ? acc.token : "";
            try {
                String body = httpRaw("GET", GATEWAY + path, null, token);
                cb.onResult(new JSONObject(body), null);
            } catch (Exception e) {
                cb.onResult(null, e.getMessage());
            }
        }).start();
    }

    private static void post(final String path, final JSONObject body, final String token,
                             final JsonCallback cb) {
        new Thread(() -> {
            try {
                String resp = httpRaw("POST", GATEWAY + path,
                        body != null ? body.toString() : null, token);
                cb.onResult(new JSONObject(resp), null);
            } catch (Exception e) {
                cb.onResult(null, e.getMessage());
            }
        }).start();
    }

    private static String httpRaw(String method, String urlStr, String body, String token)
            throws Exception {
        return httpRawApp(method, urlStr, body, token, "1,5");
    }

    /** 同 httpRaw，但可指定 ApplicationType。 */
    private static String httpRawApp(String method, String urlStr, String body,
                                     String token, String appType) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(urlStr).openConnection();
        try {
            c.setRequestMethod(method);
            c.setConnectTimeout(15000);
            c.setReadTimeout(15000);
            c.setRequestProperty("User-Agent", UA);
            c.setRequestProperty("Content-Type", "application/json");
            c.setRequestProperty("ApplicationType", appType);
            c.setRequestProperty("Accept-Language", "zh-Hans-CN;q=1");
            if (token != null && !token.isEmpty()) {
                c.setRequestProperty("Authorization", token);
            }
            if (body != null) {
                c.setDoOutput(true);
                OutputStream os = c.getOutputStream();
                os.write(body.getBytes("UTF-8"));
                os.flush();
                os.close();
            }
            int http = c.getResponseCode();
            BufferedReader rd = new BufferedReader(new InputStreamReader(
                    http >= 400 ? c.getErrorStream() : c.getInputStream(), "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = rd.readLine()) != null) sb.append(line);
            rd.close();
            return sb.toString();
        } finally {
            c.disconnect();
        }
    }

    private static String enc(String s) {
        try {
            return URLEncoder.encode(s, "UTF-8");
        } catch (Exception e) {
            return s;
        }
    }
}
