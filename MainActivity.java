package com.denben.pomodorokombat;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.media.MediaPlayer;
import android.os.*;
import android.view.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class MainActivity extends Activity {
    KombatView v;
    CountDownTimer timer;
    SharedPreferences sp;
    long remain = 25 * 60 * 1000L, total = remain, startedAt = 0;
    boolean running = false, ru = true, sound = true, vibration = true, amoled = true;
    int screen = 0, wins = 0, sessions = 0, rounds = 0, streak = 0, xp = 250, theme = 0, selectedMinutes = 25;
    long focusMs = 0;
    String lastDay = "";

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        sp = getSharedPreferences("pk_state", MODE_PRIVATE);
        load();
        channel();
        v = new KombatView(this);
        setContentView(v);
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 7);
        }
    }

    @Override protected void onPause(){ super.onPause(); save(); }
    @Override protected void onDestroy(){ super.onDestroy(); stop(false); save(); }

    void load(){
        ru = sp.getBoolean("ru", true); sound = sp.getBoolean("sound", true); vibration = sp.getBoolean("vibration", true); amoled = sp.getBoolean("amoled", true);
        wins = sp.getInt("wins", 0); sessions = sp.getInt("sessions", 0); rounds = sp.getInt("rounds", 0); streak = sp.getInt("streak", 0); xp = sp.getInt("xp", 250); theme = sp.getInt("theme", 0);
        focusMs = sp.getLong("focusMs", 0); lastDay = sp.getString("lastDay", "");
    }
    void save(){
        sp.edit().putBoolean("ru", ru).putBoolean("sound", sound).putBoolean("vibration", vibration).putBoolean("amoled", amoled)
                .putInt("wins", wins).putInt("sessions", sessions).putInt("rounds", rounds).putInt("streak", streak).putInt("xp", xp).putInt("theme", theme)
                .putLong("focusMs", focusMs).putString("lastDay", lastDay).apply();
    }
    void start(long minutes){
        stop(false); selectedMinutes = (int)minutes; total = minutes * 60 * 1000L; remain = total; running = true; startedAt = System.currentTimeMillis();
        play(R.raw.fight_start); buzz(70); tick();
    }
    void tick(){
        timer = new CountDownTimer(remain, 1000) {
            public void onTick(long ms){ remain = ms; v.invalidate(); }
            public void onFinish(){ finishRound(); }
        }.start();
    }
    void stop(boolean reset){ if(timer != null) timer.cancel(); running = false; if(reset) remain = total; }
    void finishRound(){
        running = false; remain = 0; sessions++; wins++; rounds++; xp += Math.max(50, selectedMinutes * 6); focusMs += total;
        updateStreak(); play(R.raw.round_win); buzz(350); notifyDone(); save(); v.invalidate();
    }
    void updateStreak(){
        String today = new SimpleDateFormat("yyyyMMdd", Locale.US).format(new Date());
        Calendar cal = Calendar.getInstance(); cal.add(Calendar.DATE, -1);
        String yesterday = new SimpleDateFormat("yyyyMMdd", Locale.US).format(cal.getTime());
        if(today.equals(lastDay)) return;
        streak = yesterday.equals(lastDay) ? Math.min(999, streak + 1) : 1;
        lastDay = today;
    }
    void play(int id){ if(!sound) return; try{ MediaPlayer mp = MediaPlayer.create(this, id); mp.setOnCompletionListener(MediaPlayer::release); mp.start(); }catch(Exception ignored){} }
    void buzz(long ms){ if(!vibration) return; try{ Vibrator vib = (Vibrator)getSystemService(VIBRATOR_SERVICE); if(vib==null)return; if(Build.VERSION.SDK_INT>=26) vib.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE)); else vib.vibrate(ms); }catch(Exception ignored){} }
    void channel(){ if(Build.VERSION.SDK_INT>=26){ NotificationChannel c = new NotificationChannel("pk", "Pomodoro Kombat", NotificationManager.IMPORTANCE_DEFAULT); c.setDescription("Round completion alerts"); getSystemService(NotificationManager.class).createNotificationChannel(c); } }
    void notifyDone(){
        Intent i = new Intent(this, MainActivity.class); PendingIntent pi = PendingIntent.getActivity(this, 0, i, Build.VERSION.SDK_INT>=23?PendingIntent.FLAG_IMMUTABLE:0);
        Notification.Builder nb = Build.VERSION.SDK_INT>=26 ? new Notification.Builder(this, "pk") : new Notification.Builder(this);
        nb.setSmallIcon(android.R.drawable.ic_lock_idle_alarm).setContentTitle("Pomodoro Kombat")
                .setContentText(ru ? "Раунд завершён. Победа!" : "Round complete. Victory!").setContentIntent(pi).setAutoCancel(true);
        ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(1, nb.build());
    }
    String t(String r,String e){return ru?r:e;}
    String rank(){ if(xp>=5000)return "GRANDMASTER"; if(xp>=2500)return "CHAMPION"; if(xp>=1000)return "KOMBATANT"; return "NOVICE"; }
    void exportStats(){
        String body = "Pomodoro Kombat stats\n" + "Rank: " + rank() + "\nXP: " + xp + "\nSessions: " + sessions + "\nFocus: " + (focusMs/3600000) + "h " + ((focusMs/60000)%60) + "m\nStreak: " + streak + "\nWins: " + wins;
        Intent send = new Intent(Intent.ACTION_SEND); send.setType("text/plain"); send.putExtra(Intent.EXTRA_SUBJECT, "Pomodoro Kombat Stats"); send.putExtra(Intent.EXTRA_TEXT, body); startActivity(Intent.createChooser(send, t("Экспорт статистики", "Export stats")));
    }

    class KombatView extends View {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG); RectF r = new RectF(); Random rnd = new Random(3); long born = System.currentTimeMillis();
        int red(){ return theme==1 ? Color.rgb(32,160,255) : Color.rgb(216,35,22); }
        int gold(){ return Color.rgb(232,172,36); }
        KombatView(Context c){ super(c); setFocusable(true); }
        protected void onDraw(Canvas c){ int w=getWidth(), h=getHeight(); drawBg(c,w,h); p.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)); if(screen==0) welcome(c,w,h); else if(screen==1) timer(c,w,h); else if(screen==2) sessions(c,w,h); else if(screen==3) stats(c,w,h); else missions(c,w,h); nav(c,w,h); }
        void drawBg(Canvas c,int w,int h){
            c.drawColor(amoled ? Color.BLACK : Color.rgb(7,6,5));
            p.setStrokeWidth(1); rnd.setSeed(2); for(int i=0;i<110;i++){ p.setColor(Color.argb(25,110+rnd.nextInt(90),90,55)); c.drawLine(rnd.nextInt(Math.max(w,1)),rnd.nextInt(Math.max(h,1)),rnd.nextInt(Math.max(w,1)),rnd.nextInt(Math.max(h,1)),p); }
            if(screen==1){ for(int i=0;i<2;i++) flame(c, 65+i*(w-130), h-190, 1.0f); }
        }
        void flame(Canvas c,float x,float y,float s){ float k=(System.currentTimeMillis()-born)%900/900f; p.setStyle(Paint.Style.FILL); p.setColor(Color.argb(200,255,70,10)); Path a=new Path(); a.moveTo(x,y); a.cubicTo(x-28*s,y-50*s,x-10*s,y-90*s,x,y-120*s-20*k); a.cubicTo(x+30*s,y-70*s,x+24*s,y-35*s,x,y); c.drawPath(a,p); p.setColor(Color.argb(220,255,190,30)); c.drawCircle(x,y-45*s-20*k,18*s,p); }
        void panel(Canvas c,float l,float t,float rr,float b){ r.set(l,t,rr,b); p.setStyle(Paint.Style.FILL); p.setColor(Color.rgb(14,12,10)); c.drawRoundRect(r,18,18,p); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(4); p.setColor(Color.rgb(105,82,54)); c.drawRoundRect(r,18,18,p); p.setStyle(Paint.Style.FILL); }
        void txt(Canvas c,String s,float x,float y,int size,int color,Paint.Align a){ p.setTextAlign(a); p.setTextSize(size); p.setColor(color); p.setStyle(Paint.Style.FILL); c.drawText(s,x,y,p); }
        void logo(Canvas c,float cx,float cy,float rad){ p.setColor(theme==1?Color.rgb(15,105,180):Color.rgb(210,75,18)); c.drawCircle(cx,cy,rad,p); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(rad/10); p.setColor(gold()); c.drawCircle(cx,cy,rad,p); p.setStyle(Paint.Style.FILL); p.setColor(Color.rgb(8,8,8)); Path path=new Path(); path.moveTo(cx-rad*.45f,cy); path.cubicTo(cx-rad*.1f,cy-rad*.55f,cx+rad*.5f,cy-rad*.35f,cx+rad*.35f,cy+rad*.15f); path.cubicTo(cx+rad*.1f,cy+rad*.1f,cx-rad*.05f,cy+rad*.4f,cx-rad*.5f,cy+rad*.25f); c.drawPath(path,p); }
        void welcome(Canvas c,int w,int h){ logo(c,w/2,165,82); txt(c,"POMODORO",w/2,315,48,red(),Paint.Align.CENTER); txt(c,"KOMBAT",w/2,370,44,gold(),Paint.Align.CENTER); txt(c,t("ПРИГОТОВЬСЯ","PREPARE YOURSELF"),w/2,480,28,red(),Paint.Align.CENTER); txt(c,t("ФОКУС. БОЙ. ФИНИШ.","FOCUS. FIGHT. FINISH."),w/2,535,25,Color.LTGRAY,Paint.Align.CENTER); button(c,w/2-115,650,w/2+115,718,t("ВОЙТИ","ENTER")); txt(c,"18+",w/2,h-90,24,red(),Paint.Align.CENTER); }
        void timer(Canvas c,int w,int h){ txt(c,t("ВОИН","WARRIOR"),55,68,22,gold(),Paint.Align.LEFT); txt(c,"WINS: "+wins,w-55,68,22,gold(),Paint.Align.RIGHT); logo(c,w/2,65,42); txt(c,selectedMinutes==50?"LONG SESSION":selectedMinutes==15?"SHORT SESSION":"FOCUS SESSION",w/2,150,30,red(),Paint.Align.CENTER); p.setColor(Color.rgb(31,28,23)); c.drawCircle(w/2,330,145,p); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(12); p.setColor(Color.rgb(100,82,60)); c.drawCircle(w/2,330,145,p); p.setColor(red()); p.setStrokeWidth(14); r.set(w/2-145,185,w/2+145,475); float sweep = total>0 ? 360f*(total-remain)/total : 360; c.drawArc(r,-90,sweep,false,p); p.setStyle(Paint.Style.FILL); long sec=remain/1000; txt(c,String.format(Locale.US,"%02d:%02d",sec/60,sec%60),w/2,350,72,red(),Paint.Align.CENTER); txt(c,"ROUND "+(rounds+1),w/2,410,28,gold(),Paint.Align.CENTER); button(c,w/2-130,620,w/2+130,700,running?t("СТОП","STOP"):"FIGHT!"); txt(c,t("Нажми Missions для выбора режима","Tap Missions to choose session"),w/2,750,18,Color.GRAY,Paint.Align.CENTER); }
        void sessions(Canvas c,int w,int h){ txt(c,"SESSIONS",w/2,70,32,gold(),Paint.Align.CENTER); txt(c,t("ВЫБЕРИ БОЙ","CHOOSE YOUR FIGHT"),w/2,135,26,red(),Paint.Align.CENTER); item(c,60,185,w-60,305,"FOCUS SESSION","25 MIN",t("Держи фокус","Stay focused")); item(c,60,330,w-60,450,"SHORT SESSION","15 MIN",t("Быстрый фокус","Quick focus")); item(c,60,475,w-60,595,"LONG SESSION","50 MIN",t("Глубокая работа","Deep work")); item(c,60,620,w-60,740,"CUSTOM","5-90 MIN",t("Каждый тап +5 мин","Tap to add 5 min")); }
        void item(Canvas c,float l,float t,float rr,float b,String a,String m,String d){ panel(c,l,t,rr,b); logo(c,l+60,(t+b)/2,36); txt(c,a,l+120,t+45,24,gold(),Paint.Align.LEFT); txt(c,m,l+120,t+78,20,Color.WHITE,Paint.Align.LEFT); txt(c,d,l+120,t+108,18,Color.LTGRAY,Paint.Align.LEFT); txt(c,"›",rr-35,(t+b)/2+10,46,Color.rgb(210,170,92),Paint.Align.CENTER); }
        void stats(Canvas c,int w,int h){ txt(c,"STATS",w/2,70,32,gold(),Paint.Align.CENTER); panel(c,45,115,w-45,250); logo(c,110,180,45); txt(c,t("ВОИН","WARRIOR"),170,160,25,gold(),Paint.Align.LEFT); txt(c,rank(),170,195,22,Color.WHITE,Paint.Align.LEFT); txt(c,xp+" XP",170,230,20,Color.LTGRAY,Paint.Align.LEFT); panel(c,45,285,w-45,525); txt(c,"OVERALL STATS",w/2,330,25,red(),Paint.Align.CENTER); txt(c,t("Сессии: ","Sessions: ")+sessions,90,380,22,Color.WHITE,Paint.Align.LEFT); txt(c,t("Фокус: ","Focus: ")+(focusMs/3600000)+"h "+((focusMs/60000)%60)+"m",90,425,22,Color.WHITE,Paint.Align.LEFT); txt(c,t("Серия: ","Streak: ")+streak,90,470,22,Color.WHITE,Paint.Align.LEFT); txt(c,t("Победы: ","Wins: ")+wins,90,515,22,Color.WHITE,Paint.Align.LEFT); panel(c,45,560,w-45,675); txt(c,"STREAK",w/2,605,25,red(),Paint.Align.CENTER); for(int i=1;i<=7;i++){ txt(c,i<=Math.min(7,streak)?"☠":"○",65+i*45,650,30,i<=Math.min(7,streak)?gold():Color.DKGRAY,Paint.Align.CENTER);} button(c,70,710,w-70,780,t("ЭКСПОРТ СТАТИСТИКИ","EXPORT STATS")); }
        void missions(Canvas c,int w,int h){ txt(c,"SETTINGS",w/2,70,32,gold(),Paint.Align.CENTER); row(c,60,135,w-60,205,t("Язык","Language"),ru?"RU":"EN"); row(c,60,225,w-60,295,t("Тема","Theme"),theme==0?"SCORPION":"SUB-ZERO"); row(c,60,315,w-60,385,t("Звук","Sound"),sound?"ON":"OFF"); row(c,60,405,w-60,475,t("Вибрация","Vibration"),vibration?"ON":"OFF"); row(c,60,495,w-60,565,"AMOLED",amoled?"ON":"OFF"); panel(c,60,600,w-60,760); txt(c,t("МИССИИ","MISSIONS"),w/2,645,25,red(),Paint.Align.CENTER); txt(c,t("1. Заверши 1 раунд сегодня","1. Finish 1 round today"),90,690,20,Color.WHITE,Paint.Align.LEFT); txt(c,t("2. Набери серию 5 дней","2. Reach 5-day streak"),90,730,20,Color.WHITE,Paint.Align.LEFT); }
        void row(Canvas c,float l,float t,float rr,float b,String name,String val){ panel(c,l,t,rr,b); txt(c,name,l+25,t+45,22,Color.WHITE,Paint.Align.LEFT); txt(c,val,rr-25,t+45,22,gold(),Paint.Align.RIGHT); }
        void button(Canvas c,float l,float t,float rr,float b,String s){ panel(c,l,t,rr,b); txt(c,s,(l+rr)/2,t+48,28,gold(),Paint.Align.CENTER); }
        void nav(Canvas c,int w,int h){ if(screen==0)return; float y=h-78,bw=w/4f; String[] ns={"TIMER","STATS","MISSIONS","SET"}; for(int i=0;i<4;i++){ panel(c,i*bw,y,(i+1)*bw,y+78); txt(c,ns[i],i*bw+bw/2,y+50,16,i==screen-1?red():gold(),Paint.Align.CENTER); } }
        public boolean onTouchEvent(MotionEvent e){ if(e.getAction()!=MotionEvent.ACTION_UP)return true; float x=e.getX(), y=e.getY(); play(R.raw.button_hit); buzz(25);
            if(screen==0){ screen=1; invalidate(); return true; }
            if(y>getHeight()-90){ int idx=(int)(x/(getWidth()/4f)); screen=idx+1; invalidate(); return true; }
            if(screen==1 && y>590 && y<730){ if(running) stop(false); else start(selectedMinutes); invalidate(); return true; }
            if(screen==2){ if(y>185&&y<305){selectedMinutes=25; start(25); screen=1;} else if(y>330&&y<450){selectedMinutes=15; start(15); screen=1;} else if(y>475&&y<595){selectedMinutes=50; start(50); screen=1;} else if(y>620&&y<740){selectedMinutes += 5; if(selectedMinutes>90) selectedMinutes=5;} invalidate(); return true; }
            if(screen==3 && y>700 && y<790){ exportStats(); return true; }
            if(screen==4){ if(y>135&&y<205)ru=!ru; else if(y>225&&y<295)theme=1-theme; else if(y>315&&y<385)sound=!sound; else if(y>405&&y<475)vibration=!vibration; else if(y>495&&y<565)amoled=!amoled; save(); invalidate(); return true; }
            return true; }
    }
}
