package io.repaint.maven.tiles;

public class TileExecutionException extends RuntimeException {
  public TileExecutionException(String message) {
    super(message);
  }

  public TileExecutionException(String message, Throwable cause) {
    super(message, cause);
  }

  public TileExecutionException(Throwable cause) {
    super(cause);
  }
}
