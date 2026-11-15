package app.photos.unlimited;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Private metadata cache. No operation in this class deletes local or remote media. */
public final class InventoryDb extends SQLiteOpenHelper {
  public record Scan(
      LibraryProtocol.Cursor cursor, String generation, int pages, long updated, String message) {}

  public record Summary(
      long items, long chargedItems, long chargedBytes, long unknown, long zero, Scan scan) {}

  public InventoryDb(Context context) {
    super(context, "inventory.db", null, 1);
  }

  @Override
  public void onCreate(SQLiteDatabase db) {
    db.execSQL(
        "CREATE TABLE scans(account TEXT PRIMARY KEY,generation TEXT NOT NULL,stage TEXT NOT"
            + " NULL,resume BLOB NOT NULL,sync BLOB NOT NULL,next_sync BLOB NOT NULL,more INTEGER"
            + " NOT NULL,pages INTEGER NOT NULL,updated INTEGER NOT NULL,message TEXT NOT NULL)");
    db.execSQL(
        "CREATE TABLE media(account TEXT NOT NULL,media_key TEXT NOT NULL,name TEXT NOT NULL,type"
            + " INTEGER NOT NULL,size INTEGER,charged INTEGER,taken INTEGER NOT NULL,trash INTEGER"
            + " NOT NULL,archived INTEGER NOT NULL,origin INTEGER NOT NULL,PRIMARY"
            + " KEY(account,media_key))");
    db.execSQL("CREATE INDEX charged_media ON media(account,charged DESC)");
  }

  @Override
  public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
    throw new IllegalStateException("Unknown inventory schema");
  }

  public Scan scan(String account) {
    try (Cursor c =
        getReadableDatabase()
            .query("scans", null, "account=?", new String[] {account}, null, null, null)) {
      if (!c.moveToFirst())
        return new Scan(LibraryProtocol.Cursor.fresh(), "", 0, 0, "Ready to scan");
      return new Scan(
          new LibraryProtocol.Cursor(
              LibraryProtocol.Stage.valueOf(s(c, "stage")),
              c.getBlob(c.getColumnIndexOrThrow("resume")),
              c.getBlob(c.getColumnIndexOrThrow("sync")),
              c.getBlob(c.getColumnIndexOrThrow("next_sync")),
              n(c, "more") == 1),
          s(c, "generation"),
          (int) n(c, "pages"),
          n(c, "updated"),
          s(c, "message"));
    }
  }

  public Scan reset(String account) {
    SQLiteDatabase db = getWritableDatabase();
    db.beginTransaction();
    try {
      clear(account);
      ContentValues v = cursorValues(LibraryProtocol.Cursor.fresh());
      v.put("account", account);
      v.put("generation", UUID.randomUUID().toString());
      v.put("pages", 0);
      v.put("updated", System.currentTimeMillis());
      v.put("message", "Scanning Google Photos…");
      db.insertOrThrow("scans", null, v);
      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }
    return scan(account);
  }

  public void clear(String account) {
    getWritableDatabase().delete("media", "account=?", new String[] {account});
    getWritableDatabase().delete("scans", "account=?", new String[] {account});
  }

  public boolean apply(
      String account, Scan before, LibraryProtocol.Page page, LibraryProtocol.Cursor after) {
    SQLiteDatabase db = getWritableDatabase();
    db.beginTransaction();
    try {
      if (!before.generation.equals(scan(account).generation)) return false;
      for (LibraryProtocol.Item item : page.items()) {
        ContentValues v = new ContentValues();
        v.put("account", account);
        v.put("media_key", item.key());
        v.put("name", item.name());
        v.put("type", item.type());
        v.put("size", item.size());
        v.put("charged", item.charged());
        v.put("taken", item.taken());
        v.put("trash", item.trash() ? 1 : 0);
        v.put("archived", item.archived() ? 1 : 0);
        v.put("origin", item.origin());
        db.insertWithOnConflict("media", null, v, SQLiteDatabase.CONFLICT_REPLACE);
      }
      for (String key : page.deletions())
        db.delete("media", "account=? AND media_key=?", new String[] {account, key});
      ContentValues v = cursorValues(after);
      v.put("pages", before.pages + 1);
      v.put("updated", System.currentTimeMillis());
      v.put(
          "message",
          after.stage() == LibraryProtocol.Stage.DONE
              ? "Scan finished"
              : "Scanning Google Photos…");
      db.update(
          "scans", v, "account=? AND generation=?", new String[] {account, before.generation});
      db.setTransactionSuccessful();
      return true;
    } finally {
      db.endTransaction();
    }
  }

  public void message(String account, String generation, String message) {
    ContentValues v = new ContentValues();
    v.put("message", message);
    getWritableDatabase()
        .update("scans", v, "account=? AND generation=?", new String[] {account, generation});
  }

  public Summary summary(String account) {
    try (Cursor c =
        getReadableDatabase()
            .rawQuery(
                "SELECT COUNT(*),COALESCE(SUM(CASE WHEN charged>0 THEN 1 ELSE 0"
                    + " END),0),COALESCE(SUM(charged),0),COALESCE(SUM(CASE WHEN charged IS NULL"
                    + " THEN 1 ELSE 0 END),0),COALESCE(SUM(CASE WHEN charged=0 THEN 1 ELSE 0"
                    + " END),0) FROM media WHERE account=?",
                new String[] {account})) {
      c.moveToFirst();
      return new Summary(
          c.getLong(0), c.getLong(1), c.getLong(2), c.getLong(3), c.getLong(4), scan(account));
    }
  }

  public List<LibraryProtocol.Item> list(String account, int quotaFilter, int type, int offset) {
    String where =
        "account=?"
            + (quotaFilter == 1 ? " AND charged>0" : quotaFilter == 2 ? " AND charged IS NULL" : "")
            + (type > 0 ? " AND type=" + type : "");
    List<LibraryProtocol.Item> list = new ArrayList<>();
    try (Cursor c =
        getReadableDatabase()
            .query(
                "media",
                null,
                where,
                new String[] {account},
                null,
                null,
                "charged IS NULL ASC, charged DESC, name COLLATE NOCASE ASC, media_key ASC",
                Math.max(0, offset) + ",51")) {
      while (c.moveToNext())
        list.add(
            new LibraryProtocol.Item(
                s(c, "media_key"),
                s(c, "name"),
                (int) n(c, "type"),
                optional(c, "size"),
                optional(c, "charged"),
                n(c, "taken"),
                n(c, "trash") == 1,
                n(c, "archived") == 1,
                (int) n(c, "origin")));
    }
    return list;
  }

  private static ContentValues cursorValues(LibraryProtocol.Cursor cursor) {
    ContentValues v = new ContentValues();
    v.put("stage", cursor.stage().name());
    v.put("resume", cursor.resume());
    v.put("sync", cursor.sync());
    v.put("next_sync", cursor.nextSync());
    v.put("more", cursor.more() ? 1 : 0);
    return v;
  }

  private static String s(Cursor c, String key) {
    return c.getString(c.getColumnIndexOrThrow(key));
  }

  private static long n(Cursor c, String key) {
    return c.getLong(c.getColumnIndexOrThrow(key));
  }

  private static Long optional(Cursor c, String key) {
    int i = c.getColumnIndexOrThrow(key);
    return c.isNull(i) ? null : c.getLong(i);
  }
}
