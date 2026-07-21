package com.water.widget;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 账户数据模型。对应一次 /acc/login 成功后的会话。
 * 每个账户独立保存 token、用户信息、以及分配的热/冷水设备 ID。
 */
public class Account {
    public String phone;
    public String token;     // 主 token（支付宝小程序 / 短信登录）
    public String appToken;  // 设备控制登录信息
    public String uid;
    public String eid;
    public String name;
    public String hotDid;
    public String coldDid;
    /** 当前账户曾使用过的设备，供首页快速切换。 */
    public List<String> recentDeviceIds = new ArrayList<>();

    public Account() {}

    public Account(String phone) {
        this.phone = phone;
    }

    public JSONObject toJson() {
        JSONObject o = new JSONObject();
        try {
            o.put("phone", n(phone));
            o.put("token", n(token));
            o.put("appToken", n(appToken));
            o.put("uid", n(uid));
            o.put("eid", n(eid));
            o.put("name", n(name));
            o.put("hotDid", n(hotDid));
            o.put("coldDid", n(coldDid));
            o.put("recentDeviceIds", new JSONArray(recentDeviceIds));
        } catch (JSONException ignored) {}
        return o;
    }

    public static Account fromJson(JSONObject o) {
        Account a = new Account();
        a.phone = o.optString("phone", "");
        a.token = o.optString("token", "");
        a.appToken = o.optString("appToken", "");
        a.uid = o.optString("uid", "");
        a.eid = o.optString("eid", "");
        a.name = o.optString("name", "");
        a.hotDid = o.optString("hotDid", "");
        a.coldDid = o.optString("coldDid", "");
        JSONArray recent = o.optJSONArray("recentDeviceIds");
        if (recent != null) {
            for (int i = 0; i < recent.length(); i++) a.rememberDevice(recent.optString(i, ""));
        }
        a.rememberDevice(a.hotDid);
        a.rememberDevice(a.coldDid);
        return a;
    }

    /** 是否已登录（有 token）。 */
    public boolean hasToken() {
        return token != null && !token.isEmpty();
    }

    /** 是否有官方 APP token。 */
    public boolean hasAppToken() {
        return appToken != null && !appToken.isEmpty();
    }

    /** 是否已分配好出水设备（至少有一个设备即可）。 */
    public boolean hasDevices() {
        return notEmpty(hotDid) || notEmpty(coldDid);
    }

    /** 取热水 did，没有则用冷水 did。 */
    public String hotOrFallback() {
        return notEmpty(hotDid) ? hotDid : coldDid;
    }

    /** 取冷水 did，没有则用热水 did。 */
    public String coldOrFallback() {
        return notEmpty(coldDid) ? coldDid : hotDid;
    }

    /** 记录设备，保留最近使用的 12 台，方便从首页快速指定。 */
    public void rememberDevice(String deviceId) {
        if (!notEmpty(deviceId)) return;
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        unique.add(deviceId);
        if (recentDeviceIds != null) unique.addAll(recentDeviceIds);
        recentDeviceIds = new ArrayList<>(unique);
        if (recentDeviceIds.size() > 12) recentDeviceIds = new ArrayList<>(recentDeviceIds.subList(0, 12));
    }

    public List<String> rememberedDevices() {
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        if (notEmpty(hotDid)) unique.add(hotDid);
        if (notEmpty(coldDid)) unique.add(coldDid);
        if (recentDeviceIds != null) unique.addAll(recentDeviceIds);
        return new ArrayList<>(unique);
    }

    private static boolean notEmpty(String s) {
        return s != null && !s.isEmpty();
    }

    private static String n(String s) {
        return s == null ? "" : s;
    }

    @Override
    public String toString() {
        return clean(name != null && !name.isEmpty() ? name : phone);
    }

    private static String clean(String s) {
        if (s == null) return "";
        return s.replace("&amp;", "&")
                .replace("&#38;", "&")
                .replace("amp;", "")
                .trim();
    }
}
