package br.com.contadorflexoes;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Path;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
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
import java.util.Calendar;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class PushupActivity extends androidx.activity.ComponentActivity {
    
    private TextView countText, setText, statusText;
    private CircleTimerView restCircle;
    private ViewFlipper pages;
    private TextView pageTabWorkout, pageTabProgress, progressSummary, progressDetail;
    private int progressPeriod=0;
    private int completedReps=0;
    private boolean sessionSaved=false;
    private float touchStartX, touchStartY;
    private EditText seriesInput, targetInput, restInput;
    private Button startButton, stopButton;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private ExecutorService cameraExecutor;
    private PoseDetector detector;
    private ProcessCameraProvider cameraProvider;
    private boolean workout=false, down=false, resting=false, cameraStarted=false, countingEnabled=false;
    private int reps=0, sets=1, totalSets=3, target=10, restSeconds=60, remaining=0;
    private long lastRepAt=0;
    private Runnable restTick, countdownTick;
    private int countdownSeconds=3;
    private final AtomicBoolean processing=new AtomicBoolean(false);

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(0xFF080604);
        getWindow().setNavigationBarColor(0xFF080604);
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
        l.setBackground(round(0xFF17110A,16)); return l;
    }
    private android.graphics.drawable.GradientDrawable round(int color,int radius){
        android.graphics.drawable.GradientDrawable d=new android.graphics.drawable.GradientDrawable();
        d.setColor(color); d.setCornerRadius(dp(radius)); return d;
    }
    private void buildUi(){
        LinearLayout shell=column();
        shell.setBackgroundColor(0xFF080604);
        pages=new ViewFlipper(this);
        pages.addView(buildWorkoutPage());
        pages.addView(buildProgressPage());
        shell.addView(pages,new LinearLayout.LayoutParams(-1,0,1));
        pages.setOnTouchListener((v,event)->{
            if(event.getAction()==android.view.MotionEvent.ACTION_DOWN){touchStartX=event.getX();touchStartY=event.getY();return true;}
            if(event.getAction()==android.view.MotionEvent.ACTION_UP){
                float dx=event.getX()-touchStartX,dy=event.getY()-touchStartY;
                if(Math.abs(dx)>dp(55)&&Math.abs(dx)>Math.abs(dy)){showPage(dx<0?1:0);return true;}
            }
            return true;
        });
        setContentView(shell);
    }
    private void showPage(int page){
        pages.setDisplayedChild(page);
        if(pageTabWorkout!=null&&pageTabProgress!=null){
            pageTabWorkout.setTextColor(page==0?0xFFFFB000:0xFFB8A88F);
            pageTabProgress.setTextColor(page==1?0xFFFFB000:0xFFB8A88F);
            pageTabWorkout.setText(page==0?"●  TREINO":"TREINO");
            pageTabProgress.setText(page==1?"●  PROGRESSO":"PROGRESSO  ›");
        }
        if(page==1)refreshProgress();
    }
    private ScrollView pageScroll(LinearLayout root){
        ScrollView sc=new ScrollView(this);sc.setFillViewport(true);sc.setBackgroundColor(0xFF080604);
        root.setPadding(dp(16),dp(8),dp(16),dp(20));sc.addView(root);return sc;
    }
    private android.graphics.drawable.GradientDrawable premiumCard(int bg,int stroke,int radius){
        android.graphics.drawable.GradientDrawable d=round(bg,radius);
        d.setStroke(dp(1),stroke);
        return d;
    }
    private LinearLayout premiumPanel(){
        TechPanelLayout l=new TechPanelLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);l.setPadding(dp(12),dp(10),dp(12),dp(10));return l;
    }
    private class TechPanelLayout extends LinearLayout {
        private final Paint edge=new Paint(3);private final Path path=new Path();
        TechPanelLayout(Context c){super(c);setWillNotDraw(false);setLayerType(View.LAYER_TYPE_SOFTWARE,null);}
        @Override protected void onDraw(Canvas c){super.onDraw(c);float w=getWidth(),h=getHeight(),cut=dp(17),in=dp(5);
            path.reset();path.moveTo(cut,1);path.lineTo(w-cut,1);path.lineTo(w-1,cut);path.lineTo(w-1,h-cut);path.lineTo(w-cut,h-1);path.lineTo(cut,h-1);path.lineTo(1,h-cut);path.lineTo(1,cut);path.close();
            edge.setStyle(Paint.Style.FILL);edge.setColor(0xFF080808);c.drawPath(path,edge);
            edge.setStyle(Paint.Style.STROKE);edge.setStrokeWidth(dp(1.5f));edge.setColor(0xFFFFB900);edge.setShadowLayer(dp(7),0,0,0xCCFF9D00);c.drawPath(path,edge);edge.clearShadowLayer();
            path.reset();path.moveTo(cut+in,in);path.lineTo(w-cut-in,in);path.lineTo(w-in,cut+in);path.lineTo(w-in,h-cut-in);path.lineTo(w-cut-in,h-in);path.lineTo(cut+in,h-in);path.lineTo(in,h-cut-in);path.lineTo(in,cut+in);path.close();
            edge.setStrokeWidth(dp(0.8f));edge.setColor(0xFF654717);c.drawPath(path,edge);
        }
    }
    private TextView centered(String value,int size,int color,boolean bold){
        TextView t=text(value,size,color,bold);t.setGravity(Gravity.CENTER);return t;
    }
    private int sumRepsSince(long from){
        JSONArray a=loadHistory();int sum=0;long now=System.currentTimeMillis();
        for(int i=0;i<a.length();i++){JSONObject o=a.optJSONObject(i);if(o!=null){long t=o.optLong("time",0);if(t>=from&&t<=now)sum+=o.optInt("reps",0);}}
        return sum;
    }
    private View buildWorkoutPage(){
        LinearLayout root=column();
        root.setPadding(dp(12),dp(5),dp(12),dp(8));
        root.setClipToPadding(false);

        LinearLayout hero=premiumPanel();hero.setOrientation(LinearLayout.HORIZONTAL);hero.setGravity(Gravity.CENTER_VERTICAL);
        hero.addView(new HeroArtworkView(this),new LinearLayout.LayoutParams(dp(154),dp(140)));
        LinearLayout repBlock=column();repBlock.setGravity(Gravity.CENTER);
        LinearLayout seriesRow=new LinearLayout(this);seriesRow.setGravity(Gravity.CENTER);
        TextView flexIcon=centered("💪",20,0xFFFFC400,true);seriesRow.addView(flexIcon);
        TextView seriesLabel=centered("  SÉRIE ATUAL",14,Color.WHITE,true);seriesRow.addView(seriesLabel);
        repBlock.addView(seriesRow);
        setText=centered("1 / 3",29,0xFFFFC400,true);
        repBlock.addView(setText);
        countText=centered("0",60,Color.WHITE,true);repBlock.addView(countText);
        TextView repCaption=centered("FLEXÕES",12,0xFFD9B65A,true);repBlock.addView(repCaption);
        TextView targetCaption=centered("META DA SÉRIE  •  10",12,0xFFFFC400,true);android.graphics.drawable.GradientDrawable targetBg=round(0xFF090806,18);targetBg.setStroke(dp(1),0xFFFFB900);targetCaption.setBackground(targetBg);targetCaption.setPadding(dp(9),dp(3),dp(9),dp(3));repBlock.addView(targetCaption);
        hero.addView(repBlock,new LinearLayout.LayoutParams(0,-2,1));
        LinearLayout.LayoutParams heroP=new LinearLayout.LayoutParams(-1,0,1.62f);heroP.bottomMargin=dp(7);root.addView(hero,heroP);

        LinearLayout quickStats=new LinearLayout(this);quickStats.setOrientation(LinearLayout.HORIZONTAL);
        String[] statNames={"HOJE","ESTA SEMANA","ESTE MÊS"};
        Calendar statNow=Calendar.getInstance();
        Calendar dayStart=(Calendar)statNow.clone();dayStart.set(Calendar.HOUR_OF_DAY,0);dayStart.set(Calendar.MINUTE,0);dayStart.set(Calendar.SECOND,0);dayStart.set(Calendar.MILLISECOND,0);
        Calendar weekStart=(Calendar)dayStart.clone();weekStart.add(Calendar.DAY_OF_YEAR,-6);
        Calendar monthStart=(Calendar)dayStart.clone();monthStart.set(Calendar.DAY_OF_MONTH,1);
        String[] statValues={String.valueOf(sumRepsSince(dayStart.getTimeInMillis())),String.valueOf(sumRepsSince(weekStart.getTimeInMillis())),String.valueOf(sumRepsSince(monthStart.getTimeInMillis()))};
        String[] statIcons={"📅","🗓️","🏆"};
        for(int i=0;i<3;i++){
            LinearLayout item=premiumPanel();item.setGravity(Gravity.CENTER);item.setPadding(dp(5),dp(10),dp(5),dp(10));
            item.addView(centered(statIcons[i],23,0xFFFFC400,true));
            TextView statLabel=centered(statNames[i],11,Color.WHITE,true);statLabel.setTypeface(android.graphics.Typeface.create("sans-serif",android.graphics.Typeface.BOLD_ITALIC));item.addView(statLabel);
            item.addView(centered(statValues[i],25,Color.WHITE,true));
            LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(0,-1,1);ip.leftMargin=dp(i==0?0:4);ip.rightMargin=dp(4);quickStats.addView(item,ip);
        }
        LinearLayout.LayoutParams qsp=new LinearLayout.LayoutParams(-1,0,0.78f);qsp.bottomMargin=dp(7);root.addView(quickStats,qsp);

        restCircle=new CircleTimerView(this);
        restCircle.setDisplay("00:00","DESCANSO",0f);
        LinearLayout.LayoutParams timerP=new LinearLayout.LayoutParams(-1,0,1.28f);
        timerP.bottomMargin=dp(4);root.addView(restCircle,timerP);

        statusText=centered("PRONTO PARA COMEÇAR",13,0xFFFFC400,true);
        statusText.setTextSize(12);statusText.setVisibility(View.GONE);

        LinearLayout settings=premiumPanel();settings.setPadding(dp(12),dp(9),dp(12),dp(8));
        TextView st=text("⚙  CONFIGURAÇÕES DO TREINO",17,Color.WHITE,true);st.setTypeface(android.graphics.Typeface.create("sans-serif",android.graphics.Typeface.BOLD_ITALIC));st.setPadding(0,0,0,dp(4));settings.addView(st);
        LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout c1=column(),c2=column(),c3=column();
        addField(c1,"SÉRIES","3",true);addField(c2,"FLEXÕES POR SÉRIE","10",false);addField(c3,"DESCANSO (SEGUNDOS)","60",false);
        LinearLayout.LayoutParams p1=new LinearLayout.LayoutParams(0,-2,1);p1.rightMargin=dp(4);
        LinearLayout.LayoutParams p2=new LinearLayout.LayoutParams(0,-2,1);p2.leftMargin=dp(3);p2.rightMargin=dp(3);
        LinearLayout.LayoutParams p3=new LinearLayout.LayoutParams(0,-2,1);p3.leftMargin=dp(4);
        row.addView(c1,p1);row.addView(c2,p2);row.addView(c3,p3);settings.addView(row);
                LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,0,0.86f);sp.bottomMargin=dp(7);root.addView(settings,sp);

        startButton=new TechStartButton(this);startButton.setText("▶   INICIAR TREINO");startButton.setAllCaps(false);startButton.setTextSize(17);startButton.setTypeface(android.graphics.Typeface.create("sans-serif",android.graphics.Typeface.BOLD_ITALIC));startButton.setTextColor(0xFF100B00);
        android.graphics.drawable.GradientDrawable startBg=new android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.TL_BR,new int[]{0xFFFFE45A,0xFFFFC400,0xFFFFAA00});startBg.setCornerRadius(dp(7));startBg.setStroke(dp(2),0xFFFFE47A);startButton.setBackgroundColor(Color.TRANSPARENT);startButton.setElevation(dp(8));
        LinearLayout.LayoutParams startP=new LinearLayout.LayoutParams(-1,0,0.58f);startP.topMargin=dp(1);root.addView(startButton,startP);
        stopButton=new Button(this);stopButton.setText("■   ENCERRAR TREINO");stopButton.setAllCaps(false);stopButton.setTextSize(13);stopButton.setTextColor(0xFFE8DCC2);stopButton.setBackground(premiumCard(0xFF15110B,0xFF70521A,26));
        stopButton.setVisibility(View.GONE);root.addView(stopButton,new LinearLayout.LayoutParams(-1,dp(38)));
        startButton.setOnClickListener(v->startWorkout());stopButton.setOnClickListener(v->stopWorkout());
        return root;
    }

    private class HeroArtworkView extends View {
        private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
        private final android.graphics.Bitmap bitmap;
        private final Path clip=new Path();
        HeroArtworkView(Context context){super(context);android.graphics.Bitmap b=null;try{b=android.graphics.BitmapFactory.decodeResource(getResources(),R.mipmap.ic_launcher);}catch(Exception ignored){}bitmap=b;}
        @Override protected void onDraw(Canvas canvas){
            super.onDraw(canvas);if(bitmap==null)return;
            float w=getWidth(),h=getHeight();
            clip.reset();clip.addOval(new RectF(0,0,w,h),Path.Direction.CW);
            int save=canvas.save();canvas.clipPath(clip);
            // Crop the launcher artwork before its small bottom shortcut symbols.
            android.graphics.Rect src=new android.graphics.Rect(0,0,bitmap.getWidth(),(int)(bitmap.getHeight()*0.78f));
            canvas.drawBitmap(bitmap,src,new RectF(0,0,w,h),p);
            canvas.restoreToCount(save);
        }
    }

    private class TechStartButton extends Button {
        private final Paint p=new Paint(3);
        private final Path shape=new Path();
        TechStartButton(Context context){super(context);setGravity(Gravity.CENTER);setPadding(dp(8),0,dp(8),0);setIncludeFontPadding(false);}
        @Override protected void onDraw(Canvas canvas){
            float w=getWidth(),h=getHeight(),tip=dp(28),in=dp(5);
            shape.reset();shape.moveTo(tip, in);shape.lineTo(w-tip,in);shape.lineTo(w-in, h/2);shape.lineTo(w-tip,h-in);shape.lineTo(tip,h-in);shape.lineTo(in,h/2);shape.close();
            p.setStyle(Paint.Style.FILL);p.setShader(new android.graphics.LinearGradient(0,0,0,h,0xFFFFE65C,0xFFFFB800,android.graphics.Shader.TileMode.CLAMP));canvas.drawPath(shape,p);p.setShader(null);
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(2));p.setColor(0xFFFFE78A);p.setShadowLayer(dp(8),0,0,0xFFFFA000);canvas.drawPath(shape,p);p.clearShadowLayer();
            shape.reset();shape.moveTo(tip+in,in+dp(5));shape.lineTo(w-tip-in,in+dp(5));shape.lineTo(w-in-dp(6),h/2);shape.lineTo(w-tip-in,h-in-dp(5));shape.lineTo(tip+in,h-in-dp(5));shape.lineTo(in+dp(6),h/2);shape.close();
            p.setStrokeWidth(dp(1));p.setColor(0xFF9A6500);canvas.drawPath(shape,p);
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(3));p.setColor(0xFFFFB900);
            Path chevron=new Path();chevron.moveTo(dp(18),dp(10));chevron.lineTo(dp(8),h/2);chevron.lineTo(dp(18),h-dp(10));canvas.drawPath(chevron,p);
            chevron.reset();chevron.moveTo(w-dp(18),dp(10));chevron.lineTo(w-dp(8),h/2);chevron.lineTo(w-dp(18),h-dp(10));canvas.drawPath(chevron,p);
            super.onDraw(canvas);
        }
    }

    private class CircleTimerView extends View {
        private final Paint paint=new Paint(3);
        private final RectF frame=new RectF(), inner=new RectF();
        private final Path bevel=new Path();
        private String value="00:00", label="DESCANSO";
        private float progress=0f;
        CircleTimerView(Context c){super(c);}
        void setDisplay(String v,String l,float p){value=v;label=l;progress=Math.max(0f,Math.min(1f,p));invalidate();}
        @Override protected void onDraw(Canvas c){
            super.onDraw(c);
            float w=getWidth(),h=getHeight(),cut=dp(38),pad=dp(4);
            bevel.reset();bevel.moveTo(cut,pad);bevel.lineTo(w-cut,pad);bevel.lineTo(w-pad,cut);bevel.lineTo(w-pad,h-cut);bevel.lineTo(w-cut,h-pad);bevel.lineTo(cut,h-pad);bevel.lineTo(pad,h-cut);bevel.lineTo(pad,cut);bevel.close();
            paint.setStyle(Paint.Style.FILL);paint.setColor(0xFF070605);c.drawPath(bevel,paint);
            paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(2));paint.setColor(0xFFFFBD00);paint.setShadowLayer(dp(12),0,0,0xFFFFA000);c.drawPath(bevel,paint);paint.clearShadowLayer();
            Path innerPath=new Path();float inset=dp(13),ic=dp(30);
            innerPath.moveTo(ic,inset);innerPath.lineTo(w-ic,inset);innerPath.lineTo(w-inset,ic);innerPath.lineTo(w-inset,h-ic);innerPath.lineTo(w-ic,h-inset);innerPath.lineTo(ic,h-inset);innerPath.lineTo(inset,h-ic);innerPath.lineTo(inset,ic);innerPath.close();
            paint.setStrokeWidth(dp(1));paint.setColor(0xFF684819);c.drawPath(innerPath,paint);
            // Segmented illuminated side rails, following the broad curved timer housing.
            int segments=11;
            for(int side=0;side<2;side++){
                for(int i=0;i<segments;i++){
                    float t=i/(float)(segments-1);
                    float y=dp(52)+t*(h-dp(104));
                    float curve=(float)Math.sin(t*Math.PI)*dp(23);
                    float x=side==0?dp(37)+curve:w-dp(37)-curve;
                    float len=dp(16),th=dp(7);
                    paint.setStyle(Paint.Style.FILL);
                    paint.setColor(i<Math.round(progress*segments)?0xFFFFD02B:0xFF634719);
                    paint.setShadowLayer(dp(5),0,0,i<Math.round(progress*segments)?0xFFFFA000:0x00000000);
                    c.drawRoundRect(x-len/2,y-th/2,x+len/2,y+th/2,dp(3),dp(3),paint);
                    paint.clearShadowLayer();
                }
            }
            paint.setStyle(Paint.Style.STROKE);paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeWidth(dp(5));
            paint.setColor(0xFF50370F);c.drawArc(dp(45),dp(42),w-dp(45),h-dp(32),200,140,false,paint);
            paint.setColor(0xFFFFC400);paint.setShadowLayer(dp(9),0,0,0xFFFFA000);c.drawArc(dp(45),dp(42),w-dp(45),h-dp(32),200,140*progress,false,paint);paint.clearShadowLayer();
            float cx=w/2f;
            // Stopwatch glyph.
            float clockX=cx-dp(65),clockY=dp(39),rr=dp(8);
            paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(2));paint.setColor(0xFFFFC400);
            c.drawCircle(clockX,clockY,rr,paint);c.drawLine(clockX,clockY,clockX,clockY-dp(5),paint);c.drawLine(clockX,clockY,clockX+dp(4),clockY+dp(2),paint);
            c.drawLine(clockX-dp(3),clockY-dp(11),clockX+dp(3),clockY-dp(11),paint);
            paint.setStyle(Paint.Style.FILL);paint.setTextAlign(Paint.Align.CENTER);
            paint.setTypeface(android.graphics.Typeface.create("sans-serif",android.graphics.Typeface.BOLD_ITALIC));
            paint.setTextSize(dp(18));paint.setColor(0xFFF4EBDD);c.drawText(label,cx+dp(8),dp(46),paint);
            paint.setTypeface(android.graphics.Typeface.create("sans-serif-condensed",android.graphics.Typeface.BOLD_ITALIC));
            paint.setTextSize(Math.min(dp(82),h*0.48f));paint.setColor(0xFFFFC400);paint.setShadowLayer(dp(10),0,0,0xFFFFA000);c.drawText(value,cx,Math.min(h-dp(40),h*0.73f),paint);paint.clearShadowLayer();
            paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(2));paint.setColor(0xFFFFC400);c.drawLine(dp(28),h-dp(13),w-dp(28),h-dp(13),paint);
        }
        private float cx(){return getWidth()/2f;}
    }

    private View buildProgressPage(){
        LinearLayout root=column();
        TextView title=text("SEU PROGRESSO",22,Color.WHITE,true);title.setGravity(Gravity.CENTER);title.setPadding(0,dp(12),0,dp(4));root.addView(title);
        TextView hint=text("Deslize para a esquerda durante o treino para ver seu histórico.",12,0xFFB8A88F,false);
        hint.setGravity(Gravity.CENTER);hint.setPadding(0,0,0,dp(16));root.addView(hint);
        LinearLayout periodRow=new LinearLayout(this);periodRow.setOrientation(LinearLayout.HORIZONTAL);
        String[] labels={"7 DIAS","MÊS","ANO"};
        for(int i=0;i<3;i++){final int index=i;Button b=new Button(this);b.setText(labels[i]);b.setAllCaps(false);b.setTextSize(12);b.setOnClickListener(v->{progressPeriod=index;refreshProgress();});periodRow.addView(b,new LinearLayout.LayoutParams(0,dp(46),1));}
        root.addView(periodRow);
        LinearLayout summary=card();summary.setGravity(Gravity.CENTER);summary.setPadding(dp(14),dp(18),dp(14),dp(18));
        progressSummary=text("0 FLEXÕES",30,0xFFFFB000,true);progressSummary.setGravity(Gravity.CENTER);
        progressDetail=text("0 treinos concluídos",14,0xFFD8C5A3,false);progressDetail.setGravity(Gravity.CENTER);progressDetail.setPadding(0,dp(6),0,0);
        summary.addView(progressSummary);summary.addView(progressDetail);
        LinearLayout.LayoutParams sumP=new LinearLayout.LayoutParams(-1,-2);sumP.topMargin=dp(12);root.addView(summary,sumP);
        LinearLayout history=card();TextView h=text("HISTÓRICO DE TREINOS",14,Color.WHITE,true);history.addView(h);
        TextView desc=text("Cada treino concluído ou encerrado fica salvo neste aparelho.",12,0xFFB8A88F,false);desc.setPadding(0,dp(5),0,dp(8));history.addView(desc);
        LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(-1,-2);hp.topMargin=dp(12);root.addView(history,hp);
        return pageScroll(root);
    }
    private void refreshProgress(){
        if(progressSummary==null)return;
        JSONArray data=loadHistory();Calendar now=Calendar.getInstance();
        Calendar start=(Calendar)now.clone();
        if(progressPeriod==0){start.add(Calendar.DAY_OF_YEAR,-6);start.set(Calendar.HOUR_OF_DAY,0);start.set(Calendar.MINUTE,0);start.set(Calendar.SECOND,0);}
        else if(progressPeriod==1){start.set(Calendar.DAY_OF_MONTH,1);start.set(Calendar.HOUR_OF_DAY,0);start.set(Calendar.MINUTE,0);start.set(Calendar.SECOND,0);}
        else {start.set(Calendar.DAY_OF_YEAR,1);start.set(Calendar.HOUR_OF_DAY,0);start.set(Calendar.MINUTE,0);start.set(Calendar.SECOND,0);}
        int repsSum=0,workouts=0,best=0;long from=start.getTimeInMillis();
        for(int i=0;i<data.length();i++){JSONObject o=data.optJSONObject(i);if(o==null)continue;long time=o.optLong("time",0);if(time>=from&&time<=now.getTimeInMillis()){int n=o.optInt("reps",0);repsSum+=n;workouts++;best=Math.max(best,n);}}
        String period=progressPeriod==0?"nos últimos 7 dias":progressPeriod==1?"neste mês":"neste ano";
        progressSummary.setText(String.format(Locale.getDefault(),"%d FLEXÕES",repsSum));
        progressDetail.setText(workouts+" treinos "+period+"  •  melhor treino: "+best+" flexões");
        LinearLayout history=(LinearLayout)((LinearLayout)((ScrollView)pages.getChildAt(1)).getChildAt(0)).getChildAt(4);
        while(history.getChildCount()>2)history.removeViewAt(2);
        if(workouts==0){TextView empty=text("Ainda não há treinos registrados neste período.\\nComplete um treino para começar seu histórico.",13,0xFFB8A88F,false);empty.setPadding(0,dp(12),0,dp(4));history.addView(empty);}
        else{
            for(int i=data.length()-1;i>=0;i--){JSONObject o=data.optJSONObject(i);if(o==null||o.optLong("time",0)<from)continue;
                Calendar c=Calendar.getInstance();c.setTimeInMillis(o.optLong("time",0));
                String date=String.format(Locale.getDefault(),"%02d/%02d/%04d",c.get(Calendar.DAY_OF_MONTH),c.get(Calendar.MONTH)+1,c.get(Calendar.YEAR));
                TextView item=text("•  "+date+"     "+o.optInt("reps",0)+" flexões",14,Color.WHITE,false);item.setPadding(0,dp(8),0,dp(8));history.addView(item);
            }
        }
    }
    private JSONArray loadHistory(){
        try{return new JSONArray(getSharedPreferences("flex_history",MODE_PRIVATE).getString("sessions","[]"));}
        catch(Exception e){return new JSONArray();}
    }
    private void saveWorkout(){
        if(sessionSaved||completedReps<=0)return;
        try{JSONArray a=loadHistory();JSONObject o=new JSONObject();o.put("time",System.currentTimeMillis());o.put("reps",completedReps);o.put("sets",sets);a.put(o);
            getSharedPreferences("flex_history",MODE_PRIVATE).edit().putString("sessions",a.toString()).apply();sessionSaved=true;
        }catch(Exception ignored){}
    }

    private void addField(LinearLayout parent,String caption,String initial,boolean series){
        TextView label=text(caption,10,0xFFD8C5A3,true); label.setSingleLine(true);label.setGravity(Gravity.CENTER);label.setPadding(0,0,0,dp(5)); parent.addView(label);
        LinearLayout control=new LinearLayout(this);control.setGravity(Gravity.CENTER);control.setOrientation(LinearLayout.HORIZONTAL);control.setPadding(dp(2),dp(2),dp(2),dp(2));control.setBackground(round(0xFF303030,24));
        Button minus=new Button(this);minus.setText("−");minus.setTextSize(17);minus.setMinWidth(0);minus.setMinimumWidth(0);minus.setPadding(0,0,0,0);
        minus.setTextColor(0xFFFFC400);minus.setBackground(round(0xFF252525,24));
        EditText e=new EditText(this);e.setSingleLine(true);e.setInputType(2);e.setText(initial);
        e.setTextColor(Color.WHITE);e.setTextSize(18);e.setSelectAllOnFocus(true);e.setGravity(Gravity.CENTER);
        e.setPadding(0,0,0,0);e.setBackgroundColor(Color.TRANSPARENT);
        Button plus=new Button(this);plus.setText("+");plus.setTextSize(17);plus.setMinWidth(0);plus.setMinimumWidth(0);plus.setPadding(0,0,0,0);
        plus.setTextColor(0xFFFFC400);plus.setBackground(round(0xFF252525,24));
        LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(dp(28),dp(38));
        control.addView(minus,bp);LinearLayout.LayoutParams ep=new LinearLayout.LayoutParams(0,dp(38),1);ep.leftMargin=dp(2);ep.rightMargin=dp(2);control.addView(e,ep);control.addView(plus,new LinearLayout.LayoutParams(dp(28),dp(38)));
        parent.addView(control);
        int min=series?1:1,max=series?30:caption.startsWith("FLEXÕES")?200:600;
        int step=caption.startsWith("DESCANSO")?5:1;
        minus.setOnClickListener(v->{try{int n=Integer.parseInt(e.getText().toString());e.setText(String.valueOf(Math.max(min,n-step)));}catch(Exception ignored){}});
        plus.setOnClickListener(v->{try{int n=Integer.parseInt(e.getText().toString());e.setText(String.valueOf(Math.min(max,n+step)));}catch(Exception ignored){}});
        if(series)seriesInput=e;else if(caption.startsWith("FLEXÕES"))targetInput=e;else restInput=e;
    }

    private void startWorkout(){
        try { totalSets=Integer.parseInt(seriesInput.getText().toString()); target=Integer.parseInt(targetInput.getText().toString()); restSeconds=Integer.parseInt(restInput.getText().toString()); }
        catch(Exception e){ Toast.makeText(this,"Preencha todas as configurações.",Toast.LENGTH_SHORT).show(); return; }
        if(totalSets<1||totalSets>30||target<1||target>200||restSeconds<5||restSeconds>600){
            Toast.makeText(this,"Séries: 1 a 30. Flexões: 1 a 200. Descanso: 5 a 600 segundos.",Toast.LENGTH_LONG).show(); return;
        }
        reps=0; completedReps=0; sessionSaved=false; sets=1; down=false; workout=true; resting=false; countingEnabled=false; updateStats();
        setInputsEnabled(false); startButton.setEnabled(false); stopButton.setVisibility(View.VISIBLE); stopButton.setEnabled(true);
        statusText.setText("PREPARE-SE PARA COMEÇAR");
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
            cameraProvider=f.get();
            ImageAnalysis a=new ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build();
            a.setAnalyzer(cameraExecutor,this::analyze); cameraProvider.unbindAll();
            cameraProvider.bindToLifecycle(this,CameraSelector.DEFAULT_FRONT_CAMERA,a); cameraStarted=true;
            startCountdown();
        }catch(Exception e){statusText.setText("NÃO FOI POSSÍVEL ABRIR A CÂMERA");}},ContextCompat.getMainExecutor(this));
    }
    private void startCountdown(){
        countingEnabled=false; countdownSeconds=3;
        statusText.setText("PREPARE-SE");
        restCircle.setDisplay("3","PREPARE-SE",0f);
        if(countdownTick!=null)handler.removeCallbacks(countdownTick);
        countdownTick=new Runnable(){
            @Override public void run(){
                if(!workout)return;
                if(countdownSeconds>1){
                    countdownSeconds--;
                    restCircle.setDisplay(String.valueOf(countdownSeconds),"PREPARE-SE",0f);
                    handler.postDelayed(this,1000);
                }else{
                    countingEnabled=true;
                    restCircle.setDisplay("00:00","TREINO ATIVO",0f);
                    statusText.setText("VALENDO! PODE COMEÇAR");
                    vibrate();
                }
            }
        };
        handler.postDelayed(countdownTick,1000);
    }
    private void analyze(ImageProxy proxy){
        if(!processing.compareAndSet(false,true)){proxy.close();return;}
        android.media.Image media=proxy.getImage();
        if(media==null){processing.set(false);proxy.close();return;}
        detector.process(InputImage.fromMediaImage(media,proxy.getImageInfo().getRotationDegrees()))
            .addOnSuccessListener(p->{if(!workout||resting||!countingEnabled)return; Float a=armAngle(p); if(a==null)return;
                long now=System.currentTimeMillis(); if(a<95)down=true;
                else if(a>155&&down&&now-lastRepAt>500){down=false;lastRepAt=now;reps++;completedReps++;runOnUiThread(this::onRep);}
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
        restCircle.setDisplay(String.format(Locale.getDefault(),"%02d:%02d",remaining/60,remaining%60),"DESCANSO",restSeconds<=0?0f:(float)(restSeconds-remaining)/restSeconds);
        if(remaining<=0){resting=false;reps=0;sets++;updateStats();statusText.setText("VAMOS! PRÓXIMA SÉRIE");restCircle.setDisplay("00:00","DESCANSO",0f);vibrate();return;}
        remaining--; restTick=this::tickRest; handler.postDelayed(restTick,1000);
    }
    private void updateStats(){
        countText.setText(String.valueOf(reps));
        String progress=sets+"/"+totalSets;
        SpannableString styled=new SpannableString(progress);
        int slash=progress.indexOf("/");
        if(slash>=0){
            styled.setSpan(new ForegroundColorSpan(0xFF8BA99F),slash,slash+1,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            styled.setSpan(new RelativeSizeSpan(0.78f),slash,slash+1,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        setText.setText(styled);
    }
    private void finishWorkout(){
        workout=false;resting=false;countingEnabled=false;if(countdownTick!=null)handler.removeCallbacks(countdownTick);statusText.setText("TREINO CONCLUÍDO! PARABÉNS!");
        restCircle.setDisplay("✓","CONCLUÍDO",1f);
        vibrate(); if(cameraProvider!=null)cameraProvider.unbindAll();cameraStarted=false;
        setInputsEnabled(true);startButton.setEnabled(true);stopButton.setEnabled(false);stopButton.setVisibility(View.GONE);
    }
    private void vibrate(){
        try{Vibrator v=(Vibrator)getSystemService(Context.VIBRATOR_SERVICE);if(v!=null){
            if(Build.VERSION.SDK_INT>=26)v.vibrate(VibrationEffect.createOneShot(250,180));else v.vibrate(250);
        }}catch(Exception ignored){}
    }
    private void stopWorkout(){
        workout=false;resting=false;countingEnabled=false;if(countdownTick!=null)handler.removeCallbacks(countdownTick);if(restTick!=null)handler.removeCallbacks(restTick);if(cameraProvider!=null)cameraProvider.unbindAll();cameraStarted=false;
        setInputsEnabled(true);startButton.setEnabled(true);stopButton.setEnabled(false);stopButton.setVisibility(View.GONE);
        statusText.setText("TREINO ENCERRADO");restCircle.setDisplay("00:00","DESCANSO",0f);}
    @Override protected void onDestroy(){
        super.onDestroy();if(cameraProvider!=null)cameraProvider.unbindAll();
        if(detector!=null)detector.close();if(cameraExecutor!=null)cameraExecutor.shutdown();
        handler.removeCallbacksAndMessages(null);
    }
}