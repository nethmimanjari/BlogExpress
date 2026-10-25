package uk.ac.wlv.blogexpress;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles all SQLite database operations (CRUD + search) for messages.
 * Uses the Singleton pattern so the whole app shares one database connection.
 * All queries use "?" placeholders (parameterised queries) to prevent SQL injection.
 *
 * Version 2 adds the "is_draft" column. onUpgrade() migrates old databases
 * without deleting any existing posts.
 */
public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String DB_NAME = "blogexpress.db";
    private static final int DB_VERSION = 2;

    private static final String TABLE = "messages";
    private static final String COL_ID = "id";
    private static final String COL_TITLE = "title";
    private static final String COL_CONTENT = "content";
    private static final String COL_IMAGE = "image_path";
    private static final String COL_CREATED = "created_at";
    private static final String COL_UPDATED = "updated_at";
    private static final String COL_UPLOADED = "uploaded";
    private static final String COL_REMOTE_URL = "remote_url";
    private static final String COL_DRAFT = "is_draft";

    private static DatabaseHelper instance;

    /** Returns the single shared instance of the database helper. */
    public static synchronized DatabaseHelper getInstance(Context context) {
        if (instance == null) {
            instance = new DatabaseHelper(context.getApplicationContext());
        }
        return instance;
    }

    private DatabaseHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE + " ("
                + COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COL_TITLE + " TEXT NOT NULL, "
                + COL_CONTENT + " TEXT NOT NULL, "
                + COL_IMAGE + " TEXT, "
                + COL_CREATED + " INTEGER NOT NULL, "
                + COL_UPDATED + " INTEGER NOT NULL, "
                + COL_UPLOADED + " INTEGER NOT NULL DEFAULT 0, "
                + COL_REMOTE_URL + " TEXT, "
                + COL_DRAFT + " INTEGER NOT NULL DEFAULT 0)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Keep existing posts: only add the new column
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN " + COL_DRAFT
                    + " INTEGER NOT NULL DEFAULT 0");
        }
    }

    // ---------------- CREATE ----------------

    /** Inserts a new message and stores the generated id in the object. */
    public long insertMessage(Message m) {
        long id = getWritableDatabase().insert(TABLE, null, toValues(m));
        m.setId(id);
        return id;
    }

    // ---------------- READ ----------------

    /** Returns one message by id, or null if it does not exist. */
    public Message getMessage(long id) {
        try (Cursor c = getReadableDatabase().query(TABLE, null, COL_ID + " = ?",
                new String[]{String.valueOf(id)}, null, null, null)) {
            return c.moveToFirst() ? fromCursor(c) : null;
        }
    }

    /** Returns all messages, newest first. */
    public List<Message> getAllMessages() {
        return queryList(null, null);
    }

    /** Returns every message whose title or content contains the search text. */
    public List<Message> searchMessages(String text) {
        String like = "%" + text + "%";
        return queryList(COL_TITLE + " LIKE ? OR " + COL_CONTENT + " LIKE ?",
                new String[]{like, like});
    }

    /** Returns messages that have not been uploaded yet. */
    public List<Message> getPendingMessages() {
        return queryList(COL_UPLOADED + " = ?", new String[]{"0"});
    }

    /** Returns how many messages are stored. */
    public long getCount() {
        return DatabaseUtils.queryNumEntries(getReadableDatabase(), TABLE);
    }

    private List<Message> queryList(String where, String[] args) {
        List<Message> list = new ArrayList<>();
        try (Cursor c = getReadableDatabase().query(TABLE, null, where, args,
                null, null, COL_UPDATED + " DESC")) {
            while (c.moveToNext()) {
                list.add(fromCursor(c));
            }
        }
        return list;
    }

    // ---------------- UPDATE ----------------

    public int updateMessage(Message m) {
        return getWritableDatabase().update(TABLE, toValues(m), COL_ID + " = ?",
                new String[]{String.valueOf(m.getId())});
    }

    /** Marks a message as published online and saves the post URL. */
    public void markUploaded(long id, String url) {
        ContentValues v = new ContentValues();
        v.put(COL_UPLOADED, 1);
        v.put(COL_REMOTE_URL, url);
        getWritableDatabase().update(TABLE, v, COL_ID + " = ?", new String[]{String.valueOf(id)});
    }

    // ---------------- DELETE ----------------

    /** Deletes one message and its photo file. */
    public void deleteMessage(long id) {
        Message m = getMessage(id);
        if (m != null) {
            ImageUtils.deleteImage(m.getImagePath());
        }
        getWritableDatabase().delete(TABLE, COL_ID + " = ?", new String[]{String.valueOf(id)});
    }

    /** Deletes a group of messages inside one transaction (all or nothing). */
    public void deleteMessages(List<Long> ids) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            for (long id : ids) {
                deleteMessage(id);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    /** Deletes every message and every photo. */
    public void deleteAllMessages() {
        for (Message m : getAllMessages()) {
            ImageUtils.deleteImage(m.getImagePath());
        }
        getWritableDatabase().delete(TABLE, null, null);
    }

    // ---------------- Helpers ----------------

    private ContentValues toValues(Message m) {
        ContentValues v = new ContentValues();
        v.put(COL_TITLE, m.getTitle());
        v.put(COL_CONTENT, m.getContent());
        v.put(COL_IMAGE, m.getImagePath());
        v.put(COL_CREATED, m.getCreatedAt());
        v.put(COL_UPDATED, m.getUpdatedAt());
        v.put(COL_UPLOADED, m.isUploaded() ? 1 : 0);
        v.put(COL_REMOTE_URL, m.getRemoteUrl());
        v.put(COL_DRAFT, m.isDraft() ? 1 : 0);
        return v;
    }

    private Message fromCursor(Cursor c) {
        Message m = new Message();
        m.setId(c.getLong(c.getColumnIndexOrThrow(COL_ID)));
        m.setTitle(c.getString(c.getColumnIndexOrThrow(COL_TITLE)));
        m.setContent(c.getString(c.getColumnIndexOrThrow(COL_CONTENT)));
        m.setImagePath(c.getString(c.getColumnIndexOrThrow(COL_IMAGE)));
        m.setCreatedAt(c.getLong(c.getColumnIndexOrThrow(COL_CREATED)));
        m.setUpdatedAt(c.getLong(c.getColumnIndexOrThrow(COL_UPDATED)));
        m.setUploaded(c.getInt(c.getColumnIndexOrThrow(COL_UPLOADED)) == 1);
        m.setRemoteUrl(c.getString(c.getColumnIndexOrThrow(COL_REMOTE_URL)));
        m.setDraft(c.getInt(c.getColumnIndexOrThrow(COL_DRAFT)) == 1);
        return m;
    }
}