package com.leaf.hyperdragshare.codex;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;

import java.lang.reflect.Proxy;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public final class ModuleActivationTest {
    @Before public void clearRuntimeReport() {
        ModuleActivation.clearPortalReport(RuntimeEnvironment.getApplication());
    }

    private Bundle report(int pid, Lifetime lifetime) {
        Bundle report = new Bundle();
        report.putLong(ModuleActivation.EXTRA_VERSION_CODE, BuildConfig.VERSION_CODE);
        report.putInt(ModuleActivation.EXTRA_PROCESS_ID, pid);
        report.putBinder(ModuleActivation.EXTRA_LIFETIME_TOKEN, lifetime.token);
        return report;
    }

    private Bundle readyReport(int pid, Lifetime lifetime) {
        Bundle report = report(pid, lifetime);
        report.putBoolean(ModuleActivation.EXTRA_PORTAL_ROOT_GRANTED, true);
        report.putBoolean(ModuleActivation.EXTRA_ROOT_INPUT_READY, true);
        report.putBoolean(ModuleActivation.EXTRA_PRESS_READER_READY, true);
        return report;
    }

    @Test
    public void injectionAndPortalRootReportsMustMatchTheRunningBuild() {
        assertTrue(ModuleActivation.matchesCurrentBuild(BuildConfig.VERSION_CODE));
        assertFalse(ModuleActivation.matchesCurrentBuild(BuildConfig.VERSION_CODE - 1L));

        Context context = RuntimeEnvironment.getApplication();
        Lifetime lifetime = new Lifetime();
        Bundle rootDenied = report(10, lifetime);
        rootDenied.putBoolean(ModuleActivation.EXTRA_PORTAL_ROOT_GRANTED, false);
        ModuleActivation.recordInjected(context, rootDenied);
        assertTrue(ModuleActivation.isCurrentBuildInjected(context));
        assertFalse(ModuleActivation.isCurrentBuildPortalRootGranted(context));

        Bundle rootGranted = report(10, lifetime);
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
        Bundle report = report(1234, new Lifetime());
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
        Bundle report = report(42, new Lifetime());
        report.putBoolean(ModuleActivation.EXTRA_PORTAL_ROOT_GRANTED, true);
        report.putBoolean(ModuleActivation.EXTRA_PRESS_READER_READY, true);
        report.putBoolean(ModuleActivation.EXTRA_ROOT_INPUT_READY, true);
        ModuleActivation.recordInjected(context, report);
        assertTrue(ModuleActivation.isPressReaderReady(context));
        assertTrue(ModuleActivation.isRootInputReady(context));

        Bundle restarted = report(43, new Lifetime());
        ModuleActivation.recordInjected(context, restarted);
        assertTrue(ModuleActivation.isCurrentBuildInjected(context));
        assertFalse(ModuleActivation.isCurrentBuildPortalRootGranted(context));
        assertFalse(ModuleActivation.isPressReaderReady(context));
        assertFalse(ModuleActivation.isRootInputReady(context));
        assertEquals("", ModuleActivation.pressReaderError(context));
    }

    @Test
    public void readerFailurePersistsWithoutClaimingRootWasDenied() {
        Context context = RuntimeEnvironment.getApplication();
        Bundle report = report(10, new Lifetime());
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

    @Test
    public void inputFailureRemainsVisibleWhenReaderIsReady() {
        Context context = RuntimeEnvironment.getApplication();
        Bundle report = report(10, new Lifetime());
        report.putBoolean(ModuleActivation.EXTRA_PORTAL_ROOT_GRANTED, true);
        report.putBoolean(ModuleActivation.EXTRA_PRESS_READER_READY, true);
        report.putBoolean(ModuleActivation.EXTRA_ROOT_INPUT_READY, false);
        report.putString(ModuleActivation.EXTRA_ROOT_INPUT_ERROR, "Root 输入命令被系统结束（137）");
        ModuleActivation.recordInjected(context, report);
        assertTrue(ModuleActivation.isCurrentBuildPortalRootGranted(context));
        assertTrue(ModuleActivation.isPressReaderReady(context));
        assertFalse(ModuleActivation.isRootInputReady(context));
        assertEquals("Root 输入命令被系统结束（137）", ModuleActivation.rootInputError(context));
        ModuleActivation.clearPortalReport(context);
        assertEquals("", ModuleActivation.rootInputError(context));
    }

    @Test public void currentPortalDeathClearsAllOfItsReadiness() {
        Context context = RuntimeEnvironment.getApplication();
        Lifetime lifetime = new Lifetime();
        ModuleActivation.recordInjected(context, readyReport(70, lifetime));
        assertTrue(ModuleActivation.isCurrentBuildInjected(context));
        assertTrue(ModuleActivation.isCurrentBuildPortalRootGranted(context));
        assertTrue(ModuleActivation.isRootInputReady(context));
        assertTrue(ModuleActivation.isPressReaderReady(context));

        // Binder can already be dead before the queued death callback reaches the module.
        lifetime.alive = false;
        assertFalse(ModuleActivation.isCurrentBuildInjected(context));
        assertFalse(ModuleActivation.isRootInputReady(context));
        lifetime.die();
        assertFalse(ModuleActivation.isCurrentBuildInjected(context));
        assertFalse(ModuleActivation.isCurrentBuildPortalRootGranted(context));
        assertFalse(ModuleActivation.isRootInputReady(context));
        assertFalse(ModuleActivation.isPressReaderReady(context));
        assertEquals(0, ModuleActivation.injectedProcessId(context));
        assertTrue(context.getSharedPreferences(ModuleActivation.PREFS_NAME, Context.MODE_PRIVATE).getAll().isEmpty());
    }

    @Test public void queuedDeathFromOldPortalCannotClearItsReplacement() {
        Context context = RuntimeEnvironment.getApplication();
        Lifetime old = new Lifetime();
        Lifetime replacement = new Lifetime();
        ModuleActivation.recordInjected(context, readyReport(70, old));
        IBinder.DeathRecipient queuedDeath = old.death;
        ModuleActivation.recordInjected(context, readyReport(71, replacement));
        assertEquals(1, old.unlinks);

        queuedDeath.binderDied();
        assertEquals(71, ModuleActivation.injectedProcessId(context));
        assertTrue(ModuleActivation.isCurrentBuildInjected(context));
        assertTrue(ModuleActivation.isRootInputReady(context));
        assertTrue(ModuleActivation.isPressReaderReady(context));
    }

    @Test public void historicalReadyPreferencesWithoutAnInMemoryTokenAreNotActivation() {
        Context context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences(ModuleActivation.PREFS_NAME, Context.MODE_PRIVATE).edit()
                .putLong("injected_version", BuildConfig.VERSION_CODE).putInt("process_id", 70)
                .putLong("portal_root_version", BuildConfig.VERSION_CODE).putBoolean("portal_root_granted", true)
                .putLong("root_input_version", BuildConfig.VERSION_CODE).putBoolean("root_input_ready", true)
                .putLong("press_reader_version", BuildConfig.VERSION_CODE).putBoolean("press_reader_ready", true)
                .commit();
        assertFalse(ModuleActivation.isCurrentBuildInjected(context));
        assertFalse(ModuleActivation.isCurrentBuildPortalRootGranted(context));
        assertFalse(ModuleActivation.isRootInputReady(context));
        assertFalse(ModuleActivation.isPressReaderReady(context));
        assertEquals(0, ModuleActivation.injectedProcessId(context));
    }

    @Test public void deadTokenAtLinkTimeLeavesNoHistoricalActivation() {
        Context context = RuntimeEnvironment.getApplication();
        ModuleActivation.recordInjected(context, readyReport(70, new Lifetime()));
        Lifetime ended = new Lifetime();
        ended.alive = false;
        ModuleActivation.recordInjected(context, readyReport(71, ended));
        assertEquals(1, ended.links);
        assertFalse(ModuleActivation.isCurrentBuildInjected(context));
        assertFalse(ModuleActivation.isRootInputReady(context));
        assertFalse(ModuleActivation.isPressReaderReady(context));
    }

    @Test public void statusUpdatesShareOneDeathSubscriptionAndMissingTokenIsRejected() {
        Context context = RuntimeEnvironment.getApplication();
        Lifetime lifetime = new Lifetime();
        ModuleActivation.recordInjected(context, report(70, lifetime));
        ModuleActivation.recordInjected(context, readyReport(70, lifetime));
        assertEquals(1, lifetime.links);
        assertTrue(ModuleActivation.isRootInputReady(context));

        Bundle incomplete = readyReport(70, lifetime);
        incomplete.remove(ModuleActivation.EXTRA_LIFETIME_TOKEN);
        ModuleActivation.recordInjected(context, incomplete);
        assertFalse(ModuleActivation.isCurrentBuildInjected(context));
        assertEquals(1, lifetime.unlinks);
    }

    /** A controllable remote Binder boundary; no processes or Root commands are started. */
    private static final class Lifetime {
        boolean alive = true;
        int links;
        int unlinks;
        IBinder.DeathRecipient death;
        final IBinder token = (IBinder) Proxy.newProxyInstance(IBinder.class.getClassLoader(),
                new Class<?>[]{IBinder.class}, (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "isBinderAlive":
                        case "pingBinder": return alive;
                        case "linkToDeath":
                            links++;
                            if (!alive) throw new RemoteException("Portal already ended");
                            death = (IBinder.DeathRecipient) args[0];
                            return null;
                        case "unlinkToDeath":
                            if (death != args[0]) return false;
                            unlinks++;
                            death = null;
                            return true;
                        case "toString": return "PortalLifetime";
                        default: throw new AssertionError("Unexpected Binder call: " + method.getName());
                    }
                });

        void die() {
            alive = false;
            death.binderDied();
        }
    }
}
