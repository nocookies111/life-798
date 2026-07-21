package com.water.widget;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 多账户存储。账户列表 + 当前选中账户，持久化在 SharedPreferences（JSON）。
 * 同时负责从旧版单账户配置（water_cfg）迁移。
 */
public class AccountStore {
    private static final String PREFS = "accounts_cfg";
    private static final String KEY_LIST = "accounts";
    private static final String KEY_CURRENT = "current_phone";
    private static final String KEY_MIGRATED = "migrated_v3";

    private static final String OLD_PREFS = "water_cfg";

    public static List<Account> list(Context ctx) {
        ensureMigrated(ctx);
        List<Account> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(sp(ctx).getString(KEY_LIST, "[]"));
            for (int i = 0; i < arr.length(); i++) {
                out.add(Account.fromJson(arr.getJSONObject(i)));
            }
        } catch (JSONException ignored) {}
        return out;
    }

    public static Account getCurrent(Context ctx) {
        ensureMigrated(ctx);
        String curPhone = sp(ctx).getString(KEY_CURRENT, "");
        for (Account a : list(ctx)) {
            if (a.phone.equals(curPhone)) return a;
        }
        // 当前指向的账户不存在了，回退到第一个
        List<Account> all = list(ctx);
        if (!all.isEmpty()) {
            setCurrent(ctx, all.get(0).phone);
            return all.get(0);
        }
        return null;
    }

    /** 按手机号查找账户，找不到返回 null。 */
    public static Account get(Context ctx, String phone) {
        for (Account a : list(ctx)) {
            if (a.phone.equals(phone)) return a;
        }
        return null;
    }

    public static void addOrUpdate(Context ctx, Account acc) {
        addOrUpdateKeepingCurrent(ctx, acc);
        setCurrent(ctx, acc.phone);
    }

    /** 保存账户但不改变当前选中账户。用于后台补全和编辑非当前账户。 */
    public static void addOrUpdateKeepingCurrent(Context ctx, Account acc) {
        List<Account> all = list(ctx);
        boolean found = false;
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).phone.equals(acc.phone)) {
                all.set(i, acc);
                found = true;
                break;
            }
        }
        if (!found) all.add(acc);
        save(ctx, all);
    }

    public static void updateCurrent(Context ctx, Account acc) {
        addOrUpdate(ctx, acc);
    }

    public static void remove(Context ctx, String phone) {
        List<Account> all = list(ctx);
        List<Account> out = new ArrayList<>();
        for (Account a : all) {
            if (!a.phone.equals(phone)) out.add(a);
        }
        save(ctx, out);
        if (sp(ctx).getString(KEY_CURRENT, "").equals(phone)) {
            sp(ctx).edit().putString(KEY_CURRENT,
                    out.isEmpty() ? "" : out.get(0).phone).apply();
        }
    }

    public static void setCurrent(Context ctx, String phone) {
        sp(ctx).edit().putString(KEY_CURRENT, phone).apply();
    }

    /** 从旧版 water_cfg（单 token + dids）迁移为第一个账户。仅执行一次。 */
    private static void ensureMigrated(Context ctx) {
        SharedPreferences sp = sp(ctx);
        if (sp.getBoolean(KEY_MIGRATED, false)) return;
        sp.edit().putBoolean(KEY_MIGRATED, true).apply();

        SharedPreferences old = ctx.getSharedPreferences(OLD_PREFS, Context.MODE_PRIVATE);
        String token = old.getString("token", "");
        String hot = old.getString("hot_did", "");
        String cold = old.getString("cold_did", "");
        if (!token.isEmpty()) {
            Account a = new Account("已导入的账户");
            a.token = token;
            a.hotDid = hot;
            a.coldDid = cold;
            a.name = "已导入的账户";
            addOrUpdate(ctx, a);
        }
    }

    private static void save(Context ctx, List<Account> all) {
        JSONArray arr = new JSONArray();
        for (Account a : all) arr.put(a.toJson());
        sp(ctx).edit().putString(KEY_LIST, arr.toString()).apply();
    }

    private static SharedPreferences sp(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
