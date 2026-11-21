package app.photos.unlimited;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.*;

/** One-way upload ledger, scoped to account and local media version. Never deletes media. */
public final class BackupDb extends SQLiteOpenHelper {
  public record Item(
      long id,
      String uri,
      String name,
      String folder,
      String mime,
      long size,
      long modified,
      long taken,
      int attempts,
      String state) {}

  public BackupDb(Context context) {
    super(context, "uploads.db", null, 1);
  }

  @Override
  public void onCreate(SQLiteDatabase db) {
    db.execSQL(
        "CREATE TABLE uploads (id INTEGER PRIMARY KEY, account TEXT NOT NULL, uri TEXT NOT NULL,"
            + " name TEXT NOT NULL, folder TEXT NOT NULL, mime TEXT NOT NULL, size INTEGER NOT"
            + " NULL, modified INTEGER NOT NULL, taken INTEGER NOT NULL, state TEXT NOT NULL"
            + " DEFAULT 'ready', attempts INTEGER NOT NULL DEFAULT 0, retry_at INTEGER NOT NULL"
            + " DEFAULT 0, remote_key TEXT, error TEXT, updated INTEGER NOT NULL,"
            + " UNIQUE(account,uri))");
    db.execSQL("CREATE INDEX pending ON uploads(account,state,retry_at)");
  }

  @Override
  public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
    throw new IllegalStateException("Unknown database upgrade");
  }

  public void discover(
      String account,
      String uri,
      String name,
      String folder,
      String mime,
      long size,
      long modified,
      long taken) {
    SQLiteDatabase db = getWritableDatabase();
    ContentValues values = new ContentValues();
    values.put("account", account);
    values.put("uri", uri);
    values.put("name", name);
    values.put("folder", folder);
    values.put("mime", mime);
    values.put("size", size);
    values.put("modified", modified);
    values.put("taken", taken);
    values.put("updated", System.currentTimeMillis());
    if (db.insertWithOnConflict("uploads", null, values, SQLiteDatabase.CONFLICT_IGNORE) == -1) {
      // Keep completed/failed versions intact. Only changed local bytes become candidates again.
      ContentValues changed = new ContentValues(values);
      changed.put("state", "ready");
      changed.put("attempts", 0);
      changed.put("retry_at", 0);
      changed.putNull("remote_key");
      changed.putNull("error");
      db.update(
          "uploads",
          changed,
          "account=? AND uri=? AND (size<>? OR modified<>?)",
          new String[] {account, uri, Long.toString(size), Long.toString(modified)});
      ContentValues path = new ContentValues();
      path.put("folder", folder);
      path.put("name", name);
      db.update("uploads", path, "account=? AND uri=?", new String[] {account, uri});
    }
  }

  public void recover(String account) {
    ContentValues v = new ContentValues();
    v.put("state", "ready");
    getWritableDatabase()
        .update("uploads", v, "account=? AND state='running'", new String[] {account});
  }

  public Item next(String account, Set<String> folders) {
    if (folders.isEmpty()) return null;
    SQLiteDatabase db = getWritableDatabase();
    db.beginTransaction();
    try {
      List<String> args =
          new ArrayList<>(List.of(account, Long.toString(System.currentTimeMillis())));
      args.addAll(folders);
      String where =
          "account=? AND state IN ('ready','error') AND retry_at<=? AND folder IN ("
              + String.join(",", Collections.nCopies(folders.size(), "?"))
              + ")";
      Item item = null;
      try (Cursor c =
          db.query(
              "uploads",
              null,
              where,
              args.toArray(new String[0]),
              null,
              null,
              "modified DESC, id DESC",
              "1")) {
        if (c.moveToFirst()) item = item(c);
      }
      if (item != null) {
        ContentValues v = new ContentValues();
        v.put("state", "running");
        v.put("updated", System.currentTimeMillis());
        db.update("uploads", v, "id=?", new String[] {Long.toString(item.id)});
      }
      db.setTransactionSuccessful();
      return item;
    } finally {
      db.endTransaction();
    }
  }

  public void complete(Item item, String key, boolean existing) {
    ContentValues v = new ContentValues();
    v.put("state", existing ? "existing" : "done");
    v.put("remote_key", key);
    v.putNull("error");
    save(item, v);
  }

  public void release(Item item) {
    ContentValues v = new ContentValues();
    v.put("state", "ready");
    save(item, v);
  }

  public void fail(Item item, String message, boolean permanent) {
    ContentValues v = new ContentValues();
    v.put("state", permanent ? "blocked" : "error");
    v.put("error", message);
    v.put("attempts", item.attempts + 1);
    long delay = Math.min(6 * 60 * 60 * 1000L, 30000L << Math.min(item.attempts, 10));
    v.put("retry_at", System.currentTimeMillis() + delay);
    save(item, v);
  }

  public void retry(String account) {
    ContentValues v = new ContentValues();
    v.put("state", "ready");
    v.put("attempts", 0);
    v.put("retry_at", 0);
    v.putNull("error");
    getWritableDatabase()
        .update("uploads", v, "account=? AND state IN ('error','blocked')", new String[] {account});
  }

  private void save(Item item, ContentValues values) {
    values.put("updated", System.currentTimeMillis());
    getWritableDatabase().update("uploads", values, "id=?", new String[] {Long.toString(item.id)});
  }

  public Map<String, Integer> counts(String account) {
    Map<String, Integer> result = new HashMap<>();
    try (Cursor c =
        getReadableDatabase()
            .rawQuery(
                "SELECT state,COUNT(*) FROM uploads WHERE account=? GROUP BY state",
                new String[] {account})) {
      while (c.moveToNext()) result.put(c.getString(0), c.getInt(1));
    }
    return result;
  }

  public Map<String, Integer> counts(String account, Set<String> folders) {
    Map<String, Integer> result = new HashMap<>();
    if (folders.isEmpty()) return result;
    List<String> args = new ArrayList<>(List.of(account));
    args.addAll(folders);
    try (Cursor c =
        getReadableDatabase()
            .rawQuery(
                "SELECT state,COUNT(*) FROM uploads WHERE account=? AND folder IN ("
                    + String.join(",", Collections.nCopies(folders.size(), "?"))
                    + ") GROUP BY state",
                args.toArray(new String[0]))) {
      while (c.moveToNext()) result.put(c.getString(0), c.getInt(1));
    }
    return result;
  }

  public List<String> recent(String account) {
    List<String> result = new ArrayList<>();
    try (Cursor c =
        getReadableDatabase()
            .query(
                "uploads",
                new String[] {"name", "state", "error"},
                "account=? AND state<>'ready'",
                new String[] {account},
                null,
                null,
                "updated DESC",
                "50")) {
      while (c.moveToNext()) {
        String state =
            switch (c.getString(1)) {
              case "done" -> Text.get(R.string.history_uploaded);
              case "existing" -> Text.get(R.string.history_existing);
              case "running" -> Text.get(R.string.history_uploading);
              default -> c.getString(2) == null ? Text.get(R.string.history_waiting) : c.getString(2);
            };
        result.add(c.getString(0) + "\n" + state);
      }
    }
    return result;
  }

  public boolean pending(String account, Set<String> folders) {
    if (folders.isEmpty()) return false;
    List<String> args = new ArrayList<>(List.of(account));
    args.addAll(folders);
    try (Cursor c =
        getReadableDatabase()
            .rawQuery(
                "SELECT 1 FROM uploads WHERE account=? AND state IN ('ready','running','error') AND"
                    + " folder IN ("
                    + String.join(",", Collections.nCopies(folders.size(), "?"))
                    + ") LIMIT 1",
                args.toArray(new String[0]))) {
      return c.moveToFirst();
    }
  }

  public long nextAttemptAt(String account, Set<String> folders) {
    if (folders.isEmpty()) return 0;
    List<String> args = new ArrayList<>(List.of(account));
    args.addAll(folders);
    try (Cursor c =
        getReadableDatabase()
            .rawQuery(
                "SELECT MIN(retry_at) FROM uploads WHERE account=? AND state IN"
                    + " ('ready','running','error') AND folder IN ("
                    + String.join(",", Collections.nCopies(folders.size(), "?"))
                    + ")",
                args.toArray(new String[0]))) {
      return c.moveToFirst() ? c.getLong(0) : 0;
    }
  }

  private static Item item(Cursor c) {
    return new Item(
        c.getLong(c.getColumnIndexOrThrow("id")),
        s(c, "uri"),
        s(c, "name"),
        s(c, "folder"),
        s(c, "mime"),
        l(c, "size"),
        l(c, "modified"),
        l(c, "taken"),
        c.getInt(c.getColumnIndexOrThrow("attempts")),
        s(c, "state"));
  }

  private static String s(Cursor c, String key) {
    return c.getString(c.getColumnIndexOrThrow(key));
  }

  private static long l(Cursor c, String key) {
    return c.getLong(c.getColumnIndexOrThrow(key));
  }
}
