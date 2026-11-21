package app.photos.unlimited;

import static org.junit.Assert.*;

import java.io.IOException;
import java.util.List;
import org.junit.Test;

public class LibraryProtocolTest {
  private static Proto item(String key, Long charged) {
    Proto details = new Proto().text(4, "photo.jpg").number(10, 500).number(7, 123);
    if (charged != null) details.message(35, new Proto().number(2, charged));
    return new Proto().text(1, key).message(2, details).message(5, new Proto().number(1, 1));
  }

  private static LibraryProtocol.Page page(String resume, String sync, boolean more) {
    return new LibraryProtocol.Page(List.of(), List.of(), resume.getBytes(), sync.getBytes(), more);
  }

  @Test
  public void absentQuotaRemainsUnknownAndExplicitZeroIsRetained() throws Exception {
    byte[] response =
        new Proto()
            .message(
                1,
                new Proto()
                    .text(6, "sync")
                    .message(2, item("a", null))
                    .message(2, item("b", 0L))
                    .message(2, item("c", 123L)))
            .build();
    var p = LibraryProtocol.parse(response);
    assertNull(p.items().get(0).charged());
    assertEquals(Long.valueOf(0), p.items().get(1).charged());
    assertEquals(Long.valueOf(123), p.items().get(2).charged());
  }

  @Test
  public void negativeQuotaIsUnknown() throws Exception {
    var p =
        LibraryProtocol.parse(
            new Proto().message(1, new Proto().text(6, "sync").message(2, item("a", -1L))).build());
    assertNull(p.items().get(0).charged());
  }

  @Test
  public void deletionsAndFlagsAreReadWithoutMutatingRemoteData() throws Exception {
    Proto media = item("a", 42L);
    byte[] response =
        new Proto()
            .message(
                1,
                new Proto()
                    .text(6, "sync")
                    .message(2, media)
                    .message(
                        9,
                        new Proto()
                            .message(
                                1,
                                new Proto()
                                    .number(1, 1)
                                    .message(2, new Proto().text(1, "deleted")))))
            .build();
    var p = LibraryProtocol.parse(response);
    assertEquals(List.of("deleted"), p.deletions());
    assertEquals(1, p.items().size());
  }

  @Test
  public void initialAndDeltaPagesKeepTheirOwnCursors() throws Exception {
    var c =
        LibraryProtocol.advance(
            LibraryProtocol.Cursor.fresh(), page("initial-page", "sync-a", false));
    assertEquals(LibraryProtocol.Stage.INITIAL, c.stage());
    c = LibraryProtocol.advance(c, page("", "", false));
    assertEquals(LibraryProtocol.Stage.SYNC, c.stage());
    c = LibraryProtocol.advance(c, page("delta-page", "sync-b", false));
    assertEquals(LibraryProtocol.Stage.DELTA, c.stage());
    assertArrayEquals("sync-a".getBytes(), c.sync());
    c = LibraryProtocol.advance(c, page("", "", false));
    assertEquals(LibraryProtocol.Stage.DONE, c.stage());
    assertArrayEquals("sync-b".getBytes(), c.sync());
  }

  @Test
  public void anEmptyDeltaPageEndsTheDeltaPass() throws Exception {
    byte[] s1 = "sync-1".getBytes(), s2 = "sync-2".getBytes();
    var done =
        LibraryProtocol.advance(
            new LibraryProtocol.Cursor(LibraryProtocol.Stage.DELTA, "r".getBytes(), s1, s2, false),
            LibraryProtocol.EMPTY);
    assertEquals(LibraryProtocol.Stage.DONE, done.stage());
    assertArrayEquals(s2, done.sync());
    assertArrayEquals(s2, done.nextSync());
    // An earlier page said "more" and the sync token moved: another sync pass follows.
    var again =
        LibraryProtocol.advance(
            new LibraryProtocol.Cursor(LibraryProtocol.Stage.DELTA, "r".getBytes(), s1, s2, true),
            LibraryProtocol.EMPTY);
    assertEquals(LibraryProtocol.Stage.SYNC, again.stage());
    assertArrayEquals(s2, again.sync());
    // "More" without a new sync token would loop: still SCAN_CURSOR.
    ScanFailure stuck =
        assertThrows(
            ScanFailure.class,
            () ->
                LibraryProtocol.advance(
                    new LibraryProtocol.Cursor(
                        LibraryProtocol.Stage.DELTA, "r".getBytes(), s1, s1, true),
                    LibraryProtocol.EMPTY));
    assertEquals("SCAN_CURSOR", stuck.label());
  }

  @Test
  public void continuationRequiresAdvancingSync() throws Exception {
    var c =
        new LibraryProtocol.Cursor(
            LibraryProtocol.Stage.SYNC, new byte[0], "same".getBytes(), "same".getBytes(), false);
    assertThrows(IOException.class, () -> LibraryProtocol.advance(c, page("", "same", true)));
    assertEquals(
        LibraryProtocol.Stage.SYNC, LibraryProtocol.advance(c, page("", "new", true)).stage());
  }

  @Test
  public void repeatedPagesAreRejected() {
    var c =
        new LibraryProtocol.Cursor(
            LibraryProtocol.Stage.INITIAL,
            "same".getBytes(),
            "s".getBytes(),
            "s".getBytes(),
            false);
    assertThrows(IOException.class, () -> LibraryProtocol.advance(c, page("same", "", false)));
  }

  @Test
  public void unexpectedOrTruncatedResponsesAreNotEmptySuccess() {
    assertThrows(IOException.class, () -> LibraryProtocol.parse(new byte[0]));
    assertThrows(
        IOException.class,
        () -> LibraryProtocol.parse(new Proto().message(1, new Proto().number(90, 1)).build()));
    assertThrows(IOException.class, () -> LibraryProtocol.parse(new byte[] {10, 5, 8}));
    assertThrows(
        IOException.class,
        () ->
            LibraryProtocol.parse(
                new Proto()
                    .message(
                        1,
                        new Proto()
                            .text(6, "s")
                            .message(2, new Proto().message(2, new Proto().text(4, "no-key"))))
                    .build()));
  }

  @Test
  public void unreadableItemBecomesUnknownAndPageStillParses() throws Exception {
    Proto ambiguous =
        new Proto()
            .text(1, "b")
            .message(
                2,
                new Proto()
                    .text(4, "b.jpg")
                    .number(10, 500)
                    .number(10, 600)
                    .message(35, new Proto().number(2, 77)))
            .message(5, new Proto().number(1, 1));
    Proto badName =
        new Proto()
            .text(1, "c")
            .message(
                2,
                new Proto()
                    .bytes(4, new byte[] {(byte) 0xC3, 0x28})
                    .number(10, 500)
                    .message(35, new Proto().number(2, 88)))
            .message(5, new Proto().number(1, 1));
    var p =
        LibraryProtocol.parse(
            new Proto()
                .message(
                    1,
                    new Proto()
                        .text(6, "sync")
                        .message(2, item("a", 42L))
                        .message(2, ambiguous)
                        .message(2, badName))
                .build());
    assertEquals(3, p.items().size());
    var normal = p.items().get(0);
    assertEquals("a", normal.key());
    assertEquals("photo.jpg", normal.name());
    assertEquals(Long.valueOf(42), normal.charged());
    assertEquals(1, normal.type());
    var unknown = p.items().get(1);
    assertEquals("b", unknown.key());
    assertEquals("Unnamed media", unknown.name());
    assertNull(unknown.charged());
    assertNull(unknown.size());
    assertSame(LibraryProtocol.Extra.NONE, unknown.extra());
    assertNotNull(ReclaimEngine.ineligible(unknown));
    // The name is also the re-upload name, so a damaged one makes the item unknown.
    var named = p.items().get(2);
    assertEquals("c", named.key());
    assertEquals("Unnamed media", named.name());
    assertNull(named.charged());
    assertNotNull(ReclaimEngine.ineligible(named));
  }

  @Test
  public void overlongNameMakesOnlyThatItemUnknown() throws Exception {
    Proto longName =
        new Proto()
            .text(1, "long")
            .message(
                2,
                new Proto()
                    .text(4, "n".repeat(16385))
                    .number(10, 500)
                    .message(35, new Proto().number(2, 9)))
            .message(5, new Proto().number(1, 1));
    var p =
        LibraryProtocol.parse(
            new Proto()
                .message(
                    1,
                    new Proto()
                        .text(6, "sync")
                        .message(2, longName)
                        .message(2, item("a", 42L)))
                .build());
    assertEquals(2, p.items().size());
    assertEquals("long", p.items().get(0).key());
    assertNull(p.items().get(0).charged());
    assertSame(LibraryProtocol.Extra.NONE, p.items().get(0).extra());
    assertEquals(Long.valueOf(42), p.items().get(1).charged());
  }

  private static byte[] deletionPage(Proto entry) {
    return new Proto()
        .message(1, new Proto().text(6, "s").message(9, new Proto().message(1, entry)))
        .build();
  }

  @Test
  public void deletionEntriesStayStrict() throws Exception {
    assertEquals(
        "SCAN_ITEM_TEXT_LONG",
        label(
            () ->
                LibraryProtocol.parse(
                    deletionPage(
                        new Proto()
                            .number(1, 1)
                            .message(2, new Proto().text(1, "d".repeat(16385)))))));
    assertEquals(
        "SCAN_ITEM_INVALID",
        label(
            () ->
                LibraryProtocol.parse(
                    deletionPage(new Proto().number(1, 1).message(2, new Proto().text(1, " "))))));
    var skipped =
        LibraryProtocol.parse(
            deletionPage(new Proto().number(1, 2).message(2, new Proto().text(1, "other"))));
    assertEquals(List.of(), skipped.deletions());
  }

  private static String pageLabel(byte[] response) {
    return label(() -> LibraryProtocol.parse(response));
  }

  private static byte[] rootOf(byte[] root) {
    return new Proto().bytes(1, root).build();
  }

  @Test
  public void pageFailuresNameStepReasonFieldAndWire() {
    byte[] root = new Proto().text(6, "s").build();
    assertEquals(
        "SCAN_FORMAT_PAGE_ENV_DUP_F1",
        pageLabel(new Proto().bytes(1, root).bytes(1, root).build()));
    // The wire check runs first: a right-type field 1 followed by a varint field 1 is WIRE.
    assertEquals(
        "SCAN_FORMAT_PAGE_ENV_WIRE_F1_W0",
        pageLabel(new Proto().bytes(1, root).number(1, 5).build()));
    assertEquals("SCAN_FORMAT_PAGE_ENV_NONE", pageLabel(new Proto().text(2, "x").build()));
    // Tag 0x63 is field 12 with wire type 3 (a group), which the decoder doesn't support.
    assertEquals(
        "SCAN_FORMAT_PAGE_ROOT_WIRE_F12_W3",
        pageLabel(rootOf(new byte[] {0x32, 1, 's', 0x63})));
    assertEquals(
        "SCAN_FORMAT_PAGE_SHAPE_WIRE_F7_W2",
        pageLabel(rootOf(new Proto().text(6, "s").text(7, "x").build())));
    assertEquals(
        "SCAN_FORMAT_PAGE_SHAPE_NONE_F90", pageLabel(rootOf(new Proto().number(90, 1).build())));
    // Collections (3) and envelopes (12) only: the label names the lowest field.
    assertEquals(
        "SCAN_FORMAT_PAGE_SHAPE_NONE_F3",
        pageLabel(rootOf(new Proto().text(12, "e").text(3, "c").build())));
    assertEquals(
        "SCAN_FORMAT_PAGE_END_DUP_F6",
        pageLabel(rootOf(new Proto().text(6, "s").text(6, "t").build())));
    assertEquals(
        "SCAN_FORMAT_PAGE_END_DUP_F7",
        pageLabel(rootOf(new Proto().text(6, "s").number(7, 2).number(7, 2).build())));
    // Tag 0x38 is field 7 (varint) with no value after it: the field is added after the tag.
    assertEquals("SCAN_FORMAT_PAGE_ROOT_CUT_F7", pageLabel(rootOf(new byte[] {0x32, 1, 's', 0x38})));
    // A zero tag is invalid and names no field.
    assertEquals("SCAN_FORMAT_PAGE_ROOT_TAG", pageLabel(rootOf(new byte[] {0x32, 1, 's', 0x00})));
  }

  @Test
  public void stageIsAddedOnlyToPageLabelsAndKeepsTheMessage() {
    // Tag FF FF FF FF 0F is field 536870911 with wire type 7: the longest reachable label.
    ScanFailure longest =
        assertThrows(
            ScanFailure.class,
            () ->
                LibraryProtocol.parse(
                    rootOf(
                        new byte[] {0x32, 1, 's', (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, 0x0F})));
    assertEquals("SCAN_FORMAT_PAGE_ROOT_WIRE_F536870911_W7", longest.label());
    ScanFailure staged = LibraryProtocol.withStage(longest, LibraryProtocol.Stage.INITIAL);
    assertEquals("SCAN_FORMAT_PAGE_ROOT_WIRE_F536870911_W7_I", staged.label());
    assertTrue(staged.label().length() <= 48);
    assertEquals(longest.getMessage(), staged.getMessage());
    ScanFailure key = new ScanFailure("SCAN_FORMAT_KEY", "k");
    assertSame(key, LibraryProtocol.withStage(key, LibraryProtocol.Stage.DELTA));
    assertEquals(
        "SCAN_FORMAT_PAGE_END_DUP_F6_N",
        LibraryProtocol.withStage(
                new ScanFailure("SCAN_FORMAT_PAGE_END_DUP_F6", "m"), LibraryProtocol.Stage.NEW)
            .label());
  }

  @Test
  public void malformedKeepsMessagesAndNeverWrapsFieldNumbers() {
    Proto.Malformed cut =
        assertThrows(Proto.Malformed.class, () -> Proto.values(new byte[] {10, 5, 8}));
    assertEquals("Incomplete protocol response", cut.getMessage());
    Proto.Malformed dup =
        assertThrows(
            Proto.Malformed.class,
            () -> Proto.message(new Proto().text(3, "a").text(3, "b").build(), 3));
    assertEquals("Ambiguous protocol message", dup.getMessage());
    Proto.Malformed integer =
        assertThrows(
            Proto.Malformed.class,
            () -> Proto.integer(new Proto().number(3, 1).number(3, 2).build(), 3));
    assertEquals("Ambiguous protocol integer", integer.getMessage());
    // The wire check runs first for integers too: a varint field 3 then a text field 3 is WIRE.
    Proto.Malformed wrongType =
        assertThrows(
            Proto.Malformed.class,
            () -> Proto.integer(new Proto().number(3, 1).text(3, "x").build(), 3));
    assertEquals("WIRE", wrongType.reason);
    assertEquals(3, wrongType.field);
    assertEquals(2, wrongType.wire);
    assertEquals("Ambiguous protocol integer", wrongType.getMessage());
    // Tag varint 82 80 80 80 40 is field 2^31 with wire type 2, then a length past the end.
    Proto.Malformed wrapped =
        assertThrows(
            Proto.Malformed.class,
            () ->
                Proto.fields(
                    new byte[] {(byte) 0x82, (byte) 0x80, (byte) 0x80, (byte) 0x80, 0x40, 5}, 2));
    assertEquals("Incomplete protocol response", wrapped.getMessage());
    assertEquals(0, wrapped.field);
  }

  private static String label(org.junit.function.ThrowingRunnable run) {
    ScanFailure failure = assertThrows(ScanFailure.class, run);
    return failure.label();
  }

  @Test
  public void scanFailuresCarryFixedLabels() {
    assertEquals(
        "SCAN_ITEM_TEXT_LONG",
        label(
            () ->
                LibraryProtocol.parse(
                    new Proto()
                        .message(1, new Proto().text(6, "s").message(2, item("x".repeat(16385), 1L)))
                        .build())));
    assertEquals("SCAN_FORMAT_PAGE_ENV_NONE", label(() -> LibraryProtocol.parse(new byte[0])));
    assertEquals(
        "SCAN_FORMAT_PAGE_ENV_CUT_F1", label(() -> LibraryProtocol.parse(new byte[] {10, 5, 8})));
    // Field 2 claims five bytes but carries one: the page itself is cut off.
    assertEquals(
        "SCAN_FORMAT_PAGE_ROOT_CUT_F2",
        label(
            () ->
                LibraryProtocol.parse(
                    new Proto().bytes(1, new byte[] {0x32, 1, 's', 0x12, 5, 'a'}).build())));
    assertEquals(
        "SCAN_FORMAT_KEY",
        label(
            () ->
                LibraryProtocol.parse(
                    new Proto()
                        .message(
                            1, new Proto().text(6, "s").message(2, item("a", 1L).text(1, "b")))
                        .build())));
    // Field 5 claims nine bytes but carries one: the item envelope itself is broken.
    assertEquals(
        "SCAN_FORMAT_ITEM",
        label(
            () ->
                LibraryProtocol.parse(
                    new Proto()
                        .message(
                            1,
                            new Proto()
                                .text(6, "s")
                                .bytes(2, new byte[] {0x0a, 1, 'k', 0x2a, 9, 8}))
                        .build())));
    // A keyed item with no details at all is malformed, not an unknown item.
    assertEquals(
        "SCAN_ITEM_INVALID",
        label(
            () ->
                LibraryProtocol.parse(
                    new Proto()
                        .message(
                            1,
                            new Proto()
                                .text(6, "s")
                                .message(
                                    2,
                                    new Proto().text(1, "k").message(5, new Proto().number(1, 1))))
                        .build())));
    assertEquals(
        "SCAN_FORMAT_DELETION",
        label(
            () ->
                LibraryProtocol.parse(
                    new Proto()
                        .message(
                            1,
                            new Proto()
                                .text(6, "s")
                                .message(
                                    9,
                                    new Proto()
                                        .message(1, new Proto().number(1, 1).number(1, 1))))
                        .build())));
    assertEquals(
        "SCAN_ITEM_INVALID",
        label(
            () ->
                LibraryProtocol.parse(
                    new Proto()
                        .message(1, new Proto().text(6, "s").message(2, item(" ", 1L)))
                        .build())));
    var c =
        new LibraryProtocol.Cursor(
            LibraryProtocol.Stage.INITIAL,
            "same".getBytes(),
            "s".getBytes(),
            "s".getBytes(),
            false);
    assertEquals("SCAN_CURSOR", label(() -> LibraryProtocol.advance(c, page("same", "", false))));
  }

  @Test
  public void requestChangesOnlyCursorFields() throws Exception {
    byte[] template =
        new Proto()
            .message(
                1,
                new Proto()
                    .message(1, new Proto().number(7, 9))
                    .text(4, "old")
                    .text(6, "old")
                    .number(7, 2))
            .message(2, new Proto().number(8, 3))
            .build();
    var c =
        new LibraryProtocol.Cursor(
            LibraryProtocol.Stage.DELTA,
            "resume".getBytes(),
            "sync".getBytes(),
            new byte[0],
            false);
    byte[] result = LibraryProtocol.request(template, c), body = Proto.message(result, 1);
    assertArrayEquals("resume".getBytes(), Proto.message(body, 4));
    assertArrayEquals("sync".getBytes(), Proto.message(body, 6));
    assertArrayEquals(Proto.message(Proto.message(template, 1), 1), Proto.message(body, 1));
    assertArrayEquals(Proto.message(template, 2), Proto.message(result, 2));
  }
}
