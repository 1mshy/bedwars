package com.imshy.bedwars;

import org.junit.Test;

import java.util.concurrent.PriorityBlockingQueue;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * Tests for the fetch-queue ordering. HypixelAPI.PrioritizedFetchTask must sort
 * by FetchPriority declaration order (lower ordinal first) with the monotonic
 * sequence number as a FIFO tie-breaker within one level — this is what the
 * PriorityBlockingQueue inside HypixelAPI's executor relies on.
 */
public class HypixelAPIPriorityTest {

    private static final Runnable NOOP = new Runnable() {
        @Override
        public void run() {
        }
    };

    private static HypixelAPI.PrioritizedFetchTask task(HypixelAPI.FetchPriority priority, long sequence) {
        return new HypixelAPI.PrioritizedFetchTask(priority, sequence, NOOP);
    }

    // ==================== PRIORITY ORDERING ====================

    @Test
    public void explicitRunsBeforeUnknownPlayerEvenWhenSubmittedLater() {
        HypixelAPI.PrioritizedFetchTask explicit = task(HypixelAPI.FetchPriority.EXPLICIT, 99);
        HypixelAPI.PrioritizedFetchTask unknown = task(HypixelAPI.FetchPriority.UNKNOWN_PLAYER, 1);

        assertTrue(explicit.compareTo(unknown) < 0);
        assertTrue(unknown.compareTo(explicit) > 0);
    }

    @Test
    public void unknownPlayerRunsBeforeNormal() {
        HypixelAPI.PrioritizedFetchTask unknown = task(HypixelAPI.FetchPriority.UNKNOWN_PLAYER, 50);
        HypixelAPI.PrioritizedFetchTask normal = task(HypixelAPI.FetchPriority.NORMAL, 1);

        assertTrue(unknown.compareTo(normal) < 0);
    }

    @Test
    public void everyLevelOutranksAllLowerLevelsRegardlessOfSequence() {
        HypixelAPI.FetchPriority[] levels = HypixelAPI.FetchPriority.values();
        for (int high = 0; high < levels.length; high++) {
            for (int low = high + 1; low < levels.length; low++) {
                // Higher-priority task submitted much later still wins.
                HypixelAPI.PrioritizedFetchTask first = task(levels[high], 1000);
                HypixelAPI.PrioritizedFetchTask second = task(levels[low], 0);
                assertTrue(levels[high] + " should outrank " + levels[low],
                        first.compareTo(second) < 0);
            }
        }
    }

    @Test
    public void declarationOrderIsHighToLow() {
        HypixelAPI.FetchPriority[] levels = HypixelAPI.FetchPriority.values();
        assertEquals(5, levels.length);
        assertSame(HypixelAPI.FetchPriority.EXPLICIT, levels[0]);
        assertSame(HypixelAPI.FetchPriority.UNKNOWN_PLAYER, levels[1]);
        assertSame(HypixelAPI.FetchPriority.TAB_HIGH_STAR, levels[2]);
        assertSame(HypixelAPI.FetchPriority.NORMAL, levels[3]);
        assertSame(HypixelAPI.FetchPriority.BACKGROUND, levels[4]);
    }

    // ==================== FIFO TIE-BREAKING ====================

    @Test
    public void fifoWithinSameLevel() {
        HypixelAPI.PrioritizedFetchTask first = task(HypixelAPI.FetchPriority.NORMAL, 1);
        HypixelAPI.PrioritizedFetchTask second = task(HypixelAPI.FetchPriority.NORMAL, 2);

        assertTrue(first.compareTo(second) < 0);
        assertTrue(second.compareTo(first) > 0);
        assertEquals(0, first.compareTo(task(HypixelAPI.FetchPriority.NORMAL, 1)));
    }

    @Test
    public void autoAssignedSequenceIsMonotonic() {
        HypixelAPI.PrioritizedFetchTask first =
                new HypixelAPI.PrioritizedFetchTask(HypixelAPI.FetchPriority.NORMAL, NOOP);
        HypixelAPI.PrioritizedFetchTask second =
                new HypixelAPI.PrioritizedFetchTask(HypixelAPI.FetchPriority.NORMAL, NOOP);

        assertTrue(first.sequence < second.sequence);
        assertTrue(first.compareTo(second) < 0);
    }

    @Test
    public void nullPriorityFallsBackToNormal() {
        HypixelAPI.PrioritizedFetchTask nullPriority = task(null, 1);
        HypixelAPI.PrioritizedFetchTask normal = task(HypixelAPI.FetchPriority.NORMAL, 2);

        assertSame(HypixelAPI.FetchPriority.NORMAL, nullPriority.priority);
        assertTrue(nullPriority.compareTo(normal) < 0);
    }

    // ==================== QUEUE INTEGRATION ====================

    @Test
    public void priorityBlockingQueueDrainsPriorityThenFifo() {
        PriorityBlockingQueue<Runnable> queue = new PriorityBlockingQueue<Runnable>();
        queue.offer(task(HypixelAPI.FetchPriority.NORMAL, 2));
        queue.offer(task(HypixelAPI.FetchPriority.BACKGROUND, 1));
        queue.offer(task(HypixelAPI.FetchPriority.EXPLICIT, 6));
        queue.offer(task(HypixelAPI.FetchPriority.UNKNOWN_PLAYER, 3));
        queue.offer(task(HypixelAPI.FetchPriority.NORMAL, 0));
        queue.offer(task(HypixelAPI.FetchPriority.TAB_HIGH_STAR, 5));

        assertDrains(queue, HypixelAPI.FetchPriority.EXPLICIT, 6);
        assertDrains(queue, HypixelAPI.FetchPriority.UNKNOWN_PLAYER, 3);
        assertDrains(queue, HypixelAPI.FetchPriority.TAB_HIGH_STAR, 5);
        assertDrains(queue, HypixelAPI.FetchPriority.NORMAL, 0);
        assertDrains(queue, HypixelAPI.FetchPriority.NORMAL, 2);
        assertDrains(queue, HypixelAPI.FetchPriority.BACKGROUND, 1);
        assertTrue(queue.isEmpty());
    }

    private static void assertDrains(PriorityBlockingQueue<Runnable> queue,
            HypixelAPI.FetchPriority expectedPriority, long expectedSequence) {
        HypixelAPI.PrioritizedFetchTask polled = (HypixelAPI.PrioritizedFetchTask) queue.poll();
        assertSame(expectedPriority, polled.priority);
        assertEquals(expectedSequence, polled.sequence);
    }
}
