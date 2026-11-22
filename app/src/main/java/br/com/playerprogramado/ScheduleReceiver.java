package br.com.playerprogramado;

import android.app.*;
import android.content.*;
import android.os.Build;
import java.util.*;
import org.json.*;

public class ScheduleReceiver extends BroadcastReceiver {
    static final String ACTION_START="br.com.playerprogramado.START";
    static final String ACTION_STOP="br.com.playerprogramado.STOP";

    @Override public void onReceive(Context context, Intent intent) {
        Intent service=new Intent(context,PlayerService.class);
        service.setAction(ACTION_START.equals(intent.getAction())?PlayerService.ACTION_PLAY:PlayerService.ACTION_STOP);
        if(Build.VERSION.SDK_INT>=26) context.startForegroundService(service); else context.startService(service);
        int id=intent.getIntExtra("schedule_id",0);
        if(ACTION_START.equals(intent.getAction())) scheduleNext(context,id,true);
        else scheduleNext(context,id,false);
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
            int index=id/2;if(index>=a.length())return;
            JSONObject o=a.getJSONObject(index);
            String time=o.getString(start?"start":"end");
            AlarmManager am=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);
            schedule(c,am,id,parse(time),start?ACTION_START:ACTION_STOP);
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
