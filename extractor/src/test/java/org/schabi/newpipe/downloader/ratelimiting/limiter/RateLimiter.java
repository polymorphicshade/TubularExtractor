/*
 * Copyright (C) 2012 The Guava Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package org.schabi.newpipe.downloader.ratelimiting.limiter;

import static java.lang.Math.max;
import static java.util.concurrent.TimeUnit.MICROSECONDS;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

import com.google.errorprone.annotations.CanIgnoreReturnValue;

import org.schabi.newpipe.downloader.ratelimiting.limiter.SmoothRateLimiter.SmoothWarmingUp;

import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * A rate limiter. Conceptually, a rate limiter distributes permits at a configurable rate. Each
 * {@link #acquire()} blocks if necessary until a permit is available, and then takes it. Once
 * acquired, permits need not be released.
 *
 * <p>{@code RateLimiter} is safe for concurrent use: It will restrict the total rate of calls from
 * all threads. Note, however, that it does not guarantee fairness.
 *
 * <p>Rate limiters are often used to restrict the rate at which some physical or logical resource
 * is accessed. This is in contrast to {@link java.util.concurrent.Semaphore} which restricts the
 * number of concurrent accesses instead of the rate (note though that concurrency and rate are
 * closely related, e.g. see <a href="http://en.wikipedia.org/wiki/Little%27s_law">Little's
 * Law</a>).
 *
 * <p>A {@code RateLimiter} is defined primarily by the rate at which permits are issued. Absent
 * additional configuration, permits will be distributed at a fixed rate, defined in terms of
 * permits per second. Permits will be distributed smoothly, with the delay between individual
 * permits being adjusted to ensure that the configured rate is maintained.
 *
 * <p>It is possible to configure a {@code RateLimiter} to have a warmup period during which time
 * the permits issued each second steadily increases until it hits the stable rate.
 *
 * <p>As an example, imagine that we have a list of tasks to execute, but we don't want to submit
 * more than 2 per second:
 *
 * <pre>{@code
 * final RateLimiter rateLimiter = RateLimiter.create(2.0); // rate is "2 permits per second"
 * void submitTasks(List<Runnable> tasks, Executor executor) {
 *   for (Runnable task : tasks) {
 *     rateLimiter.acquire(); // may wait
 *     executor.execute(task);
 *   }
 * }
 * }</pre>
 *
 * <p>As another example, imagine that we produce a stream of data, and we want to cap it at 5kb per
 * second. This could be accomplished by requiring a permit per byte, and specifying a rate of 5000
 * permits per second:
 *
 * <pre>{@code
 * final RateLimiter rateLimiter = RateLimiter.create(5000.0); // rate = 5000 permits per second
 * void submitPacket(byte[] packet) {
 *   rateLimiter.acquire(packet.length);
 *   networkService.send(packet);
 * }
 * }</pre>
 *
 * <p>It is important to note that the number of permits requested <i>never</i> affects the
 * throttling of the request itself (an invocation to {@code acquire(1)} and an invocation to {@code
 * acquire(1000)} will result in exactly the same throttling, if any), but it affects the throttling
 * of the <i>next</i> request. I.e., if an expensive task arrives at an idle RateLimiter, it will be
 * granted immediately, but it is the <i>next</i> request that will experience extra throttling,
 * thus paying for the cost of the expensive task.
 *
 * @author Dimitris Andreou
 * @since 13.0
 */
public abstract class RateLimiter {
    public static final double DEFAULT_COLD_FACTOR = 3.0;

    /**
     * Creates a {@code RateLimiter} with the specified stable throughput, given as "permits per
     * second" (commonly referred to as <i>QPS</i>, queries per second), and a <i>warmup period</i>,
     * during which the {@code RateLimiter} smoothly ramps up its rate, until it reaches its maximum
     * rate at the end of the period (as long as there are enough requests to saturate it). Similarly,
     * if the {@code RateLimiter} is left <i>unused</i> for a duration of {@code warmupPeriod}, it
     * will gradually return to its "cold" state, i.e. it will go through the same warming up process
     * as when it was first created.
     *
     * <p>The returned {@code RateLimiter} is intended for cases where the resource that actually
     * fulfills the requests (e.g., a remote server) needs "warmup" time, rather than being
     * immediately accessed at the stable (maximum) rate.
     *
     * <p>The returned {@code RateLimiter} starts in a "cold" state (i.e. the warmup period will
     * follow), and if it is left unused for long enough, it will return to that state.
     *
     * @param permitsPerSecond the rate of the returned {@code RateLimiter}, measured in how many
     *     permits become available per second
     * @param warmupPeriod the duration of the period where the {@code RateLimiter} ramps up its rate,
     *     before reaching its stable (maximum) rate
     * @throws IllegalArgumentException if {@code permitsPerSecond} is negative or zero or {@code
     *     warmupPeriod} is negative
     * @since 28.0 (but only since 33.4.0 in the Android flavor)
     */
    public static RateLimiter create(
            final double permitsPerSecond,
            final Duration warmupPeriod
    ) {
        return create(permitsPerSecond, warmupPeriod, DEFAULT_COLD_FACTOR);
    }

    /**
     * Creates a {@code RateLimiter} with the specified stable throughput, given as "permits per
     * second" (commonly referred to as <i>QPS</i>, queries per second), and a <i>warmup period</i>,
     * during which the {@code RateLimiter} smoothly ramps up its rate, until it reaches its maximum
     * rate at the end of the period (as long as there are enough requests to saturate it). Similarly,
     * if the {@code RateLimiter} is left <i>unused</i> for a duration of {@code warmupPeriod}, it
     * will gradually return to its "cold" state, i.e. it will go through the same warming up process
     * as when it was first created.
     *
     * <p>The returned {@code RateLimiter} is intended for cases where the resource that actually
     * fulfills the requests (e.g., a remote server) needs "warmup" time, rather than being
     * immediately accessed at the stable (maximum) rate.
     *
     * <p>The returned {@code RateLimiter} starts in a "cold" state (i.e. the warmup period will
     * follow), and if it is left unused for long enough, it will return to that state.
     *
     * @param permitsPerSecond the rate of the returned {@code RateLimiter}, measured in how many
     *     permits become available per second
     * @param warmupPeriod the duration of the period where the {@code RateLimiter} ramps up its rate,
     *     before reaching its stable (maximum) rate
     * @param coldFactor see {@link SmoothWarmingUp}
     * @throws IllegalArgumentException if {@code permitsPerSecond} is negative or zero or {@code
     *     warmupPeriod} is negative
     * @since 28.0 (but only since 33.4.0 in the Android flavor)
     */
    public static RateLimiter create(
        final double permitsPerSecond,
        final Duration warmupPeriod,
        final double coldFactor
    ) {
        return create(permitsPerSecond, warmupPeriod.toNanos(), TimeUnit.NANOSECONDS, coldFactor);
    }

    /**
     * Creates a {@code RateLimiter} with the specified stable throughput, given as "permits per
     * second" (commonly referred to as <i>QPS</i>, queries per second), and a <i>warmup period</i>,
     * during which the {@code RateLimiter} smoothly ramps up its rate, until it reaches its maximum
     * rate at the end of the period (as long as there are enough requests to saturate it). Similarly,
     * if the {@code RateLimiter} is left <i>unused</i> for a duration of {@code warmupPeriod}, it
     * will gradually return to its "cold" state, i.e. it will go through the same warming up process
     * as when it was first created.
     *
     * <p>The returned {@code RateLimiter} is intended for cases where the resource that actually
     * fulfills the requests (e.g., a remote server) needs "warmup" time, rather than being
     * immediately accessed at the stable (maximum) rate.
     *
     * <p>The returned {@code RateLimiter} starts in a "cold" state (i.e. the warmup period will
     * follow), and if it is left unused for long enough, it will return to that state.
     *
     * @param permitsPerSecond the rate of the returned {@code RateLimiter}, measured in how many
     *     permits become available per second
     * @param warmupPeriod the duration of the period where the {@code RateLimiter} ramps up its rate,
     *     before reaching its stable (maximum) rate
     * @param unit the time unit of the warmupPeriod argument
     * @param coldFactor see {@link SmoothWarmingUp}
     * @throws IllegalArgumentException if {@code permitsPerSecond} is negative or zero or {@code
     *     warmupPeriod} is negative
     */
    @SuppressWarnings("GoodTime") // should accept a java.time.Duration
    public static RateLimiter create(
        final double permitsPerSecond,
        final long warmupPeriod,
        final TimeUnit unit,
        final double coldFactor
    ) {
        if(warmupPeriod < 0) {
            throw new IllegalArgumentException(
                "warmupPeriod must not be negative: " + warmupPeriod);
        }
        return create(
            permitsPerSecond, warmupPeriod, unit, coldFactor, SleepingStopwatch.createFromSystemTimer());
    }

    static RateLimiter create(
        final double permitsPerSecond,
        final long warmupPeriod,
        final TimeUnit unit,
        final double coldFactor,
        final SleepingStopwatch stopwatch) {
        final RateLimiter rateLimiter = new SmoothWarmingUp(stopwatch, warmupPeriod, unit, coldFactor);
        rateLimiter.setRate(permitsPerSecond);
        return rateLimiter;
    }

    /**
     * The underlying timer; used both to measure elapsed time and sleep as necessary. A separate
     * object to facilitate testing.
     */
    private final SleepingStopwatch stopwatch;

    // Can't be initialized in the constructor because mocks don't call the constructor.
    private volatile Object mutexDoNotUseDirectly;

    private Object mutex() {
        Object mutex = mutexDoNotUseDirectly;
        if (mutex == null) {
            synchronized (this) {
                mutex = mutexDoNotUseDirectly;
                if (mutex == null) {
                    mutexDoNotUseDirectly = mutex = new Object();
                }
            }
        }
        return mutex;
    }

    RateLimiter(final SleepingStopwatch stopwatch) {
        this.stopwatch = Objects.requireNonNull(stopwatch);
    }

    /**
     * Updates the stable rate of this {@code RateLimiter}, that is, the {@code permitsPerSecond}
     * argument provided in the factory method that constructed the {@code RateLimiter}. Currently
     * throttled threads will <b>not</b> be awakened as a result of this invocation, thus they do not
     * observe the new rate; only subsequent requests will.
     *
     * <p>Note though that, since each request repays (by waiting, if necessary) the cost of the
     * <i>previous</i> request, this means that the very next request after an invocation to {@code
     * setRate} will not be affected by the new rate; it will pay the cost of the previous request,
     * which is in terms of the previous rate.
     *
     * <p>The behavior of the {@code RateLimiter} is not modified in any other way, e.g. if the {@code
     * RateLimiter} was configured with a warmup period of 20 seconds, it still has a warmup period of
     * 20 seconds after this method invocation.
     *
     * @param permitsPerSecond the new stable rate of this {@code RateLimiter}
     * @throws IllegalArgumentException if {@code permitsPerSecond} is negative or zero
     */
    public final void setRate(final double permitsPerSecond) {
        if(permitsPerSecond <= 0.0) {
            throw new IllegalArgumentException("rate must be positive");
        }
        synchronized (mutex()) {
            doSetRate(permitsPerSecond, stopwatch.readMicros());
        }
    }

    abstract void doSetRate(double permitsPerSecond, long nowMicros);

    /**
     * Returns the stable rate (as {@code permits per seconds}) with which this {@code RateLimiter} is
     * configured with. The initial value of this is the same as the {@code permitsPerSecond} argument
     * passed in the factory method that produced this {@code RateLimiter}, and it is only updated
     * after invocations to {@linkplain #setRate}.
     */
    public final double getRate() {
        synchronized (mutex()) {
            return doGetRate();
        }
    }

    abstract double doGetRate();

    /**
     * Acquires a single permit from this {@code RateLimiter}, blocking until the request can be
     * granted. Tells the amount of time slept, if any.
     *
     * <p>This method is equivalent to {@code acquire(1)}.
     *
     * @return time spent sleeping to enforce rate, in seconds; 0.0 if not rate-limited
     * @since 16.0 (present in 13.0 with {@code void} return type})
     */
    @CanIgnoreReturnValue
    public double acquire() {
        return acquire(1);
    }

    /**
     * Acquires the given number of permits from this {@code RateLimiter}, blocking until the request
     * can be granted. Tells the amount of time slept, if any.
     *
     * @param permits the number of permits to acquire
     * @return time spent sleeping to enforce rate, in seconds; 0.0 if not rate-limited
     * @throws IllegalArgumentException if the requested number of permits is negative or zero
     * @since 16.0 (present in 13.0 with {@code void} return type})
     */
    @CanIgnoreReturnValue
    public double acquire(final int permits) {
        final long microsToWait = reserve(permits);
        stopwatch.sleepMicrosUninterruptibly(microsToWait);
        return 1.0 * microsToWait / SECONDS.toMicros(1L);
    }

    /**
     * Reserves the given number of permits from this {@code RateLimiter} for future use, returning
     * the number of microseconds until the reservation can be consumed.
     *
     * @return time in microseconds to wait until the resource can be acquired, never negative
     */
    final long reserve(final int permits) {
        checkPermits(permits);
        synchronized (mutex()) {
            return reserveAndGetWaitLength(permits, stopwatch.readMicros());
        }
    }

    /**
     * Acquires a permit from this {@code RateLimiter} if it can be obtained without exceeding the
     * specified {@code timeout}, or returns {@code false} immediately (without waiting) if the permit
     * would not have been granted before the timeout expired.
     *
     * <p>This method is equivalent to {@code tryAcquire(1, timeout)}.
     *
     * @param timeout the maximum time to wait for the permit. Negative values are treated as zero.
     * @return {@code true} if the permit was acquired, {@code false} otherwise
     * @throws IllegalArgumentException if the requested number of permits is negative or zero
     * @since 28.0 (but only since 33.4.0 in the Android flavor)
     */
    public boolean tryAcquire(final Duration timeout) {
        return tryAcquire(1, timeout.toNanos(), TimeUnit.NANOSECONDS);
    }

    /**
     * Acquires a permit from this {@code RateLimiter} if it can be obtained without exceeding the
     * specified {@code timeout}, or returns {@code false} immediately (without waiting) if the permit
     * would not have been granted before the timeout expired.
     *
     * <p>This method is equivalent to {@code tryAcquire(1, timeout, unit)}.
     *
     * @param timeout the maximum time to wait for the permit. Negative values are treated as zero.
     * @param unit the time unit of the timeout argument
     * @return {@code true} if the permit was acquired, {@code false} otherwise
     * @throws IllegalArgumentException if the requested number of permits is negative or zero
     */
    @SuppressWarnings("GoodTime") // should accept a java.time.Duration
    public boolean tryAcquire(final long timeout, final TimeUnit unit) {
        return tryAcquire(1, timeout, unit);
    }

    /**
     * Acquires permits from this {@link RateLimiter} if it can be acquired immediately without delay.
     *
     * <p>This method is equivalent to {@code tryAcquire(permits, 0, anyUnit)}.
     *
     * @param permits the number of permits to acquire
     * @return {@code true} if the permits were acquired, {@code false} otherwise
     * @throws IllegalArgumentException if the requested number of permits is negative or zero
     * @since 14.0
     */
    public boolean tryAcquire(final int permits) {
        return tryAcquire(permits, 0, MICROSECONDS);
    }

    /**
     * Acquires a permit from this {@link RateLimiter} if it can be acquired immediately without
     * delay.
     *
     * <p>This method is equivalent to {@code tryAcquire(1)}.
     *
     * @return {@code true} if the permit was acquired, {@code false} otherwise
     * @since 14.0
     */
    public boolean tryAcquire() {
        return tryAcquire(1, 0, MICROSECONDS);
    }

    /**
     * Acquires the given number of permits from this {@code RateLimiter} if it can be obtained
     * without exceeding the specified {@code timeout}, or returns {@code false} immediately (without
     * waiting) if the permits would not have been granted before the timeout expired.
     *
     * @param permits the number of permits to acquire
     * @param timeout the maximum time to wait for the permits. Negative values are treated as zero.
     * @return {@code true} if the permits were acquired, {@code false} otherwise
     * @throws IllegalArgumentException if the requested number of permits is negative or zero
     * @since 28.0 (but only since 33.4.0 in the Android flavor)
     */
    public boolean tryAcquire(final int permits, final Duration timeout) {
        return tryAcquire(permits, timeout.toNanos(), TimeUnit.NANOSECONDS);
    }

    /**
     * Acquires the given number of permits from this {@code RateLimiter} if it can be obtained
     * without exceeding the specified {@code timeout}, or returns {@code false} immediately (without
     * waiting) if the permits would not have been granted before the timeout expired.
     *
     * @param permits the number of permits to acquire
     * @param timeout the maximum time to wait for the permits. Negative values are treated as zero.
     * @param unit the time unit of the timeout argument
     * @return {@code true} if the permits were acquired, {@code false} otherwise
     * @throws IllegalArgumentException if the requested number of permits is negative or zero
     */
    @SuppressWarnings("GoodTime") // should accept a java.time.Duration
    public boolean tryAcquire(final int permits, final long timeout, final TimeUnit unit) {
        final long timeoutMicros = max(unit.toMicros(timeout), 0);
        checkPermits(permits);
        final long microsToWait;
        synchronized (mutex()) {
            final long nowMicros = stopwatch.readMicros();
            if (!canAcquire(nowMicros, timeoutMicros)) {
                return false;
            } else {
                microsToWait = reserveAndGetWaitLength(permits, nowMicros);
            }
        }
        stopwatch.sleepMicrosUninterruptibly(microsToWait);
        return true;
    }

    private boolean canAcquire(final long nowMicros, final long timeoutMicros) {
        return queryEarliestAvailable(nowMicros) - timeoutMicros <= nowMicros;
    }

    /**
     * Reserves next ticket and returns the wait time that the caller must wait for.
     *
     * @return the required wait time, never negative
     */
    final long reserveAndGetWaitLength(final int permits, final long nowMicros) {
        final long momentAvailable = reserveEarliestAvailable(permits, nowMicros);
        return max(momentAvailable - nowMicros, 0);
    }

    /**
     * Returns the earliest time that permits are available (with one caveat).
     *
     * @return the time that permits are available, or, if permits are available immediately, an
     *     arbitrary past or present time
     */
    abstract long queryEarliestAvailable(long nowMicros);

    /**
     * Reserves the requested number of permits and returns the time that those permits can be used
     * (with one caveat).
     *
     * @return the time that the permits may be used, or, if the permits may be used immediately, an
     *     arbitrary past or present time
     */
    abstract long reserveEarliestAvailable(int permits, long nowMicros);

    @Override
    public String toString() {
        return String.format(Locale.ROOT, "RateLimiter[stableRate=%3.1fqps]", getRate());
    }

    abstract static class SleepingStopwatch {
        /** Constructor for use by subclasses. */
        protected SleepingStopwatch() {}

        protected abstract long readMicros();

        protected abstract void sleepMicrosUninterruptibly(long micros);

        public static SleepingStopwatch createFromSystemTimer() {
            return new SleepingStopwatch() {
                final long startTimeNano = System.nanoTime();

                @Override
                protected long readMicros() {
                    return MICROSECONDS.convert(System.nanoTime() - startTimeNano, NANOSECONDS);
                }

                @Override
                protected void sleepMicrosUninterruptibly(final long micros) {
                    if (micros > 0) {
                        final long sleepMs = MICROSECONDS.toMillis(micros);
                        if (sleepMs > 0) {
                            try {
                                Thread.sleep(sleepMs);
                            } catch (final InterruptedException iex) {
                                Thread.currentThread().interrupt();
                            }
                        }
                    }
                }
            };
        }
    }

    private static void checkPermits(final int permits) {
        if(permits <= 0.0) {
            throw new IllegalArgumentException(
                "Requested permits (" + permits + ") must be positive");
        }
    }
}