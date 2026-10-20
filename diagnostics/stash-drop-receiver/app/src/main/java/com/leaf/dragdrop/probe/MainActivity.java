package com.leaf.dragdrop.probe;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
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
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
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
        showReceiver();
    }

    private void showReceiver() {
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
        addButton(root, "显示识字样本", this::showRecognitionSample);
        addButton(root, "识别生成二维码", this::recognizeGeneratedQr);
        addButton(root, "核对测试复制", this::verifyTestCopy);
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

    private void showRecognitionSample() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24, 50, 24, 30);
        addButton(root, "返回拖拽接收器", this::showReceiver);
        TextView comparison = new TextView(this);
        comparison.setText("普通文字对照：只采集下方生成图片。");
        comparison.setTextSize(18);
        root.addView(comparison);

        Bitmap bitmap = Bitmap.createBitmap(900, 500, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(Color.WHITE);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.BLACK);
        paint.setTextSize(46);
        canvas.drawText("传送门图片文字识别", 40, 85, paint);
        canvas.drawText("小米15 Pro · HyperOS 4", 40, 170, paint);
        canvas.drawText("IMAGE OCR SAMPLE 217", 40, 255, paint);
        canvas.drawText("Copy Search Share 12345", 40, 340, paint);
        canvas.drawText("只使用程序生成内容", 40, 425, paint);
        ImageView image = new ImageView(this);
        image.setContentDescription("图片");
        image.setImageBitmap(bitmap);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int imageWidth = getResources().getDisplayMetrics().widthPixels - 48;
        root.addView(image, new LinearLayout.LayoutParams(-1, imageWidth * 500 / 900));
        setContentView(root);
    }

    private void addButton(LinearLayout root, String label, Runnable action) {
        Button button = new Button(this);
        button.setText(label);
        button.setOnClickListener(view -> action.run());
        root.addView(button);
    }

    private void verifyTestCopy() {
        try {
            ClipData clip = getSystemService(ClipboardManager.class).getPrimaryClip();
            CharSequence text = clip != null && clip.getItemCount() > 0 ? clip.getItemAt(0).getText() : null;
            boolean matches = text != null && "https://example.com/".contentEquals(text);
            result.setText("测试复制匹配：" + matches);
        } catch (SecurityException error) {
            result.setText("测试复制读取受限：" + error);
        }
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

    private void recognizeGeneratedQr() {
        Bitmap bitmap = Bitmap.createBitmap(640, 640, Bitmap.Config.ARGB_8888);
        try {
            BitMatrix code = new QRCodeWriter().encode("https://example.com/",
                    BarcodeFormat.QR_CODE, 640, 640);
            int[] pixels = new int[640 * 640];
            for (int y = 0; y < 640; y++) {
                for (int x = 0; x < 640; x++) {
                    pixels[y * 640 + x] = code.get(x, y) ? Color.BLACK : Color.WHITE;
                }
            }
            bitmap.setPixels(pixels, 0, 640, 0, 0, 640, 640);
            try (FileOutputStream output = new FileOutputStream(new File(getFilesDir(), "sample.png"))) {
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                    throw new IOException("PNG encoding failed");
                }
            }
        } catch (WriterException | IOException error) {
            result.setText("生成二维码失败：" + error);
            return;
        } finally {
            bitmap.recycle();
        }
        Intent intent = new Intent(Intent.ACTION_SEND)
                .setClassName("com.leaf.hyperdragshare.codex", "com.leaf.hyperdragshare.codex.ImageOcrActivity")
                .setDataAndType(IMAGE, "image/png")
                .putExtra(Intent.EXTRA_STREAM, IMAGE)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.setClipData(ClipData.newRawUri("generated QR", IMAGE));
        startActivity(intent);
    }
}
