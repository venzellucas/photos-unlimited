package app.photos.unlimited;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class TextTest {
  @Test
  public void withoutResourcesTextIsAMarkerNotWording() {
    // JVM tests have no Android resources: code under test must never depend on the wording.
    assertEquals("#" + R.string.job_done, Text.get(R.string.job_done));
    assertEquals("#" + R.string.job_not_replaced, Text.get(R.string.job_not_replaced, "why"));
    assertEquals(
        "#" + R.plurals.backup_status_attention + ":2",
        Text.plural(R.plurals.backup_status_attention, 2, 2));
  }
}
