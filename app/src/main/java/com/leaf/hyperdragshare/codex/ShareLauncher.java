package com.leaf.hyperdragshare.codex;

import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

final class ShareLauncher {
    private ShareLauncher() {}

    static void launch(
            Context context,
            CapturedContent payload,
            ShareTarget target,
            Uri stagedImage,
            DragShareToast toast) {
        launch(context, payload, target, stagedImage, toast, OperationTrace.fresh("share"));
    }

    static void launch(
            Context context,
            CapturedContent payload,
            ShareTarget target,
            Uri stagedImage,
            DragShareToast toast,
            String operation) {
        try {
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType(payload.mimeType());
            intent.setComponent(target.component);
            intent.addCategory(Intent.CATEGORY_DEFAULT);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            DragShareLog.i("DragShare/Share", "prepare target="
                    + target.component.flattenToShortString()
                    + " kind=" + (payload.isImage() ? "image" : "text")
                    + " mime=" + payload.mimeType());

            if (!payload.isImage()) {
                intent.putExtra(Intent.EXTRA_TEXT, payload.text);
            } else {
                if (stagedImage == null) {
                    throw new IllegalArgumentException("Image URI is not ready");
                }
                intent.setDataAndType(stagedImage, payload.mimeType());
                intent.putExtra(Intent.EXTRA_STREAM, stagedImage);
                intent.putExtra(Intent.EXTRA_TITLE, "drag-share.png");
                ClipData clip = ClipData.newUri(
                        context.getContentResolver(), "drag-share-image", stagedImage);
                OperationTrace.put(clip, operation);
                intent.setClipData(clip);
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                ImageStagingClient.grantReadAccess(
                        context,
                        stagedImage,
                        target.component.getPackageName());
            }
            android.os.Bundle trace = new android.os.Bundle();
            OperationTrace.put(trace, operation);
            intent.putExtras(trace);
            OperationTrace.event(operation, "share", "activity_requested", stagedImage);
            context.startActivity(intent);
            OperationTrace.event(operation, "share", "activity_launch_returned", stagedImage);
            DragShareLog.i("DragShare/Share", "activity launch returned target="
                    + target.component.flattenToShortString());
        } catch (Throwable error) {
            String errorType = error.getClass().getSimpleName();
            OperationTrace.event(operation, "share", "failed_" + errorType, stagedImage);
            DragShareLog.w("DragShare/Share", "launch failed target="
                    + target.component.flattenToShortString() + " error=" + errorType);
            if (stagedImage != null) {
                try {
                    ImageStagingClient.revokeReadAccess(
                            context,
                            stagedImage,
                            target.component.getPackageName());
                } catch (Throwable ignored) {
                    // Preserve the original launch failure.
                }
            }
            if (toast != null) {
                toast.show("无法打开 " + target.label);
            }
        }
    }
}
