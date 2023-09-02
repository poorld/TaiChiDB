package com.lckj.dpm;

import android.content.ContentResolver;
import android.content.Context;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * TaiChi
 *
 * AKA quantum entanglement
 */
public class TaiChi {

    private static final String TAG = "TinyDB";
    private ContentResolver mContentResolver;

    public static final int OPERATION_ADD = 1;
    public static final int OPERATION_DELETE = 2;

    private static final String splitChar = "☯";

    public TaiChi(Context context) {
        mContentResolver = context.getContentResolver();
        mKeys = new HashMap<>();
    }

    public enum TYPE {
        Global, Secure, System
    }

    // Global, Secure, System
    private Map<String, TYPE> mKeys;

    public void registerKey(String key, TYPE type) {
        mKeys.putIfAbsent(key, type);
    }


    public void add(String key, String value) {
        TYPE type = mKeys.get(key);
        if (type != null) {
            op(key, value, type, OPERATION_ADD);
        }
    }

    public void delete(String key, String value) {
        TYPE type = mKeys.get(key);
        if (type != null) {
            op(key, value, type, OPERATION_DELETE);
        }
    }

    public void deleteAll(String key) {
        TYPE type = mKeys.get(key);
        switch (type) {
            case Global:
                Settings.Global.putString(mContentResolver, key, "");
                break;
            case Secure:
                Settings.Secure.putString(mContentResolver, key, "");
                break;
            case System:
                Settings.System.putString(mContentResolver, key, "");
                break;
        }
    }

    public Set<String> query(String key) {
        if (TextUtils.isEmpty(key)) {
            return null;
        }
        TYPE type = mKeys.get(key);
        if (type == null) {
            return null;
        }
        String values = null;
        switch (type) {
            case Global:
                values = Settings.Global.getString(mContentResolver, key);
                break;
            case Secure:
                values = Settings.Secure.getString(mContentResolver, key);
                break;
            case System:
                values = Settings.System.getString(mContentResolver, key);
                break;
        }
        if (!TextUtils.isEmpty(values)) {
            return Arrays.stream(values.split(splitChar)).collect(Collectors.toSet());
        }
        return null;
    }

    private void op(String key, String value, TYPE type, int operation) {
        if (TextUtils.isEmpty(value)) {
            return;
        }
        if (value.contains(splitChar)) {
            return;
        }
        Set<String> list = query(key);
        if (operation == OPERATION_DELETE && list == null) {
            return;
        }

        if (operation == OPERATION_ADD && list == null) {
            list = new HashSet<>();
        }

        boolean result =
                operation == OPERATION_ADD ? list.add(value) :
                operation == OPERATION_DELETE && list.contains(value) && list.remove(value);
        Log.d(TAG, "result: " + result);

        if (!result) {
            return;
        }
        String collect = list.stream().collect(Collectors.joining(splitChar));
        Log.d(TAG, "collect:" + collect);
        switch (type) {
            case Global:
                Settings.Global.putString(mContentResolver, key, collect);
                break;
            case Secure:
                Settings.Secure.putString(mContentResolver, key, collect);
                break;
            case System:
                Settings.System.putString(mContentResolver, key, collect);
                break;
        }

    }

}
