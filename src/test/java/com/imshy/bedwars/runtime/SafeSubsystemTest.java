package com.imshy.bedwars.runtime;

import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Tests for {@link SafeSubsystem}: failure counting, quarantine at the
 * threshold, skip-while-quarantined, health report and re-arm.
 */
public class SafeSubsystemTest {

    private static final Runnable BOOM = new Runnable() {
        @Override
        public void run() {
            throw new IllegalStateException("boom");
        }
    };

    @Before
    public void setUp() {
        SafeSubsystem.resetForTests();
    }

    @Test
    public void healthyBodyRunsAndReportsOk() {
        final AtomicInteger runs = new AtomicInteger();
        SafeSubsystem.run("alpha", new Runnable() {
            @Override
            public void run() {
                runs.incrementAndGet();
            }
        });
        assertEquals(1, runs.get());
        assertFalse(SafeSubsystem.isQuarantined("alpha"));
        List<String> report = SafeSubsystem.healthReport();
        assertEquals(1, report.size());
        assertEquals("alpha: OK", report.get(0));
    }

    @Test
    public void throwingBodyIsContainedAndQuarantinedAtThreshold() {
        for (int i = 0; i < SafeSubsystem.QUARANTINE_THRESHOLD; i++) {
            assertFalse(SafeSubsystem.isQuarantined("beta"));
            SafeSubsystem.run("beta", BOOM);
        }
        assertTrue(SafeSubsystem.isQuarantined("beta"));
        assertTrue(SafeSubsystem.healthReport().get(0).contains("QUARANTINED"));
    }

    @Test
    public void quarantinedSubsystemIsSkipped() {
        for (int i = 0; i < SafeSubsystem.QUARANTINE_THRESHOLD; i++) {
            SafeSubsystem.run("gamma", BOOM);
        }
        final AtomicInteger runs = new AtomicInteger();
        SafeSubsystem.run("gamma", new Runnable() {
            @Override
            public void run() {
                runs.incrementAndGet();
            }
        });
        assertEquals(0, runs.get());
    }

    @Test
    public void rearmRestoresQuarantinedSubsystems() {
        for (int i = 0; i < SafeSubsystem.QUARANTINE_THRESHOLD; i++) {
            SafeSubsystem.run("delta", BOOM);
        }
        assertTrue(SafeSubsystem.isQuarantined("delta"));
        assertEquals(1, SafeSubsystem.rearmAll());
        assertFalse(SafeSubsystem.isQuarantined("delta"));

        final AtomicInteger runs = new AtomicInteger();
        SafeSubsystem.run("delta", new Runnable() {
            @Override
            public void run() {
                runs.incrementAndGet();
            }
        });
        assertEquals(1, runs.get());
    }

    @Test
    public void recoveredErrorsShowInReportWithoutQuarantine() {
        SafeSubsystem.run("eps", BOOM);
        assertFalse(SafeSubsystem.isQuarantined("eps"));
        assertTrue(SafeSubsystem.healthReport().get(0).contains("OK (1 recovered"));
    }
}
