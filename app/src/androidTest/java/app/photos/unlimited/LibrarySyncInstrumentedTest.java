package app.photos.unlimited;

import static org.junit.Assert.*;

import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.*;
import org.junit.runner.RunWith;

/** Runs the real page loop against a fake transport; test emulator only (it clears the account). */
@RunWith(AndroidJUnit4.class)
public class LibrarySyncInstrumentedTest {
  static final String ACCOUNT = "scan-fixture@example.com";
  static final String CREDENTIAL =
      "Email=scan-fixture%40example.com&Token=synthetic-only&androidId=1234&service=fixture";

  Context context;
  InventoryDb db;

  @Before
  public void setup() throws Exception {
    context = ApplicationProvider.getApplicationContext();
    new AccountStore(context).save(CREDENTIAL);
    db = new InventoryDb(context);
    db.clear(ACCOUNT);
  }

  @After
  public void cleanup() {
    db.clear(ACCOUNT);
    db.close();
    new AccountStore(context).clear();
  }

  @Test
  public void malformedPageLabelCarriesTheScanStage() throws Exception {
    PhotosApi.Transport transport =
        (url, method, headers, body, length, stop) -> {
          if (url.equals(PhotosApi.AUTH))
            return new PhotosApi.Response(
                "Auth=synthetic-bearer\nExpiry=9999999999\n".getBytes(StandardCharsets.UTF_8),
                Map.of());
          // Field 1 claims five bytes but carries one: the outer response is cut off.
          return new PhotosApi.Response(new byte[] {10, 5, 8}, Map.of());
        };
    AtomicBoolean stop = new AtomicBoolean();
    PhotosApi api = new PhotosApi(CREDENTIAL, transport, stop);
    assertEquals(LibraryProtocol.Stage.NEW, db.scan(ACCOUNT).cursor().stage());
    ScanFailure failure =
        assertThrows(
            ScanFailure.class,
            () ->
                LibrarySync.run(
                    context,
                    db,
                    api,
                    new AccountStore(context),
                    ACCOUNT,
                    stop,
                    1,
                    System.currentTimeMillis() + 60_000,
                    null));
    assertEquals("SCAN_FORMAT_PAGE_ENV_CUT_F1_N", failure.label());
    assertEquals("Incomplete protocol response", failure.getMessage());
    assertEquals("SCAN_FORMAT_PAGE_ENV_CUT_F1_N", InventoryService.label(failure));
  }

  /** Answers auth separately, then returns the given pages in order and records each request. */
  static PhotosApi.Transport pages(List<byte[]> requests, byte[]... responses) {
    int[] next = {0};
    return (url, method, headers, body, length, stop) -> {
      if (url.equals(PhotosApi.AUTH))
        return new PhotosApi.Response(
            "Auth=synthetic-bearer\nExpiry=9999999999\n".getBytes(StandardCharsets.UTF_8),
            Map.of());
      ByteArrayOutputStream sent = new ByteArrayOutputStream();
      body.write(sent);
      requests.add(sent.toByteArray());
      return new PhotosApi.Response(responses[next[0]++], Map.of());
    };
  }

  static byte[] response(Proto root) {
    return new Proto().message(1, root).build();
  }

  static byte[] bytes(String text) {
    return text.getBytes(StandardCharsets.UTF_8);
  }

  InventoryDb.Scan run(PhotosApi.Transport transport) throws Exception {
    AtomicBoolean stop = new AtomicBoolean();
    return LibrarySync.run(
        context,
        db,
        new PhotosApi(CREDENTIAL, transport, stop),
        new AccountStore(context),
        ACCOUNT,
        stop,
        5,
        System.currentTimeMillis() + 60_000,
        null);
  }

  @Test
  public void aDeltaPageWithNoKnownFieldsEndsTheScanAndKeepsTheLabel() throws Exception {
    db.reset(ACCOUNT);
    List<byte[]> requests = new ArrayList<>();
    InventoryDb.Scan done =
        run(
            pages(
                requests,
                response(new Proto().bytes(6, bytes("s1"))), // NEW: no resume, so SYNC next
                response(new Proto().bytes(1, bytes("r")).bytes(6, bytes("s2"))), // SYNC → DELTA
                response(new Proto().text(3, "collection")))); // DELTA: no known fields
    assertEquals(3, requests.size());
    // The delta request carries the resume token and the first sync token.
    byte[] delta = Proto.message(requests.get(2), 1);
    assertArrayEquals(bytes("r"), Proto.message(delta, 4));
    assertArrayEquals(bytes("s1"), Proto.message(delta, 6));
    assertEquals(LibraryProtocol.Stage.DONE, done.cursor().stage());
    assertArrayEquals(bytes("s2"), done.cursor().sync());
    assertArrayEquals(bytes("s2"), done.cursor().nextSync());
    assertEquals("Scan finished (SCAN_FORMAT_PAGE_SHAPE_NONE_F3_D skipped)", done.message());
    // The shape-only summary for issue #2: pages per stage, root fields, the skipped page.
    String report = LibraryShape.report(context);
    assertTrue(report, report.contains("\nPages: D=1 N=1 S=1"));
    assertTrue(report, report.contains("\n  N: 6:2x1\n  S: 1:2x1 6:2x1"));
    assertTrue(report, report.contains("\nUnreadable item occurrences: 0"));
    assertTrue(report, report.contains("\nScan: " + done.generation().substring(0, 8)));
    assertTrue(report, report.contains("\nSkipped delta pages: 1\n  x1: 3:2T(s)"));
    assertFalse(report, report.contains("collection"));
    // A new scan starts its own summary.
    db.reset(ACCOUNT);
    assertTrue(
        LibraryShape.load(context, db.scan(ACCOUNT).generation())
            .report()
            .endsWith("No scan recorded yet."));
  }

  @Test
  public void aLastDeltaPageWithOnlyTheCollectionFieldFinishesNormally() throws Exception {
    db.reset(ACCOUNT);
    InventoryDb.Scan done =
        run(
            pages(
                new ArrayList<>(),
                response(new Proto().bytes(6, bytes("s1"))),
                response(new Proto().bytes(1, bytes("r")).bytes(6, bytes("s2")).text(4, "c")),
                response(new Proto().message(4, new Proto().text(1, "c").number(2, 1)))));
    assertEquals(LibraryProtocol.Stage.DONE, done.cursor().stage());
    assertArrayEquals(bytes("s2"), done.cursor().sync());
    assertFalse(done.message(), done.message().contains("skipped"));
    String report = LibraryShape.report(context);
    assertTrue(report, report.contains("\nSkipped delta pages: 0"));
    assertTrue(report, report.contains("\n  D: 4:2x1"));
  }

  @Test
  public void theSamePageInTheInitialListingStillFails() throws Exception {
    db.reset(ACCOUNT);
    List<byte[]> requests = new ArrayList<>();
    ScanFailure failure =
        assertThrows(
            ScanFailure.class,
            () ->
                run(
                    pages(
                        requests,
                        response(new Proto().bytes(1, bytes("r0")).bytes(6, bytes("s1"))),
                        response(new Proto().text(3, "collection")))));
    assertEquals(2, requests.size());
    assertEquals("SCAN_FORMAT_PAGE_SHAPE_NONE_F3_I", failure.label());
    assertEquals(LibraryProtocol.Stage.INITIAL, db.scan(ACCOUNT).cursor().stage());
  }

  @Test
  public void onlyShapeNoneIsReadAsEmptyAndOnlyInTheDelta() throws Exception {
    byte[] nothing = response(new Proto().text(3, "collection"));
    // The last page of each sequence, the label it must fail with, and the stage it must keep.
    Object[][] cases = {
      {response(new Proto()), "SCAN_FORMAT_PAGE_ENV_NONE_D", LibraryProtocol.Stage.DELTA},
      {response(new Proto().number(9, 1)), "SCAN_FORMAT_PAGE_SHAPE_WIRE_F9_W0_D", LibraryProtocol.Stage.DELTA},
      {null, "SCAN_FORMAT_PAGE_SHAPE_NONE_F3_S", LibraryProtocol.Stage.SYNC},
    };
    for (Object[] c : cases) {
      db.reset(ACCOUNT);
      byte[] first = response(new Proto().bytes(6, bytes("s1"))); // NEW: SYNC next
      byte[] sync = response(new Proto().bytes(1, bytes("r")).bytes(6, bytes("s2"))); // → DELTA
      PhotosApi.Transport transport =
          c[0] == null
              ? pages(new ArrayList<>(), first, nothing)
              : pages(new ArrayList<>(), first, sync, (byte[]) c[0]);
      ScanFailure failure = assertThrows(ScanFailure.class, () -> run(transport));
      assertEquals(c[1], failure.label());
      assertEquals(c[2], db.scan(ACCOUNT).cursor().stage());
    }
  }
}
