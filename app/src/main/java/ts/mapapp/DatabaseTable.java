package ts.mapapp;

import android.content.ContentValues;
import android.content.Context;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.util.Scanner;

/**
 *
 */

public class DatabaseTable {
    private static final String TAG = "LocationDatabase";

    //The columns we'll include in the dictionary table
    public static final String COL_NAME = "NAME";
    public static final String COL_LATITUDE = "LATITUDE";
    public static final String COL_LONGITUDE = "LONGITUDE";

    private static final String DATABASE_NAME = "LOCATIONS";
    private static final String FTS_VIRTUAL_TABLE = "FTS";
    private static final int DATABASE_VERSION = 1;

    private final DatabaseOpenHelper mDatabaseOpenHelper;

    public DatabaseTable(Context context) {
        Log.e(TAG, "hello");
        mDatabaseOpenHelper = new DatabaseOpenHelper(context);
    }

    public Cursor getWordMatches(String query, String[] columns) {
        String selection = COL_NAME + " MATCH ?";
        String[] selectionArgs = new String[] {query+"*"};

        return query(selection, selectionArgs, columns);
    }

    private Cursor query(String selection, String[] selectionArgs, String[] columns) {
        SQLiteQueryBuilder builder = new SQLiteQueryBuilder();
        builder.setTables(FTS_VIRTUAL_TABLE);

        Cursor cursor = builder.query(mDatabaseOpenHelper.getReadableDatabase(),
                columns, selection, selectionArgs, null, null, null);

        if (cursor == null) {
            return null;
        } else if (!cursor.moveToFirst()) {
            cursor.close();
            return null;
        }
        return cursor;
    }

    private static class DatabaseOpenHelper extends SQLiteOpenHelper {

        private final Context mHelperContext;
        private SQLiteDatabase mDatabase;

        private static final String FTS_TABLE_CREATE =
                "CREATE VIRTUAL TABLE " + FTS_VIRTUAL_TABLE +
                        " USING fts3 (" +
                        COL_NAME + ", " +
                        COL_LATITUDE + ", " +
                        COL_LONGITUDE + ")";

        DatabaseOpenHelper(Context context) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
            mHelperContext = context;
            onCreate(super.getReadableDatabase());
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            Log.e(TAG, "creating...");
            mDatabase = db;
            mDatabase.execSQL("DROP TABLE IF EXISTS " + FTS_VIRTUAL_TABLE);
            mDatabase.execSQL(FTS_TABLE_CREATE);
            Log.e(TAG, "exec'd");
            loadLocationsTable();
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            Log.w(TAG, "Upgrading database from version " + oldVersion + " to "
                    + newVersion + ", which will destroy all old data");
            db.execSQL("DROP TABLE IF EXISTS " + FTS_VIRTUAL_TABLE);
            onCreate(db);
        }

        private void loadLocationsTable() {
            new Thread(new Runnable() {
                public void run() {
                    try {
                        loadLocations();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            }).start();
        }

        private void loadLocations() throws IOException {
            final Resources resources = mHelperContext.getResources();
            InputStream inputStream = resources.openRawResource(R.raw.locations);
            //BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            Scanner scanner = new Scanner(inputStream);
            String name;
            while (scanner.hasNext()) {
                name = scanner.next();
                while (!scanner.hasNextDouble()) {
                    name = name + " " + scanner.next();
                }
                Log.e(TAG, name);
                double lat = scanner.nextDouble();
                double lon = scanner.nextDouble();
                Log.e(TAG, String.valueOf(lat) + " " + String.valueOf(lon));
                long id = addLocation(name, lat, lon);
                if (id < 0) {
                    Log.e(TAG, "unable to add location: " + name);
                }
            }
            scanner.close();
        }

        public long addLocation(String name, double latitude, double longitude) {
            ContentValues initialValues = new ContentValues();
            initialValues.put(COL_NAME, name);
            initialValues.put(COL_LATITUDE, latitude);
            initialValues.put(COL_LONGITUDE, longitude);

            return mDatabase.insert(FTS_VIRTUAL_TABLE, null, initialValues);
        }
    }
}
