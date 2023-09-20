TinyDBService.java(services层)
```java
package xxx;

import android.xxx.db.DBKey;
import android.xxx.sys.aidl.ITinyDBService;
import android.content.ContentResolver;
import android.content.Context;
import android.os.Binder;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


public class TinyDBService extends ITinyDBService.Stub {

    private static final String TAG = "TinyDB";

    private static final int OPERATION_ADD = 1;
    private static final int OPERATION_DELETE = 2;

    private static final String splitChar = "☯";

    private ContentResolver mContentResolver;

    // Global, Secure, System
    private Map<String, DBKey> mKeys;

    public TinyDBService(Context context) {
        mContentResolver = context.getContentResolver();
        mKeys = new HashMap<>();
    }

    @Override
    public void registerKey(DBKey key) {
        Log.d(TAG, "registerKey: " + key.getKey());
        mKeys.putIfAbsent(key.getKey(), key);
    }

    @Override
    public boolean add(DBKey key, String value) {
        DBKey registeredKey = mKeys.get(key.getKey());
        if (registeredKey != null) {
            return op(key, value, OPERATION_ADD);
        }
        return false;
    }

    @Override
    public boolean delete(DBKey key, String value) {
        DBKey registeredKey = mKeys.get(key.getKey());
        if (registeredKey != null) {
            return op(key, value, OPERATION_DELETE);
        }
        return false;
    }

    @Override
    public void deleteAll(DBKey key) {
        DBKey registeredKey = mKeys.get(key.getKey());
        long ident = Binder.clearCallingIdentity();
        try {
            if (registeredKey != null) {
                switch (key.getType()) {
                    case Global:
                        Settings.Global.putString(mContentResolver, key.getKey(), "");
                        break;
                    case Secure:
                        Settings.Secure.putString(mContentResolver, key.getKey(), "");
                        break;
                    case System:
                        Settings.System.putString(mContentResolver, key.getKey(), "");
                        break;
                }
            }
        } finally {
            Binder.restoreCallingIdentity(ident);
        }
    }

    @Override
    public void update(DBKey key, String oldValue, String newValue) {
        if (delete(key, oldValue)) {
            add(key, newValue);
        }
    }

    @Override
    public List<String> query(DBKey key) {

        DBKey registeredKey = mKeys.get(key.getKey());
        if (registeredKey == null) {
            return null;
        }
        String values = null;
        switch (key.getType()) {
            case Global:
                values = Settings.Global.getString(mContentResolver, key.getKey());
                break;
            case Secure:
                values = Settings.Secure.getString(mContentResolver, key.getKey());
                break;
            case System:
                values = Settings.System.getString(mContentResolver, key.getKey());
                break;
        }
        if (!TextUtils.isEmpty(values)) {
            return Arrays.stream(values.split(splitChar)).collect(Collectors.toList());
        }
        return null;
    }

    @Override
    public boolean contains(DBKey key, String value) {
        if (TextUtils.isEmpty(value)) {
            return false;
        }

        List<String> list = query(key);
        if (list != null) {
            return list.contains(value);
        }
        return false;
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

```

ITinyDBService.aidl(manager层)
```
package android.xxx.sys.aidl;

import android.xxx.db.DBKey;
import java.util.List;

interface ITinyDBService {

    void registerKey(in DBKey key);
    boolean add(in DBKey key, String value);
    boolean delete(in DBKey key, String value);
    void deleteAll(in DBKey key);
    void update(in DBKey key, String oldValue, String newValue);
    List<String> query(in DBKey key);
    boolean contains(in DBKey key, String value);
}
```

DBKey.java(manager层)
```java
package android.xxx.db;

import android.os.Parcel;
import android.os.Parcelable;

public enum DBKey implements Parcelable {
    // 在这里注册db
    MY_NTP("my_ntp_server", TYPE.Global),
    YOUR_LIST("your_list", TYPE.Secure)
    ;

    public static DBKey getDBKey(String key) {
        for (DBKey k : DBKey.values()) {
            if (k.mKey.equals(key)) {
                return k;
            }
        }
        return null;
    }


    public enum TYPE implements Parcelable {
        Global, Secure, System;

        public static TYPE fromInteger(int value)
        {
            return values()[value];
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            //dest.writeInt(this.ordinal());
            dest.writeString(name());
        }
        
        public static final Creator<TYPE> CREATOR = new Creator<TYPE>() {
            @Override
            public TYPE createFromParcel(Parcel in) {
                //return TYPE.fromInteger(in.readInt());
                return TYPE.valueOf(in.readString());
            }

            @Override
            public TYPE[] newArray(int size) {
                return new TYPE[size];
            }
        };
    }

    private String mKey;
    private TYPE mType;

    DBKey(String key, TYPE type) {
        mKey = key;
        mType = type;
    }



    public String getKey() {
        return mKey;
    }

    public TYPE getType() {
        return mType;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(this.mKey);
        //dest.writeParcelable(mType, 0);
    }

    public static final Creator<DBKey> CREATOR = new Creator<DBKey>() {
        @Override
        public DBKey createFromParcel(Parcel in) {
            //return DBKey.valueOf(in.readString());
            return DBKey.getDBKey(in.readString());
        }

        @Override
        public DBKey[] newArray(int size) {
            return new DBKey[size];
        }
    };

}

```

DBKey.aidl(manager层)
```
package android.xxx.db;

parcelable DBKey;
```

TinyDBManager.java(manager层)
```
package android.xxx.db;

import android.xxx.sys.aidl.ITinyDBService;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceManager;

//import com.android.server.blovedream.db.TinyDBService;

import java.util.List;

public class TinyDBManager {
    private static final String TINYDB_SERVICE_NAME = "your_tiny_db";
    private ITinyDBService tinyDB;
    //private TinyDBService tinyDB;

    public TinyDBManager() {
        tinyDB = getServiceInterface();
    }


    private ITinyDBService getServiceInterface() {
        IBinder b = ServiceManager.getService(TINYDB_SERVICE_NAME);
        if (b == null) {
            return null;
        }
        return ITinyDBService.Stub.asInterface(b);
    }


    public void registerKey(DBKey key) {
        if (tinyDB == null) {
            return;
        }
        try {
            tinyDB.registerKey(key);
        } catch (RemoteException e) {
        }

    }

    public boolean add(DBKey key, String value) {
        if (tinyDB == null) {
            return false;
        }
        try {
            return tinyDB.add(key, value);
        } catch (RemoteException e) {
        }
        return false;
    }

    public boolean delete(DBKey key, String value) {
        if (tinyDB == null) {
            return false;
        }
        try {
            return tinyDB.delete(key, value);
        } catch (RemoteException e) {
        }
        return false;
    }
    public boolean deleteAll(DBKey key) {
        if (tinyDB == null) {
            return false;
        }
        try {
            tinyDB.deleteAll(key);
        } catch (RemoteException e) {
        }
        return false;
    }


    public boolean update(DBKey key, String oldValue, String newValue) {
        if (tinyDB == null) {
            return false;
        }
        try {
            tinyDB.update(key, oldValue, newValue);
        } catch (RemoteException e) {
        }
        return false;
    }

    public List<String> query(DBKey key) {
        if (tinyDB == null) {
            return null;
        }
        try {
            return tinyDB.query(key);
        } catch (RemoteException e) {
        }
        return null;
    }

    public boolean contains(DBKey key, String value) {
        if (tinyDB == null) {
            return false;
        }
        try {
            tinyDB.contains(key, value);
        } catch (RemoteException e) {
        }
        return false;
    }
}

```

SystemServer.java
```
try {
    Slog.i(TAG, "add tiny db");
    mTinyDBService = new TinyDBService(context);
    ServiceManager.addService("your_tiny_db", mTinyDBService);
} catch (Throwable e) {
    Slog.e(TAG, "Failure starting tiny db Service.", e);
}
```

使用
```
dbManager = new TinyDBManager();
dbManager.registerKey(DBKey.MY_NTP);

public void addNtpServer(String ntp) {
    dbManager.add(DBKey.MY_NTP, ntp);
}

public void removeNtpServer(String ntp) {
    dbManager.delete(DBKey.MY_NTP, ntp);
}

public void clearNtpServer() {
    dbManager.deleteAll(DBKey.MY_NTP);
}

public List<String> getNtpServerList() {
    return dbManager.query(DBKey.MY_NTP);
}
```