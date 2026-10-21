package com.leaf.hyperdragshare.codex;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.Binder;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

/** The system sweeps disposable images; persistent stash files are never visited. */
public final class SharedImageCleanupJob extends JobService {
    static final int JOB_ID = 133;
    static final String PREFS = "image_cleanup";
    static final String LAST_ERROR = "last_error";
    private static final String TAG = "DragShare/ImageCleanup";
    private static final long MAX_AGE_MS = TimeUnit.HOURS.toMillis(24);
    private final Handler main = new Handler(Looper.getMainLooper());
    private Thread worker;

    static void schedule(Context context) {
        JobScheduler scheduler = context.getSystemService(JobScheduler.class);
        try {
            if (scheduler.getPendingJob(JOB_ID) != null) return;
            JobInfo job = new JobInfo.Builder(JOB_ID,
                    new ComponentName(context, SharedImageCleanupJob.class))
                    .setPeriodic(TimeUnit.HOURS.toMillis(12))
                    .setRequiresBatteryNotLow(true)
                    .build();
            if (scheduler.schedule(job) != JobScheduler.RESULT_SUCCESS) {
                recordFailure(context, "系统未能安排暂存图片清理", null);
            }
        } catch (SecurityException | IllegalStateException error) {
            recordFailure(context, "无法安排暂存图片清理，请查看诊断日志", error);
        }
    }

    @Override public boolean onStartJob(JobParameters params) {
        worker = new Thread(() -> {
            Thread current = Thread.currentThread();
            try {
                int removed = cleanup(this);
                if (!current.isInterrupted()) {
                    getSharedPreferences(PREFS, MODE_PRIVATE).edit().remove(LAST_ERROR).apply();
                    DragShareLog.i(TAG, "expired private images removed=" + removed);
                }
            } catch (IOException | SecurityException error) {
                recordFailure(this, "暂存图片未能清理，请查看诊断日志；收藏不受影响", error);
            } finally {
                main.post(() -> {
                    if (worker == current) {
                        worker = null;
                        jobFinished(params, false);
                    }
                });
            }
        }, "image-cache-cleanup");
        worker.start();
        return true;
    }

    @Override public boolean onStopJob(JobParameters params) {
        if (worker != null) worker.interrupt();
        worker = null;
        return false;
    }

    static void cleanupBeforeImport(Context context) {
        long identity = Binder.clearCallingIdentity();
        try {
            cleanup(context);
        } catch (IOException | SecurityException error) {
            recordFailure(context, "暂存图片未能清理，请查看诊断日志；收藏不受影响", error);
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    static synchronized int cleanup(Context context) throws IOException {
        int removed = 0;
        long cutoff = System.currentTimeMillis() - MAX_AGE_MS;
        for (String name : new String[] {"shared-images", "ocr-input"}) {
            File directory = new File(context.getCacheDir(), name);
            if (!directory.exists()) continue;
            File[] files = directory.listFiles();
            if (files == null) throw new IOException("无法读取图片暂存目录：" + name);
            for (File file : files) {
                if (Thread.currentThread().isInterrupted()) return removed;
                if (!file.isFile() || file.lastModified() >= cutoff) continue;
                if (!file.delete() && file.exists()) throw new IOException("无法删除过期图片：" + file.getName());
                removed++;
                String token = ShareUriToken.parse(file.getName());
                if (name.equals("shared-images") && token != null) {
                    context.revokeUriPermission(ImageStagingClient.BASE_URI.buildUpon()
                            .appendPath("shared").appendPath(file.getName()).build(),
                            Intent.FLAG_GRANT_READ_URI_PERMISSION);
                }
            }
        }
        return removed;
    }

    private static void recordFailure(Context context, String message, Exception error) {
        if (error == null) DragShareLog.w(TAG, message);
        else DragShareLog.w(TAG, message, error);
        context.getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(LAST_ERROR, message).apply();
    }
}
