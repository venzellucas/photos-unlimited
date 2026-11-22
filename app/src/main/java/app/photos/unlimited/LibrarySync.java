package app.photos.unlimited;

import android.content.Context;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.Base64;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Read-only library pagination shared by the storage scan and reclaim verification. One run at a
 * time per process: both writers update the same cached cursor.
 */
public final class LibrarySync {
  private static final AtomicBoolean LOCK = new AtomicBoolean();

  public interface Progress {
    void page(InventoryDb.Scan scan);
  }

  private LibrarySync() {}

  public static boolean tryLock() {
    return LOCK.compareAndSet(false, true);
  }

  public static void unlock() {
    LOCK.set(false);
  }

  public static boolean locked() {
    return LOCK.get();
  }

  /** Reads pages from the cached cursor until the scan is done or a limit is reached. */
  public static InventoryDb.Scan run(
      Context context,
      InventoryDb db,
      PhotosApi api,
      AccountStore store,
      String account,
      AtomicBoolean stop,
      int maxPages,
      long deadline,
      Progress progress)
      throws IOException {
    InventoryDb.Scan current = db.scan(account);
    // Shape-only summary for issues #1 and #2, also saved when the run stops early.
    LibraryShape shape = LibraryShape.load(context, current.generation());
    try {
      current =
          pages(context, db, api, store, account, stop, maxPages, deadline, progress, current, shape);
    } finally {
      record(context, () -> shape.save(context));
    }
    return current;
  }

  /** Diagnostics must never break a scan or hide its own failure. */
  private static void record(Context context, Runnable change) {
    try {
      change.run();
    } catch (RuntimeException ignored) {
    }
  }

  private static InventoryDb.Scan pages(
      Context context,
      InventoryDb db,
      PhotosApi api,
      AccountStore store,
      String account,
      AtomicBoolean stop,
      int maxPages,
      long deadline,
      Progress progress,
      InventoryDb.Scan current,
      LibraryShape shape)
      throws IOException {
    Set<String> seen = new HashSet<>();
    int pages = 0;
    String skipped = null; // the label of a delta page read as empty, kept as evidence
    while (current.cursor().stage() != LibraryProtocol.Stage.DONE
        && pages < maxPages
        && System.currentTimeMillis() < deadline) {
      PhotosApi.checkStopped(stop);
      String position =
          current.cursor().stage()
              + ":"
              + Base64.getEncoder().encodeToString(current.cursor().resume())
              + ":"
              + Base64.getEncoder().encodeToString(current.cursor().sync());
      if (!seen.add(position)) throw new ScanFailure("SCAN_CURSOR_CYCLE", "Library cursor cycle");
      if (!account.equals(store.email())) throw new InterruptedIOException();
      String template =
          switch (current.cursor().stage()) {
            case INITIAL -> "initial";
            case DELTA -> "delta";
            default -> "state";
          };
      byte[] base;
      try (var input = context.getAssets().open("library-" + template + ".bin")) {
        base = input.readAllBytes();
      }
      LibraryProtocol.Stage stage = current.cursor().stage();
      LibraryProtocol.Page page;
      String skippedShape = null; // set when this page is read as empty
      try {
        page = api.libraryPage(LibraryProtocol.request(base, current.cursor()));
      } catch (ScanFailure e) {
        // A delta page with none of the fields the app reads ends the delta pass, as upstream
        // GPMC does. Every other stage stays strict: an early end there would skip items.
        if (stage != LibraryProtocol.Stage.DELTA
            || !e.label().startsWith("SCAN_FORMAT_PAGE_SHAPE_NONE"))
          throw LibraryProtocol.withStage(e, stage);
        page = LibraryProtocol.EMPTY;
        skipped = LibraryProtocol.withStage(e, stage).label();
        skippedShape = e.shape() == null ? "" : e.shape();
      }
      PhotosApi.checkStopped(stop);
      if (!account.equals(store.email())) throw new InterruptedIOException();
      LibraryProtocol.Cursor next = LibraryProtocol.advance(current.cursor(), page);
      if (!db.apply(account, current, page, next)) throw new InterruptedIOException();
      // Counted only once the page is committed, so a retried page is never counted twice.
      LibraryProtocol.Page committed = page;
      String skippedSketch = skippedShape;
      record(
          context,
          () -> {
            if (skippedSketch != null) shape.skipped(stage, skippedSketch);
            else shape.page(stage, committed);
            shape.save(context);
          });
      current = db.scan(account);
      pages++;
      if (progress != null) progress.page(current);
    }
    if (skipped != null && current.cursor().stage() == LibraryProtocol.Stage.DONE) {
      db.message(account, current.generation(), Text.get(R.string.scan_finished_skipped, skipped));
      current = db.scan(account);
    }
    return current;
  }
}
