package app.photos.unlimited;

import java.io.IOException;

/** A library scan failure with a fixed, secret-free label for diagnosis. */
final class ScanFailure extends IOException {
  private final String label;
  private final String shape;

  ScanFailure(String label, String message) {
    this(label, message, null);
  }

  /** {@code shape} is a shape-only {@link LibraryShape} sketch of what failed, or null. */
  ScanFailure(String label, String message, String shape) {
    super(message);
    if (label == null || !label.matches("[A-Z0-9_]{1,48}"))
      throw new IllegalArgumentException("Invalid scan failure label");
    this.label = label;
    this.shape = shape;
  }

  String label() {
    return label;
  }

  String shape() {
    return shape;
  }
}
