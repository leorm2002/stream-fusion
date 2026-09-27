package fuse.benchmarks;

public record Transaction(long id, int userId, double amount, String category, int status) {
  public static final int STATUS_PENDING = 0;
  public static final int STATUS_COMPLETED = 1;
  public static final int STATUS_REFUNDED = 2;
  public static final int STATUS_CANCELLED = 3;
}
