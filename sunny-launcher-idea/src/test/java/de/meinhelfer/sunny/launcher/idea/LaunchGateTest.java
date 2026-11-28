package de.meinhelfer.sunny.launcher.idea;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static de.meinhelfer.sunny.launcher.idea.LaunchGate.Result.BLOCKED;
import static de.meinhelfer.sunny.launcher.idea.LaunchGate.Result.OPENED;
import static de.meinhelfer.sunny.launcher.idea.LaunchGate.Result.UNAVAILABLE;
import static org.junit.Assert.assertEquals;

public class LaunchGateTest {
    private static final AppTarget CAMERA = new AppTarget("fixture.camera", "fixture.camera.Launch");
    private static final AppTarget PRIVATE = new AppTarget("fixture.camera", "fixture.camera.Private");

    private final FakeApps apps = new FakeApps();

    @Test
    public void visibleExplicitRequestChecksAndStartsTheExactComponentInOrder() {
        assertEquals(OPENED, LaunchGate.openFromTap(CAMERA, true, Set.of(CAMERA), apps));
        assertEquals(List.of("check " + CAMERA, "start " + CAMERA), apps.calls);
    }

    @Test
    public void backgroundRequestNeverQueriesOrStartsAnApp() {
        assertEquals(UNAVAILABLE, LaunchGate.openFromTap(CAMERA, false, Set.of(CAMERA), apps));
        assertEquals(List.of(), apps.calls);
    }

    @Test
    public void anotherComponentInTheSamePackageIsNotAuthorizedByTheVisibleEntry() {
        assertEquals(UNAVAILABLE, LaunchGate.openFromTap(PRIVATE, true, Set.of(CAMERA), apps));
        assertEquals(List.of(), apps.calls);
    }

    @Test
    public void syntheticExampleCannotOpenAnApp() {
        assertEquals(UNAVAILABLE, LaunchGate.openFromTap(null, true, Set.of(CAMERA), apps));
        assertEquals(List.of(), apps.calls);
    }

    @Test
    public void formerlyVisibleAppIsRecheckedBeforeStarting() {
        apps.available = false;
        assertEquals(UNAVAILABLE, LaunchGate.openFromTap(CAMERA, true, Set.of(CAMERA), apps));
        assertEquals(List.of("check " + CAMERA), apps.calls);
    }

    @Test
    public void appRemovingItselfBetweenCheckAndStartGetsAnUnavailableResult() {
        apps.result = UNAVAILABLE;
        assertEquals(UNAVAILABLE, LaunchGate.openFromTap(CAMERA, true, Set.of(CAMERA), apps));
        assertEquals(List.of("check " + CAMERA, "start " + CAMERA), apps.calls);
    }

    @Test
    public void deniedPlatformQueryDoesNotAttemptToStart() {
        apps.denyCheck = true;
        assertEquals(BLOCKED, LaunchGate.openFromTap(CAMERA, true, Set.of(CAMERA), apps));
        assertEquals(List.of("check " + CAMERA), apps.calls);
    }

    @Test
    public void deniedLaunchReturnsGenericBlockedResultWithoutRetry() {
        apps.denyStart = true;
        assertEquals(BLOCKED, LaunchGate.openFromTap(CAMERA, true, Set.of(CAMERA), apps));
        assertEquals(List.of("check " + CAMERA, "start " + CAMERA), apps.calls);
    }

    private static final class FakeApps implements LaunchGate.Apps {
        final List<String> calls = new ArrayList<>();
        boolean available = true;
        boolean denyCheck;
        boolean denyStart;
        LaunchGate.Result result = OPENED;

        @Override
        public boolean isLaunchable(AppTarget target) {
            calls.add("check " + target);
            if (denyCheck) throw new SecurityException("Fixture detail");
            return available;
        }

        @Override
        public LaunchGate.Result start(AppTarget target) {
            calls.add("start " + target);
            if (denyStart) throw new SecurityException("Fixture detail");
            return result;
        }
    }
}
