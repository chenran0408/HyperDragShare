package com.leaf.hyperdragshare.codex;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.os.Bundle;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public final class ModuleActivationTest {
    @Test
    public void injectionAndPortalRootReportsMustMatchTheRunningBuild() {
        assertTrue(ModuleActivation.matchesCurrentBuild(BuildConfig.VERSION_CODE));
        assertFalse(ModuleActivation.matchesCurrentBuild(BuildConfig.VERSION_CODE - 1L));

        Context context = RuntimeEnvironment.getApplication();
        Bundle rootDenied = new Bundle();
        rootDenied.putLong(ModuleActivation.EXTRA_VERSION_CODE, BuildConfig.VERSION_CODE);
        rootDenied.putBoolean(ModuleActivation.EXTRA_PORTAL_ROOT_GRANTED, false);
        ModuleActivation.recordInjected(context, rootDenied);
        assertTrue(ModuleActivation.isCurrentBuildInjected(context));
        assertFalse(ModuleActivation.isCurrentBuildPortalRootGranted(context));

        Bundle rootGranted = new Bundle();
        rootGranted.putLong(ModuleActivation.EXTRA_VERSION_CODE, BuildConfig.VERSION_CODE);
        rootGranted.putBoolean(ModuleActivation.EXTRA_PORTAL_ROOT_GRANTED, true);
        ModuleActivation.recordInjected(context, rootGranted);
        assertTrue(ModuleActivation.isCurrentBuildPortalRootGranted(context));

        Bundle staleRootReport = new Bundle();
        staleRootReport.putLong(
                ModuleActivation.EXTRA_VERSION_CODE,
                BuildConfig.VERSION_CODE - 1L);
        staleRootReport.putBoolean(ModuleActivation.EXTRA_PORTAL_ROOT_GRANTED, false);
        ModuleActivation.recordInjected(context, staleRootReport);
        assertTrue(ModuleActivation.isCurrentBuildPortalRootGranted(context));
    }

    @Test
    public void handshakeOnlyReadsTheNoopPortalProvider() {
        String command = ModuleActivation.portalHandshakeCommand();
        assertTrue(command.startsWith("content query --user "));
        assertTrue(command.contains("content://com.miui.contentextension.provider.switchcontrolprovider"));
        assertFalse(command.contains("start"));
        assertFalse(command.contains("call"));
        assertFalse(command.contains("com.miui.contentcatcher"));
        assertFalse(command.contains("force-stop"));
    }

    @Test
    public void explicitReloadClearsOldReadinessButKeepsUserSettings() {
        Context context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences("drag_share_settings", Context.MODE_PRIVATE).edit()
                .putBoolean("thumb_trigger_enabled", true).commit();
        Bundle report = new Bundle();
        report.putLong(ModuleActivation.EXTRA_VERSION_CODE, BuildConfig.VERSION_CODE);
        report.putInt(ModuleActivation.EXTRA_PROCESS_ID, 1234);
        report.putBoolean(ModuleActivation.EXTRA_PORTAL_ROOT_GRANTED, true);
        ModuleActivation.recordInjected(context, report);
        assertEquals(1234, ModuleActivation.injectedProcessId(context));
        ModuleActivation.clearPortalReport(context);
        assertFalse(ModuleActivation.isCurrentBuildInjected(context));
        assertFalse(ModuleActivation.isCurrentBuildPortalRootGranted(context));
        assertEquals(0, ModuleActivation.injectedProcessId(context));
        assertTrue(context.getSharedPreferences("drag_share_settings", Context.MODE_PRIVATE)
                .getBoolean("thumb_trigger_enabled", false));
        report.putLong(ModuleActivation.EXTRA_VERSION_CODE, BuildConfig.VERSION_CODE - 1L);
        ModuleActivation.recordInjected(context, report);
        assertEquals(0, ModuleActivation.injectedProcessId(context));
    }

    @Test
    public void blacklistCommandStartsOnlyTheRequestedPortalActivity() {
        assertEquals(
                "am start --user current -n com.miui.contentextension/"
                        + "com.miui.contentextension.setting.whitelist.BlacklistSettingActivity",
                ModuleActivation.portalBlacklistCommand());
    }

    @Test
    public void newHostCannotInheritOldRootOrReaderReadiness() {
        Context context = RuntimeEnvironment.getApplication();
        Bundle report = new Bundle();
        report.putLong(ModuleActivation.EXTRA_VERSION_CODE, BuildConfig.VERSION_CODE);
        report.putInt(ModuleActivation.EXTRA_PROCESS_ID, 42);
        report.putBoolean(ModuleActivation.EXTRA_PORTAL_ROOT_GRANTED, true);
        report.putBoolean(ModuleActivation.EXTRA_PRESS_READER_READY, true);
        ModuleActivation.recordInjected(context, report);
        assertTrue(ModuleActivation.isPressReaderReady(context));

        Bundle restarted = new Bundle();
        restarted.putLong(ModuleActivation.EXTRA_VERSION_CODE, BuildConfig.VERSION_CODE);
        restarted.putInt(ModuleActivation.EXTRA_PROCESS_ID, 43);
        ModuleActivation.recordInjected(context, restarted);
        assertTrue(ModuleActivation.isCurrentBuildInjected(context));
        assertFalse(ModuleActivation.isCurrentBuildPortalRootGranted(context));
        assertFalse(ModuleActivation.isPressReaderReady(context));
        assertEquals("", ModuleActivation.pressReaderError(context));
    }

    @Test
    public void readerFailurePersistsWithoutClaimingRootWasDenied() {
        Context context = RuntimeEnvironment.getApplication();
        Bundle report = new Bundle();
        report.putLong(ModuleActivation.EXTRA_VERSION_CODE, BuildConfig.VERSION_CODE);
        report.putBoolean(ModuleActivation.EXTRA_PORTAL_ROOT_GRANTED, true);
        report.putBoolean(ModuleActivation.EXTRA_PRESS_READER_READY, false);
        report.putString(ModuleActivation.EXTRA_PRESS_READER_ERROR, "Root 子进程已退出");
        ModuleActivation.recordInjected(context, report);
        assertTrue(ModuleActivation.isCurrentBuildPortalRootGranted(context));
        assertFalse(ModuleActivation.isPressReaderReady(context));
        assertEquals("Root 子进程已退出", ModuleActivation.pressReaderError(context));
        ModuleActivation.clearPortalReport(context);
        assertEquals("", ModuleActivation.pressReaderError(context));
    }
}
