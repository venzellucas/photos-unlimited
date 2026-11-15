package app.photos.unlimited;

import static org.junit.Assert.*;

import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.util.List;
import org.junit.*;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class InventoryInstrumentedTest {
  private Context context;

  private LibraryProtocol.Item item(String key, Long charged) {
    return new LibraryProtocol.Item(key, key + ".jpg", 1, 100L, charged, 1, false, false, 1);
  }

  @Before
  public void setup() {
    context = ApplicationProvider.getApplicationContext();
    context.deleteDatabase("inventory.db");
  }

  @After
  public void cleanup() {
    context.deleteDatabase("inventory.db");
  }

  @Test
  public void pageAndCursorSurviveReopenWithNullQuotaAndAccountIsolation() {
    try (InventoryDb db = new InventoryDb(context)) {
      var scan = db.reset("a");
      var cursor =
          new LibraryProtocol.Cursor(
              LibraryProtocol.Stage.INITIAL,
              "next".getBytes(),
              "sync".getBytes(),
              "sync".getBytes(),
              false);
      assertTrue(
          db.apply(
              "a",
              scan,
              new LibraryProtocol.Page(
                  List.of(item("unknown", null), item("zero", 0L), item("charged", 50L)),
                  List.of(),
                  new byte[0],
                  new byte[0],
                  false),
              cursor));
    }
    try (InventoryDb db = new InventoryDb(context)) {
      var summary = db.summary("a");
      assertEquals(3, summary.items());
      assertEquals(1, summary.unknown());
      assertEquals(1, summary.zero());
      assertEquals(50, summary.chargedBytes());
      assertArrayEquals("next".getBytes(), summary.scan().cursor().resume());
      assertEquals(0, db.summary("b").items());
      assertEquals("charged", db.list("a", 1, 0, 0).get(0).key());
      assertNull(db.list("a", 2, 0, 0).get(0).charged());
    }
  }

  @Test
  public void rescanGenerationRejectsOldPagesAndClearCannotRepopulate() {
    try (InventoryDb db = new InventoryDb(context)) {
      var old = db.reset("a");
      var fresh = db.reset("a");
      var page =
          new LibraryProtocol.Page(
              List.of(item("old", 50L)), List.of(), new byte[0], new byte[0], false);
      assertFalse(db.apply("a", old, page, old.cursor()));
      assertEquals(0, db.summary("a").items());
      db.clear("a");
      assertFalse(db.apply("a", fresh, page, fresh.cursor()));
      assertEquals(0, db.summary("a").items());
    }
  }

  @Test
  public void duplicatePagesDoNotDoubleCountAndDeletesOnlyTouchCache() {
    try (InventoryDb db = new InventoryDb(context)) {
      var scan = db.reset("a");
      var page =
          new LibraryProtocol.Page(
              List.of(item("one", 50L)), List.of(), new byte[0], new byte[0], false);
      db.apply("a", scan, page, scan.cursor());
      db.apply("a", db.scan("a"), page, scan.cursor());
      assertEquals(1, db.summary("a").items());
      db.apply(
          "a",
          db.scan("a"),
          new LibraryProtocol.Page(List.of(), List.of("one"), new byte[0], new byte[0], false),
          scan.cursor());
      assertEquals(0, db.summary("a").items());
    }
  }

  @Test
  public void packagedTemplatesHaveNoForeignAccountIdentifier() throws Exception {
    for (String name : List.of("state", "initial", "delta")) {
      byte[] data;
      try (var in = context.getAssets().open("library-" + name + ".bin")) {
        data = in.readAllBytes();
      }
      assertEquals(0, Proto.message(Proto.message(Proto.message(data, 1), 22), 2).length);
      assertTrue(Proto.message(Proto.message(data, 1), 1).length > 0);
    }
  }
}
