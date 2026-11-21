package app.photos.unlimited;

import android.content.Context;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/** Production bridge from the reclaim engine to Google Photos and the inventory cache. */
public final class ReclaimRemote implements ReclaimEngine.Remote {
  private final Context context;
  private final PhotosApi api;
  private final AccountStore store;
  private final String account;
  private final AtomicBoolean stop;

  public ReclaimRemote(Context context, PhotosApi api, String account, AtomicBoolean stop) {
    this.context = context.getApplicationContext();
    this.api = api;
    this.store = new AccountStore(context);
    this.account = account;
    this.stop = stop;
  }

  @Override
  public List<ReclaimEngine.Local> localOriginals(ReclaimEngine.Job job) {
    MediaScanner scanner = new MediaScanner(context);
    List<ReclaimEngine.Local> result = new java.util.ArrayList<>();
    for (MediaScanner.Candidate c : scanner.candidates(job.name, job.size))
      result.add(
          new ReclaimEngine.Local(
              "a matching file", encode(c), () -> scanner.openOriginal(c.uri())));
    return result;
  }

  static String encode(MediaScanner.Candidate c) {
    return String.join(
        "\n", c.uri(), c.name(), c.path(), c.mime(), Long.toString(c.taken()));
  }

  static MediaScanner.Candidate decode(String ref) throws IOException {
    String[] p = ref.split("\n", -1);
    if (p.length != 5) throw new IOException("Unreadable phone file record");
    try {
      return new MediaScanner.Candidate(p[0], p[1], p[2], p[3], Long.parseLong(p[4]));
    } catch (NumberFormatException e) {
      throw new IOException("Unreadable phone file record");
    }
  }

  @Override
  public void staged(File file) {
    ReclaimRecovery.index(context, file);
  }

  @Override
  public boolean phoneCopyPresent(String ref) throws IOException {
    return new MediaScanner(context).exists(decode(ref).uri());
  }

  @Override
  public void restorePhoneCopy(String ref, File copy) throws IOException {
    new MediaScanner(context).restore(decode(ref), copy);
  }

  @Override
  public String find(byte[] sha1) throws IOException {
    return api.find(sha1);
  }

  @Override
  public List<PhotosApi.Link> downloadLinks(String key) throws IOException {
    return PhotosApi.links(api.prepareDownload(key));
  }

  @Override
  public byte[] download(String url, File target, long limit, PhotosApi.Progress progress)
      throws IOException {
    return api.download(url, target, limit, progress);
  }

  @Override
  public void trash(String dedup) throws IOException {
    api.moveToTrash(List.of(dedup));
  }

  @Override
  public void delete(String dedup) throws IOException {
    api.deletePermanently(List.of(dedup));
  }

  @Override
  public String upload(ReclaimEngine.Job job, File file, PhotosApi.Progress progress)
      throws IOException {
    // Library timestamps are milliseconds; the commit takes seconds. EXIF still wins in Photos.
    long seconds = job.taken > 100_000_000_000L ? job.taken / 1000 : job.taken;
    if (seconds <= 0) seconds = file.lastModified() / 1000;
    return api.upload(
        () -> new FileInputStream(file), job.name, job.size, seconds, job.sha1, progress);
  }

  @Override
  public LibraryProtocol.Item lookup(String key) throws IOException {
    if (!LibrarySync.tryLock())
      throw new ReclaimEngine.Later(Text.get(R.string.reclaim_wait_scan));
    try (InventoryDb db = new InventoryDb(context)) {
      InventoryDb.Scan scan = db.scan(account);
      if (scan.cursor().stage() == LibraryProtocol.Stage.DONE) db.beginDelta(account);
      LibrarySync.run(
          context,
          db,
          api,
          store,
          account,
          stop,
          40,
          System.currentTimeMillis() + 2 * 60 * 1000L,
          null);
      return db.item(account, key);
    } finally {
      LibrarySync.unlock();
    }
  }

  @Override
  public void setCaption(String dedup, String caption) throws IOException {
    api.setCaption(dedup, caption);
  }

  @Override
  public void setFavorite(String dedup) throws IOException {
    api.setFavorite(dedup, true);
  }

  @Override
  public void setArchived(String dedup) throws IOException {
    api.setArchived(List.of(dedup), true);
  }

  @Override
  public void restore(String dedup) throws IOException {
    api.restoreFromTrash(List.of(dedup));
  }
}
