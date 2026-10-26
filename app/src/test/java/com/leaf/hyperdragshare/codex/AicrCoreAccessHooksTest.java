package com.leaf.hyperdragshare.codex;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class AicrCoreAccessHooksTest {
    @Test public void allowsMmggCallsFromCoreBinder() {
        assertTrue(AicrCoreAccessHooks.shouldAllowSystemSignatureMatch(10279, 1000, 10279,
                new String[] {"com.leaf.hyperdragshare.codex"}, coreStack()));
    }

    @Test public void rejectsOtherCallerPackages() {
        assertFalse(AicrCoreAccessHooks.shouldAllowSystemSignatureMatch(10280, 1000, 10280,
                new String[] {"com.example.other"}, coreStack()));
    }

    @Test public void globalSettingAllowsOtherApplicationOnlyAtAicrCoreSignatureGate() {
        String[] caller = {"com.example.other"};
        assertFalse(AicrCoreAccessHooks.shouldAllowSystemSignatureMatch(
                10280, 1000, 10280, caller, coreStack()));
        assertTrue(AicrCoreAccessHooks.shouldAllowSystemSignatureMatch(
                10280, 1000, 10280, caller, coreStack(), true));
        assertFalse(AicrCoreAccessHooks.shouldAllowSystemSignatureMatch(
                10280, 1000, 10280, caller, new StackTraceElement[] {
                        new StackTraceElement("android.app.SomeOtherCaller", "onTransact", null, 1)
                }, true));
    }

    @Test public void acceptsCoreBinderInnerClassWithoutVersionSpecificNumber() {
        // AICR's anonymous Binder class number can differ between builds.
        assertTrue(AicrCoreAccessHooks.shouldAllowSystemSignatureMatch(10279, 1000, 10279,
                new String[] {"com.leaf.hyperdragshare.codex"}, new StackTraceElement[] {
                        new StackTraceElement("com.xiaomi.aicr.access.AiCrCoreService$17",
                                "onTransact", "AiCrCoreService.java", 1)
                }));
    }

    @Test public void rejectsOtherBinderFrames() {
        assertFalse(AicrCoreAccessHooks.shouldAllowSystemSignatureMatch(10279, 1000, 10279,
                new String[] {"com.leaf.hyperdragshare.codex"}, new StackTraceElement[] {
                        new StackTraceElement("com.xiaomi.aicr.access.AiCrCognitionService$3",
                                "onTransact", "AiCrCognitionService.java", 1)
                }));
    }

    @Test public void rejectsUnrelatedUidComparisonAndMismatchedBinderCaller() {
        String[] mmgg = {"com.leaf.hyperdragshare.codex"};
        assertFalse(AicrCoreAccessHooks.shouldAllowSystemSignatureMatch(10279, 1000, 10280,
                mmgg, coreStack()));
        assertFalse(AicrCoreAccessHooks.shouldAllowSystemSignatureMatch(10279, 1001, 10279,
                mmgg, coreStack()));
        assertFalse(AicrCoreAccessHooks.shouldAllowSystemSignatureMatch(1000, 10279, 10279,
                mmgg, coreStack()));
    }

    @Test public void rejectsMissingEvidence() {
        assertFalse(AicrCoreAccessHooks.shouldAllowSystemSignatureMatch(10279, 1000, 10279,
                null, coreStack()));
        assertFalse(AicrCoreAccessHooks.shouldAllowSystemSignatureMatch(10279, 1000, 10279,
                new String[] {"com.leaf.hyperdragshare.codex"}, null));
    }

    @Test public void globalSettingStillRequiresARealApplicationUidAndPackage() {
        assertFalse(AicrCoreAccessHooks.shouldAllowSystemSignatureMatch(
                9999, 1000, 9999, new String[] {"com.example.other"}, coreStack(), true));
        assertFalse(AicrCoreAccessHooks.shouldAllowSystemSignatureMatch(
                10280, 1000, 10280, new String[0], coreStack(), true));
        assertFalse(AicrCoreAccessHooks.shouldAllowSystemSignatureMatch(
                10280, 1001, 10280, new String[] {"com.example.other"}, coreStack(), true));
    }

    private static StackTraceElement[] coreStack() {
        return new StackTraceElement[] {
                new StackTraceElement("com.xiaomi.aicr.access.AiCrCoreService$2",
                        "onTransact", "AiCrCoreService.java", 1)
        };
    }
}
