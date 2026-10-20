package com.leaf.hyperdragshare.codex;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;

import java.io.IOException;

/** Only the cross-UID store operation; persistent files remain owned by the module app. */
final class StashClient {
    static final String METHOD_PUT = "stash_put";
    static final String EXTRA_TEXT = "text";
    static final String EXTRA_IMAGE = "image_uri";
    static final String RESULT_ID = "id";

    private StashClient() {}

    static String put(Context context, CapturedContent content, Uri stagedImage) throws IOException {
        Bundle input = new Bundle();
        if (content.isImage()) {
            if (stagedImage == null) throw new IOException("图片尚未准备完成");
            input.putParcelable(EXTRA_IMAGE, stagedImage);
        } else {
            input.putString(EXTRA_TEXT, content.text);
        }
        Bundle result = context.getContentResolver().call(ImageStagingClient.BASE_URI, METHOD_PUT, null, input);
        String id = result == null ? null : result.getString(RESULT_ID);
        if (id == null) throw new IOException("中转站未返回存入结果");
        return id;
    }
}
