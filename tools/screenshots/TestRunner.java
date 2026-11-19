package dev.exactrpn;

import org.junit.runner.Description;
import org.junit.runner.JUnitCore;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunListener;

/** Minimal JUnit4 launcher for the screenshot capture: live per-test
 * progress, summary, truncated failure traces. Lets capture-screenshots.sh
 * run ScreenshotCapture with a plain java invocation, no test runner
 * infrastructure required. */
public final class TestRunner {
    public static void main(String[] args) throws Exception {
        JUnitCore core = new JUnitCore();
        core.addListener(
                new RunListener() {
                    private long start;

                    @Override
                    public void testStarted(Description d) {
                        start = System.nanoTime();
                        System.out.println("START " + d.getMethodName());
                        System.out.flush();
                    }

                    @Override
                    public void testFinished(Description d) {
                        System.out.printf(
                                "  ok  %s (%.2fs)%n", d.getMethodName(), (System.nanoTime() - start) / 1e9);
                        System.out.flush();
                    }

                    @Override
                    public void testFailure(Failure f) {
                        System.out.println("FAIL  " + f.getDescription().getMethodName());
                        String trace = f.getTrace();
                        String[] lines = trace.split("\n");
                        for (int i = 0; i < Math.min(lines.length, 25); i++)
                            System.out.println("      " + lines[i]);
                        if (lines.length > 25) System.out.println("      ... (" + (lines.length - 25) + " more lines)");
                        System.out.flush();
                    }

                    @Override
                    public void testAssumptionFailure(Failure f) {
                        System.out.println("ASSUMPTION-FAIL " + f.getDescription().getMethodName() + ": " + f.getMessage());
                    }

                    @Override
                    public void testIgnored(Description d) {
                        System.out.println("IGNORED " + d.getMethodName());
                    }
                });
        Class<?>[] classes = new Class<?>[args.length];
        for (int i = 0; i < args.length; i++) classes[i] = Class.forName(args[i]);
        Result result = core.run(classes);
        System.out.printf(
                "%n==== SUMMARY: run=%d passed=%d failed=%d ignored=%d time=%.1fs%n",
                result.getRunCount(),
                result.getRunCount() - result.getFailureCount() - result.getIgnoreCount(),
                result.getFailureCount(),
                result.getIgnoreCount(),
                result.getRunTime() / 1000.0);
        System.exit(result.wasSuccessful() ? 0 : 1);
    }
}
