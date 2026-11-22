package br.com.playerprogramado;

import android.app.*;
import android.content.*;
import android.media.*;
import android.net.Uri;
import android.os.*;
import java.io.IOException;

public class PlayerService extends Service {
    static final String ACTION_PLAY="PLAY", ACTION_STOP="STOP", ACTION_STOP_AT_END="STOP_AT_END", ACTION_TONE="TONE";
    private boolean stopAtTrackEnd=false;
    private MediaPlayer player;
    private AudioManager audioManager;
    private AudioFocusRequest focusRequest;
    private final AudioManager.OnAudioFocusChangeListener focusListener = change -> {
        // Mantém a reprodução durante alertas e notificações breves.
    };
    private static final String CHANNEL="player_programado";

    @Override public void onCreate(){super.onCreate();audioManager=(AudioManager)getSystemService(AUDIO_SERVICE);createChannel();}
    private void requestAudioFocus(){
        if(audioManager==null)return;
        if(Build.VERSION.SDK_INT>=26){
            focusRequest=new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                .setOnAudioFocusChangeListener(focusListener).setWillPauseWhenDucked(false).build();
            audioManager.requestAudioFocus(focusRequest);
        }else{
            audioManager.requestAudioFocus(focusListener,AudioManager.STREAM_MUSIC,AudioManager.AUDIOFOCUS_GAIN);
        }
    }
    private void abandonAudioFocus(){
        if(audioManager==null)return;
        if(Build.VERSION.SDK_INT>=26 && focusRequest!=null)audioManager.abandonAudioFocusRequest(focusRequest);
        else audioManager.abandonAudioFocus(focusListener);
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        String action=intent==null?ACTION_STOP:intent.getAction();
        if(ACTION_STOP.equals(action)){stopAtTrackEnd=false;stopPlayback();stopForeground(true);stopSelf();return START_NOT_STICKY;}
        if(ACTION_STOP_AT_END.equals(action)){if(player==null){stopForeground(true);stopSelf();}else stopAtTrackEnd=true;return START_NOT_STICKY;}
        if(ACTION_TONE.equals(action)){applyTone();return START_NOT_STICKY;}
        startForeground(1,notification());
        startPlayback();
        return START_STICKY;
    }
    private void startPlayback(){
        stopPlayback();
        requestAudioFocus();
        String raw=getSharedPreferences("player",MODE_PRIVATE).getString("audio","");
        if(raw.isEmpty()){stopForeground(true);stopSelf();return;}
        try{
            player=new MediaPlayer();
            player.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());
            player.setDataSource(this,Uri.parse(raw));
            player.setLooping(false);
            stopAtTrackEnd=false;
            player.setOnErrorListener((mp,what,extra)->{stopPlayback();stopForeground(true);stopSelf();return true;});
            player.setOnPreparedListener(mp->{applyTone();mp.start();});
            player.setOnCompletionListener(mp->{if(stopAtTrackEnd){stopAtTrackEnd=false;stopPlayback();stopForeground(true);stopSelf();}else{try{mp.seekTo(0);mp.start();}catch(Exception e){stopPlayback();stopForeground(true);stopSelf();}}});
            player.prepareAsync();
        }catch(IOException|RuntimeException e){stopPlayback();stopForeground(true);stopSelf();}
    }
    private void applyTone(){
        if(player==null || Build.VERSION.SDK_INT<23)return;
        int value=getSharedPreferences("player",MODE_PRIVATE).getInt("tone",50);
        float pitch=0.5f+(value/100.0f);
        try{player.setPlaybackParams(player.getPlaybackParams().setPitch(pitch));}catch(Exception ignored){}
    }
    private void stopPlayback(){if(player!=null){try{if(player.isPlaying())player.stop();}catch(Exception ignored){} try{player.release();}catch(Exception ignored){} player=null;} abandonAudioFocus();}
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
