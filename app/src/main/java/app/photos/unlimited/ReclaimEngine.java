package app.photos.unlimited;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.security.MessageDigest;
import java.util.List;

/**
 * Re-uploads one charged Google Photos item through the Pixel upload profile.
 *
 * <p>Google deduplicates by content hash, and deletion takes that same key. A replacement with the
 * same bytes cannot coexist with the original, so the only order that cannot delete the
 * replacement is: download, prove the copy is the original by Google's own SHA-1, trash, delete
 * permanently, upload, verify. Each transition is journaled before the next remote call; after a
 * crash the job resumes from its last saved state and every step is safe to repeat.
 *
 * <p>The local copy is kept until the replacement is confirmed. Pure Java: no Android types, so the
 * state machine is unit-tested on the JVM.
 */
public final class ReclaimEngine {
  public enum State {
    /** Planned; nothing changed remotely. */
    QUEUED,
    /** A copy whose SHA-1 matches Google's recorded original is saved locally. */
    DOWNLOADED,
    /** Written before the trash request: it may or may not have reached Google. */
    TRASHING,
    TRASHED,
    /** Written before the permanent deletion request. */
    DELETING,
    /** The original is gone from Google; the local copy is the only copy until upload. */
    DELETED,
    UPLOADED,
    /** Replacement reported with zero charged storage, metadata restored. Terminal. */
    DONE,
    /** Replacement is charged. Terminal: nothing lost, nothing gained. */
    CHARGED,
    /** Stopped before any remote change. Terminal. */
    FAILED,
    /** Stopped after a remote change; needs the user. The local copy is kept. */
    ATTENTION;

    public boolean terminal() {
      return this == DONE || this == CHARGED || this == FAILED || this == ATTENTION;
    }

    /** True once the remote original may be gone: from here on the local copy is precious. */
    public boolean remoteChanged() {
      return ordinal() >= TRASHING.ordinal() && this != FAILED;
    }
  }

  public static final class Job {
    public String account, key, name, dedup, caption = "", file = "", newKey = "", message = "";
    public int type;
    public long size, taken, updated;
    public Long charged, newCharged;
    public byte[] sha1;
    public boolean favorite, archived;
    /** Google keeps only a reduced (Storage saver) copy; the original must come from the phone. */
    public boolean reduced;
    /** Google shows it as a Motion/Live Photo: the verified file must contain the video too. */
    public boolean motion;
    /**
     * The phone file that matched the original (opaque reference), while it still needs checking:
     * Google Photos with backup on deletes phone copies of items deleted from the cloud.
     */
    public String phone = "";
    public State state = State.QUEUED;
    public int attempts;

    public static Job of(String account, LibraryProtocol.Item item) {
      Job job = new Job();
      job.account = account;
      job.key = item.key();
      job.name = item.name();
      job.type = item.type();
      job.size = item.size() == null ? -1 : item.size();
      job.charged = item.charged();
      job.taken = item.taken();
      job.archived = item.archived();
      job.dedup = item.extra().dedup();
      job.sha1 = item.extra().sha1();
      job.caption = item.extra().caption();
      job.favorite = item.extra().favorite();
      job.reduced = item.extra().quality() == LibraryProtocol.Quality.REDUCED;
      job.motion = item.extra().motion();
      return job;
    }
  }

  /** Persists the job; must be durable when it returns. */
  public interface Journal {
    void save(Job job) throws IOException;
  }

  /** A file on this phone that might be the original; the engine proves it by SHA-1. */
  public record Local(String label, String ref, PhotosApi.Source source) {}

  /** Everything the engine needs from Google. PhotosApi plus a delta scan in production. */
  public interface Remote {
    /** Phone files that may be this item's original (same name or size). Empty if none. */
    default List<Local> localOriginals(Job job) throws IOException {
      return List.of();
    }

    /** Whether the phone file recorded before deletion still exists. */
    default boolean phoneCopyPresent(String ref) throws IOException {
      return true;
    }

    /** A verified copy now exists in the staging folder; make it findable by a later install. */
    default void staged(File file) {}

    /** Puts the verified copy back where the phone file was. */
    default void restorePhoneCopy(String ref, File copy) throws IOException {}

    String find(byte[] sha1) throws IOException;

    List<PhotosApi.Link> downloadLinks(String key) throws IOException;

    byte[] download(String url, File target, long limit, PhotosApi.Progress progress)
        throws IOException;

    void trash(String dedup) throws IOException;

    void delete(String dedup) throws IOException;

    String upload(Job job, File file, PhotosApi.Progress progress) throws IOException;

    /** Latest library record after a delta scan, or null while Google has not reported it. */
    LibraryProtocol.Item lookup(String key) throws IOException;

    void setCaption(String dedup, String caption) throws IOException;

    void setFavorite(String dedup) throws IOException;

    void setArchived(String dedup) throws IOException;

    void restore(String dedup) throws IOException;
  }

  /** Deleting is refused after this many checks that still find the original. */
  static final int MAX_DELETE_CHECKS = 12;

  public interface Events {
    void step(Job job, String detail);

    default void bytes(Job job, long done, long total) {}
  }

  public interface Clock {
    long now();

    void sleep(long millis) throws InterruptedIOException;
  }

  static final Clock SYSTEM =
      new Clock() {
        public long now() {
          return System.currentTimeMillis();
        }

        public void sleep(long millis) throws InterruptedIOException {
          try {
            Thread.sleep(millis);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new InterruptedIOException();
          }
        }
      };

  /** Thrown when a step must wait for Google; the job stays in its state for a later run. */
  public static final class Later extends IOException {
    Later(String message) {
      super(message);
    }
  }

  private final Remote remote;
  private final Journal journal;
  private final Events events;
  private final File dir;
  private final Clock clock;

  public ReclaimEngine(Remote remote, Journal journal, Events events, File dir, Clock clock) {
    this.remote = remote;
    this.journal = journal;
    this.events = events;
    this.dir = dir;
    this.clock = clock;
  }

  /** Why an inventory item cannot be reclaimed, or null when it can. */
  public static String ineligible(LibraryProtocol.Item item) {
    LibraryProtocol.Extra x = item.extra();
    // A reduced copy is worth replacing with the original even when it is free (pre-2021).
    if (x.quality() != LibraryProtocol.Quality.REDUCED) {
      if (item.charged() == null) return "Google hasn’t reported its storage use.";
      if (item.charged() <= 0) return "It doesn’t use storage.";
    }
    if (item.trash()) return "It’s in the trash.";
    if (item.origin() != 0 && item.origin() != 1) return "It’s shared with you, not yours.";
    if (x.locked()) return "It’s in the Locked Folder.";
    if (!x.canonical()) return "It’s an alternate version of another item.";
    if (item.type() != 1 && item.type() != 2) return "Only photos and videos are supported.";
    if (!x.hashKnown())
      return "Google didn’t report the original’s checksum, so a download can’t be verified.";
    if (x.dedup().isEmpty()) return "Google didn’t report the identifier needed to replace it.";
    if (item.size() == null || item.size() <= 0) return "Google didn’t report its file size.";
    return null;
  }

  /** Runs one job until it finishes, must wait for Google, or the deadline passes. */
  public Job run(Job job, long deadline) throws IOException {
    while (!job.state.terminal()) {
      if (clock.now() > deadline) throw new Later("Paused; this item will continue next run.");
      try {
        advance(job);
      } catch (Later | InterruptedIOException e) {
        throw e;
      } catch (PhotosApi.ApiException e) {
        if (e.signIn || !e.permanent) throw e;
        stop(job, e.getMessage());
      }
    }
    return job;
  }

  void advance(Job job) throws IOException {
    switch (job.state) {
      case QUEUED -> download(job);
      case DOWNLOADED -> {
        if (!verifyLocal(job)) return;
        save(job, State.TRASHING, "Moving the original to the trash");
        remote.trash(job.dedup);
        save(job, State.TRASHED, "Original moved to the trash");
      }
      case TRASHING -> {
        if (!localOk(job)) {
          undo(job);
          return;
        }
        remote.trash(job.dedup);
        save(job, State.TRASHED, "Original moved to the trash");
      }
      case TRASHED -> {
        if (!localOk(job)) {
          undo(job);
          return;
        }
        save(job, State.DELETING, "Deleting the original permanently");
        remote.delete(job.dedup);
        save(job, State.DELETED, "Original deleted; uploading the saved copy");
      }
      case DELETING -> {
        if (!localOk(job)) {
          undo(job);
          return;
        }
        remote.delete(job.dedup);
        save(job, State.DELETED, "Original deleted; uploading the saved copy");
      }
      case DELETED -> upload(job);
      case UPLOADED -> verify(job);
      default -> throw new IllegalStateException(job.state.name());
    }
  }

  private void download(Job job) throws IOException {
    if (job.sha1 == null || job.sha1.length != 20 || job.dedup.isEmpty() || job.size <= 0) {
      fail(job, "Missing the checksum or identifier needed for a safe replacement.");
      return;
    }
    events.step(job, "Checking Google’s record of the original");
    String owner = remote.find(job.sha1);
    if (owner.isEmpty()) {
      fail(job, "Google no longer reports this item. Refresh the storage scan.");
      return;
    }
    if (!owner.equals(job.key)) {
      fail(job, "Another library item has identical bytes; deleting one could remove both.");
      return;
    }
    if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("No space for the local copy");
    File part = new File(dir, PARTIAL + fileName(job));
    File done = new File(dir, fileName(job));
    // The phone's own copy saves a download, and for Storage saver items it is the only original.
    for (Local local : remote.localOriginals(job)) {
      events.step(job, "Checking " + local.label() + " on this phone");
      byte[] digest;
      try {
        // A reduced item's reported size is the compressed copy's, not the original's.
        digest = copy(local.source(), part, job.reduced ? 64L << 30 : job.size);
      } catch (InterruptedIOException e) {
        part.delete();
        throw e;
      } catch (IOException e) {
        part.delete();
        continue;
      }
      if ((job.reduced || part.length() == job.size) && MessageDigest.isEqual(digest, job.sha1)) {
        if (!part.renameTo(done)) throw new IOException("Could not keep the local copy");
        if (job.motion && !hasEmbeddedVideo(done)) {
          done.delete();
          fail(job, SEPARATE_VIDEO);
          return;
        }
        remote.staged(done);
        job.file = done.getAbsolutePath();
        job.size = done.length();
        job.phone = local.ref();
        save(job, State.DOWNLOADED, "Original found on this phone and verified");
        return;
      }
      part.delete();
    }
    if (job.reduced) {
      fail(
          job,
          "Google keeps only a reduced copy, and the original wasn’t found on this phone."
              + " Nothing was changed.");
      return;
    }
    List<PhotosApi.Link> links = remote.downloadLinks(job.key);
    int tried = 0;
    for (PhotosApi.Link link : links) {
      tried++;
      events.step(job, "Downloading the original (" + link.path() + ")");
      byte[] digest;
      try {
        digest =
            remote.download(
                link.url(), part, job.size, (bytes, total) -> events.bytes(job, bytes, job.size));
      } catch (InterruptedIOException e) {
        part.delete();
        throw e;
      } catch (IOException e) {
        part.delete();
        events.step(job, "Candidate " + link.path() + " failed: " + e.getMessage());
        continue;
      }
      if (part.length() == job.size && MessageDigest.isEqual(digest, job.sha1)) {
        if (!part.renameTo(done)) throw new IOException("Could not keep the local copy");
        if (job.motion && !hasEmbeddedVideo(done)) {
          done.delete();
          fail(job, SEPARATE_VIDEO);
          return;
        }
        remote.staged(done);
        job.file = done.getAbsolutePath();
        save(job, State.DOWNLOADED, "Original saved and verified (" + link.path() + ")");
        return;
      }
      events.step(
          job,
          "Candidate "
              + link.path()
              + " is not the original ("
              + part.length()
              + " bytes, checksum "
              + (MessageDigest.isEqual(digest, job.sha1) ? "matches" : "differs")
              + ")");
      part.delete();
    }
    fail(
        job,
        tried == 0
            ? "Google offered no download for this item."
            : "No download matched Google’s checksum of the original. Nothing was changed.");
  }

  private void upload(Job job) throws IOException {
    File file = new File(job.file);
    if (!verifyLocal(job)) return;
    String present = remote.find(job.sha1);
    if (present.equals(job.key)) {
      if (job.attempts + 1 >= MAX_DELETE_CHECKS) {
        stop(
            job,
            "Google still has the original after deletion, so nothing was uploaded. Check"
                + " Google Photos trash; the verified copy is kept in this app.");
        return;
      }
      job.attempts++;
      journal.save(job);
      throw new Later("Google still reports the deleted original. Waiting before uploading.");
    }
    if (!present.isEmpty()) {
      // Same bytes under a new key: our earlier upload committed before the app stopped.
      job.newKey = present;
      save(job, State.UPLOADED, "Replacement already uploaded");
      return;
    }
    events.step(job, "Uploading the saved copy");
    String key = remote.upload(job, file, (bytes, total) -> events.bytes(job, bytes, total));
    if (key.isEmpty() || key.equals(job.key)) {
      stop(job, "Google matched the upload to the deleted original. The local copy is kept.");
      return;
    }
    job.newKey = key;
    save(job, State.UPLOADED, "Uploaded; waiting for Google’s storage report");
  }

  private void verify(Job job) throws IOException {
    events.step(job, "Checking the replacement’s storage use");
    LibraryProtocol.Item item = remote.lookup(job.newKey);
    if (item == null || item.charged() == null) {
      job.attempts++;
      journal.save(job);
      throw new Later("Waiting for Google to report the replacement’s storage use.");
    }
    job.newCharged = item.charged();
    String dedup = item.extra().dedup().isEmpty() ? job.dedup : item.extra().dedup();
    events.step(job, "Restoring caption, favorite, and archive");
    if (!job.caption.isEmpty() && !job.caption.equals(item.extra().caption()))
      remote.setCaption(dedup, job.caption);
    if (job.favorite && !item.extra().favorite()) remote.setFavorite(dedup);
    if (job.archived && !item.archived()) remote.setArchived(dedup);
    if (item.extra().quality() == LibraryProtocol.Quality.REDUCED) {
      save(
          job,
          State.ATTENTION,
          "Google stored the replacement as a reduced copy again. The original is kept in this"
              + " app.");
      return;
    }
    if (job.newCharged == 0) {
      save(job, State.DONE, "Replaced; now uses no storage");
      // Keep the copy until the phone file is checked; see guard().
      if (job.phone.isEmpty()) new File(job.file).delete();
    } else {
      save(
          job,
          State.CHARGED,
          "The replacement still uses storage. The local copy is kept; check the Pixel upload"
              + " profile before continuing.");
    }
  }

  /** True when a finished job still has to check its phone file. */
  public static boolean needsGuard(Job job) {
    return !job.phone.isEmpty()
        && (job.state == State.DONE || job.state == State.CHARGED || job.state == State.ATTENTION);
  }

  /**
   * Run some time after a job finishes. If Google Photos removed the phone file in response to the
   * cloud deletion, put the verified copy back. The app's own copy is then removed only for DONE.
   */
  public void guard(Job job) throws IOException {
    if (!needsGuard(job)) return;
    File copy = new File(job.file);
    if (!remote.phoneCopyPresent(job.phone)) {
      if (!copy.isFile() || !MessageDigest.isEqual(sha1(copy), job.sha1)) {
        String before = job.message;
        job.phone = "";
        save(job, job.state, before + " The phone copy was removed by another app.");
        return;
      }
      events.step(job, "Google Photos removed the phone copy; restoring it");
      remote.restorePhoneCopy(job.phone, copy);
      job.message += " Phone copy restored after Google Photos removed it.";
    }
    job.phone = "";
    if (job.state == State.DONE) copy.delete();
    job.updated = clock.now();
    journal.save(job);
  }

  /** Before each step that relies on it, the local copy must still be the verified original. */
  private boolean localOk(Job job) throws IOException {
    File file = new File(job.file);
    return file.isFile()
        && file.length() == job.size
        && MessageDigest.isEqual(sha1(file), job.sha1);
  }

  private boolean verifyLocal(Job job) throws IOException {
    if (localOk(job)) return true;
    stop(job, "The saved copy is missing or changed. Nothing more was done.");
    return false;
  }

  /**
   * The local copy went bad before deletion was confirmed: put the original back. Google still
   * knowing the original by its hash proves the restore had something to restore.
   */
  private void undo(Job job) throws IOException {
    events.step(job, "The saved copy changed; restoring the original from the trash");
    remote.restore(job.dedup);
    if (remote.find(job.sha1).equals(job.key))
      save(job, State.FAILED, "The saved copy changed, so the original was restored from trash.");
    else
      save(
          job,
          State.ATTENTION,
          "The saved copy changed after the original was removed. Check Google Photos trash.");
  }

  private void fail(Job job, String message) throws IOException {
    save(job, State.FAILED, message);
  }

  private void stop(Job job, String message) throws IOException {
    save(job, job.state.remoteChanged() ? State.ATTENTION : State.FAILED, message);
  }

  private void save(Job job, State state, String message) throws IOException {
    job.state = state;
    job.message = message;
    job.attempts = 0;
    job.updated = clock.now();
    journal.save(job);
    events.step(job, message);
  }

  static final String SEPARATE_VIDEO =
      "This Live Photo keeps its video in a separate file that can’t be verified, so replacing it"
          + " would lose the motion. Nothing was changed.";

  /**
   * True when the file carries its own video: Android Motion Photos (Pixel MotionPhoto and legacy
   * MicroVideo, Samsung) append an MP4 after the image. An iPhone Live Photo's image file does not;
   * its video is a separate file Google pairs with it, which a single re-upload would lose.
   */
  public static boolean hasEmbeddedVideo(File file) throws IOException {
    byte[] buffer = new byte[256 * 1024];
    byte[] carry = new byte[0];
    long offset = 0; // file offset of the first byte in `window`
    try (InputStream in = new FileInputStream(file)) {
      int n;
      while ((n = in.read(buffer)) != -1) {
        byte[] window = new byte[carry.length + n];
        System.arraycopy(carry, 0, window, 0, carry.length);
        System.arraycopy(buffer, 0, window, carry.length, n);
        for (int i = 4; i + 8 <= window.length; i++) {
          if (window[i] != 'f' || window[i + 1] != 't' || window[i + 2] != 'y' || window[i + 3] != 'p')
            continue;
          long at = offset + i - 4; // where the box starts
          if (at == 0) continue; // the file's own container header (HEIC/AVIF)
          long size =
              ((window[i - 4] & 255L) << 24)
                  | ((window[i - 3] & 255L) << 16)
                  | ((window[i - 2] & 255L) << 8)
                  | (window[i - 1] & 255L);
          if (size < 16 || size > 4096) continue;
          String brand = new String(window, i + 4, 4, java.nio.charset.StandardCharsets.US_ASCII);
          if (brand.matches("[A-Za-z0-9 ]{4}")
              && !brand.startsWith("hei")
              && !brand.startsWith("mif")
              && !brand.startsWith("avi")) return true;
        }
        int keep = Math.min(window.length, 11);
        carry = java.util.Arrays.copyOfRange(window, window.length - keep, window.length);
        offset += window.length - keep;
      }
    }
    return false;
  }

  /** Prefix of a copy still being written; never complete, never uploaded by recovery. */
  public static final String PARTIAL = "partial_";

  private static final java.util.regex.Pattern STAGED =
      java.util.regex.Pattern.compile("([0-9a-f]{40})_([0-9a-f]{8})_(.+)");

  /** Short, non-reversible tag for the account a staged file belongs to. */
  public static String accountTag(String account) {
    MessageDigest d = PhotosApi.sha1();
    return hex(d.digest(account.trim().toLowerCase(java.util.Locale.ROOT)
            .getBytes(java.nio.charset.StandardCharsets.UTF_8)))
        .substring(0, 8);
  }

  /**
   * Staged copies are named {@code <sha1>_<account tag>_<original name>}. A fresh install without
   * the journal can prove a staged file is complete and original by hashing it against its own
   * name, and never uploads it into a different account.
   */
  static String fileName(Job job) {
    String safe = job.name.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_");
    if (safe.length() > 120) safe = safe.substring(safe.length() - 120);
    // Shared media folders accept files by extension: make sure there is a media one.
    String lower = safe.toLowerCase(java.util.Locale.ROOT);
    boolean video = job.type == 2;
    if (!(video
        ? lower.matches(".*\\.(mp4|m4v|mov|3gp|3g2|webm|mkv|avi|mts|m2ts|ts)$")
        : lower.matches(".*\\.(jpe?g|png|gif|webp|heic|heif|avif|dng|cr2|cr3|nef|arw|orf|rw2|raf|tiff?|bmp)$")))
      safe += video ? ".mp4" : ".jpg";
    return hex(job.sha1) + "_" + accountTag(job.account) + "_" + safe;
  }

  /** The account tag in a staged file name, or null. */
  public static String stagedAccount(String fileName) {
    java.util.regex.Matcher m = STAGED.matcher(fileName == null ? "" : fileName);
    return m.matches() ? m.group(2) : null;
  }

  /** The SHA-1 a staged file name promises, or null for partial and foreign files. */
  public static String stagedHash(String fileName) {
    if (fileName == null || fileName.startsWith(PARTIAL)) return null;
    java.util.regex.Matcher m = STAGED.matcher(fileName);
    return m.matches() ? m.group(1) : null;
  }

  /** The name a staged file was uploaded under. */
  public static String stagedName(String fileName) {
    java.util.regex.Matcher m = STAGED.matcher(fileName);
    return m.matches() ? m.group(3) : fileName;
  }

  private static byte[] copy(PhotosApi.Source source, File target, long limit)
      throws IOException {
    MessageDigest digest = PhotosApi.sha1();
    long total = 0;
    try (InputStream in = source.open();
        java.io.FileOutputStream out = new java.io.FileOutputStream(target)) {
      byte[] buffer = new byte[128 * 1024];
      int n;
      while ((n = in.read(buffer)) != -1) {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException();
        total += n;
        if (total > limit) throw new IOException("Different file");
        digest.update(buffer, 0, n);
        out.write(buffer, 0, n);
      }
      out.getFD().sync();
    }
    return digest.digest();
  }

  /** Lowercase hex; java.util.HexFormat needs API 34 and the app supports 33. */
  public static String hex(byte[] bytes) {
    StringBuilder out = new StringBuilder(bytes.length * 2);
    for (byte b : bytes) out.append(Character.forDigit((b >> 4) & 15, 16)).append(Character.forDigit(b & 15, 16));
    return out.toString();
  }

  static byte[] sha1(File file) throws IOException {
    MessageDigest digest = PhotosApi.sha1();
    try (InputStream in = new FileInputStream(file)) {
      byte[] buffer = new byte[128 * 1024];
      int n;
      while ((n = in.read(buffer)) != -1) digest.update(buffer, 0, n);
    }
    return digest.digest();
  }
}
