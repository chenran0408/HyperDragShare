package com.leaf.hyperdragshare.diagnostics;
import android.content.Context;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.os.Looper;
public final class RootClipboardProbe {
 public static void main(String[] args) throws Exception {
  Looper.prepareMainLooper();
  Class<?> type=Class.forName("android.app.ActivityThread");
  Object thread=type.getDeclaredMethod("systemMain").invoke(null);
  Context context=(Context)type.getDeclaredMethod("getSystemContext").invoke(thread);
  ClipboardManager manager=context.getSystemService(ClipboardManager.class);
  String expected=args.length == 0 ? "https://example.com/" : args[0];
  manager.setPrimaryClip(ClipData.newPlainText("QR TEST",expected));
  ClipData actual=manager.getPrimaryClip();
  boolean matches=actual!=null && actual.getItemCount()>0 && expected.contentEquals(actual.getItemAt(0).getText());
  System.out.println("root_written_matches="+matches);
  System.exit(matches?0:2);
 }
}
