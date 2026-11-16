package com.ordanexa.fidoreader;

import android.app.*;
import android.os.*;
import android.content.*;
import android.hardware.usb.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.*;
import android.content.res.ColorStateList;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

public final class MainActivity extends Activity {
    private static final String USB_PERMISSION="com.ordanexa.fidoreader.USB_PERMISSION";
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private UsbManager usb;private UsbDevice selected;private UsbIo transport;
    private static final int BG=0xfff5f7f9,SURFACE=0xffffffff,BORDER=0xffe4e7eb,INK=0xff16181d,MUTED=0xff5b636e,ACCENT=0xfff23058;
    private LinearLayout root,entries,unlockPanel,vaultHeader;private TextView status,deviceText,deviceDetail,vaultCount;private EditText pin;private Button connect,unlock;
    private View deviceBadge;
    private final List<Entry> secrets=new ArrayList<>();
    private final List<TextView> passwordViews=new ArrayList<>();
    private int generation=0;private boolean busy=false,foreground=false,receiverRegistered=false;
    private String clipboardLabel;private long clipboardExpiry;
    private final Runnable autoLock=()->lock("Automatisch gesperrt");
    private final Runnable clearClipboard=this::clearOwnClipboard;
    private static final class Entry {String vault,title,user;char[] password;Entry(String v,String t,String u,String p){vault=v;title=t;user=u;password=p.toCharArray();}void close(){Arrays.fill(password,'\0');}}
    private final BroadcastReceiver receiver=new BroadcastReceiver(){public void onReceive(Context context,Intent intent){
        if(USB_PERMISSION.equals(intent.getAction())){
            UsbDevice d=intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
            if(selected!=null&&d!=null&&selected.getDeviceId()==d.getDeviceId()){
                if(intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED,false)&&usb.hasPermission(selected)){status.setText("Verbunden. Gib die PIN deines Security Keys ein.");deviceDetail.setText("USB-C · Verbunden");deviceBadge.setBackground(round(0xff59d3a6,20,0));unlock.setEnabled(true);}
                else status.setText("USB-Zugriff nicht freigegeben");
            }
        }else if(UsbManager.ACTION_USB_DEVICE_DETACHED.equals(intent.getAction())){
            UsbDevice d=intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
            if(selected!=null&&d!=null&&selected.getDeviceId()==d.getDeviceId()){lock("Security Key entfernt. Der Vault ist gesperrt.");selected=null;unlock.setEnabled(false);deviceText.setText("Token2 PIN+ Dual");deviceDetail.setText("Nicht verbunden");deviceBadge.setBackground(round(MUTED,20,0));clearOwnClipboard();}
        }
    }};
    @Override public void onCreate(Bundle state){
        super.onCreate(state);getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        usb=(UsbManager)getSystemService(USB_SERVICE);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(BG);scroll.setClipToPadding(false);
        scroll.setOnApplyWindowInsetsListener((v,insets)->{android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.ime());v.setPadding(bars.left,bars.top,bars.right,bars.bottom);return insets;});
        FrameLayout frame=new FrameLayout(this);scroll.addView(frame,new ScrollView.LayoutParams(-1,-2));
        root=column();root.setPadding(dp(20),dp(28),dp(20),dp(28));
        FrameLayout.LayoutParams content=new FrameLayout.LayoutParams(Math.min(getResources().getDisplayMetrics().widthPixels,dp(700)),-2,Gravity.TOP|Gravity.CENTER_HORIZONTAL);frame.addView(root,content);setContentView(scroll);
        LinearLayout brand=row();brand.setPadding(0,0,0,dp(24));
        ImageView logo=new ImageView(this);logo.setImageResource(R.drawable.fidoparole_mark);logo.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);brand.addView(logo,new LinearLayout.LayoutParams(dp(58),dp(58)));
        LinearLayout wordmark=column();LinearLayout.LayoutParams words=new LinearLayout.LayoutParams(0,-2,1);words.leftMargin=dp(14);brand.addView(wordmark,words);
        TextView title=text("FidoParole Reader",25,INK);title.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));wordmark.addView(title);
        wordmark.addView(text("Deine Passwörter. Auf deinem Key.",13,MUTED));root.addView(brand);
        LinearLayout chips=row();chips.addView(chip("USB-C"));chips.addView(chip("READ ONLY"));root.addView(chips);space(root,20);

        LinearLayout tokenCard=card();root.addView(tokenCard);tokenCard.addView(overline("DEIN SECURITY KEY"));space(tokenCard,12);
        LinearLayout deviceRow=row();deviceBadge=new View(this);LinearLayout.LayoutParams dot=new LinearLayout.LayoutParams(dp(9),dp(9));dot.rightMargin=dp(12);deviceRow.addView(deviceBadge,dot);deviceBadge.setBackground(round(MUTED,20,0));
        LinearLayout deviceLabels=column();deviceText=text("Token2 PIN+ Dual",20,INK);deviceText.setTypeface(Typeface.DEFAULT,Typeface.BOLD);deviceLabels.addView(deviceText);deviceDetail=text("Direkt per USB-C verbinden",13,MUTED);deviceLabels.addView(deviceDetail);deviceRow.addView(deviceLabels,new LinearLayout.LayoutParams(0,-2,1));tokenCard.addView(deviceRow);space(tokenCard,18);
        connect=button("Security Key verbinden",false);tokenCard.addView(connect,new LinearLayout.LayoutParams(-1,dp(50)));connect.setOnClickListener(v->findToken());space(root,14);

        unlockPanel=card();root.addView(unlockPanel);unlockPanel.addView(overline("VAULT ENTSPERREN"));space(unlockPanel,14);
        pin=new EditText(this);pin.setHint("Security-Key-PIN");pin.setTextSize(18);pin.setTextColor(INK);pin.setHintTextColor(MUTED);pin.setSingleLine(true);pin.setPadding(dp(16),dp(12),dp(16),dp(12));pin.setBackground(round(SURFACE,8,BORDER));pin.setBackgroundTintList(null);
        pin.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);pin.setSaveEnabled(false);pin.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
        pin.setImeOptions(android.view.inputmethod.EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING|android.view.inputmethod.EditorInfo.IME_ACTION_DONE);unlockPanel.addView(pin,new LinearLayout.LayoutParams(-1,dp(56)));space(unlockPanel,14);
        unlock=button("Entsperren",true);unlock.setEnabled(false);unlockPanel.addView(unlock,new LinearLayout.LayoutParams(-1,dp(52)));unlock.setOnClickListener(v->startUnlock());
        pin.setOnEditorActionListener((v,action,event)->{if(action==android.view.inputmethod.EditorInfo.IME_ACTION_DONE){startUnlock();return true;}return false;});space(unlockPanel,12);
        unlockPanel.addView(text("PIN eingeben und den Key berühren, sobald er blinkt.",13,MUTED));

        status=text("Bereit. Verbinde deinen Security Key.",14,MUTED);status.setPadding(dp(4),dp(18),dp(4),dp(18));status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);root.addView(status);
        vaultHeader=row();vaultHeader.setPadding(0,dp(2),0,dp(14));vaultCount=text("Deine Einträge",21,INK);vaultCount.setTypeface(Typeface.DEFAULT,Typeface.BOLD);vaultHeader.addView(vaultCount,new LinearLayout.LayoutParams(0,-2,1));
        Button lock=button("Sperren",false);vaultHeader.addView(lock,new LinearLayout.LayoutParams(dp(106),dp(46)));lock.setOnClickListener(v->{lock("Vault gesperrt.");clearOwnClipboard();});vaultHeader.setVisibility(View.GONE);root.addView(vaultHeader);
        entries=column();root.addView(entries);
        space(root,14);TextView footer=text("Inoffizieller Community-Reader · 0.1.2-test\nVerwaltung & Backup in FidoParole Desktop",12,MUTED);footer.setGravity(Gravity.CENTER);footer.setLineSpacing(dp(3),1);root.addView(footer);
        IntentFilter filter=new IntentFilter(USB_PERMISSION);filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        registerReceiver(receiver,filter,Context.RECEIVER_NOT_EXPORTED);receiverRegistered=true;
    }
    int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
    LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    void space(LinearLayout parent,int height){parent.addView(new View(this),new LinearLayout.LayoutParams(1,dp(height)));}
    GradientDrawable round(int color,int radius,int stroke){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));if(stroke!=0)d.setStroke(dp(1),stroke);return d;}
    LinearLayout card(){LinearLayout c=column();c.setPadding(dp(20),dp(20),dp(20),dp(20));c.setBackground(round(SURFACE,12,BORDER));return c;}
    TextView text(String value,int size,int color){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);t.setFontFeatureSettings("kern");return t;}
    TextView overline(String label){TextView t=text(label,11,MUTED);t.setLetterSpacing(0.12f);t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;}
    TextView chip(String label){TextView t=text(label,10,0xff5b636e);t.setLetterSpacing(0.08f);t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);t.setPadding(dp(12),dp(6),dp(12),dp(6));t.setBackground(round(0xffeef1f5,20,0));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,-2);lp.rightMargin=dp(8);t.setLayoutParams(lp);return t;}
    Button button(String label,boolean primary){
        Button b=new Button(this);b.setText(label);b.setTextSize(14);b.setAllCaps(false);b.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));b.setMinHeight(dp(48));b.setMinWidth(0);b.setMinimumWidth(0);b.setPadding(dp(12),0,dp(12),0);b.setStateListAnimator(null);b.setBackgroundTintList(null);
        int[][] states=new int[][]{new int[]{-android.R.attr.state_enabled},new int[]{}};
        b.setTextColor(new ColorStateList(states,new int[]{0xff8e96a2,primary?Color.WHITE:INK}));
        StateListDrawable backgrounds=new StateListDrawable();backgrounds.addState(states[0],round(0xffe4e7eb,8,0));backgrounds.addState(states[1],round(primary?ACCENT:0xffffffff,8,primary?0:BORDER));
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x22f23058),backgrounds,round(Color.WHITE,12,0)));return b;
    }
    void showUnlocked(boolean open){unlockPanel.setVisibility(open?View.GONE:View.VISIBLE);connect.setVisibility(open?View.GONE:View.VISIBLE);vaultHeader.setVisibility(open?View.VISIBLE:View.GONE);}
    void findToken(){
        lock("Security Key suchen …");selected=null;unlock.setEnabled(false);deviceDetail.setText("Nicht verbunden");deviceBadge.setBackground(round(MUTED,20,0));List<UsbDevice> found=new ArrayList<>();
        for(UsbDevice d:usb.getDeviceList().values())if(d.getVendorId()==0x349e&&d.getProductId()==0x0024)found.add(d);
        if(found.size()!=1){status.setText(found.isEmpty()?"Kein Security Key gefunden. Bitte USB-C-Verbindung prüfen.":"Bitte nur einen Token2 anschließen.");return;}
        selected=found.get(0);deviceText.setText("Token2 PIN+ Dual");deviceDetail.setText("USB-C · Zugriff freigeben");
        if(usb.hasPermission(selected)){status.setText("Verbunden. Gib die PIN deines Security Keys ein.");deviceDetail.setText("USB-C · Verbunden");deviceBadge.setBackground(round(0xff59d3a6,20,0));unlock.setEnabled(true);}
        else {PendingIntent permission=PendingIntent.getBroadcast(this,0,new Intent(USB_PERMISSION).setPackage(getPackageName()),PendingIntent.FLAG_MUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);usb.requestPermission(selected,permission);}
    }
    void startUnlock(){
        if(busy||!foreground||selected==null||!usb.hasPermission(selected))return;
        char[] chars=new char[pin.length()];pin.getText().getChars(0,chars.length,chars,0);
        ByteBuffer encoded=StandardCharsets.UTF_8.encode(CharBuffer.wrap(chars));byte[] value=new byte[encoded.remaining()];encoded.get(value);if(encoded.hasArray())Arrays.fill(encoded.array(),(byte)0);Arrays.fill(chars,'\0');pin.setText("");
        if(value.length<4||value.length>63){Crypto.wipe(value);status.setText("PIN-Länge muss 4–63 UTF-8 Bytes betragen.");return;}
        ((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(pin.getWindowToken(),0);
        lock("Verbinde mit Security Key …");final int operation=++generation;busy=true;unlock.setEnabled(false);connect.setEnabled(false);pin.setEnabled(false);
        final UsbIo io;
        try{io=new UsbIo(usb,selected);transport=io;}catch(Exception e){Crypto.wipe(value);busy=false;unlock.setEnabled(true);connect.setEnabled(true);pin.setEnabled(true);status.setText(e.getMessage());return;}
        worker.execute(()->{
            List<Reader.Vault> vaults=null;List<Entry> decoded=new ArrayList<>();Exception failure=null;
            try(Hid hid=new Hid(io)){
                Reader reader=new Reader(hid,msg->main.post(()->{if(generation==operation)status.setText(msg);}));
                vaults=reader.unlock(value);
                for(Reader.Vault vault:vaults){
                    JSONObject doc=new JSONObject(new String(vault.json,StandardCharsets.UTF_8));
                    boolean compact=doc.has("v");int version=compact?doc.getInt("v"):doc.getInt("version");
                    if((compact&&version!=2)||(!compact&&version!=1))throw new Exception("Unbekanntes Vault-Format");
                    JSONArray list=doc.getJSONArray(compact?"e":"entries");if(list.length()>256)throw new Exception("Zu viele Einträge");
                    for(int i=0;i<list.length();i++){JSONObject item=list.getJSONObject(i);String title=item.optString(compact?"t":"title","Eintrag");decoded.add(new Entry(vault.name,title,item.optString(compact?"u":"username",""),item.optString(compact?"p":"password","")));}
                }
            }catch(Exception e){failure=e;}finally{Crypto.wipe(value);if(vaults!=null)for(Reader.Vault v:vaults)v.close();}
            final Exception error=failure;
            main.post(()->{
                if(generation!=operation||!foreground){for(Entry e:decoded)e.close();return;}
                transport=null;busy=false;unlock.setEnabled(selected!=null&&usb.hasPermission(selected));connect.setEnabled(true);pin.setEnabled(true);
                if(error!=null){for(Entry e:decoded)e.close();status.setText(error instanceof javax.crypto.AEADBadTagException?"Entschlüsselung fehlgeschlagen: Format oder Schlüssel stimmt nicht.":safeError(error));return;}
                secrets.addAll(decoded);showEntries();showUnlocked(true);deviceDetail.setText("USB-C · Vault entsperrt");status.setText("Entsperrt. Wähle einen Eintrag zum Anzeigen oder Kopieren.");main.postDelayed(autoLock,60000);
            });
        });
    }
    String safeError(Exception e){String m=e.getMessage();if(m==null)return "Fehler: "+e.getClass().getSimpleName();if(e instanceof JSONException)return "Vault-JSON ungültig";return m.length()>250?"Protokollfehler: "+e.getClass().getSimpleName():m;}
    void clearPasswordViews(){for(TextView view:passwordViews){view.setText("");view.setVisibility(View.GONE);}passwordViews.clear();}
    void showEntries(){clearPasswordViews();entries.removeAllViews();vaultCount.setText(secrets.size()==1?"1 Eintrag":secrets.size()+" Einträge");for(Entry entry:secrets){
        LinearLayout card=card();LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(14);entries.addView(card,lp);
        card.addView(overline(entry.vault.isEmpty()?"VAULT":entry.vault));space(card,8);
        TextView entryTitle=text(entry.title.isEmpty()?"Ohne Titel":entry.title,22,INK);entryTitle.setTypeface(Typeface.DEFAULT,Typeface.BOLD);card.addView(entryTitle);
        if(!entry.user.isEmpty()){space(card,4);card.addView(text(entry.user,14,MUTED));}space(card,16);
        TextView password=text("",18,INK);password.setTypeface(Typeface.MONOSPACE);password.setSaveEnabled(false);password.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
        password.setPadding(dp(14),dp(12),dp(14),dp(12));password.setBackground(round(SURFACE,8,BORDER));password.setVisibility(View.GONE);card.addView(password);passwordViews.add(password);
        LinearLayout actions=row();LinearLayout.LayoutParams actionRow=new LinearLayout.LayoutParams(-1,-2);actionRow.topMargin=dp(12);card.addView(actions,actionRow);
        Button reveal=button("Anzeigen",false);reveal.setContentDescription("Passwort anzeigen: "+entry.title);LinearLayout.LayoutParams half=new LinearLayout.LayoutParams(0,dp(50),1);half.rightMargin=dp(10);actions.addView(reveal,half);
        reveal.setOnClickListener(v->{
            if(!foreground||!secrets.contains(entry))return;
            if(password.getVisibility()==View.VISIBLE){password.setText("");password.setVisibility(View.GONE);reveal.setText("Anzeigen");reveal.setContentDescription("Passwort anzeigen: "+entry.title);}
            else{password.setText(entry.password,0,entry.password.length);password.setVisibility(View.VISIBLE);reveal.setText("Ausblenden");reveal.setContentDescription("Passwort ausblenden: "+entry.title);}
            main.removeCallbacks(autoLock);main.postDelayed(autoLock,60000);
        });
        Button copy=button("Kopieren",true);copy.setContentDescription("Passwort kopieren: "+entry.title);actions.addView(copy,new LinearLayout.LayoutParams(0,dp(50),1));copy.setOnClickListener(v->copy(entry));
    }}
    void copy(Entry entry){
        if(!foreground||!secrets.contains(entry))return;
        ClipboardManager cb=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);clipboardLabel="FidoReader-"+UUID.randomUUID();
        ClipData data=ClipData.newPlainText(clipboardLabel,new String(entry.password));PersistableBundle extras=new PersistableBundle();extras.putBoolean("android.content.extra.IS_SENSITIVE",true);data.getDescription().setExtras(extras);cb.setPrimaryClip(data);
        clipboardExpiry=SystemClock.elapsedRealtime()+20000;main.removeCallbacks(clearClipboard);main.postDelayed(clearClipboard,20000);
        status.setText("Passwort kopiert · Jetzt zur Ziel-App wechseln");main.removeCallbacks(autoLock);main.postDelayed(autoLock,60000);
    }
    void clearOwnClipboard(){
        if(clipboardLabel==null)return;ClipboardManager cb=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
        try{ClipDescription desc=cb.getPrimaryClipDescription();if(desc!=null&&clipboardLabel.contentEquals(desc.getLabel())){cb.clearPrimaryClip();clipboardLabel=null;}else if(foreground)clipboardLabel=null;}catch(SecurityException ignored){}
    }
    void lock(String message){generation++;main.removeCallbacks(autoLock);if(transport!=null){transport.close();transport=null;}busy=false;
        clearPasswordViews();for(Entry e:secrets)e.close();secrets.clear();if(entries!=null)entries.removeAllViews();if(pin!=null){pin.setText("");pin.setEnabled(true);}if(connect!=null)connect.setEnabled(true);if(unlock!=null)unlock.setEnabled(selected!=null&&usb.hasPermission(selected));if(status!=null)status.setText(message);if(unlockPanel!=null)showUnlocked(false);if(deviceDetail!=null&&selected!=null)deviceDetail.setText("USB-C · Vault gesperrt");
    }
    @Override protected void onResume(){super.onResume();foreground=true;if(clipboardLabel!=null&&SystemClock.elapsedRealtime()>=clipboardExpiry)clearOwnClipboard();}
    @Override protected void onPause(){foreground=false;lock("Gesperrt · App im Hintergrund");super.onPause();}
    @Override protected void onDestroy(){lock("Gesperrt");clearOwnClipboard();main.removeCallbacksAndMessages(null);worker.shutdownNow();if(receiverRegistered)unregisterReceiver(receiver);super.onDestroy();}
}
