package com.vikrant.careSync.concurrency;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConcurrencyLearningTest {

    @Test
    void threadLifecycleJoinMakesWorkDeterministic() throws Exception {
        List<String> events = new CopyOnWriteArrayList<>();

        Thread worker = new Thread(() -> {
            events.add("worker: running");
            sleep(25);
            events.add("worker: done");
        }, "medical-summary-worker");

        assertEquals(Thread.State.NEW, worker.getState());

        events.add("main: before start");
        worker.start();
        worker.join(1_000);
        events.add("main: after join");

        assertEquals(Thread.State.TERMINATED, worker.getState());
        assertTrue(events.indexOf("worker: done") < events.indexOf("main: after join"));
    }

    @Test
    void raceConditionDropsUpdateButAtomicIntegerDoesNot() throws Exception {
        int[] unsafeCounter = {0};
        CyclicBarrier bothThreadsStart = new CyclicBarrier(2);
        CyclicBarrier bothThreadsHaveReadTheSameValue = new CyclicBarrier(2);

        Runnable unsafeIncrement = () -> {
            await(bothThreadsStart);
            int snapshot = unsafeCounter[0];
            await(bothThreadsHaveReadTheSameValue);
            unsafeCounter[0] = snapshot + 1;
        };

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = executor.submit(unsafeIncrement);
            Future<?> second = executor.submit(unsafeIncrement);

            get(first);
            get(second);
        } finally {
            executor.shutdownNow();
        }

        assertEquals(1, unsafeCounter[0], "Two increments became one lost update.");

        AtomicInteger safeCounter = new AtomicInteger();
        runConcurrently(2, 1, safeCounter::incrementAndGet);

        assertEquals(2, safeCounter.get());
    }

    @Test
    void synchronizedProtectsSharedState() throws Exception {
        SynchronizedCounter counter = new SynchronizedCounter();

        runConcurrently(8, 5_000, counter::increment);

        assertEquals(40_000, counter.value());
    }

    @Test
    void reentrantLockCanAvoidWaitingForeverForABusySlot() throws Exception {
        ReentrantLock slotLock = new ReentrantLock();
        CountDownLatch firstBookingIsCheckingSlot = new CountDownLatch(1);

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<Boolean> firstBooking = executor.submit(() -> {
                slotLock.lock();
                try {
                    firstBookingIsCheckingSlot.countDown();
                    sleep(120);
                    return true;
                } finally {
                    slotLock.unlock();
                }
            });

            assertTrue(firstBookingIsCheckingSlot.await(1, TimeUnit.SECONDS));

            boolean secondBookingGotTheSlotLock = slotLock.tryLock(30, TimeUnit.MILLISECONDS);
            if (secondBookingGotTheSlotLock) {
                slotLock.unlock();
            }

            assertFalse(secondBookingGotTheSlotLock);
            assertTrue(get(firstBooking));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void semaphoreLimitsParallelExternalCalls() throws Exception {
        Semaphore paymentGatewayLimit = new Semaphore(2);
        AtomicInteger activeCalls = new AtomicInteger();
        AtomicInteger peakActiveCalls = new AtomicInteger();
        CountDownLatch startTogether = new CountDownLatch(1);

        ExecutorService executor = Executors.newFixedThreadPool(6);
        try {
            List<Future<Void>> futures = new ArrayList<>();
            for (int i = 0; i < 6; i++) {
                futures.add(executor.submit(() -> {
                    assertTrue(startTogether.await(1, TimeUnit.SECONDS));
                    paymentGatewayLimit.acquire();
                    try {
                        int active = activeCalls.incrementAndGet();
                        peakActiveCalls.accumulateAndGet(active, Math::max);
                        sleep(40);
                        return null;
                    } finally {
                        activeCalls.decrementAndGet();
                        paymentGatewayLimit.release();
                    }
                }));
            }

            startTogether.countDown();
            for (Future<Void> future : futures) {
                get(future);
            }
        } finally {
            executor.shutdownNow();
        }

        assertEquals(2, peakActiveCalls.get());
    }

    @Test
    void blockingQueueConnectsProducerAndConsumer() throws Exception {
        String stop = "STOP";
        BlockingQueue<String> labTestQueue = new ArrayBlockingQueue<>(2);
        List<String> processed = new CopyOnWriteArrayList<>();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Void> producer = executor.submit(() -> {
                for (String labTestId : List.of("blood-test", "xray", "ecg")) {
                    labTestQueue.put(labTestId);
                }
                labTestQueue.put(stop);
                return null;
            });

            Future<Void> consumer = executor.submit(() -> {
                while (true) {
                    String labTestId = labTestQueue.take();
                    if (stop.equals(labTestId)) {
                        return null;
                    }
                    processed.add("processed-" + labTestId);
                }
            });

            get(producer);
            get(consumer);
        } finally {
            executor.shutdownNow();
        }

        assertEquals(List.of("processed-blood-test", "processed-xray", "processed-ecg"), processed);
    }

    @Test
    void completableFutureCombinesIndependentAsyncWork() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(3);
        try {
            CompletableFuture<String> patient = CompletableFuture.supplyAsync(() -> {
                sleep(30);
                return "patient#42";
            }, executor);

            CompletableFuture<String> insurancePolicy = CompletableFuture.supplyAsync(() -> {
                sleep(30);
                return "policy#gold";
            }, executor);

            CompletableFuture<String> booking = patient
                    .thenCombine(insurancePolicy, (patientId, policyId) -> patientId + " covered by " + policyId)
                    .thenCompose(summary -> CompletableFuture.supplyAsync(
                            () -> "booking-confirmed: " + summary,
                            executor
                    ))
                    .exceptionally(error -> "booking-failed");

            assertEquals("booking-confirmed: patient#42 covered by policy#gold", booking.get(1, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void scheduledExecutorRunsReminderLater() throws Exception {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        CountDownLatch reminderSent = new CountDownLatch(1);
        AtomicBoolean sent = new AtomicBoolean();

        try {
            scheduler.schedule(() -> {
                sent.set(true);
                reminderSent.countDown();
            }, 50, TimeUnit.MILLISECONDS);

            assertFalse(sent.get());
            assertTrue(reminderSent.await(1, TimeUnit.SECONDS));
            assertTrue(sent.get());
        } finally {
            scheduler.shutdownNow();
        }
    }

    @Test
    void readWriteLockAllowsManyReadersAndBlocksWriter() throws Exception {
        ReentrantReadWriteLock cacheLock = new ReentrantReadWriteLock();
        CountDownLatch readersReady = new CountDownLatch(2);
        CountDownLatch releaseReaders = new CountDownLatch(1);

        Callable<Void> reader = () -> {
            cacheLock.readLock().lock();
            try {
                readersReady.countDown();
                assertTrue(releaseReaders.await(1, TimeUnit.SECONDS));
                return null;
            } finally {
                cacheLock.readLock().unlock();
            }
        };

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Void> firstReader = executor.submit(reader);
            Future<Void> secondReader = executor.submit(reader);

            assertTrue(readersReady.await(1, TimeUnit.SECONDS));
            assertFalse(cacheLock.writeLock().tryLock(30, TimeUnit.MILLISECONDS));

            releaseReaders.countDown();
            get(firstReader);
            get(secondReader);

            assertTrue(cacheLock.writeLock().tryLock(1, TimeUnit.SECONDS));
            cacheLock.writeLock().unlock();
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void threadLocalKeepsRequestDataSeparatePerThread() throws Exception {
        ThreadLocal<String> currentUser = new ThreadLocal<>();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<String> patientRequest = executor.submit(requestWithCurrentUser(currentUser, "patient#42"));
            Future<String> doctorRequest = executor.submit(requestWithCurrentUser(currentUser, "doctor#7"));

            assertEquals(Set.of("patient#42", "doctor#7"), Set.of(get(patientRequest), get(doctorRequest)));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void consistentLockOrderingAvoidsDeadlock() throws Exception {
        NamedLock patientWallet = new NamedLock("patient-wallet");
        NamedLock clinicWallet = new NamedLock("clinic-wallet");

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<String> patientToClinic = executor.submit(() -> transfer("patient", "clinic", patientWallet, clinicWallet));
            Future<String> clinicToPatient = executor.submit(() -> transfer("clinic", "patient", clinicWallet, patientWallet));

            assertEquals("patient->clinic", get(patientToClinic));
            assertEquals("clinic->patient", get(clinicToPatient));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void virtualThreadsHandleManyWaitingTasksInJava21() throws Exception {
        ThreadFactory threadFactory = Thread.ofVirtual().name("virtual-care-worker-", 0).factory();
        ExecutorService executor = Executors.newThreadPerTaskExecutor(threadFactory);

        try {
            List<Future<Integer>> futures = IntStream.rangeClosed(1, 100)
                    .mapToObj(number -> executor.submit(() -> {
                        sleep(10);
                        return number;
                    }))
                    .toList();

            int sum = 0;
            for (Future<Integer> future : futures) {
                sum += get(future);
            }

            assertEquals(5_050, sum);
        } finally {
            executor.shutdownNow();
        }
    }

    private static Callable<String> requestWithCurrentUser(ThreadLocal<String> currentUser, String userId) {
        return () -> {
            currentUser.set(userId);
            try {
                sleep(25);
                return currentUser.get();
            } finally {
                currentUser.remove();
            }
        };
    }

    private static String transfer(String from, String to, NamedLock fromLock, NamedLock toLock) {
        NamedLock firstLock = fromLock.id().compareTo(toLock.id()) <= 0 ? fromLock : toLock;
        NamedLock secondLock = firstLock == fromLock ? toLock : fromLock;

        synchronized (firstLock) {
            sleep(20);
            synchronized (secondLock) {
                return from + "->" + to;
            }
        }
    }

    private static void runConcurrently(int workers, int repetitionsPerWorker, Runnable action) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(workers);
        CountDownLatch startTogether = new CountDownLatch(1);

        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int worker = 0; worker < workers; worker++) {
                futures.add(executor.submit(() -> {
                    assertTrue(startTogether.await(1, TimeUnit.SECONDS));
                    for (int repeat = 0; repeat < repetitionsPerWorker; repeat++) {
                        action.run();
                    }
                    return null;
                }));
            }

            startTogether.countDown();
            for (Future<?> future : futures) {
                get(future);
            }
        } finally {
            executor.shutdownNow();
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Thread was interrupted.", exception);
        }
    }

    private static void await(CyclicBarrier barrier) {
        try {
            barrier.await(1, TimeUnit.SECONDS);
        } catch (Exception exception) {
            throw new AssertionError("Timed out waiting at the barrier.", exception);
        }
    }

    private static <T> T get(Future<T> future) throws Exception {
        return future.get(2, TimeUnit.SECONDS);
    }

    private static final class SynchronizedCounter {
        private int value;

        synchronized void increment() {
            value++;
        }

        synchronized int value() {
            return value;
        }
    }

    private record NamedLock(String id) {
    }
}
