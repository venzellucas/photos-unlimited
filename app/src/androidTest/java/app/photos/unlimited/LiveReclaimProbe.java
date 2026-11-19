package app.photos.unlimited;

import static org.junit.Assert.*;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import java.net.URL;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Live experiment against the signed-in test account. Skipped unless explicitly requested:
 *
 * <pre>
 * am instrument -w -e class app.photos.unlimited.LiveReclaimProbe \
 *   -e live observe|reclaim -e name FILE_NAME_PREFIX app.photos.unlimited.test/...
 * </pre>
 *
 * observe: scans, reports which reclaim fields Google supplies, downloads candidates for matching
 * items into the cache, and compares checksums. No remote change.
 *
 * <p>reclaim: runs the real engine on exactly one charged item whose name equals {@code name}.
 *
 * <p>Logs (tag ReclaimProbe) never contain credentials, media keys, dedup keys or URLs: only
 * protobuf paths, hosts, sizes and 8-hex checksum prefixes.
 *
 * <p>Never run the whole instrumentation suite on the signed-in emulator: other classes clear the
 * saved account during cleanup.
 */
@RunWith(AndroidJUnit4.class)
public class LiveReclaimProbe {
  private static final String TAG = "ReclaimProbe";
  private final Context context = ApplicationProvider.getApplicationContext();
  private final AtomicBoolean stop = new AtomicBoolean();

  private static void log(String line) {
    Log.i(TAG, line);
    Bundle b = new Bundle();
    b.putString("stream", "ReclaimProbe: " + line + "\n");
    InstrumentationRegistry.getInstrumentation().sendStatus(0, b);
  }

  private static String hex8(byte[] b) {
    return b == null ? "none" : ReclaimEngine.hex(b).substring(0, 8);
  }

  private static String host(String url) {
    try {
      return new URL(url).getHost();
    } catch (Exception e) {
      return "invalid";
    }
  }

  private PhotosApi api(AccountStore store) throws Exception {
    String credential = store.load();
    assertFalse("No account is signed in to the app on this device", credential.isEmpty());
    return new PhotosApi(credential, stop);
  }

  private InventoryDb.Scan scan(InventoryDb db, PhotosApi api, AccountStore store, String account)
      throws Exception {
    assertTrue("Storage scan or reclaim is running", LibrarySync.tryLock());
    try {
      InventoryDb.Scan s = db.scan(account);
      if (s.generation().isEmpty()) db.reset(account);
      else if (s.cursor().stage() == LibraryProtocol.Stage.DONE) db.beginDelta(account);
      long t = System.currentTimeMillis();
      s =
          LibrarySync.run(
              context,
              db,
              api,
              store,
              account,
              stop,
              500,
              System.currentTimeMillis() + 8 * 60 * 1000L,
              null);
      log(
          "scan stage="
              + s.cursor().stage()
              + " pages="
              + s.pages()
              + " ms="
              + (System.currentTimeMillis() - t));
      return s;
    } finally {
      LibrarySync.unlock();
    }
  }

  private List<LibraryProtocol.Item> matching(InventoryDb db, String account, String prefix) {
    List<LibraryProtocol.Item> result = new ArrayList<>();
    for (int offset = 0; ; offset += 50) {
      List<LibraryProtocol.Item> page = db.list(account, 0, 0, offset);
      for (int i = 0; i < Math.min(50, page.size()); i++)
        if (page.get(i).name().startsWith(prefix)) result.add(page.get(i));
      if (page.size() <= 50) return result;
    }
  }

  private static String describe(LibraryProtocol.Item item) {
    LibraryProtocol.Extra x = item.extra();
    String why = ReclaimEngine.ineligible(item);
    return item.name()
        + " type="
        + item.type()
        + " size="
        + item.size()
        + " charged="
        + item.charged()
        + " trash="
        + item.trash()
        + " archived="
        + item.archived()
        + " origin="
        + item.origin()
        + " sha1="
        + hex8(x.sha1())
        + " dedup="
        + (x.dedup().isEmpty() ? "missing" : "present(" + x.dedup().length() + ")")
        + " dedupIsHash="
        + (x.hashKnown()
            && x.dedup()
                .equals(Base64.getUrlEncoder().withoutPadding().encodeToString(x.sha1())))
        + " caption="
        + (x.caption().isEmpty() ? "none" : "\"" + x.caption() + "\"")
        + " favorite="
        + x.favorite()
        + " edited="
        + x.edited()
        + " canonical="
        + x.canonical()
        + " locked="
        + x.locked()
        + " motion="
        + x.motion()
        + " quality="
        + x.quality()
        + " eligible="
        + (why == null ? "yes" : "no: " + why);
  }

  @Test
  public void probe() throws Exception {
    Bundle args = InstrumentationRegistry.getArguments();
    String mode = args.getString("live", "");
    Assume.assumeTrue("Live probe not requested", !mode.isEmpty());
    if (mode.equals("tag")) {
      // For building foreign-owned staging fixtures on the device; not an account identifier.
      Bundle b = new Bundle();
      b.putString("tag", ReclaimEngine.accountTag(new AccountStore(context).email()));
      InstrumentationRegistry.getInstrumentation().sendStatus(1, b);
      return;
    }
    String name = args.getString("name", "");
    assertFalse("Pass -e name FILE_NAME_PREFIX", name.isEmpty());
    AccountStore store = new AccountStore(context);
    String account = store.email();
    PhotosApi api = api(store);
    log("mode=" + mode + " account-connected=" + !account.isEmpty());
    try (InventoryDb db = new InventoryDb(context)) {
      scan(db, api, store, account);
      InventoryDb.Summary sum = db.summary(account);
      log(
          "library items="
              + sum.items()
              + " charged="
              + sum.chargedItems()
              + " zero="
              + sum.zero()
              + " unknown="
              + sum.unknown());
      List<LibraryProtocol.Item> items = matching(db, account, name);
      log("matching \"" + name + "\": " + items.size());
      for (LibraryProtocol.Item item : items) log("item " + describe(item));
      if (mode.equals("shape")) {
        byte[] base;
        try (var input = context.getAssets().open("library-state.bin")) {
          base = input.readAllBytes();
        }
        byte[] response =
            api.libraryPageRaw(LibraryProtocol.request(base, LibraryProtocol.Cursor.fresh()));
        byte[] root = Proto.message(response, 1);
        for (byte[] media : Proto.fields(root, 2)) {
          String n = LibraryProtocol.text(Proto.message(media, 2), 4);
          if (!n.startsWith(name)) continue;
          log("shape of " + n + " (field paths, sizes; no values):");
          shape(media, "", 0);
        }
        return;
      }
      if (mode.equals("observe")) observe(api, items);
      else if (mode.equals("reclaim")) reclaim(api, db, account, items, name);
      else if (mode.equals("decorate")) {
        for (LibraryProtocol.Item item : items)
          if (item.name().equals(name) && !item.trash()) {
            api.setCaption(item.extra().dedup(), "Probe caption · ç ã 🌅");
            api.setFavorite(item.extra().dedup(), true);
            api.setArchived(List.of(item.extra().dedup()), true);
            log("decorated " + item.name());
          }
        scan(db, api, store, account);
        for (LibraryProtocol.Item item : matching(db, account, name))
          log("after " + describe(item));
      }
      else fail("Unknown mode " + mode);
    }
  }

  private static void shape(byte[] data, String path, int depth) throws Exception {
    if (depth > 6) return;
    for (Proto.Value v : Proto.values(data)) {
      String here = path.isEmpty() ? "" + v.field() : path + "." + v.field();
      if (v.wire() == 0) log("  " + here + " = int");
      else if (v.wire() != 2) log("  " + here + " fixed");
      else {
        boolean nested;
        try {
          nested = !Proto.values(v.bytes()).isEmpty();
        } catch (Exception e) {
          nested = false;
        }
        if (nested && (here.startsWith("5") || here.startsWith("2.35")))
          shape(v.bytes(), here, depth + 1);
        else log("  " + here + " bytes[" + v.bytes().length + "]");
      }
    }
  }

  private void observe(PhotosApi api, List<LibraryProtocol.Item> items) throws Exception {
    File dir = new File(context.getCacheDir(), "probe");
    dir.mkdirs();
    for (LibraryProtocol.Item item : items) {
      if (item.trash()) continue;
      String owner = item.extra().hashKnown() ? api.find(item.extra().sha1()) : "";
      log(
          item.name()
              + " find(sha1)="
              + (owner.isEmpty() ? "empty" : owner.equals(item.key()) ? "this item" : "OTHER"));
      byte[] response = api.prepareDownload(item.key());
      List<PhotosApi.Link> links = PhotosApi.links(response);
      log(item.name() + " download response bytes=" + response.length + " links=" + links.size());
      for (PhotosApi.Link link : links) {
        File target = new File(dir, "candidate");
        String result;
        try {
          long limit = item.size() == null ? 4L << 30 : item.size() * 4 + (1 << 20);
          byte[] digest = api.download(link.url(), target, limit, (a, b) -> {});
          result =
              "bytes="
                  + target.length()
                  + " sha1="
                  + hex8(digest)
                  + (MessageDigest.isEqual(digest, item.extra().sha1())
                      ? " MATCHES-ORIGINAL"
                      : " differs");
        } catch (Exception e) {
          result = "error " + e.getClass().getSimpleName() + ": " + e.getMessage();
        }
        target.delete();
        log("  " + link.path() + " host=" + host(link.url()) + " " + result);
      }
    }
  }

  private void reclaim(
      PhotosApi api,
      InventoryDb db,
      String account,
      List<LibraryProtocol.Item> items,
      String name)
      throws Exception {
    LibraryProtocol.Item target = null;
    for (LibraryProtocol.Item item : items)
      if (item.name().equals(name) && !item.trash()) {
        assertNull("More than one item is named " + name, target);
        target = item;
      }
    assertNotNull("No library item named exactly " + name, target);
    String why = ReclaimEngine.ineligible(target);
    assertNull("Not eligible: " + why, why);
    final String originalKey = target.key();
    final LibraryProtocol.Item chosen = target;
    long started = System.currentTimeMillis();
    try (ReclaimDb journal = new ReclaimDb(context)) {
      ReclaimEngine.Job job = journal.job(account, originalKey);
      if (job == null || job.state == ReclaimEngine.State.FAILED) {
        journal.enqueue(ReclaimEngine.Job.of(account, chosen));
        job = journal.job(account, originalKey);
      }
      ReclaimEngine.Remote real = new ReclaimRemote(context, api, account, stop);
      ReclaimEngine.Remote observed =
          new ReclaimEngine.Remote() {
            String t() {
              return "+" + (System.currentTimeMillis() - started) + "ms ";
            }

            public String find(byte[] sha1) throws java.io.IOException {
              String k = real.find(sha1);
              log(
                  t()
                      + "find -> "
                      + (k.isEmpty() ? "empty" : k.equals(originalKey) ? "ORIGINAL" : "other key"));
              return k;
            }

            public List<ReclaimEngine.Local> localOriginals(ReclaimEngine.Job j)
                throws java.io.IOException {
              List<ReclaimEngine.Local> l = real.localOriginals(j);
              log(t() + "phone candidates " + l.size());
              return l;
            }

            public boolean phoneCopyPresent(String ref) throws java.io.IOException {
              boolean present = real.phoneCopyPresent(ref);
              log(t() + "phone copy present=" + present);
              return present;
            }

            public void restorePhoneCopy(String ref, File copy) throws java.io.IOException {
              real.restorePhoneCopy(ref, copy);
              log(t() + "phone copy restored");
            }

            public List<PhotosApi.Link> downloadLinks(String key) throws java.io.IOException {
              List<PhotosApi.Link> l = real.downloadLinks(key);
              StringBuilder paths = new StringBuilder();
              for (PhotosApi.Link link : l) paths.append(link.path()).append('@').append(host(link.url())).append(' ');
              log(t() + "links " + paths);
              return l;
            }

            public byte[] download(String url, File f, long limit, PhotosApi.Progress p)
                throws java.io.IOException {
              byte[] d = real.download(url, f, limit, p);
              log(t() + "downloaded " + f.length() + " bytes sha1=" + hex8(d));
              return d;
            }

            public void trash(String dedup) throws java.io.IOException {
              real.trash(dedup);
              log(t() + "trash ok");
            }

            public void delete(String dedup) throws java.io.IOException {
              real.delete(dedup);
              log(t() + "delete ok");
            }

            public String upload(ReclaimEngine.Job j, File f, PhotosApi.Progress p)
                throws java.io.IOException {
              String k = real.upload(j, f, p);
              log(t() + "upload -> " + (k.equals(originalKey) ? "ORIGINAL KEY" : k.isEmpty() ? "empty" : "new key"));
              return k;
            }

            public LibraryProtocol.Item lookup(String key) throws java.io.IOException {
              LibraryProtocol.Item i = real.lookup(key);
              log(t() + "lookup -> " + (i == null ? "not yet visible" : describe(i)));
              LibraryProtocol.Item old = db.item(account, originalKey);
              log(t() + "original in cache -> " + (old == null ? "gone" : describe(old)));
              return i;
            }

            public void setCaption(String d, String c) throws java.io.IOException {
              real.setCaption(d, c);
              log(t() + "caption restored");
            }

            public void setFavorite(String d) throws java.io.IOException {
              real.setFavorite(d);
              log(t() + "favorite restored");
            }

            public void setArchived(String d) throws java.io.IOException {
              real.setArchived(d);
              log(t() + "archive restored");
            }

            public void restore(String d) throws java.io.IOException {
              real.restore(d);
              log(t() + "restored from trash");
            }
          };
      ReclaimEngine engine =
          new ReclaimEngine(
              observed,
              journal,
              (j, detail) -> log("step " + j.state + ": " + detail),
              ReclaimService.dir(context),
              ReclaimEngine.SYSTEM);
      long deadline = System.currentTimeMillis() + 15 * 60 * 1000L;
      String stopAfter = InstrumentationRegistry.getArguments().getString("stopAfter", "");
      if (!stopAfter.isEmpty()) {
        // Simulates the app dying at a chosen point: step by step, then leave the job as it is.
        while (!job.state.terminal() && !job.state.name().equals(stopAfter)) engine.advance(job);
        ReclaimService.warnIfOnlyOnPhone(context, account);
        log(
            "STOPPED state="
                + job.state
                + " staged="
                + new File(job.file).getName().replaceAll("^[0-9a-f]{40}_[0-9a-f]{8}_", "<sha1>_<tag>_")
                + " stagedExists="
                + new File(job.file).exists()
                + " findAfter="
                + (api.find(job.sha1).isEmpty() ? "empty" : "present"));
        return;
      }
      while (!job.state.terminal() && System.currentTimeMillis() < deadline) {
        try {
          engine.run(job, deadline);
        } catch (ReclaimEngine.Later e) {
          log("later: " + e.getMessage());
          Thread.sleep(10000);
        }
      }
      if (ReclaimEngine.needsGuard(job)) {
        log("waiting " + ReclaimService.GUARD_DELAY / 1000 + " s before checking the phone copy");
        Thread.sleep(ReclaimService.GUARD_DELAY);
        engine.guard(job);
      }
      log(
          "RESULT state="
              + job.state
              + " newCharged="
              + job.newCharged
              + " localCopyKept="
              + new File(job.file).exists()
              + " totalMs="
              + (System.currentTimeMillis() - started)
              + " phoneCheckPending="
              + ReclaimEngine.needsGuard(job)
              + " message="
              + job.message);
    }
  }
}
