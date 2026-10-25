package com.leaf.hyperdragshare.codex;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class AicrCoreAccessHooksTest {
    @Test public void allowsMmggCallsFromCoreBinder() {
        assertTrue(AicrCoreAccessHooks.shouldAllowCoreCall("com.xiaomi.aicr",
                new String[] {"com.leaf.hyperdragshare.codex"}, coreStack()));
    }

    @Test public void rejectsOtherCallerPackages() {
        assertFalse(AicrCoreAccessHooks.shouldAllowCoreCall("com.xiaomi.aicr",
                new String[] {"com.example.other"}, coreStack()));
    }

    @Test public void acceptsCoreBinderInnerClassWithoutVersionSpecificNumber() {
        // AICR's anonymous Binder class number can differ between builds.
        assertTrue(AicrCoreAccessHooks.shouldAllowCoreCall("com.xiaomi.aicr",
                new String[] {"com.leaf.hyperdragshare.codex"}, new StackTraceElement[] {
                        new StackTraceElement("com.xiaomi.aicr.access.AiCrCoreService$17",
                                "onTransact", "AiCrCoreService.java", 1)
                }));
    }

    @Test public void rejectsCognitionServiceFramesAndNonAicrPackage() {
        assertFalse(AicrCoreAccessHooks.shouldAllowCoreCall("com.xiaomi.aicr:cognitionService",
                new String[] {"com.leaf.hyperdragshare.codex"}, coreStack()));
        assertFalse(AicrCoreAccessHooks.shouldAllowCoreCall("com.xiaomi.aicr",
                new String[] {"com.leaf.hyperdragshare.codex"}, new StackTraceElement[] {
                        new StackTraceElement("com.xiaomi.aicr.access.AiCrCognitionService$3",
                                "onTransact", "AiCrCognitionService.java", 1)
                }));
    }

    @Test public void rejectsMissingEvidence() {
        assertFalse(AicrCoreAccessHooks.shouldAllowCoreCall("com.xiaomi.aicr",
                null, coreStack()));
        assertFalse(AicrCoreAccessHooks.shouldAllowCoreCall("com.xiaomi.aicr",
                new String[] {"com.leaf.hyperdragshare.codex"}, null));
    }

    private static StackTraceElement[] coreStack() {
        return new StackTraceElement[] {
                new StackTraceElement("com.xiaomi.aicr.access.AiCrCoreService$2",
                        "onTransact", "AiCrCoreService.java", 1)
        };
    }
}
