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


    public boolean add(String key, String value) {
        TYPE type = mKeys.get(key);
        if (type != null) {
            return op(key, value, type, OPERATION_ADD);
        }
        return false;
    }

    public boolean delete(String key, String value) {
        TYPE type = mKeys.get(key);
        if (type != null) {
            return op(key, value, type, OPERATION_DELETE);
        }
        return false;
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

    public void update(String key, String oldValue, String newValue) {
        if (delete(key, oldValue)) {
            add(key, newValue);
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

    private boolean op(DBKey key, String value, int operation) {
        Log.d(TAG, String.format("op: key=%s,value=%s,type=%s,op=%d", key, value, key.getType(), operation));
        if (TextUtils.isEmpty(value)) {
            return false;
        }
        if (value.contains(splitChar)) {
            return false;
        }
        List<String> list = query(key);
        Log.d(TAG, "query: " + list);
        if (operation == OPERATION_DELETE && list == null) {
            Log.d(TAG, "op: return");
            return false;
        }

        if (operation == OPERATION_ADD) {
            if (list == null) {
                Log.d(TAG, "op: new list");
                list = new ArrayList<>();
            } else if (list.contains(value)) {
                Log.d(TAG, "list contains " + value);
                return true;
            }
        }


        boolean result =
                operation == OPERATION_ADD ? list.add(value) :
                operation == OPERATION_DELETE && list.contains(value) && list.remove(value);
        Log.d(TAG, "result: " + result);

        if (!result) {
            return false;
        }
        String collect = list.stream().collect(Collectors.joining(splitChar));
        Log.d(TAG, "collect:" + collect);
        long ident = Binder.clearCallingIdentity();
        try {
            switch (key.getType()) {
                case Global:
                    Settings.Global.putString(mContentResolver, key.getKey(), collect);
                    break;
                case Secure:
                    Settings.Secure.putString(mContentResolver, key.getKey(), collect);
                    break;
                case System:
                    Settings.System.putString(mContentResolver, key.getKey(), collect);
                    break;
            }
        } finally {
            Binder.restoreCallingIdentity(ident);
        }

        return true;

    }

}
