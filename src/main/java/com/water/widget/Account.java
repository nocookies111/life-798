package com.water.widget;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 账户数据模型。对应一次 /acc/login 成功后的会话。
 * 每个账户独立保存 token、用户信息、当前设备及最近使用的设备。
 */
public class Account {
    public String phone;
    public String token;     // 主 token（支付宝小程序 / 短信登录）
    public String appToken;  // 设备控制登录信息
    public String uid;
    public String eid;
    public String name;
    public String deviceId;
    /** 当前账户的设备列表。 */
    public List<String> recentDeviceIds = new ArrayList<>();
    /** 服务端返回的设备名称。 */
    public Map<String, String> deviceNames = new LinkedHashMap<>();
    /** 用户在本地设置的设备别名。 */
    public Map<String, String> deviceAliases = new LinkedHashMap<>();

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
            o.put("deviceId", n(deviceId));
            o.put("recentDeviceIds", new JSONArray(recentDeviceIds));
            o.put("deviceNames", mapJson(deviceNames));
            o.put("deviceAliases", mapJson(deviceAliases));
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
        a.deviceId = o.optString("deviceId", "");
        readMap(o.optJSONObject("deviceNames"), a.deviceNames);
        readMap(o.optJSONObject("deviceAliases"), a.deviceAliases);
        String legacyHotDid = o.optString("hotDid", "");
        String legacyColdDid = o.optString("coldDid", "");
        JSONArray recent = o.optJSONArray("recentDeviceIds");
        if (recent != null) {
            for (int i = 0; i < recent.length(); i++) a.rememberDevice(recent.optString(i, ""));
        }
        a.rememberDevice(legacyColdDid);
        a.rememberDevice(legacyHotDid);
        if (!notEmpty(a.deviceId)) {
            a.deviceId = notEmpty(legacyHotDid) ? legacyHotDid : legacyColdDid;
        }
        a.rememberDevice(a.deviceId);
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

    /** 是否已选择出水设备。 */
    public boolean hasDevices() {
        return notEmpty(selectedDeviceId());
    }

    /** 当前选择的设备；旧数据缺少显式选择时回退到最近设备。 */
    public String selectedDeviceId() {
        if (notEmpty(deviceId)) return deviceId;
        if (recentDeviceIds != null) {
            for (String id : recentDeviceIds) {
                if (notEmpty(id)) return id;
            }
        }
        return "";
    }

    /** 选择设备，并将它移到最近设备列表首位。 */
    public void selectDevice(String selectedDeviceId) {
        if (!notEmpty(selectedDeviceId)) return;
        deviceId = selectedDeviceId;
        rememberDevice(selectedDeviceId);
    }

    /** 记录设备，不限制界面可展示的设备数量。 */
    public void rememberDevice(String deviceId) {
        rememberDevice(deviceId, "");
    }

    /** 记录设备，并更新服务端返回的名称。 */
    public void rememberDevice(String deviceId, String deviceName) {
        if (!notEmpty(deviceId)) return;
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        unique.add(deviceId);
        if (recentDeviceIds != null) unique.addAll(recentDeviceIds);
        recentDeviceIds = new ArrayList<>(unique);
        if (notEmpty(deviceName)) deviceNames.put(deviceId, clean(deviceName));
    }

    public String deviceDisplayName(String deviceId) {
        String alias = deviceAliases.get(deviceId);
        if (notEmpty(alias)) return clean(alias);
        String officialName = deviceNames.get(deviceId);
        if (notEmpty(officialName)) return clean(officialName);
        String suffix = deviceId == null ? "" : deviceId.substring(Math.max(0, deviceId.length() - 6));
        return suffix.isEmpty() ? "未命名设备" : "设备 " + suffix;
    }

    public void setDeviceAlias(String deviceId, String alias) {
        if (!notEmpty(deviceId)) return;
        String cleaned = clean(alias);
        if (cleaned.isEmpty()) deviceAliases.remove(deviceId);
        else deviceAliases.put(deviceId, cleaned);
    }

    public void forgetDevice(String forgottenDeviceId) {
        if (!notEmpty(forgottenDeviceId)) return;
        if (recentDeviceIds != null) recentDeviceIds.remove(forgottenDeviceId);
        deviceNames.remove(forgottenDeviceId);
        deviceAliases.remove(forgottenDeviceId);
        if (forgottenDeviceId.equals(deviceId)) {
            deviceId = recentDeviceIds == null || recentDeviceIds.isEmpty() ? "" : recentDeviceIds.get(0);
        }
    }

    public List<String> rememberedDevices() {
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        if (notEmpty(selectedDeviceId())) unique.add(selectedDeviceId());
        if (recentDeviceIds != null) unique.addAll(recentDeviceIds);
        return new ArrayList<>(unique);
    }

    private static boolean notEmpty(String s) {
        return s != null && !s.isEmpty();
    }

    private static String n(String s) {
        return s == null ? "" : s;
    }

    private static JSONObject mapJson(Map<String, String> values) throws JSONException {
        JSONObject out = new JSONObject();
        if (values != null) {
            for (Map.Entry<String, String> entry : values.entrySet()) {
                if (notEmpty(entry.getKey()) && notEmpty(entry.getValue())) {
                    out.put(entry.getKey(), entry.getValue());
                }
            }
        }
        return out;
    }

    private static void readMap(JSONObject json, Map<String, String> out) {
        if (json == null) return;
        java.util.Iterator<String> keys = json.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            String value = json.optString(key, "");
            if (notEmpty(key) && notEmpty(value)) out.put(key, value);
        }
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
