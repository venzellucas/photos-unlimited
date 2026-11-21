package app.photos.unlimited;

import static org.junit.Assert.*;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.SocketTimeoutException;
import org.junit.Test;

public class ScanLabelTest {
  @Test
  public void labelsFollowTheDocumentedOrder() {
    assertEquals("SCAN_TIMEOUT", InventoryService.label(new SocketTimeoutException()));
    assertNull(InventoryService.label(new InterruptedIOException()));
    assertEquals(
        "SCAN_SIGN_IN",
        InventoryService.label(new PhotosApi.ApiException("x", true, false, 401)));
    assertEquals(
        "SCAN_HTTP_500",
        InventoryService.label(new PhotosApi.ApiException("x", false, false, 500)));
    assertEquals(
        "SCAN_FORMAT_PAGE",
        InventoryService.label(new ScanFailure("SCAN_FORMAT_PAGE", "Unrecognized")));
    assertEquals(
        "SCAN_RESPONSE_TOO_LARGE",
        InventoryService.label(new IOException("Service response is too large")));
    assertEquals("SCAN_NETWORK", InventoryService.label(new IOException("x")));
    assertEquals(
        "SCAN_CRASH_ILLEGALSTATEEXCEPTION",
        InventoryService.label(new IllegalStateException("x")));
  }

  @Test
  public void labelsAreFixedAndBounded() {
    assertThrows(IllegalArgumentException.class, () -> new ScanFailure("bad label", "x"));
    class AVeryLongExceptionClassNameThatKeepsGoingAndGoing extends RuntimeException {}
    String label = InventoryService.label(new AVeryLongExceptionClassNameThatKeepsGoingAndGoing());
    assertTrue(label.matches("SCAN_CRASH_[A-Z0-9]{1,32}"));
  }
}
