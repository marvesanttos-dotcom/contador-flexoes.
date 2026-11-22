package br.com.playerprogramado;

import android.app.*;
import android.os.*;
import android.content.*;
import android.net.Uri;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.MotionEvent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.widget.*;
import java.util.*;
import org.json.*;

public class MainActivity extends Activity {
    private static final int PICK_AUDIO = 10;
    private final ArrayList<Schedule> schedules = new ArrayList<>();
    private SharedPreferences prefs;
    private Uri audioUri;
    private EditText startField, endField, onMinutesField, offMinutesField;
    private TextView musicText;
    private LinearLayout scheduleList;
    private Button cycleToggle;

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
        root.setBackgroundColor(0xFF000000);
        TextView title=new TextView(this);
        title.setText("PLAYER PROGRAMADO"); title.setTextSize(23); title.setTextColor(0xFFFFFFFF); title.setGravity(Gravity.CENTER);
        root.addView(title,new LinearLayout.LayoutParams(-1,-2));
        TextView subtitle=new TextView(this); subtitle.setText("Sua música nos horários escolhidos"); subtitle.setGravity(Gravity.CENTER); subtitle.setPadding(0,6,0,18);
        root.addView(subtitle);
        LinearLayout timerCard=new LinearLayout(this); timerCard.setOrientation(LinearLayout.VERTICAL); timerCard.setPadding(16,12,16,12); timerCard.setBackground(roundedBackground(0xFF0A0A0A,20));
        LinearLayout.LayoutParams timerCardParams=new LinearLayout.LayoutParams(-1,-2); timerCardParams.setMargins(0,8,0,8); root.addView(timerCard,timerCardParams);
        TextView timerTitle=new TextView(this); timerTitle.setText("TEMPORIZADOR"); timerTitle.setTextSize(13); timerTitle.setTypeface(null,1); timerTitle.setTextColor(0xFFFFFFFF); timerTitle.setPadding(2,0,0,8); timerCard.addView(timerTitle);
        LinearLayout timerFields=new LinearLayout(this); timerFields.setOrientation(LinearLayout.HORIZONTAL); timerFields.setGravity(Gravity.CENTER_VERTICAL); timerCard.addView(timerFields);
        LinearLayout playingTile=new LinearLayout(this); playingTile.setOrientation(LinearLayout.VERTICAL); playingTile.setGravity(Gravity.CENTER); playingTile.setPadding(8,8,8,6); playingTile.setBackground(roundedBackground(0xFF111111,16));
        onMinutesField=new EditText(this); onMinutesField.setSingleLine(true); onMinutesField.setInputType(2); onMinutesField.setText(String.valueOf(prefs.getInt("cycle_on",60))); styleMinuteField(onMinutesField,""); onMinutesField.setTextSize(27); onMinutesField.setTypeface(null,1); onMinutesField.setBackgroundColor(0x00000000); playingTile.addView(onMinutesField,new LinearLayout.LayoutParams(-1,48));
        TextView playingLabel=new TextView(this); playingLabel.setText("TOCANDO"); playingLabel.setTextSize(10); playingLabel.setTypeface(null,1); playingLabel.setTextColor(0xFFFFFFFF); playingLabel.setGravity(Gravity.CENTER); playingTile.addView(playingLabel);
        timerFields.addView(playingTile,new LinearLayout.LayoutParams(0,82,1));
        LinearLayout stoppedTile=new LinearLayout(this); stoppedTile.setOrientation(LinearLayout.VERTICAL); stoppedTile.setGravity(Gravity.CENTER); stoppedTile.setPadding(8,8,8,6); stoppedTile.setBackground(roundedBackground(0xFF111111,16));
        offMinutesField=new EditText(this); offMinutesField.setSingleLine(true); offMinutesField.setInputType(2); offMinutesField.setText(String.valueOf(prefs.getInt("cycle_off",60))); styleMinuteField(offMinutesField,""); offMinutesField.setTextSize(27); offMinutesField.setTypeface(null,1); offMinutesField.setBackgroundColor(0x00000000); stoppedTile.addView(offMinutesField,new LinearLayout.LayoutParams(-1,48));
        TextView stoppedLabel=new TextView(this); stoppedLabel.setText("PARADO"); stoppedLabel.setTextSize(10); stoppedLabel.setTypeface(null,1); stoppedLabel.setTextColor(0xFFFFFFFF); stoppedLabel.setGravity(Gravity.CENTER); stoppedTile.addView(stoppedLabel);
        LinearLayout.LayoutParams stoppedParams=new LinearLayout.LayoutParams(0,82,1); stoppedParams.setMargins(10,0,0,0); timerFields.addView(stoppedTile,stoppedParams);
        TextView toneTitle=new TextView(this); toneTitle.setText("GROSSURA DO ÁUDIO"); toneTitle.setTextSize(18); toneTitle.setTextColor(0xFFFFFFFF); toneTitle.setPadding(0,12,0,2); root.addView(toneTitle);
        TextView toneHint=new TextView(this); toneHint.setText("Gire o botão: esquerdo mais grosso • 50% neutro • direito mais fino"); root.addView(toneHint);
        ToneKnob toneControl=new ToneKnob(this); toneControl.setValue(prefs.getInt("tone",50)); LinearLayout.LayoutParams knobParams=new LinearLayout.LayoutParams(220,220); knobParams.gravity=Gravity.CENTER; root.addView(toneControl,knobParams);
        toneControl.setOnValueChanged(value->{ prefs.edit().putInt("tone",value).apply(); Intent i=new Intent(MainActivity.this,PlayerService.class); i.setAction(PlayerService.ACTION_TONE); startService(i); });
        Button pick=new Button(this); pick.setText("ESCOLHER MÚSICA"); pick.setOnClickListener(v->{
            Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT); i.setType("audio/*"); i.addCategory(Intent.CATEGORY_OPENABLE); i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION); startActivityForResult(i,PICK_AUDIO);
        }); root.addView(pick);
        musicText=new TextView(this); musicText.setPadding(4,10,4,16); root.addView(musicText); updateMusicText();
        TextView heading=new TextView(this); heading.setText("ATÉ QUATRO PROGRAMAÇÕES"); heading.setTextSize(20); heading.setTypeface(null,1); heading.setTextColor(0xFFFFFFFF); heading.setPadding(0,20,0,8); root.addView(heading);
        TextView scheduleHint=new TextView(this); scheduleHint.setText("Cadastre até quatro períodos independentes, cada um com seu horário de ligar e desligar."); scheduleHint.setTextColor(0xFFFFFFFF); scheduleHint.setTextSize(13); root.addView(scheduleHint);
        startField=new EditText(this); startField.setSingleLine(true); startField.setHint("Toque para escolher a hora de ligar"); startField.setText("07:00 AM"); styleTimeField(startField); configureTimePicker(startField); root.addView(startField);
        endField=new EditText(this); endField.setSingleLine(true); endField.setHint("Toque para escolher a hora de desligar"); endField.setText("07:00 PM"); styleTimeField(endField); configureTimePicker(endField); root.addView(endField);
        Button add=new Button(this); add.setText("ADICIONAR HORÁRIO"); add.setOnClickListener(v->addSchedule()); root.addView(add);
        scheduleList=new LinearLayout(this); scheduleList.setOrientation(LinearLayout.VERTICAL); LinearLayout.LayoutParams scheduleParams=new LinearLayout.LayoutParams(-1,-2); scheduleParams.setMargins(0,8,0,12); root.addView(scheduleList,scheduleParams); refreshList();
        Button save=new Button(this); save.setText("SALVAR PROGRAMAÇÕES"); save.setOnClickListener(v->{
            if(audioUri==null){ Toast.makeText(this,"Escolha uma música primeiro.",Toast.LENGTH_LONG).show(); return; }
            saveSchedules(); ScheduleReceiver.rescheduleAll(this);
            Toast.makeText(this,"Programações salvas.",Toast.LENGTH_SHORT).show();
        }); root.addView(save);
        LinearLayout timerActions=new LinearLayout(this); timerActions.setOrientation(LinearLayout.HORIZONTAL); timerActions.setGravity(Gravity.CENTER_VERTICAL); LinearLayout.LayoutParams actionParams=new LinearLayout.LayoutParams(-1,-2); actionParams.setMargins(0,8,0,4); root.addView(timerActions,actionParams);
        cycleToggle=new Button(this); updateCycleButton(); cycleToggle.setTextSize(12); cycleToggle.setMinHeight(44); cycleToggle.setPadding(6,0,6,0); cycleToggle.setOnClickListener(v->{
            if(prefs.getBoolean("cycle_enabled",false)) ScheduleReceiver.stopCycle(this); else ScheduleReceiver.startCycle(this);
            updateCycleButton();
        }); timerActions.addView(cycleToggle,new LinearLayout.LayoutParams(0,48,1));
        Button stop=new Button(this); stop.setText("PARAR"); stop.setTextSize(12); stop.setMinHeight(44); stop.setPadding(6,0,6,0); stop.setOnClickListener(v->{
            ScheduleReceiver.stopCycle(this); updateCycleButton();
        }); LinearLayout.LayoutParams stopParams=new LinearLayout.LayoutParams(0,48,1); stopParams.setMargins(8,0,0,0); timerActions.addView(stop,stopParams);
        TextView foot=new TextView(this); foot.setText("Os horários se repetem diariamente. O Android pode solicitar permissão para notificações e alarmes."); foot.setTextSize(12); foot.setPadding(0,16,0,0); root.addView(foot);
        ScrollView scroll=new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setVerticalScrollBarEnabled(true);
        scroll.addView(root,new ScrollView.LayoutParams(-1,-2));
        styleAmoled(root);
        getWindow().getDecorView().setBackgroundColor(0xFF000000);
        setContentView(scroll);
    }


    private void styleAmoled(View view){
        if(view instanceof TextView){
            TextView t=(TextView)view;
            t.setTextColor(0xFFFFFFFF);
            if(view instanceof EditText){
                t.setHintTextColor(0xFFBDBDBD);
                GradientDrawable bg=roundedBackground(0xFF080808,18);
                bg.setStroke(2,0xFFFFFFFF);
                view.setBackground(bg);
            } else if(view instanceof Button){
                GradientDrawable bg=roundedBackground(0xFF080808,22);
                bg.setStroke(2,0xFFFFFFFF);
                view.setBackground(bg);
                t.setTextColor(0xFFFFFFFF);
                t.setAllCaps(false);
            }
        }
        if(view instanceof ViewGroup){
            ViewGroup group=(ViewGroup)view;
            for(int i=0;i<group.getChildCount();i++) styleAmoled(group.getChildAt(i));
        }
    }

    private void updateCycleButton(){ if(cycleToggle!=null) cycleToggle.setText(prefs.getBoolean("cycle_enabled",false)?"DESATIVAR TEMPORIZADOR":"ATIVAR TEMPORIZADOR"); }
    @Override protected void onResume(){ super.onResume(); updateCycleButton(); }
    interface ToneChanged { void onChanged(int value); }
    static class ToneKnob extends View {
        private final Paint paint=new Paint(3); private final RectF arc=new RectF(); private int value=50; private ToneChanged listener;
        ToneKnob(Context c){super(c);setLayerType(View.LAYER_TYPE_SOFTWARE,null);}
        void setValue(int v){value=Math.max(0,Math.min(100,v));invalidate();}
        void setOnValueChanged(ToneChanged l){listener=l;}
        @Override protected void onDraw(Canvas c){super.onDraw(c);float cx=getWidth()/2f,cy=getHeight()/2f,r=Math.min(cx,cy)-18;arc.set(cx-r,cy-r,cx+r,cy+r);
            paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(13);paint.setStrokeCap(Paint.Cap.ROUND);paint.setColor(0xFF555555);c.drawArc(arc,135,270,false,paint);
            paint.setColor(0xFFFFFFFF);c.drawArc(arc,135,value*2.7f,false,paint);
            double a=Math.toRadians(135+value*2.7);float px=cx+(float)Math.cos(a)*(r-3),py=cy+(float)Math.sin(a)*(r-3);
            paint.setStyle(Paint.Style.FILL);paint.setColor(0xFF080808);c.drawCircle(cx,cy,r-18,paint);paint.setColor(0xFFFFFFFF);c.drawCircle(px,py,9,paint);
            paint.setTextAlign(Paint.Align.CENTER);paint.setColor(0xFF0D0D0D);paint.setTextSize(34);c.drawText(value+"%",cx,cy+10,paint);paint.setTextSize(12);c.drawText("GROSSURA",cx,cy+35,paint);
        }
        @Override public boolean onTouchEvent(MotionEvent e){if(e.getAction()==MotionEvent.ACTION_DOWN||e.getAction()==MotionEvent.ACTION_MOVE){float dx=e.getX()-getWidth()/2f,dy=e.getY()-getHeight()/2f;double deg=Math.toDegrees(Math.atan2(dy,dx));double rel=(deg-135+360)%360;if(rel>270)rel=rel<315?270:0;value=Math.max(0,Math.min(100,(int)Math.round(rel/2.7)));invalidate();if(listener!=null)listener.onChanged(value);return true;}return e.getAction()==MotionEvent.ACTION_UP||e.getAction()==MotionEvent.ACTION_CANCEL;}
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
        if(schedules.size()>=4){ Toast.makeText(this,"Você pode cadastrar no máximo quatro programações.",Toast.LENGTH_LONG).show(); return; }
        String a=to24Hour(startField.getText().toString().trim()), b=to24Hour(endField.getText().toString().trim());
        if(a==null||b==null||a.equals(b)){ Toast.makeText(this,"Escolha horários diferentes.",Toast.LENGTH_LONG).show(); return; }
        schedules.add(new Schedule(a,b)); refreshList(); saveSchedules();
    }
    private boolean validTime(String s){
        try { String[] p=s.split(":"); if(p.length!=2)return false; int h=Integer.parseInt(p[0]),m=Integer.parseInt(p[1]); return h>=0&&h<24&&m>=0&&m<60; } catch(Exception e){return false;}
    }
    private GradientDrawable roundedBackground(int color,int radius){
        GradientDrawable d=new GradientDrawable(); d.setColor(color); d.setCornerRadius(radius); return d;
    }
    private void configureTimePicker(EditText field){
        field.setFocusable(false);
        field.setClickable(true);
        field.setCursorVisible(false);
        field.setOnClickListener(v->{
            String raw=to24Hour(field.getText().toString());
            int hour=7, minute=0;
            try { String[] parts=raw.split(":"); hour=Integer.parseInt(parts[0]); minute=Integer.parseInt(parts[1]); } catch(Exception ignored){}
            new TimePickerDialog(this,(picker,h,m)->field.setText(format12Hour(String.format(Locale.US,"%02d:%02d",h,m))),hour,minute,false).show();
        });
    }
    private String to24Hour(String value){
        try {
            String[] p=value.trim().toUpperCase(Locale.US).split("\\s+");
            String[] hm=p[0].split(":");
            if(hm.length!=2||p.length!=2)return null;
            int h=Integer.parseInt(hm[0]),m=Integer.parseInt(hm[1]);
            if(h<1||h>12||m<0||m>59||(!p[1].equals("AM")&&!p[1].equals("PM")))return null;
            if(p[1].equals("AM")){if(h==12)h=0;}else if(h!=12)h+=12;
            return String.format(Locale.US,"%02d:%02d",h,m);
        }catch(Exception e){return null;}
    }
    private String format12Hour(String value){
        try{
            String[] p=value.split(":"); int h=Integer.parseInt(p[0]),m=Integer.parseInt(p[1]);
            String suffix=h<12?"AM":"PM"; int h12=h%12; if(h12==0)h12=12;
            return String.format(Locale.US,"%02d:%02d %s",h12,m,suffix);
        }catch(Exception e){return value;}
    }
    private void styleMinuteField(EditText field,String hint){
        field.setHint(hint); field.setTextSize(14); field.setTextColor(0xFFFFFFFF); field.setHintTextColor(0xFF999999);
        field.setGravity(Gravity.CENTER); field.setPadding(8,4,8,4);
        GradientDrawable bg=roundedBackground(0xFF111111,10); bg.setStroke(1,0xFF666666); field.setBackground(bg);
    }
    private void styleTimeField(EditText field){
        field.setTextSize(17); field.setTextColor(0xFFFFFFFF); field.setPadding(18,15,18,15);
        field.setBackground(roundedBackground(0xFF0D0D0D,18));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2); p.setMargins(0,7,0,7); field.setLayoutParams(p);
    }
    private void refreshList(){
        scheduleList.removeAllViews();
        if(schedules.isEmpty()){
            TextView empty=new TextView(this); empty.setText("Ainda não há horários.\nAdicione seu primeiro período acima.");
            empty.setTextColor(0xFFFFFFFF); empty.setTextSize(14); empty.setGravity(Gravity.CENTER);
            empty.setPadding(16,22,16,22); empty.setBackground(roundedBackground(0xFF0D0D0D,18)); scheduleList.addView(empty); return;
        }
        for(int i=0;i<schedules.size();i++){
            final int index=i; Schedule s=schedules.get(i);
            LinearLayout card=new LinearLayout(this); card.setOrientation(LinearLayout.HORIZONTAL); card.setGravity(Gravity.CENTER_VERTICAL);
            card.setPadding(16,14,10,14); card.setBackground(roundedBackground(0xFF0D0D0D,18));
            LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2); cp.setMargins(0,5,0,5); scheduleList.addView(card,cp);
            LinearLayout times=new LinearLayout(this); times.setOrientation(LinearLayout.VERTICAL);
            TextView status=new TextView(this); status.setText("PROGRAMAÇÃO "+(i+1)); status.setTextSize(11); status.setTypeface(null,1); status.setTextColor(0xFFFFFFFF);
            TextView range=new TextView(this); range.setText("Ligar  "+format12Hour(s.start)+"     •     Desligar  "+format12Hour(s.end)); range.setTextSize(15); range.setTextColor(0xFFFFFFFF); range.setPadding(0,5,0,0);
            times.addView(status); times.addView(range); card.addView(times,new LinearLayout.LayoutParams(0,-2,1));
            Button remove=new Button(this); remove.setText("×"); remove.setTextSize(22); remove.setTextColor(0xFFFFFFFF);
            remove.setMinWidth(44); remove.setMinimumWidth(44); remove.setPadding(0,0,0,0);
            card.addView(remove,new LinearLayout.LayoutParams(48,48));
            remove.setOnClickListener(v->{ schedules.remove(index); refreshList(); saveSchedules(); });
        }
    }
    private void saveSchedules(){
        JSONArray arr=new JSONArray();
        for(Schedule s:schedules){ JSONObject o=new JSONObject(); try{o.put("start",s.start);o.put("end",s.end);arr.put(o);}catch(Exception ignored){} }
        prefs.edit().putString("schedules",arr.toString()).apply();
    }
    private void loadSchedules(){
        try { JSONArray a=new JSONArray(prefs.getString("schedules","[]")); for(int i=0;i<a.length() && i<4;i++){JSONObject o=a.getJSONObject(i); schedules.add(new Schedule(o.getString("start"),o.getString("end")));} } catch(Exception ignored){}
    }
}
