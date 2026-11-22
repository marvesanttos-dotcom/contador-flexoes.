package br.com.playerprogramado;

import android.app.*;
import android.content.*;
import android.media.*;
import android.net.Uri;
import android.os.*;
import java.io.IOException;

public class PlayerService extends Service {
    static final String ACTION_PLAY="PLAY", ACTION_STOP="STOP";
    private MediaPlayer player;
    private static final String CHANNEL="player_programado";

    @Override public void onCreate(){super.onCreate();createChannel();}
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        String action=intent==null?ACTION_STOP:intent.getAction();
        if(ACTION_STOP.equals(action)){stopPlayback();stopForeground(true);stopSelf();return START_NOT_STICKY;}
        startForeground(1,notification());
        startPlayback();
        return START_STICKY;
    }
    private void startPlayback(){
        stopPlayback();
        String raw=getSharedPreferences("player",MODE_PRIVATE).getString("audio","");
        if(raw.isEmpty()){stopForeground(true);stopSelf();return;}
        try{
            player=new MediaPlayer();
            player.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());
            player.setDataSource(this,Uri.parse(raw));
            player.setLooping(true);
            player.setOnErrorListener((mp,what,extra)->{stopPlayback();stopForeground(true);stopSelf();return true;});
            player.setOnPreparedListener(MediaPlayer::start);
            player.prepareAsync();
        }catch(IOException|RuntimeException e){stopPlayback();stopForeground(true);stopSelf();}
    }
    private void stopPlayback(){if(player!=null){try{if(player.isPlaying())player.stop();}catch(Exception ignored){} try{player.release();}catch(Exception ignored){} player=null;}}
    private void createChannel(){
        if(Build.VERSION.SDK_INT>=26){NotificationChannel ch=new NotificationChannel(CHANNEL,"Player Programado",NotificationManager.IMPORTANCE_LOW);((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(ch);}
    }
    private Notification notification(){
        Intent stop=new Intent(this,PlayerService.class);stop.setAction(ACTION_STOP);
        PendingIntent pi=PendingIntent.getService(this,9,stop,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,CHANNEL):new Notification.Builder(this);
        return b.setContentTitle("Player Programado").setContentText("Reprodução programada em andamento").setSmallIcon(android.R.drawable.ic_media_play).addAction(android.R.drawable.ic_media_pause,"Parar",pi).setOngoing(true).build();
    }
    @Override public void onDestroy(){stopPlayback();super.onDestroy();}
    @Override public android.os.IBinder onBind(Intent intent){return null;}
}
