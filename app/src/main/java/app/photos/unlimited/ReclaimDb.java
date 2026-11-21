package app.photos.unlimited;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.ArrayList;
import java.util.List;

/**
 * Durable reclaim journal. Separate from the inventory cache, which may be dropped and rebuilt:
 * this file records which originals were deleted and where their only copy is kept.
 */
public final class ReclaimDb extends SQLiteOpenHelper implements ReclaimEngine.Journal {
  public ReclaimDb(Context context) {
    super(context, "reclaim.db", null, 4);
    setWriteAheadLoggingEnabled(false);
  }

  @Override
  public void onConfigure(SQLiteDatabase db) {
    db.execSQL("PRAGMA synchronous=FULL");
  }

  @Override
  public void onCreate(SQLiteDatabase db) {
    db.execSQL(
        "CREATE TABLE jobs(account TEXT NOT NULL,media_key TEXT NOT NULL,name TEXT NOT NULL,type"
            + " INTEGER NOT NULL,size INTEGER NOT NULL,charged INTEGER,taken INTEGER NOT"
            + " NULL,dedup TEXT NOT NULL,sha1 BLOB NOT NULL,caption TEXT NOT NULL,favorite"
            + " INTEGER NOT NULL,archived INTEGER NOT NULL,state TEXT NOT NULL,file TEXT NOT"
            + " NULL,new_key TEXT NOT NULL,new_charged INTEGER,attempts INTEGER NOT NULL,message"
            + " TEXT NOT NULL,created INTEGER NOT NULL,updated INTEGER NOT NULL,PRIMARY"
            + " KEY(account,media_key))");
    db.execSQL("ALTER TABLE jobs ADD COLUMN reduced INTEGER NOT NULL DEFAULT 0");
    db.execSQL("ALTER TABLE jobs ADD COLUMN phone TEXT NOT NULL DEFAULT ''");
    db.execSQL("ALTER TABLE jobs ADD COLUMN motion INTEGER NOT NULL DEFAULT 0");
  }

  @Override
  public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
    // Never drop this journal: it records deleted originals and their only copies.
    if (oldVersion < 1 || oldVersion > 3) throw new IllegalStateException("Unknown reclaim schema");
    // Each column is added only if it is missing, so an upgrade that runs twice (two helpers
    // opening the journal at once right after an app update) is harmless. A future version must
    // raise the bound above and add its own "if (oldVersion < N)" step the same way.
    if (oldVersion < 2) addColumn(db, "reduced", "INTEGER NOT NULL DEFAULT 0");
    if (oldVersion < 3) addColumn(db, "phone", "TEXT NOT NULL DEFAULT ''");
    if (oldVersion < 4) addColumn(db, "motion", "INTEGER NOT NULL DEFAULT 0");
  }

  private static void addColumn(SQLiteDatabase db, String column, String definition) {
    try (Cursor c = db.rawQuery("PRAGMA table_info(jobs)", null)) {
      int name = c.getColumnIndexOrThrow("name");
      while (c.moveToNext()) if (column.equals(c.getString(name))) return;
    }
    db.execSQL("ALTER TABLE jobs ADD COLUMN " + column + " " + definition);
  }

  /** Adds a job unless one is already active or finished for that item. */
  public boolean enqueue(ReclaimEngine.Job job) {
    ReclaimEngine.Job existing = job(job.account, job.key);
    if (existing != null && existing.state != ReclaimEngine.State.FAILED) return false;
    job.state = ReclaimEngine.State.QUEUED;
    job.message = Text.get(R.string.job_waiting);
    job.updated = System.currentTimeMillis();
    ContentValues v = values(job);
    v.put("created", job.updated);
    getWritableDatabase().insertWithOnConflict("jobs", null, v, SQLiteDatabase.CONFLICT_REPLACE);
    return true;
  }

  @Override
  public void save(ReclaimEngine.Job job) {
    int rows =
        getWritableDatabase()
            .update(
                "jobs",
                values(job),
                "account=? AND media_key=?",
                new String[] {job.account, job.key});
    if (rows != 1) throw new IllegalStateException("Reclaim job disappeared");
  }

  public ReclaimEngine.Job job(String account, String key) {
    List<ReclaimEngine.Job> found = query("account=? AND media_key=?", account, key);
    return found.isEmpty() ? null : found.get(0);
  }

  /** Jobs that still need work, oldest first. Waiting-for-upload jobs come first. */
  public List<ReclaimEngine.Job> pending(String account) {
    List<ReclaimEngine.Job> result = new ArrayList<>();
    for (ReclaimEngine.Job job : query("account=?", account, null))
      if (!job.state.terminal()) result.add(job);
    result.sort(
        (a, b) ->
            a.state.remoteChanged() != b.state.remoteChanged()
                ? (a.state.remoteChanged() ? -1 : 1)
                : Long.compare(a.updated, b.updated));
    return result;
  }

  public List<ReclaimEngine.Job> recent(String account, int limit) {
    List<ReclaimEngine.Job> all = query("account=?", account, null);
    all.sort((a, b) -> Long.compare(b.updated, a.updated));
    return all.subList(0, Math.min(limit, all.size()));
  }

  /** File names of every staged copy any job (any account) is responsible for. */
  public java.util.Set<String> stagedNames() {
    java.util.Set<String> names = new java.util.HashSet<>();
    try (Cursor c = getReadableDatabase().rawQuery("SELECT file FROM jobs WHERE file<>''", null)) {
      while (c.moveToNext()) names.add(new java.io.File(c.getString(0)).getName());
    }
    return names;
  }

  /** Finished jobs that still have to check their phone file. */
  public List<ReclaimEngine.Job> guarding(String account) {
    List<ReclaimEngine.Job> result = new ArrayList<>();
    for (ReclaimEngine.Job job : query("account=?", account, null))
      if (ReclaimEngine.needsGuard(job)) result.add(job);
    return result;
  }

  /** True when this content belongs to a reclaim job (its phone copy may have been restored). */
  public boolean reclaimed(String account, byte[] sha1) {
    try (Cursor c =
        getReadableDatabase()
            .rawQuery(
                "SELECT 1 FROM jobs WHERE account=? AND hex(sha1)=? LIMIT 1",
                new String[] {account, ReclaimEngine.hex(sha1).toUpperCase(java.util.Locale.ROOT)})) {
      return c.moveToFirst();
    }
  }

  /** Items whose original was removed but whose replacement is not confirmed. */
  public int unsafe(String account) {
    int n = 0;
    for (ReclaimEngine.Job job : query("account=?", account, null))
      if (job.state.remoteChanged()
          && job.state != ReclaimEngine.State.DONE
          && job.state != ReclaimEngine.State.CHARGED) n++;
    return n;
  }

  /** Items in any account whose original was removed but whose replacement is unconfirmed. */
  public int unsafeAll() {
    int n = 0;
    try (Cursor c = getReadableDatabase().rawQuery("SELECT DISTINCT account FROM jobs", null)) {
      while (c.moveToNext()) n += unsafe(c.getString(0));
    }
    return n;
  }

  @androidx.annotation.VisibleForTesting
  void deleteForTest(String mediaKey) {
    getWritableDatabase().delete("jobs", "media_key=?", new String[] {mediaKey});
  }

  /** Cancels jobs that have not touched Google yet. */
  public void cancelQueued(String account) {
    getWritableDatabase()
        .delete("jobs", "account=? AND state=?", new String[] {account, "QUEUED"});
  }

  private List<ReclaimEngine.Job> query(String where, String account, String key) {
    List<ReclaimEngine.Job> list = new ArrayList<>();
    String[] args = key == null ? new String[] {account} : new String[] {account, key};
    try (Cursor c = getReadableDatabase().query("jobs", null, where, args, null, null, null)) {
      while (c.moveToNext()) {
        ReclaimEngine.Job job = new ReclaimEngine.Job();
        job.account = s(c, "account");
        job.key = s(c, "media_key");
        job.name = s(c, "name");
        job.type = (int) n(c, "type");
        job.size = n(c, "size");
        job.charged = optional(c, "charged");
        job.taken = n(c, "taken");
        job.dedup = s(c, "dedup");
        job.sha1 = c.getBlob(c.getColumnIndexOrThrow("sha1"));
        job.caption = s(c, "caption");
        job.favorite = n(c, "favorite") == 1;
        job.archived = n(c, "archived") == 1;
        job.reduced = n(c, "reduced") == 1;
        job.phone = s(c, "phone");
        job.motion = n(c, "motion") == 1;
        job.state = ReclaimEngine.State.valueOf(s(c, "state"));
        job.file = s(c, "file");
        job.newKey = s(c, "new_key");
        job.newCharged = optional(c, "new_charged");
        job.attempts = (int) n(c, "attempts");
        job.message = s(c, "message");
        job.updated = n(c, "updated");
        list.add(job);
      }
    }
    return list;
  }

  private static ContentValues values(ReclaimEngine.Job job) {
    ContentValues v = new ContentValues();
    v.put("account", job.account);
    v.put("media_key", job.key);
    v.put("name", job.name);
    v.put("type", job.type);
    v.put("size", job.size);
    v.put("charged", job.charged);
    v.put("taken", job.taken);
    v.put("dedup", job.dedup);
    v.put("sha1", job.sha1);
    v.put("caption", job.caption);
    v.put("favorite", job.favorite ? 1 : 0);
    v.put("archived", job.archived ? 1 : 0);
    v.put("reduced", job.reduced ? 1 : 0);
    v.put("phone", job.phone);
    v.put("motion", job.motion ? 1 : 0);
    v.put("state", job.state.name());
    v.put("file", job.file);
    v.put("new_key", job.newKey);
    v.put("new_charged", job.newCharged);
    v.put("attempts", job.attempts);
    v.put("message", job.message);
    v.put("updated", job.updated);
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
