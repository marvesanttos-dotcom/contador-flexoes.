package br.com.playerprogramado;

import android.app.*;
import android.os.*;
import android.content.*;
import android.net.Uri;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.*;
import java.util.*;
import org.json.*;

public class MainActivity extends Activity {
    private static final int PICK_AUDIO = 10;
    private final ArrayList<Schedule> schedules = new ArrayList<>();
    private SharedPreferences prefs;
    private Uri audioUri;
    private EditText startField, endField, onMinutesField, offMinutesField;
    private TextView musicText, listText;

    static class Schedule {
        String start, end;
        Schedule(String a, String b) { start=a; end=b; }
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs=getSharedPreferences("player",MODE_PRIVATE);
        String saved=prefs.getString("audio","");
        if(!saved.isEmpty()) audioUri=Uri.parse(saved);
        loadSchedules();
        buildScreen();
        ScheduleReceiver.rescheduleAll(this);
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != android.content.pm.PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 21);
    }

    private void buildScreen() {
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24,24,24,24);
        root.setBackgroundColor(0xFFF3F5F8);
        TextView title=new TextView(this);
        title.setText("PLAYER PROGRAMADO"); title.setTextSize(23); title.setTextColor(0xFF14213D); title.setGravity(Gravity.CENTER);
        root.addView(title,new LinearLayout.LayoutParams(-1,-2));
        TextView subtitle=new TextView(this); subtitle.setText("Sua música nos horários escolhidos"); subtitle.setGravity(Gravity.CENTER); subtitle.setPadding(0,6,0,18);
        root.addView(subtitle);
        TextView timerTitle=new TextView(this); timerTitle.setText("TEMPORIZADOR CICLICO (1 A 250 MINUTOS)"); timerTitle.setTextSize(18); timerTitle.setTextColor(0xFF14213D); timerTitle.setPadding(0,8,0,4); root.addView(timerTitle);
        onMinutesField=new EditText(this); onMinutesField.setSingleLine(true); onMinutesField.setInputType(2); onMinutesField.setHint("Minutos ligado"); onMinutesField.setText(String.valueOf(prefs.getInt("cycle_on",60))); root.addView(onMinutesField);
        offMinutesField=new EditText(this); offMinutesField.setSingleLine(true); offMinutesField.setInputType(2); offMinutesField.setHint("Minutos desligado"); offMinutesField.setText(String.valueOf(prefs.getInt("cycle_off",60))); root.addView(offMinutesField);
        TextView toneTitle=new TextView(this); toneTitle.setText("GROSSURA DO ÁUDIO"); toneTitle.setTextSize(18); toneTitle.setTextColor(0xFF14213D); toneTitle.setPadding(0,12,0,2); root.addView(toneTitle);
        TextView toneHint=new TextView(this); toneHint.setText("Mais grosso  ←  50% neutro  →  Mais fino"); root.addView(toneHint);
        SeekBar toneControl=new SeekBar(this); toneControl.setMax(100); toneControl.setProgress(prefs.getInt("tone",50)); root.addView(toneControl);
        toneControl.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar bar,int value,boolean fromUser){ if(fromUser){ prefs.edit().putInt("tone",value).apply(); Intent i=new Intent(MainActivity.this,PlayerService.class); i.setAction(PlayerService.ACTION_TONE); startService(i); } }
            public void onStartTrackingTouch(SeekBar bar){}
            public void onStopTrackingTouch(SeekBar bar){}
        });
        Button pick=new Button(this); pick.setText("ESCOLHER MÚSICA"); pick.setOnClickListener(v->{
            Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT); i.setType("audio/*"); i.addCategory(Intent.CATEGORY_OPENABLE); i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION); startActivityForResult(i,PICK_AUDIO);
        }); root.addView(pick);
        musicText=new TextView(this); musicText.setPadding(4,10,4,16); root.addView(musicText); updateMusicText();
        TextView heading=new TextView(this); heading.setText("PROGRAMAÇÃO DIÁRIA"); heading.setTextSize(18); heading.setTextColor(0xFF14213D); root.addView(heading);
        startField=new EditText(this); startField.setSingleLine(true); startField.setHint("Hora de ligar (HH:MM)"); startField.setText("07:00"); startField.setInputType( android.text.InputType.TYPE_CLASS_DATETIME | android.text.InputType.TYPE_DATETIME_VARIATION_TIME); root.addView(startField);
        endField=new EditText(this); endField.setSingleLine(true); endField.setHint("Hora de desligar (HH:MM)"); endField.setText("19:00"); endField.setInputType(android.text.InputType.TYPE_CLASS_DATETIME | android.text.InputType.TYPE_DATETIME_VARIATION_TIME); root.addView(endField);
        Button add=new Button(this); add.setText("ADICIONAR HORÁRIO"); add.setOnClickListener(v->addSchedule()); root.addView(add);
        listText=new TextView(this); listText.setTextColor(0xFF263238); listText.setPadding(0,14,0,14); root.addView(listText); refreshList();
        Button save=new Button(this); save.setText("SALVAR PROGRAMAÇÕES"); save.setOnClickListener(v->{
            if(audioUri==null){ Toast.makeText(this,"Escolha uma música primeiro.",Toast.LENGTH_LONG).show(); return; }
            saveSchedules(); ScheduleReceiver.rescheduleAll(this);
            Toast.makeText(this,"Programações salvas.",Toast.LENGTH_SHORT).show();
        }); root.addView(save);
        Button stop=new Button(this); stop.setText("PARAR AGORA"); stop.setOnClickListener(v->{
            Intent i=new Intent(this,PlayerService.class); i.setAction(PlayerService.ACTION_STOP); startService(i);
        }); root.addView(stop);
        TextView foot=new TextView(this); foot.setText("Os horários se repetem diariamente. O Android pode solicitar permissão para notificações e alarmes."); foot.setTextSize(12); foot.setPadding(0,16,0,0); root.addView(foot);
        setContentView(root);
    }

    private int parseMinutes(EditText field){try{return Integer.parseInt(field.getText().toString().trim());}catch(Exception e){return -1;}}
    private void updateMusicText(){ musicText.setText(audioUri==null?"Nenhuma música selecionada": "Música selecionada:\n"+audioUri.getLastPathSegment()); }
    @Override protected void onActivityResult(int req,int res,Intent data){
        super.onActivityResult(req,res,data);
        if(req==PICK_AUDIO && res==RESULT_OK && data!=null && data.getData()!=null){
            audioUri=data.getData();
            try { getContentResolver().takePersistableUriPermission(audioUri,Intent.FLAG_GRANT_READ_URI_PERMISSION); } catch(Exception ignored){}
            prefs.edit().putString("audio",audioUri.toString()).apply(); updateMusicText();
        }
    }
    private void addSchedule(){
        String a=startField.getText().toString().trim(), b=endField.getText().toString().trim();
        if(!validTime(a)||!validTime(b)||a.equals(b)){ Toast.makeText(this,"Digite horários diferentes no formato HH:MM.",Toast.LENGTH_LONG).show(); return; }
        schedules.add(new Schedule(a,b)); refreshList(); saveSchedules();
    }
    private boolean validTime(String s){
        try { String[] p=s.split(":"); if(p.length!=2)return false; int h=Integer.parseInt(p[0]),m=Integer.parseInt(p[1]); return h>=0&&h<24&&m>=0&&m<60; } catch(Exception e){return false;}
    }
    private void refreshList(){
        StringBuilder b=new StringBuilder();
        for(int i=0;i<schedules.size();i++){
            Schedule s=schedules.get(i);
            b.append(i+1).append(") Ligar ").append(s.start).append("  •  Desligar ").append(s.end).append("\n");
        }
        if(schedules.isEmpty()) b.append("Nenhum horário cadastrado.");
        listText.setText(b.toString());
        listText.setOnLongClickListener(v->{ if(!schedules.isEmpty()){schedules.remove(schedules.size()-1);refreshList();saveSchedules();return true;}return false;});
    }
    private void saveSchedules(){
        JSONArray arr=new JSONArray();
        for(Schedule s:schedules){ JSONObject o=new JSONObject(); try{o.put("start",s.start);o.put("end",s.end);arr.put(o);}catch(Exception ignored){} }
        prefs.edit().putString("schedules",arr.toString()).apply();
    }
    private void loadSchedules(){
        try { JSONArray a=new JSONArray(prefs.getString("schedules","[]")); for(int i=0;i<a.length();i++){JSONObject o=a.getJSONObject(i); schedules.add(new Schedule(o.getString("start"),o.getString("end")));} } catch(Exception ignored){}
    }
}
