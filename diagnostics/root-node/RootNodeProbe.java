import android.app.UiAutomation;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityNodeInfo;
public class RootNodeProbe {
 static int nodes,texts,chars; static int x=-1,y=-1;
 static void walk(AccessibilityNodeInfo n,int depth) {
  if(depth>64||nodes>=4000)return;
  nodes++;
  android.graphics.Rect bounds=new android.graphics.Rect(); n.getBoundsInScreen(bounds);
  if(!n.isPassword()&&n.getText()!=null&&bounds.contains(x,y)) System.out.println("HIT bounds="+bounds+" chars="+n.getText().length()+" class="+n.getClassName());
  if(!n.isPassword()&&n.getText()!=null){texts++;chars+=n.getText().length();}
  for(int i=0;i<n.getChildCount();i++){AccessibilityNodeInfo c=n.getChild(i);if(c!=null){try{walk(c,depth+1);}finally{c.recycle();}}}
 }
 public static void main(String[] args)throws Exception {
  Looper.prepareMainLooper();
  if(args.length==2){x=Integer.parseInt(args[0]);y=Integer.parseInt(args[1]);}
  Class<?> c=Class.forName("android.app.UiAutomationConnection"), i=Class.forName("android.app.IUiAutomationConnection");
  java.lang.reflect.Constructor<?> ctor=UiAutomation.class.getDeclaredConstructor(Looper.class,i);
  java.lang.reflect.Method connect=UiAutomation.class.getDeclaredMethod("connectWithTimeout",int.class,long.class), disconnect=UiAutomation.class.getDeclaredMethod("disconnect");
  System.out.println("SIGNATURE "+ctor+" "+connect);
  HandlerThread thread=new HandlerThread("RootNodeProbe");thread.start();
  UiAutomation a=(UiAutomation)ctor.newInstance(thread.getLooper(),c.getDeclaredConstructor().newInstance());
  boolean connected=false;
  try {
   long t=SystemClock.uptimeMillis();connect.invoke(a,1,3000L);connected=true;System.out.println("CONNECTED ms="+(SystemClock.uptimeMillis()-t)+" flags="+a.getServiceInfo().flags);
   for(int run=0;run<3;run++){
    t=SystemClock.uptimeMillis();a.clearCache();AccessibilityNodeInfo root=a.getRootInActiveWindow();
    if(root==null){System.out.println("READ null_root");continue;}
    try{nodes=texts=chars=0;walk(root,0);System.out.println("READ package="+root.getPackageName()+" nodes="+nodes+" textNodes="+texts+" chars="+chars+" ms="+(SystemClock.uptimeMillis()-t));}finally{root.recycle();}
   }
  } finally {if(connected)disconnect.invoke(a);thread.quitSafely();}
  System.out.println("DISCONNECTED");System.exit(0);
 }
}


