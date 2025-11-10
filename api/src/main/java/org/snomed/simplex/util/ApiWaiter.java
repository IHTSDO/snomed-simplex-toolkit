package org.snomed.simplex.util;
import java.util.concurrent.*;
import java.util.function.Predicate;

public class ApiWaiter {

	private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

	public void waitForStatus(Callable<String> statusSupplier,
		Predicate<String> statusPredicate,
		long timeout,
		TimeUnit timeoutUnit, int pollInterval, TimeUnit pollIntervalUnit) throws ExecutionException, InterruptedException {

		CompletableFuture<Void> future = new CompletableFuture<>();

		ScheduledFuture<?> pollingTask = scheduler.scheduleAtFixedRate(() -> {
			try {
				String status = statusSupplier.call();
				if (statusPredicate.test(status)) {
					future.complete(null); // done!
				}
			} catch (Exception e) {
				future.completeExceptionally(e);
			}
		}, 0, pollInterval, pollIntervalUnit);

		// Handle timeout
		scheduler.schedule(() -> future.completeExceptionally(new TimeoutException("Timed out waiting for status")), timeout, timeoutUnit);

		try {
			future.get(); // blocks until done or timeout
		} finally {
			pollingTask.cancel(true);
		}
	}
}
