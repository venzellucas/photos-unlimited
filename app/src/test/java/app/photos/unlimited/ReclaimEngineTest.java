package app.photos.unlimited;

import static org.junit.Assert.*;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.*;
import org.junit.Before;
import org.junit.Test;

/**
 * The fake mirrors the property that forces the engine's order: Google identifies media by
 * content hash, and an upload of bytes it already holds resolves to the existing item.
 */
public class ReclaimEngineTest {
  static final byte[] ORIGINAL = "original bytes with EXIF".getBytes();
  static final byte[] EDITED = "edited rendition".getBytes();

  static byte[] sha1(byte[] data) throws Exception {
    return MessageDigest.getInstance("SHA-1").digest(data);
  }

  class FakeGoogle implements ReclaimEngine.Remote {
    final Map<String, String> byHash = new HashMap<>(); // hash hex -> key
    final Set<String> trashed = new HashSet<>();
    final List<String> calls = new ArrayList<>();
    final Map<String, LibraryProtocol.Item> library = new HashMap<>();
    int forgetAfterFinds; // finds that still report a deleted original
    long replacementCharge;
    boolean lookupReady = true;
    RuntimeException failAfter; // simulated crash after a named call completed
    String failOn = "";
    IOException uploadError;
    int uploads;
    boolean phonePresent = true;
    byte[] restored;

    String hex(byte[] h) {
      return HexFormat.of().formatHex(h);
    }

    void crash(String call) {
      calls.add(call);
      if (call.equals(failOn)) {
        failOn = "";
        throw failAfter;
      }
    }

    public String find(byte[] sha1) throws IOException {
      calls.add("find");
      String key = byHash.getOrDefault(hex(sha1), "");
      if (key.isEmpty() && forgetAfterFinds > 0) {
        forgetAfterFinds--;
        return "old";
      }
      return key;
    }

    public List<PhotosApi.Link> downloadLinks(String key) {
      return List.of(
          new PhotosApi.Link("1.5.2.5", "https://x.googleusercontent.com/edited"),
          new PhotosApi.Link("1.5.2.6", "https://x.googleusercontent.com/original"));
    }

    public byte[] download(String url, File target, long limit, PhotosApi.Progress progress)
        throws IOException {
      calls.add("download " + url.substring(url.lastIndexOf('/') + 1));
      byte[] data = url.endsWith("original") ? ORIGINAL : EDITED;
      try (FileOutputStream out = new FileOutputStream(target)) {
        out.write(data);
      }
      try {
        return sha1(data);
      } catch (Exception e) {
        throw new IOException(e);
      }
    }

    public void trash(String dedup) {
      assertEquals("dedup-old", dedup);
      trashed.add(dedup);
      LibraryProtocol.Item o = library.get("old");
      if (o != null && trashVisible)
        library.put("old", copy(o, true));
      crash("trash");
    }

    public void delete(String dedup) {
      assertTrue("deleted without trashing first", trashed.contains(dedup));
      byHash.values().removeIf(k -> k.equals("old"));
      library.remove("old");
      crash("delete");
    }

    boolean trashVisible = true;

    LibraryProtocol.Item copy(LibraryProtocol.Item o, boolean trash) {
      return new LibraryProtocol.Item(
          o.key(), o.name(), o.type(), o.size(), o.charged(), o.taken(), trash, o.archived(),
          o.origin(), o.extra());
    }

    public String upload(ReclaimEngine.Job job, File file, PhotosApi.Progress progress)
        throws IOException {
      calls.add("upload");
      uploads++;
      if (uploadError != null) throw uploadError;
      byte[] data = Files.readAllBytes(file.toPath());
      String existing;
      try {
        existing = byHash.get(hex(sha1(data)));
      } catch (Exception e) {
        throw new IOException(e);
      }
      if (existing != null) return existing;
      String key = "new" + uploads;
      try {
        byHash.put(hex(sha1(data)), key);
      } catch (Exception e) {
        throw new IOException(e);
      }
      library.put(
          key,
          new LibraryProtocol.Item(
              key,
              job.name,
              1,
              (long) data.length,
              replacementCharge,
              job.taken,
              false,
              false,
              1,
              new LibraryProtocol.Extra(
                  "dedup-new", job.sha1, "", false, false, true, false, false)));
      crash("upload");
      return key;
    }

    public LibraryProtocol.Item lookup(String key) {
      calls.add("lookup");
      return lookupReady || !key.startsWith("new") ? library.get(key) : null;
    }

    public void setCaption(String dedup, String caption) {
      calls.add("caption " + dedup + " " + caption);
    }

    public void setFavorite(String dedup) {
      calls.add("favorite " + dedup);
    }

    public void setArchived(String dedup) {
      calls.add("archive " + dedup);
    }

    public void restore(String dedup) {
      calls.add("restore");
      trashed.remove(dedup);
      LibraryProtocol.Item o = library.get("old");
      if (o != null) library.put("old", copy(o, false));
    }
  }

  final class Journal implements ReclaimEngine.Journal {
    final List<ReclaimEngine.State> states = new ArrayList<>();

    public void save(ReclaimEngine.Job job) {
      states.add(job.state);
    }
  }

  static final ReclaimEngine.Clock CLOCK =
      new ReclaimEngine.Clock() {
        public long now() {
          return 1000;
        }

        public void sleep(long millis) {}
      };

  FakeGoogle google;
  Journal journal;
  File dir;
  ReclaimEngine engine;
  ReclaimEngine.Job job;
  LibraryProtocol.Item original;

  @Before
  public void setUp() throws Exception {
    google = new FakeGoogle();
    google.byHash.put(google.hex(sha1(ORIGINAL)), "old");
    journal = new Journal();
    dir = Files.createTempDirectory("reclaim").toFile();
    engine = new ReclaimEngine(google, journal, (j, d) -> {}, dir, CLOCK);
    original =
        new LibraryProtocol.Item(
            "old",
            "IMG 1.jpg",
            1,
            (long) ORIGINAL.length,
            (long) ORIGINAL.length,
            1700000000000L,
            false,
            true,
            1,
            new LibraryProtocol.Extra(
                "dedup-old", sha1(ORIGINAL), "Sunset", true, false, true, false, false));
    assertNull(ReclaimEngine.ineligible(original));
    google.library.put("old", original);
    job = ReclaimEngine.Job.of("me@example.com", original);
  }

  private void finish() throws IOException {
    for (int i = 0; i < 10 && !job.state.terminal(); i++) {
      try {
        engine.run(job, Long.MAX_VALUE);
      } catch (ReclaimEngine.Later e) {
        // Retried like the service does after a pause.
      }
    }
  }

  @Test
  public void verifiedOriginalIsDeletedThenReuploadedAndMetadataRestored() throws Exception {
    engine.run(job, Long.MAX_VALUE);
    assertEquals(ReclaimEngine.State.DONE, job.state);
    assertEquals(
        List.of(
            ReclaimEngine.State.DOWNLOADED,
            ReclaimEngine.State.TRASHING,
            ReclaimEngine.State.TRASHED,
            ReclaimEngine.State.DELETING,
            ReclaimEngine.State.DELETED,
            ReclaimEngine.State.UPLOADED,
            ReclaimEngine.State.DONE),
        journal.states);
    // The checksum, not the URL order, chose the original; edits were rejected.
    assertTrue(google.calls.contains("download edited"));
    assertTrue(google.calls.indexOf("download original") < google.calls.indexOf("trash"));
    assertTrue(google.calls.indexOf("delete") < google.calls.indexOf("upload"));
    assertTrue(google.calls.contains("caption dedup-new Sunset"));
    assertTrue(google.calls.contains("favorite dedup-new"));
    assertTrue(google.calls.contains("archive dedup-new"));
    assertEquals("new1", job.newKey);
    assertEquals(Long.valueOf(0), job.newCharged);
    assertFalse("local copy removed after success", new File(job.file).exists());
  }

  @Test
  public void nothingIsDeletedWithoutAChecksumMatch() throws Exception {
    google =
        new FakeGoogle() {
          @Override
          public List<PhotosApi.Link> downloadLinks(String key) {
            return List.of(new PhotosApi.Link("1.5.2.5", "https://x.googleusercontent.com/e"));
          }
        };
    google.byHash.put(google.hex(sha1(ORIGINAL)), "old");
    google.library.put("old", original);
    engine = new ReclaimEngine(google, journal, (j, d) -> {}, dir, CLOCK);
    engine.run(job, Long.MAX_VALUE);
    assertEquals(ReclaimEngine.State.FAILED, job.state);
    assertFalse(google.calls.contains("trash"));
    assertEquals(0, Objects.requireNonNull(dir.listFiles()).length);
  }

  @Test
  public void sharedBytesUnderAnotherKeyStopBeforeAnyChange() throws Exception {
    google.byHash.put(google.hex(sha1(ORIGINAL)), "someone-else");
    engine.run(job, Long.MAX_VALUE);
    assertEquals(ReclaimEngine.State.FAILED, job.state);
    assertEquals(List.of("find"), google.calls);
  }

  @Test
  public void crashAfterEachRemoteStepResumesSafely() throws Exception {
    for (String step : List.of("trash", "delete", "upload")) {
      setUp();
      google.failOn = step;
      google.failAfter = new IllegalStateException("process killed after " + step);
      try {
        engine.run(job, Long.MAX_VALUE);
        fail("expected crash");
      } catch (IllegalStateException expected) {
      }
      assertFalse(job.state.terminal());
      finish();
      assertEquals(step, ReclaimEngine.State.DONE, job.state);
      assertEquals(step, 1, google.uploads);
    }
  }

  @Test
  public void waitsWhileGoogleStillReportsTheDeletedOriginal() throws Exception {
    google.forgetAfterFinds = 2;
    try {
      engine.run(job, Long.MAX_VALUE);
      fail("expected to wait");
    } catch (ReclaimEngine.Later expected) {
    }
    assertEquals(ReclaimEngine.State.DELETED, job.state);
    assertEquals(0, google.uploads);
    finish();
    assertEquals(ReclaimEngine.State.DONE, job.state);
    assertEquals(1, google.uploads);
  }

  @Test
  public void waitsForStorageReportBeforeDeclaringSuccess() throws Exception {
    google.lookupReady = false;
    try {
      engine.run(job, Long.MAX_VALUE);
      fail("expected to wait");
    } catch (ReclaimEngine.Later expected) {
    }
    assertEquals(ReclaimEngine.State.UPLOADED, job.state);
    assertTrue(new File(job.file).exists());
    google.lookupReady = true;
    finish();
    assertEquals(ReclaimEngine.State.DONE, job.state);
  }

  @Test
  public void chargedReplacementKeepsTheLocalCopy() throws Exception {
    google.replacementCharge = 25;
    engine.run(job, Long.MAX_VALUE);
    assertEquals(ReclaimEngine.State.CHARGED, job.state);
    assertTrue(new File(job.file).exists());
  }

  @Test
  public void changedLocalCopyBeforeTrashStopsWithNoRemoteChange() throws Exception {
    google =
        new FakeGoogle() {
          @Override
          public void trash(String dedup) {
            fail("must not trash");
          }
        };
    google.byHash.put(google.hex(sha1(ORIGINAL)), "old");
    google.library.put("old", original);
    engine = new ReclaimEngine(google, journal, (j, d) -> {}, dir, CLOCK);
    engine.advance(job);
    assertEquals(ReclaimEngine.State.DOWNLOADED, job.state);
    try (FileOutputStream out = new FileOutputStream(job.file, true)) {
      out.write(1);
    }
    engine.run(job, Long.MAX_VALUE);
    assertEquals(ReclaimEngine.State.FAILED, job.state);
  }

  @Test
  public void changedLocalCopyAfterTrashRestoresTheOriginal() throws Exception {
    google.failOn = "trash";
    google.failAfter = new IllegalStateException("stop after trash");
    try {
      engine.run(job, Long.MAX_VALUE);
    } catch (IllegalStateException expected) {
    }
    assertEquals(ReclaimEngine.State.TRASHING, job.state);
    try (FileOutputStream out = new FileOutputStream(job.file, true)) {
      out.write(1);
    }
    engine.run(job, Long.MAX_VALUE);
    assertEquals(ReclaimEngine.State.FAILED, job.state);
    assertTrue(google.calls.contains("restore"));
    assertFalse(google.calls.contains("delete"));
    assertTrue(google.trashed.isEmpty());
  }

  @Test
  public void anOriginalGoogleNeverForgetsIsNotReuploaded() throws Exception {
    google.forgetAfterFinds = 1000;
    finish();
    for (int i = 0; i < 20 && !job.state.terminal(); i++) finish();
    assertEquals(ReclaimEngine.State.ATTENTION, job.state);
    assertEquals(0, google.uploads);
    assertTrue(new File(job.file).exists());
  }

  @Test
  public void uploadFailureAfterDeletionNeedsAttentionAndKeepsTheCopy() throws Exception {
    google.uploadError = new PhotosApi.ApiException("rejected", false, true, 400);
    engine.run(job, Long.MAX_VALUE);
    assertEquals(ReclaimEngine.State.ATTENTION, job.state);
    assertTrue(new File(job.file).exists());
  }

  @Test
  public void transientUploadFailureAfterDeletionIsRetried() throws Exception {
    google.uploadError = new PhotosApi.ApiException("HTTP 503", false, false, 503);
    try {
      engine.run(job, Long.MAX_VALUE);
      fail("transient error should propagate for a later retry");
    } catch (PhotosApi.ApiException expected) {
    }
    assertEquals(ReclaimEngine.State.DELETED, job.state);
    google.uploadError = null;
    finish();
    assertEquals(ReclaimEngine.State.DONE, job.state);
  }

  @Test
  public void pauseIsNotAFailure() throws Exception {
    google =
        new FakeGoogle() {
          @Override
          public String find(byte[] sha1) throws InterruptedIOException {
            throw new InterruptedIOException("paused");
          }
        };
    engine = new ReclaimEngine(google, journal, (j, d) -> {}, dir, CLOCK);
    assertThrows(InterruptedIOException.class, () -> engine.run(job, Long.MAX_VALUE));
    assertEquals(ReclaimEngine.State.QUEUED, job.state);
  }

  /** Google's record of the original with other reported properties. */
  LibraryProtocol.Item variant(long size, boolean reduced, boolean motion, byte[] sha1) {
    LibraryProtocol.Extra x = original.extra();
    return new LibraryProtocol.Item(
        "old", original.name(), original.type(), size, original.charged(), original.taken(),
        false, original.archived(), 1,
        new LibraryProtocol.Extra(x.dedup(), sha1, x.caption(), x.favorite(), x.edited(),
            true, false, motion,
            reduced ? LibraryProtocol.Quality.REDUCED : LibraryProtocol.Quality.ORIGINAL));
  }

  ReclaimEngine engineWithPhone(byte[] phoneFile, boolean reduced) throws Exception {
    google =
        new FakeGoogle() {
          @Override
          public List<ReclaimEngine.Local> localOriginals(ReclaimEngine.Job job) {
            return List.of(
                new ReclaimEngine.Local(
                    "wrong", "ref-wrong", () -> new java.io.ByteArrayInputStream(EDITED)),
                new ReclaimEngine.Local(
                    "match", "ref-match", () -> new java.io.ByteArrayInputStream(phoneFile)));
          }

          @Override
          public boolean phoneCopyPresent(String ref) {
            assertEquals("ref-match", ref);
            return phonePresent;
          }

          @Override
          public void restorePhoneCopy(String ref, File copy) throws IOException {
            assertEquals("ref-match", ref);
            restored = Files.readAllBytes(copy.toPath());
          }

          @Override
          public List<PhotosApi.Link> downloadLinks(String key) {
            calls.add("links");
            return reduced
                ? List.of(new PhotosApi.Link("1.5.2.6", "https://x.googleusercontent.com/e"))
                : super.downloadLinks(key);
          }
        };
    google.byHash.put(google.hex(sha1(ORIGINAL)), "old");
    google.library.put("old", original);
    job.reduced = reduced;
    if (reduced) google.library.put("old", variant(ORIGINAL.length, true, false, sha1(ORIGINAL)));
    return new ReclaimEngine(google, journal, (j, d) -> {}, dir, CLOCK);
  }

  @Test
  public void phoneOriginalIsUsedWithoutDownloading() throws Exception {
    engine = engineWithPhone(ORIGINAL, false);
    engine.run(job, Long.MAX_VALUE);
    assertEquals(ReclaimEngine.State.DONE, job.state);
    assertFalse(google.calls.contains("links"));
    assertFalse(google.calls.stream().anyMatch(c -> c.startsWith("download")));
  }

  @Test
  public void phoneCopyRemovedByGooglePhotosIsPutBack() throws Exception {
    engine = engineWithPhone(ORIGINAL, false);
    engine.run(job, Long.MAX_VALUE);
    assertEquals(ReclaimEngine.State.DONE, job.state);
    assertTrue("copy kept until the phone is checked", new File(job.file).exists());
    assertTrue(ReclaimEngine.needsGuard(job));
    google.phonePresent = false;
    engine.guard(job);
    assertArrayEquals(ORIGINAL, google.restored);
    assertFalse(new File(job.file).exists());
    assertFalse(ReclaimEngine.needsGuard(job));
  }

  @Test
  public void phoneCopyStillPresentJustReleasesTheAppCopy() throws Exception {
    engine = engineWithPhone(ORIGINAL, false);
    engine.run(job, Long.MAX_VALUE);
    engine.guard(job);
    assertNull(google.restored);
    assertFalse(new File(job.file).exists());
    assertFalse(ReclaimEngine.needsGuard(job));
  }

  @Test
  public void downloadedOriginalWithoutPhoneCopyNeedsNoCheck() throws Exception {
    engine.run(job, Long.MAX_VALUE);
    assertFalse(ReclaimEngine.needsGuard(job));
  }

  @Test
  public void reducedCopyIsReplacedOnlyByTheVerifiedPhoneOriginal() throws Exception {
    engine = engineWithPhone(ORIGINAL, true);
    job.size = 7; // Google reports the reduced copy's size, not the original's
    engine.run(job, Long.MAX_VALUE);
    assertEquals(ReclaimEngine.State.DONE, job.state);
    assertEquals(ORIGINAL.length, job.size);

    setUp();
    engine = engineWithPhone("not the original".getBytes(), true);
    engine.run(job, Long.MAX_VALUE);
    assertEquals(ReclaimEngine.State.FAILED, job.state);
    // Google's reduced copy is never downloaded as a stand-in for the original.
    assertFalse(google.calls.contains("links"));
    assertFalse(google.calls.contains("trash"));
  }

  @Test
  public void reducedItemsAreEligibleEvenWhenFree() throws Exception {
    LibraryProtocol.Extra reduced =
        new LibraryProtocol.Extra(
            "d1234567",
            sha1(ORIGINAL),
            "",
            false,
            false,
            true,
            false,
            false,
            LibraryProtocol.Quality.REDUCED);
    assertNull(
        ReclaimEngine.ineligible(
            new LibraryProtocol.Item("k", "a.jpg", 1, 10L, 0L, 0, false, false, 1, reduced)));
  }

  @Test
  public void stagedNamesProveContentAndAccount() throws Exception {
    engine.run(job, Long.MAX_VALUE);
    // The copy is gone after success; the name scheme is what recovery relies on.
    String name = ReclaimEngine.fileName(job);
    assertEquals(ReclaimEngine.hex(sha1(ORIGINAL)), ReclaimEngine.stagedHash(name));
    assertEquals(ReclaimEngine.accountTag("me@example.com"), ReclaimEngine.stagedAccount(name));
    assertEquals(ReclaimEngine.accountTag(" Me@Example.com"), ReclaimEngine.stagedAccount(name));
    assertNotEquals(ReclaimEngine.accountTag("other@example.com"), ReclaimEngine.stagedAccount(name));
    assertEquals("IMG 1.jpg", ReclaimEngine.stagedName(name));
    assertFalse("tag must not reveal the address", name.contains("example"));
    assertNull(ReclaimEngine.stagedHash(ReclaimEngine.PARTIAL + name));
    assertNull(ReclaimEngine.stagedHash("IMG_0001.jpg"));
    job.name = "clip";
    job.type = 2;
    assertTrue(ReclaimEngine.fileName(job).endsWith("_clip.mp4"));
    job.name = "a/b:c.HEIC";
    job.type = 1;
    assertTrue(ReclaimEngine.fileName(job).endsWith("_a_b_c.HEIC"));
  }

  static byte[] concat(byte[]... parts) {
    java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
    for (byte[] p : parts) out.writeBytes(p);
    return out.toByteArray();
  }

  static byte[] box(String type, String brand, int size) {
    java.nio.ByteBuffer b = java.nio.ByteBuffer.allocate(size);
    b.putInt(size).put(type.getBytes()).put(brand.getBytes());
    return b.array();
  }

  static boolean embedded(byte[] data) throws Exception {
    File f = File.createTempFile("motion", ".bin");
    Files.write(f.toPath(), data);
    try {
      return ReclaimEngine.hasEmbeddedVideo(f);
    } finally {
      f.delete();
    }
  }

  @Test
  public void embeddedVideoIsRecognisedOnlyWhenPresent() throws Exception {
    byte[] jpeg = concat(new byte[] {(byte) 0xff, (byte) 0xd8}, new byte[5000], new byte[] {(byte) 0xff, (byte) 0xd9});
    byte[] mp4 = concat(box("ftyp", "isom", 24), new byte[3000]);
    assertFalse("plain photo", embedded(jpeg));
    assertTrue("Pixel Motion Photo", embedded(concat(jpeg, mp4)));
    assertTrue("Samsung Motion Photo", embedded(concat(jpeg, "MotionPhoto_Data".getBytes(), mp4)));
    byte[] heic = concat(box("ftyp", "heic", 24), new byte[4000]);
    assertFalse("HEIC's own header is not a video", embedded(heic));
    assertTrue("HEIC Motion Photo", embedded(concat(heic, box("ftyp", "mp42", 20), new byte[100])));
    // A video box straddling the 256 KiB read boundary is still found.
    byte[] big = new byte[256 * 1024 - 6];
    assertTrue(embedded(concat(jpeg, big, mp4)));
  }

  @Test
  public void livePhotoWithSeparateVideoIsRefusedBeforeAnyChange() throws Exception {
    engine = engineWithPhone(ORIGINAL, false);
    // Google shows motion, but ORIGINAL carries no video of its own
    google.library.put("old", variant(ORIGINAL.length, false, true, sha1(ORIGINAL)));
    engine.run(job, Long.MAX_VALUE);
    assertEquals(ReclaimEngine.State.FAILED, job.state);
    assertFalse(google.calls.contains("trash"));
    assertEquals(0, Objects.requireNonNull(dir.listFiles()).length);
  }

  @Test
  public void motionPhotoWithEmbeddedVideoIsReplaced() throws Exception {
    byte[] motion = concat(ORIGINAL, box("ftyp", "mp42", 24), new byte[64]);
    google = new FakeGoogle();
    google.byHash.put(google.hex(sha1(motion)), "old");
    job.sha1 = sha1(motion);
    job.size = motion.length;
    job.motion = true;
    engine =
        new ReclaimEngine(
            new FakeGoogle() {
              {
                byHash.put(hex(sha1(motion)), "old");
                library.put("old", variant(motion.length, false, true, job.sha1));
              }

              @Override
              public List<ReclaimEngine.Local> localOriginals(ReclaimEngine.Job j) {
                return List.of(
                    new ReclaimEngine.Local(
                        "motion", "ref-match", () -> new java.io.ByteArrayInputStream(motion)));
              }

              @Override
              public boolean phoneCopyPresent(String ref) {
                return true;
              }
            },
            journal,
            (j, d) -> {},
            dir,
            CLOCK);
    engine.run(job, Long.MAX_VALUE);
    assertEquals(ReclaimEngine.State.DONE, job.state);
  }

  @Test
  public void permanentDeleteWaitsForTheTrashToShowAndOtherwiseRestores() throws Exception {
    google.trashVisible = false; // Google never shows the item as trashed
    for (int i = 0; i < 20 && !job.state.terminal(); i++) {
      try {
        engine.run(job, Long.MAX_VALUE);
      } catch (ReclaimEngine.Later e) {
      }
    }
    assertEquals(ReclaimEngine.State.FAILED, job.state);
    assertFalse(google.calls.contains("delete"));
    assertTrue(google.calls.contains("restore"));
  }

  @Test
  public void itemChangedSinceSelectionIsNotTouched() throws Exception {
    google.library.put("old", google.copy(original, true)); // user trashed it meanwhile
    engine.run(job, Long.MAX_VALUE);
    assertEquals(ReclaimEngine.State.FAILED, job.state);
    assertFalse(google.calls.contains("trash"));
  }

  @Test
  public void mismatchedReplacementKeepsTheCopy() throws Exception {
    google =
        new FakeGoogle() {
          @Override
          public LibraryProtocol.Item lookup(String key) {
            LibraryProtocol.Item i = super.lookup(key);
            if (i != null && key.startsWith("new"))
              return new LibraryProtocol.Item(key, i.name(), 1, i.size() + 1, 0L, 0, false,
                  false, 1, i.extra());
            return i;
          }
        };
    google.byHash.put(google.hex(sha1(ORIGINAL)), "old");
    google.library.put("old", original);
    engine = new ReclaimEngine(google, journal, (j, d) -> {}, dir, CLOCK);
    engine.run(job, Long.MAX_VALUE);
    assertEquals(ReclaimEngine.State.ATTENTION, job.state);
    assertTrue(new File(job.file).exists());
  }

  @Test
  public void ineligibleItemsAreExplained() throws Exception {
    LibraryProtocol.Extra ok =
        new LibraryProtocol.Extra("d1234567", sha1(ORIGINAL), "", false, false, true, false, false);
    LibraryProtocol.Item free =
        new LibraryProtocol.Item("k", "a.jpg", 1, 10L, 0L, 0, false, false, 1, ok);
    assertNotNull(ReclaimEngine.ineligible(free));
    LibraryProtocol.Item shared =
        new LibraryProtocol.Item("k", "a.jpg", 1, 10L, 10L, 0, false, false, 4, ok);
    assertNotNull(ReclaimEngine.ineligible(shared));
    LibraryProtocol.Item noHash =
        new LibraryProtocol.Item(
            "k",
            "a.jpg",
            1,
            10L,
            10L,
            0,
            false,
            false,
            1,
            new LibraryProtocol.Extra("d1234567", null, "", false, false, true, false, false));
    assertNotNull(ReclaimEngine.ineligible(noHash));
    LibraryProtocol.Item motion =
        new LibraryProtocol.Item(
            "k",
            "a.MP.jpg",
            1,
            10L,
            10L,
            0,
            false,
            false,
            1,
            new LibraryProtocol.Extra("d1234567", sha1(ORIGINAL), "", false, false, true, false, true));
    assertNull("motion items are decided by their verified file", ReclaimEngine.ineligible(motion));
    assertNull(
        ReclaimEngine.ineligible(
            new LibraryProtocol.Item("k", "a.jpg", 1, 10L, 10L, 0, false, false, 1, ok)));
  }
}
