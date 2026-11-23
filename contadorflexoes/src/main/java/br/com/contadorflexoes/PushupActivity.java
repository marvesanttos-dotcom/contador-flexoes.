package br.com.contadorflexoes;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.*;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import androidx.camera.core.*;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.pose.*;
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class PushupActivity extends androidx.activity.ComponentActivity {
    private PreviewView preview;
    private TextView countText, setText, statusText, timerText;
    private EditText seriesInput, targetInput, restInput;
    private Button startButton, stopButton;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private ExecutorService cameraExecutor;
    private PoseDetector detector;
    private ProcessCameraProvider cameraProvider;
    private boolean workout=false, down=false, resting=false, cameraStarted=false;
    private int reps=0, sets=1, totalSets=3, target=10, restSeconds=60, remaining=0;
    private long lastRepAt=0;
    private Runnable restTick;
    private final AtomicBoolean processing=new AtomicBoolean(false);

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(0xFF080B0F);
        getWindow().setNavigationBarColor(0xFF080B0F);
        cameraExecutor=Executors.newSingleThreadExecutor();
        detector=PoseDetection.getClient(new PoseDetectorOptions.Builder().setDetectorMode(PoseDetectorOptions.STREAM_MODE).build());
        buildUi();
    }

    private int dp(float v){ return (int)(v*getResources().getDisplayMetrics().density+0.5f); }
    private TextView text(String value,int size,int color,boolean bold){
        TextView t=new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(color);
        if(bold)t.setTypeface(null,1); return t;
    }
    private LinearLayout column(){
        LinearLayout l=new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l;
    }
    private LinearLayout card(){
        LinearLayout l=column(); l.setPadding(dp(14),dp(12),dp(14),dp(12));
        l.setBackground(round(0xFF151D27,16)); return l;
    }
    private android.graphics.drawable.GradientDrawable round(int color,int radius){
        android.graphics.drawable.GradientDrawable d=new android.graphics.drawable.GradientDrawable();
        d.setColor(color); d.setCornerRadius(dp(radius)); return d;
    }
    private void buildUi(){
        ScrollView scroll=new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(0xFF080B0F);
        LinearLayout root=column(); root.setPadding(dp(16),dp(12),dp(16),dp(20));
        scroll.addView(root);

        TextView title=text("CONTADOR DE FLEXÕES",20,Color.WHITE,true);
        title.setGravity(Gravity.CENTER); title.setPadding(0,dp(4),0,dp(4)); root.addView(title);
        TextView hint=text("Deixe o corpo inteiro visível e posicione o celular de lado.",13,0xFF9EADBD,false);
        hint.setGravity(Gravity.CENTER); hint.setPadding(dp(8),dp(3),dp(8),dp(10)); root.addView(hint);

        preview=new PreviewView(this); preview.setScaleType(PreviewView.ScaleType.FILL_CENTER);
        preview.setBackground(round(0xFF202B38,14));
        LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(-1,dp(185)); pp.bottomMargin=dp(12);
        root.addView(preview,pp);

        LinearLayout stats=column();
        LinearLayout repCard=card(); repCard.setGravity(Gravity.CENTER);
        TextView repLabel=text("FLEXÕES",12,0xFF9EADBD,true); repLabel.setGravity(Gravity.CENTER);
        countText=text("0",48,0xFF59D6A5,true); countText.setGravity(Gravity.CENTER);
        repCard.addView(repLabel); repCard.addView(countText);
        stats.addView(repCard,new LinearLayout.LayoutParams(-1,-2));

        LinearLayout seriesCard=card(); seriesCard.setGravity(Gravity.CENTER);
        seriesCard.setBackground(round(0xFF203A36,16));
        TextView seriesLabel=text("SÉRIE ATUAL",12,0xFFB7E8D5,true); seriesLabel.setGravity(Gravity.CENTER);
        setText=text("1 de 3",27,0xFF59D6A5,true); setText.setGravity(Gravity.CENTER);
        seriesCard.addView(seriesLabel); seriesCard.addView(setText);
        LinearLayout.LayoutParams seriesParams=new LinearLayout.LayoutParams(-1,-2);
        seriesParams.topMargin=dp(8);
        stats.addView(seriesCard,seriesParams);
        root.addView(stats);

        statusText=text("PRONTO PARA COMEÇAR",14,0xFF59D6A5,true);
        statusText.setGravity(Gravity.CENTER); statusText.setPadding(0,dp(12),0,dp(4)); root.addView(statusText);
        timerText=text("DESCANSO  00:00",20,Color.WHITE,true);
        timerText.setGravity(Gravity.CENTER); timerText.setPadding(0,dp(2),0,dp(12)); root.addView(timerText);

        LinearLayout settings=card();
        TextView st=text("CONFIGURAÇÕES DO TREINO",14,Color.WHITE,true);
        st.setPadding(0,0,0,dp(10)); settings.addView(st);

        LinearLayout row=new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout c1=column(), c2=column();
        addField(c1,"QUANTIDADE DE SÉRIES","3",true);
        addField(c2,"FLEXÕES POR SÉRIE","10",false);
        LinearLayout.LayoutParams p1=new LinearLayout.LayoutParams(0,-2,1); p1.rightMargin=dp(6);
        LinearLayout.LayoutParams p2=new LinearLayout.LayoutParams(0,-2,1); p2.leftMargin=dp(6);
        row.addView(c1,p1); row.addView(c2,p2); settings.addView(row);
        LinearLayout restCol=column(); LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,-2); rp.topMargin=dp(10);
        addField(restCol,"TEMPO DE DESCANSO (SEGUNDOS)","60",false); settings.addView(restCol,rp);
        TextView note=text("Exemplo: 3 séries × 10 flexões. O descanso começa sozinho ao completar cada série.",12,0xFF9EADBD,false);
        note.setPadding(0,dp(8),0,0); settings.addView(note);
        LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,-2); sp.topMargin=dp(4); sp.bottomMargin=dp(12); root.addView(settings,sp);

        startButton=new Button(this); startButton.setText("INICIAR TREINO");
        startButton.setAllCaps(false); startButton.setTextSize(16); startButton.setTextColor(0xFF07110D);
        startButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF59D6A5));
        root.addView(startButton,new LinearLayout.LayoutParams(-1,dp(54)));
        stopButton=new Button(this); stopButton.setText("ENCERRAR TREINO"); stopButton.setAllCaps(false);
        stopButton.setEnabled(false); root.addView(stopButton,new LinearLayout.LayoutParams(-1,dp(50)));
        startButton.setOnClickListener(v->startWorkout()); stopButton.setOnClickListener(v->stopWorkout());
        setContentView(scroll);
    }

    private void addField(LinearLayout parent,String caption,String initial,boolean series){
        TextView label=text(caption,11,0xFFB8C5D2,true); label.setPadding(0,0,0,dp(5)); parent.addView(label);
        EditText e=new EditText(this); e.setSingleLine(true); e.setInputType(2); e.setText(initial);
        e.setTextColor(Color.WHITE); e.setTextSize(18); e.setSelectAllOnFocus(true);
        e.setGravity(Gravity.CENTER); e.setPadding(dp(8),0,dp(8),0);
        e.setBackground(round(0xFF263443,10));
        parent.addView(e,new LinearLayout.LayoutParams(-1,dp(50)));
        if(series)seriesInput=e; else if(caption.startsWith("FLEXÕES"))targetInput=e; else restInput=e;
    }

    private void startWorkout(){
        try { totalSets=Integer.parseInt(seriesInput.getText().toString()); target=Integer.parseInt(targetInput.getText().toString()); restSeconds=Integer.parseInt(restInput.getText().toString()); }
        catch(Exception e){ Toast.makeText(this,"Preencha todas as configurações.",Toast.LENGTH_SHORT).show(); return; }
        if(totalSets<1||totalSets>30||target<1||target>200||restSeconds<5||restSeconds>600){
            Toast.makeText(this,"Séries: 1 a 30. Flexões: 1 a 200. Descanso: 5 a 600 segundos.",Toast.LENGTH_LONG).show(); return;
        }
        reps=0; sets=1; down=false; workout=true; resting=false; updateStats();
        setInputsEnabled(false); startButton.setEnabled(false); stopButton.setEnabled(true);
        statusText.setText("PREPARE-SE — POSICIONE-SE DE LADO");
        if(ContextCompat.checkSelfPermission(this,Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED)
            ActivityCompat.requestPermissions(this,new String[]{Manifest.permission.CAMERA},77);
        else startCamera();
    }
    private void setInputsEnabled(boolean enabled){seriesInput.setEnabled(enabled);targetInput.setEnabled(enabled);restInput.setEnabled(enabled);}
    @Override public void onRequestPermissionsResult(int r,String[] p,int[] g){
        super.onRequestPermissionsResult(r,p,g);
        if(r==77&&g.length>0&&g[0]==PackageManager.PERMISSION_GRANTED)startCamera();
        else {Toast.makeText(this,"A câmera é necessária para contar as flexões.",Toast.LENGTH_LONG).show();stopWorkout();}
    }
    private void startCamera(){
        if(cameraStarted)return;
        ListenableFuture<ProcessCameraProvider> f=ProcessCameraProvider.getInstance(this);
        f.addListener(()->{try{
            cameraProvider=f.get(); Preview p=new Preview.Builder().build(); p.setSurfaceProvider(preview.getSurfaceProvider());
            ImageAnalysis a=new ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build();
            a.setAnalyzer(cameraExecutor,this::analyze); cameraProvider.unbindAll();
            cameraProvider.bindToLifecycle(this,CameraSelector.DEFAULT_BACK_CAMERA,p,a); cameraStarted=true;
            statusText.setText("CÂMERA ATIVA — PODE COMEÇAR");
        }catch(Exception e){statusText.setText("NÃO FOI POSSÍVEL ABRIR A CÂMERA");}},ContextCompat.getMainExecutor(this));
    }
    private void analyze(ImageProxy proxy){
        if(!processing.compareAndSet(false,true)){proxy.close();return;}
        android.media.Image media=proxy.getImage();
        if(media==null){processing.set(false);proxy.close();return;}
        detector.process(InputImage.fromMediaImage(media,proxy.getImageInfo().getRotationDegrees()))
            .addOnSuccessListener(p->{if(!workout||resting)return; Float a=armAngle(p); if(a==null)return;
                long now=System.currentTimeMillis(); if(a<95)down=true;
                else if(a>155&&down&&now-lastRepAt>500){down=false;lastRepAt=now;reps++;runOnUiThread(this::onRep);}
            }).addOnCompleteListener(t->{proxy.close();processing.set(false);});
    }
    private Float armAngle(Pose p){
        PoseLandmark s=best(p,11,12),e=best(p,13,14),w=best(p,15,16);
        if(s==null||e==null||w==null)return null;
        float ax=s.getPosition().x-e.getPosition().x,ay=s.getPosition().y-e.getPosition().y;
        float bx=w.getPosition().x-e.getPosition().x,by=w.getPosition().y-e.getPosition().y;
        double dot=ax*bx+ay*by,mag=Math.sqrt((ax*ax+ay*ay)*(bx*bx+by*by));
        if(mag==0)return null; return(float)Math.toDegrees(Math.acos(Math.max(-1,Math.min(1,dot/mag))));
    }
    private PoseLandmark best(Pose p,int l,int r){
        PoseLandmark a=p.getPoseLandmark(l),b=p.getPoseLandmark(r);
        if(a==null)return b;if(b==null)return a;
        return a.getInFrameLikelihood()>=b.getInFrameLikelihood()?a:b;
    }
    private void onRep(){
        if(!workout)return; updateStats(); statusText.setText("FLEXÃO "+reps+" DE "+target);
        if(reps>=target){ if(sets>=totalSets){finishWorkout();return;} beginRest(); }
    }
    private void beginRest(){resting=true;down=false;remaining=restSeconds;statusText.setText("SÉRIE CONCLUÍDA! HORA DE DESCANSAR");vibrate();tickRest();}
    private void tickRest(){
        timerText.setText(String.format(Locale.getDefault(),"DESCANSO  %02d:%02d",remaining/60,remaining%60));
        if(remaining<=0){resting=false;reps=0;sets++;updateStats();statusText.setText("VAMOS! PRÓXIMA SÉRIE");timerText.setText("DESCANSO  00:00");vibrate();return;}
        remaining--; restTick=this::tickRest; handler.postDelayed(restTick,1000);
    }
    private void updateStats(){countText.setText(String.valueOf(reps));setText.setText(sets+" de "+totalSets);}
    private void finishWorkout(){
        workout=false;resting=false;statusText.setText("TREINO CONCLUÍDO! PARABÉNS!");
        timerText.setText(" "+totalSets+" SÉRIES COMPLETAS");
        vibrate(); if(cameraProvider!=null)cameraProvider.unbindAll();cameraStarted=false;
        setInputsEnabled(true);startButton.setEnabled(true);stopButton.setEnabled(false);
    }
    private void vibrate(){
        try{Vibrator v=(Vibrator)getSystemService(Context.VIBRATOR_SERVICE);if(v!=null){
            if(Build.VERSION.SDK_INT>=26)v.vibrate(VibrationEffect.createOneShot(250,180));else v.vibrate(250);
        }}catch(Exception ignored){}
    }
    private void stopWorkout(){
        workout=false;resting=false;if(restTick!=null)handler.removeCallbacks(restTick);
        if(cameraProvider!=null)cameraProvider.unbindAll();cameraStarted=false;
        setInputsEnabled(true);startButton.setEnabled(true);stopButton.setEnabled(false);
        statusText.setText("TREINO ENCERRADO");timerText.setText("DESCANSO  00:00");
    }
    @Override protected void onDestroy(){
        super.onDestroy();if(cameraProvider!=null)cameraProvider.unbindAll();
        if(detector!=null)detector.close();if(cameraExecutor!=null)cameraExecutor.shutdown();
        handler.removeCallbacksAndMessages(null);
    }
}