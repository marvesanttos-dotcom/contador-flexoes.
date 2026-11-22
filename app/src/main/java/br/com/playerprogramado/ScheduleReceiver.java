package br.com.playerprogramado;

import android.app.*;
import android.content.*;
import android.os.Build;
import android.os.SystemClock;
import java.util.*;
import org.json.*;

public class ScheduleReceiver extends BroadcastReceiver {
    static final String ACTION_START="br.com.playerprogramado.START";
    static final String ACTION_STOP="br.com.playerprogramado.STOP";
    static final String ACTION_CYCLE_ON="br.com.playerprogramado.CYCLE_ON";
    static final String ACTION_CYCLE_OFF="br.com.playerprogramado.CYCLE_OFF";
    static final int CYCLE_REQUEST=22001;

    @Override public void onReceive(Context context, Intent intent) {
        String action=intent.getAction();
        if(ACTION_CYCLE_ON.equals(action)){
            if(!enabled(context))return;
            startPlayer(context,PlayerService.ACTION_PLAY);
            scheduleCycle(context,ACTION_CYCLE_OFF,minutes(context,"cycle_on",60));
            return;
        }
        if(ACTION_CYCLE_OFF.equals(action)){
            if(!enabled(context))return;
            startPlayer(context,PlayerService.ACTION_STOP_AT_END);
            scheduleCycle(context,ACTION_CYCLE_ON,minutes(context,"cycle_off",60));
            return;
        }
        // A programação diária agora ativa e desativa o temporizador cíclico.
        if(ACTION_START.equals(action)) startCycle(context);
        else stopCycle(context);
        int id=intent.getIntExtra("schedule_id",0);
        if(ACTION_START.equals(action)) scheduleNext(context,id,true);
        else scheduleNext(context,id,false);
    }

    private static void startPlayer(Context c,String action){
        Intent i=new Intent(c,PlayerService.class);i.setAction(action);
        if(Build.VERSION.SDK_INT>=26)c.startForegroundService(i);else c.startService(i);
    }
    private static boolean enabled(Context c){return c.getSharedPreferences("player",Context.MODE_PRIVATE).getBoolean("cycle_enabled",false);}
    private static int minutes(Context c,String key,int fallback){return c.getSharedPreferences("player",Context.MODE_PRIVATE).getInt(key,fallback);}
    static void startCycle(Context c){
        c.getSharedPreferences("player",Context.MODE_PRIVATE).edit().putBoolean("cycle_enabled",true).apply();
        startPlayer(c,PlayerService.ACTION_PLAY);
        scheduleCycle(c,ACTION_CYCLE_OFF,minutes(c,"cycle_on",60));
    }
    static void stopCycle(Context c){
        c.getSharedPreferences("player",Context.MODE_PRIVATE).edit().putBoolean("cycle_enabled",false).apply();
        AlarmManager am=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);
        am.cancel(cyclePending(c,ACTION_CYCLE_ON));am.cancel(cyclePending(c,ACTION_CYCLE_OFF));
        startPlayer(c,PlayerService.ACTION_STOP);
    }
    static void restoreCycle(Context c){
        if(enabled(c))scheduleCycle(c,ACTION_CYCLE_ON,1);
    }
    private static PendingIntent cyclePending(Context c,String action){
        Intent i=new Intent(c,ScheduleReceiver.class);i.setAction(action);
        return PendingIntent.getBroadcast(c,CYCLE_REQUEST+(ACTION_CYCLE_OFF.equals(action)?1:0),i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }
    private static void scheduleCycle(Context c,String action,int mins){
        AlarmManager am=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);
        long when=SystemClock.elapsedRealtime()+mins*60L*1000L;
        PendingIntent pi=cyclePending(c,action);
        if(Build.VERSION.SDK_INT>=31 && !am.canScheduleExactAlarms()) am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP,when,pi);
        else if(Build.VERSION.SDK_INT>=23)am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP,when,pi);
        else am.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP,when,pi);
    }

    static void rescheduleAll(Context c) {
        SharedPreferences p=c.getSharedPreferences("player",Context.MODE_PRIVATE);
        try {
            JSONArray a=new JSONArray(p.getString("schedules","[]"));
            AlarmManager am=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);
            for(int i=0;i<a.length();i++){
                JSONObject o=a.getJSONObject(i);
                schedule(c,am,i*2,parse(o.getString("start")),ACTION_START);
                schedule(c,am,i*2+1,parse(o.getString("end")),ACTION_STOP);
            }
        } catch(Exception ignored){}
    }
    private static int parse(String s){String[] p=s.split(":");return Integer.parseInt(p[0])*60+Integer.parseInt(p[1]);}
    private static void scheduleNext(Context c,int id,boolean start){
        SharedPreferences p=c.getSharedPreferences("player",Context.MODE_PRIVATE);
        try{
            JSONArray a=new JSONArray(p.getString("schedules","[]"));
            int index=id;if(index>=a.length())return;
            JSONObject o=a.getJSONObject(index);
            String time=o.getString(start?"start":"end");
            AlarmManager am=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);
            schedule(c,am,index*2+(start?0:1),parse(time),start?ACTION_START:ACTION_STOP);
        }catch(Exception ignored){}
    }
    private static void schedule(Context c,AlarmManager am,int id,int minute,String action){
        Calendar cal=Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY,minute/60); cal.set(Calendar.MINUTE,minute%60); cal.set(Calendar.SECOND,0); cal.set(Calendar.MILLISECOND,0);
        if(cal.getTimeInMillis()<=System.currentTimeMillis()) cal.add(Calendar.DAY_OF_YEAR,1);
        Intent i=new Intent(c,ScheduleReceiver.class); i.setAction(action); i.putExtra("schedule_id",id/2);
        PendingIntent pi=PendingIntent.getBroadcast(c,id+(action.equals(ACTION_STOP)?10000:0),i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        if(Build.VERSION.SDK_INT>=31 && !am.canScheduleExactAlarms()) am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,cal.getTimeInMillis(),pi);
        else if(Build.VERSION.SDK_INT>=23) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,cal.getTimeInMillis(),pi);
        else am.setExact(AlarmManager.RTC_WAKEUP,cal.getTimeInMillis(),pi);
    }
}