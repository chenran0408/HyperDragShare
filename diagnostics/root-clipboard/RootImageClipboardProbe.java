package com.leaf.hyperdragshare.diagnostics;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.net.Uri;
import android.os.Looper;
import java.nio.charset.StandardCharsets;

/** Generated share image only. Reports the failing Binder boundary without the capability URI. */
public final class RootImageClipboardProbe {
 public static void main(String[] args) throws Exception {
  if (args.length>0 && "--methods".equals(args[0])) {
   for (java.lang.reflect.Method method: Class.forName("android.content.IClipboard").getDeclaredMethods())
    if (method.getName().contains("PrimaryClip")) System.out.println(method.toString());
   System.exit(0);
  }
  Looper.prepareMainLooper();
  Class<?> type=Class.forName("android.app.ActivityThread");
  Object thread=type.getDeclaredMethod("systemMain").invoke(null);
  Context context=(Context)type.getDeclaredMethod("getSystemContext").invoke(thread);
  if (args.length>0 && "--shell".equals(args[0])) context=context.createPackageContext("com.android.shell",0);
  Uri uri=Uri.parse(new String(System.in.readAllBytes(),StandardCharsets.UTF_8).trim());
  if (!"com.leaf.hyperdragshare.codex.share".equals(uri.getAuthority())) throw new IllegalArgumentException();
  String phase="create clip";
  try {
   ClipData clip=args.length==0 ? ClipData.newUri(context.getContentResolver(),"generated image",uri)
       : new ClipData("generated image",new String[]{"image/png"},new ClipData.Item(uri));
   ClipboardManager manager=context.getSystemService(ClipboardManager.class);
   phase="set clipboard";
   ClipData actual;
   if (args.length>0 && "--shell".equals(args[0])) {
    Object binder=Class.forName("android.os.ServiceManager").getDeclaredMethod("getService",String.class).invoke(null,"clipboard");
    Object service=Class.forName("android.content.IClipboard$Stub").getDeclaredMethod("asInterface",android.os.IBinder.class).invoke(null,binder);
    Class<?> clipboard=Class.forName("android.content.IClipboard");
    clipboard.getDeclaredMethod("setPrimaryClip",ClipData.class,String.class,String.class,int.class,int.class)
        .invoke(service,clip,"com.android.shell",null,0,0);
    phase="read clipboard";
    actual=(ClipData)clipboard.getDeclaredMethod("getPrimaryClip",String.class,String.class,int.class,int.class)
        .invoke(service,"com.android.shell",null,0,0);
   } else {
    manager.setPrimaryClip(clip);
    phase="read clipboard";
    actual=manager.getPrimaryClip();
   }
   System.out.println("image_uri_matches="+(actual!=null && uri.equals(actual.getItemAt(0).getUri())));
  } catch (Exception error) {
   if (error instanceof java.lang.reflect.InvocationTargetException && error.getCause() instanceof Exception)
    error=(Exception)error.getCause();
   System.out.println("phase="+phase+" error="+error.getClass().getSimpleName()+" reason="+
       String.valueOf(error.getMessage()).replace(uri.toString(),"<generated-image>"));
  }
  System.exit(0);
 }
}
