package com.leaf.dragdrop.probe;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.DragAndDropPermissions;
import android.view.DragEvent;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/** Independent UID, generated samples only; no network or screen capture. */
public final class MainActivity extends Activity {
    private TextView result;
    private static final Uri IMAGE = Uri.parse("content://com.leaf.dragdrop.probe.image/sample");

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24, 50, 24, 30);
        result = new TextView(this);
        result.setTextSize(18);
        result.setText("独立 UID 拖拽接收器\n只存入并取回生成的 STASH-115 样本");
        root.addView(result);
        addButton(root, "存入 STASH-115-A", () -> shareText("STASH-115-A"));
        addButton(root, "存入 STASH-115-B", () -> shareText("STASH-115-B"));
        addButton(root, "存入生成图片", this::shareImage);
        TextView drop = new TextView(this);
        drop.setText("拖到这里松手\n支持文字 / 图片 / 多项\n结果显示在顶部");
        drop.setTextSize(24);
        drop.setGravity(Gravity.CENTER);
        drop.setBackgroundColor(0xffe4efff);
        drop.setOnDragListener((view, event) -> {
            switch (event.getAction()) {
                case DragEvent.ACTION_DRAG_STARTED:
                    result.setText("收到 DRAG_STARTED，等待松手");
                    return true;
                case DragEvent.ACTION_DROP:
                    return receive(event);
                case DragEvent.ACTION_DRAG_ENDED:
                    if (!event.getResult()) result.append("\n拖拽未被接收");
                    return true;
                default: return true;
            }
        });
        root.addView(drop, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
    }

    private void addButton(LinearLayout root, String label, Runnable action) {
        Button button = new Button(this);
        button.setText(label);
        button.setOnClickListener(view -> action.run());
        root.addView(button);
    }

    private Intent share(String type) {
        return new Intent(Intent.ACTION_SEND).setType(type)
                .setClassName("com.leaf.hyperdragshare.codex", "com.leaf.hyperdragshare.codex.StashActivity");
    }

    private void shareText(String text) { startActivity(share("text/plain").putExtra(Intent.EXTRA_TEXT, text)); }

    private void shareImage() {
        Bitmap bitmap = Bitmap.createBitmap(640, 240, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(Color.WHITE);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.BLACK);
        paint.setTextSize(52);
        canvas.drawText("STASH-115 PNG", 30, 130, paint);
        try (FileOutputStream output = new FileOutputStream(new File(getFilesDir(), "sample.png"))) {
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) throw new IOException("PNG encoding failed");
        } catch (IOException error) {
            result.setText("生成图片失败：" + error);
            return;
        } finally { bitmap.recycle(); }
        Intent intent = share("image/png").putExtra(Intent.EXTRA_STREAM, IMAGE)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.setClipData(ClipData.newRawUri("sample", IMAGE));
        startActivity(intent);
    }

    private boolean receive(DragEvent event) {
        DragAndDropPermissions permission = requestDragAndDropPermissions(event);
        ClipData clip = event.getClipData();
        StringBuilder report = new StringBuilder("DROP OK uid=").append(android.os.Process.myUid())
                .append(" items=").append(clip.getItemCount());
        try {
            for (int index = 0; index < clip.getItemCount(); index++) {
                ClipData.Item item = clip.getItemAt(index);
                if (item.getUri() != null) {
                    byte[] bytes;
                    try (InputStream input = getContentResolver().openInputStream(item.getUri())) {
                        if (input == null) throw new IOException("no image stream");
                        bytes = input.readAllBytes();
                    }
                    Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                    if (bitmap == null) throw new IOException("image decode failed");
                    report.append("\nimage ").append(bitmap.getWidth()).append('x').append(bitmap.getHeight())
                            .append(" bytes=").append(bytes.length)
                            .append(" png=").append(bytes.length >= 8 && bytes[0] == (byte)0x89 && bytes[1] == 'P');
                    bitmap.recycle();
                } else report.append("\ntext: ").append(item.getText());
            }
            result.setText(report);
            Log.i("StashDropProbe", report.toString());
            return true;
        } catch (IOException | SecurityException error) {
            result.setText("DROP FAILED uid=" + android.os.Process.myUid() + "\n" + error);
            Log.e("StashDropProbe", "drop read failed", error);
            return false;
        } finally { if (permission != null) permission.release(); }
    }
}
