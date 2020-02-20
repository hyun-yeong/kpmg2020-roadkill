/*
 * Copyright 2016 The TensorFlow Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package demo.tensorflow.org.customvision_sample;

import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Typeface;

import android.media.ImageReader.OnImageAvailableListener;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.util.Size;
import android.util.TypedValue;
import android.view.Display;

import com.microsoft.cognitiveservices.speech.CancellationDetails;
import com.microsoft.cognitiveservices.speech.CancellationReason;
import com.microsoft.cognitiveservices.speech.ResultReason;
import com.microsoft.cognitiveservices.speech.SpeechConfig;
import com.microsoft.cognitiveservices.speech.SpeechRecognitionResult;
import com.microsoft.cognitiveservices.speech.SpeechRecognizer;
import com.microsoft.cognitiveservices.speech.SpeechSynthesisResult;
import com.microsoft.cognitiveservices.speech.SpeechSynthesizer;

import java.util.List;
import java.util.Vector;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import demo.tensorflow.org.customvision_sample.OverlayView.DrawCallback;
import demo.tensorflow.org.customvision_sample.env.BorderedText;
import demo.tensorflow.org.customvision_sample.env.Logger;

public class ObjectDetectorActivity extends CameraActivity implements OnImageAvailableListener {
    private static final Logger LOGGER = new Logger();

    private static final Size DESIRED_PREVIEW_SIZE = new Size(640, 480);
    private static final float TEXT_SIZE_DIP = 10;

    //Speech SDK
    private static String speechSubscriptionKey = "41bf04c4a128410bbb79bac741267696";
    private static String serviceRegion = "koreacentral";

    private SpeechConfig speechConfig;
    private SpeechSynthesizer synthesizer;

    private Integer sensorOrientation;
    private MSCognitiveServicesCustomVisionObjectDetector classifier;
    private BorderedText borderedText;

    //
    private Thread dialogThread;
    private Object lock;
    private AlertDialog dialog;
    private Runnable runnable;
    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        classifier = new MSCognitiveServicesCustomVisionObjectDetector(this);

        speechConfig = SpeechConfig.fromSubscription(speechSubscriptionKey, serviceRegion);



        // Alerting thread
        lock= new Object();

        runnable = new Runnable(){
            @Override
            public void run() {
                try {
                    // Note: this will block the UI thread, so eventually, you want to register for the event
                    synthesizer = new SpeechSynthesizer(speechConfig);
                    Future<SpeechSynthesisResult> task = synthesizer.SpeakTextAsync("Do you want to report?");
                    assert (task != null);

                    SpeechSynthesisResult result =task.get();
                    if (result.getReason() == ResultReason.SynthesizingAudioCompleted) {
                        LOGGER.i("speech success");
                    } else if (result.getReason() == ResultReason.Canceled) {
                        LOGGER.i("speech failed");
                    }
                    result.close();
                    synthesizer.close();
                } catch (Exception ex){
                    assert(false);
                }
                makeDecision();
            }
        };
    }

    public void makeDecision(){
        SpeechRecognizer reco = new SpeechRecognizer(speechConfig);
        assert(reco != null);
        System.out.println("Say something");
        Future<SpeechRecognitionResult> task = reco.recognizeOnceAsync();
        assert(task != null);

        try {
            SpeechRecognitionResult result = task.get();
            assert(result != null);

            if (result.getReason() == ResultReason.RecognizedSpeech) {
                System.out.println("RECOGNIZED: Text=" + result.getText());
                if(result.getText().equals("Yes.")){
                    System.out.println(" yes recognized.");
                    dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick();
                    System.out.println(dialog.isShowing());
                }else if(result.getText().equals("No.")){
                    System.out.println(" no recognized.");
                    dialog.getButton(DialogInterface.BUTTON_NEGATIVE).performClick();
                    System.out.println(dialog.isShowing());
                }
            }
            else if (result.getReason() == ResultReason.NoMatch) {
                System.out.println("NOMATCH: Speech could not be recognized.");
            }
            else if (result.getReason() == ResultReason.Canceled) {
                CancellationDetails cancellation = CancellationDetails.fromResult(result);
                System.out.println("CANCELED: Reason=" + cancellation.getReason());

                if (cancellation.getReason() == CancellationReason.Error) {
                    System.out.println("CANCELED: ErrorCode=" + cancellation.getErrorCode());
                    System.out.println("CANCELED: ErrorDetails=" + cancellation.getErrorDetails());
                    System.out.println("CANCELED: Did you update the subscription info?");
                }

            }

            result.close();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }

        reco.close();
    }

    // Ask to user reporting the case
    public void showAlert(){
        AlertDialog.Builder builder = new AlertDialog.Builder(ObjectDetectorActivity.this);
        dialog=builder.setTitle("Roadkill detected")
                .setMessage("Do you want to report?")
                .setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        if(dialogThread.isAlive()){
                            dialogThread.interrupt();
                        }
                        LOGGER.i("YES","ok button clicked");
                        releaseLock();
                    }
                })
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        if(dialogThread.isAlive()){
                            dialogThread.interrupt();
                        }
                        LOGGER.i("NO","cancle button clicked");
                        releaseLock();
                    }
                })
                .create();
        dialog.show();
    }
    void releaseLock(){
        synchronized (lock){
            lock.notify();
            Log.e("TAG","notify");
        }
    }



    @Override
    public synchronized void onStop() {
        super.onStop();

        if (classifier != null) {
            classifier.close();
        }
    }

    @Override
    protected int getLayoutId() {
        return R.layout.camera_connection_fragment;
    }

    @Override
    protected Size getDesiredPreviewFrameSize() {
        return DESIRED_PREVIEW_SIZE;
    }

    @Override
    public void onPreviewSizeChosen(final Size size, final int rotation) {
        final float textSizePx = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, TEXT_SIZE_DIP, getResources().getDisplayMetrics());
        borderedText = new BorderedText(textSizePx);
        borderedText.setTypeface(Typeface.MONOSPACE);

        previewWidth = size.getWidth();
        previewHeight = size.getHeight();

        final Display display = getWindowManager().getDefaultDisplay();
        final int screenOrientation = display.getRotation();

        LOGGER.i("Sensor orientation: %d, Screen orientation: %d", rotation, screenOrientation);

        sensorOrientation = rotation + screenOrientation;

        LOGGER.i("Initializing at size %dx%d", previewWidth, previewHeight);
        rgbFrameBitmap = Bitmap.createBitmap(previewWidth, previewHeight, Config.ARGB_8888);

        yuvBytes = new byte[3][];

        addCallback(
                new DrawCallback() {
                    @Override
                    public void drawCallback(final Canvas canvas) {
                        renderDebug(canvas);
                    }
                });
    }

    protected void processImageRGBbytes(int[] rgbBytes) {
        rgbFrameBitmap.setPixels(rgbBytes, 0, previewWidth, 0, 0, previewWidth, previewHeight);

        runInBackground(
                new Runnable() {
                    @Override
                    public void run() {
                        final long startTime = SystemClock.uptimeMillis();
                        List<ObjectDetector.BoundingBox> results = classifier.detectObjects(rgbFrameBitmap, sensorOrientation);
                        lastProcessingTimeMs = SystemClock.uptimeMillis() - startTime;

                        LOGGER.i("Detect: %s", results);
                        if (resultsView == null) {
                            resultsView = findViewById(R.id.results);
                        }
                        resultsView.setResults(results);


                        // if killed animal detected, detecting thread stop and ask to user
                        if(results !=null){
                            for(int j=0; j<results.size();j++) {
                                if (results.get(j).getClassIdentifier().equals("kill")) {
                                    runOnUiThread(new Runnable(){
                                        @Override
                                        public void run() {
                                            showAlert();
                                        }
                                    });
                                    // alert Thread run
                                    dialogThread = new Thread(runnable);
                                    dialogThread.start();


                                    synchronized (lock){
                                        try {
                                            lock.wait();
                                        } catch (InterruptedException e) {
                                            e.printStackTrace();
                                        }
                                    }
                                    break;
                                }
                            }
                        }

                        requestRender();
                        computing = false;
                        if (postInferenceCallback != null) {
                            postInferenceCallback.run();
                        }
                    }
                });

    }

    @Override
    public void onSetDebug(boolean debug) {
    }

    private void renderDebug(final Canvas canvas) {
        if (!isDebug()) {
            return;
        }

        final Vector<String> lines = new Vector<String>();
        lines.add("Inference time: " + lastProcessingTimeMs + "ms");
        borderedText.drawLines(canvas, 10, canvas.getHeight() - 10, lines);
    }
}
