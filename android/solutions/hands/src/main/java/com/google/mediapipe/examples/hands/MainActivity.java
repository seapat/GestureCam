// Copyright 2021 The MediaPipe Authors.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.mediapipe.examples.hands;

import android.annotation.SuppressLint;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.SharedPreferences;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import android.widget.Button;
import android.widget.ImageView;

// ContentResolver dependency
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mediapipe.components.CameraXPreviewHelper;
import com.google.mediapipe.formats.proto.LandmarkProto.Landmark;
import com.google.mediapipe.formats.proto.LandmarkProto.NormalizedLandmark;
import com.google.mediapipe.solutioncore.CameraInput;
import com.google.mediapipe.solutioncore.SolutionGlSurfaceView;
import com.google.mediapipe.solutions.hands.HandLandmark;
import com.google.mediapipe.solutions.hands.Hands;
import com.google.mediapipe.solutions.hands.HandsOptions;
import com.google.mediapipe.solutions.hands.HandsResult;

import java.io.FileNotFoundException;
import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;


/**
 * Main activity of MediaPipe Hands app.
 */
public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    // Run the pipeline and the model inference on GPU or CPU.
    private static final boolean RUN_ON_GPU = true;
    // Denotes activation of the counter previous to the shot
    public static boolean captureFlag = false;
    // Counter var for previous to the shot
    public int counter;
    // Last Gesture registered
    public HandGesture lastGesture;
    // Activation Gesture for shot. Default VICTORY HAND
    public HandGesture activationGesture = HandGesture.VICTORY;
    // init settings screen
    public PrefScreen prefFragment = new PrefScreen();
    private Hands hands;
    private InputSource inputSource = InputSource.UNKNOWN;
    // the selfie camera will be shown on start-up
    private CameraInput.CameraFacing cameraFaceMediapipe = CameraInput.CameraFacing.FRONT;
    private int cameraFaceCameraX = CameraSelector.LENS_FACING_FRONT;
    // Live camera demo UI and camera components.
    private CameraInput cameraInput;
    private SolutionGlSurfaceView<HandsResult> glSurfaceView;
    // Gesture pausing between recognition and shot
    private TextView timer;
    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
    private ImageCapture imageCapture;

    private String curGesture = HandGesture.UNDEFINED.toString();


    @Override
    public void onBackPressed() {
        if (getSupportFragmentManager().getBackStackEntryCount() > 0) {
            findViewById(R.id.ParentLayout).setVisibility(View.VISIBLE);
            getSupportFragmentManager().popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
        } else {
            super.onBackPressed();
        }
    }

    private void replaceFragment(Fragment fragment) {

        findViewById(R.id.ParentLayout).setVisibility(View.GONE);


        // used to open the settings screen
        FragmentManager supportFragmentManager = getSupportFragmentManager();
        supportFragmentManager.beginTransaction()
                .replace(android.R.id.content, fragment)
                .setReorderingAllowed(true)
                .addToBackStack(null)
                .commit();

    }
  private Bitmap bmp_save = null;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);
    Objects.requireNonNull(getSupportActionBar()).hide();
    setupLiveDemoUiComponents();

    assignViews();

    _btn_map_depot.setOnClickListener(new Button.OnClickListener() {
      @Override
      public void onClick(View v) {
          if(!captureFlag) {
              Intent intent = new Intent();
              /* 开启Pictures画面Type设定为image */
              intent.setType("image/*");
              /* 使用Intent.ACTION_GET_CONTENT这个Action */
              intent.setAction(Intent.ACTION_GET_CONTENT);
              /* 取得相片后返回本画面 */
              startActivityForResult(intent, 1);
              _btn_save_img.setVisibility(View.VISIBLE);
              _btn_save_cen.setVisibility(View.VISIBLE);
          }
      }
    });

    _btn_save_img.setOnClickListener(new Button.OnClickListener() {
      @Override
      public void onClick(View v) {
        _iv.setImageBitmap(null);
        _btn_save_img.setVisibility(View.INVISIBLE);
        _btn_save_cen.setVisibility(View.INVISIBLE);
        _btn_map_depot.setVisibility(View.INVISIBLE);
        captureFlag = false;

        long unixTime = System.currentTimeMillis() / 1000;
        String timestamp = Long.toString(unixTime);
        MediaStore.Images.Media.insertImage(getContentResolver(), bmp_save, timestamp, "description");
        Toast.makeText(MainActivity.this, "Photo has been saved successfully to " + MediaStore.Images.Media.EXTERNAL_CONTENT_URI.getPath(), Toast.LENGTH_SHORT).show();
      }
    });

    _btn_save_cen.setOnClickListener(new Button.OnClickListener(){

        @Override
        public void onClick(View view) {
            _iv.setImageBitmap(null);
            _btn_save_img.setVisibility(View.INVISIBLE);
            _btn_save_cen.setVisibility(View.INVISIBLE);
            _btn_map_depot.setVisibility(View.INVISIBLE);
            captureFlag = false;
        }
    });
  }

    Executor getExecutor() {
        return ContextCompat.getMainExecutor(this);
    }


    @Override
    protected void onResume() {
        super.onResume();
        if (inputSource == InputSource.CAMERA) {
            // Restarts the camera and the opengl surface rendering.
            cameraInput = new CameraInput(this);

            cameraInput.setNewFrameListener(textureFrame -> hands.send(textureFrame));
            glSurfaceView.post(this::startCamera);
            glSurfaceView.setVisibility(View.VISIBLE);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (inputSource == InputSource.CAMERA) {
            glSurfaceView.setVisibility(View.GONE);
            cameraInput.close();
        }
    }


  /** Sets up the UI components for the live demo with camera input. */
  private void setupLiveDemoUiComponents() {

      FloatingActionButton btn = findViewById(R.id.settingsButton);
      btn.setOnClickListener(new View.OnClickListener() {
          @Override
          public void onClick(View v) {
              if(!captureFlag)
              replaceFragment(prefFragment);
          }
      });

      timer = (TextView) findViewById(R.id.timer);


      FloatingActionButton cameraFaceButton = findViewById(R.id.cameraFaceButton);
      cameraFaceButton.setOnClickListener(
              v -> {
                  if (cameraFaceMediapipe == CameraInput.CameraFacing.FRONT) {
                      cameraFaceMediapipe = CameraInput.CameraFacing.BACK;
                      cameraFaceCameraX = CameraSelector.LENS_FACING_BACK;
                  } else {
                      cameraFaceMediapipe = CameraInput.CameraFacing.FRONT;
                      cameraFaceCameraX = CameraSelector.LENS_FACING_FRONT;
                  }
                  cameraInput.close();
                  this.onResume();
              });

      FloatingActionButton takePictureButton = findViewById(R.id.takePictureButton);
      takePictureButton.setOnClickListener(button -> capturePhoto());

      stopCurrentPipeline();
      setupStreamingModePipeline(InputSource.CAMERA);
  }

    ///////////////////////////////
    //// SETTINGS: GESTURE SELECTION ////
    /////////////////////////////


    // Unicode emoji to String
    private String getEmoji(int unicode) {
        return new String(Character.toChars(unicode));
    }


    /**
     * Sets up core workflow for streaming mode.
     */
    private void setupStreamingModePipeline(InputSource inputSource) {
        this.inputSource = inputSource;
        // Initializes a new MediaPipe Hands solution instance in the streaming mode.
        hands =
                new Hands(
                        this,
                        HandsOptions.builder()
                                .setStaticImageMode(false)
                                .setMaxNumHands(1)
                                .setRunOnGpu(RUN_ON_GPU)
                                .build());
        hands.setErrorListener((message, e) -> Log.e(TAG, "MediaPipe Hands error:" + message));

        if (inputSource == InputSource.CAMERA) {
            cameraInput = new CameraInput(this);
            cameraInput.setNewFrameListener(textureFrame -> hands.send(textureFrame));
        }

        // Initializes a new Gl surface view with a user-defined HandsResultGlRenderer.
        glSurfaceView =
                new SolutionGlSurfaceView<>(this, hands.getGlContext(), hands.getGlMajorVersion());
        glSurfaceView.setSolutionResultRenderer(new HandsResultGlRenderer());
        glSurfaceView.setRenderInputImage(true);

        // The runnable to start camera after the gl surface view is attached.
        // For video input source, videoInput.start() will be called when the video uri is available.
        if (inputSource == InputSource.CAMERA) {
            glSurfaceView.post(this::startCamera);
        }

        // Updates the preview layout.
        FrameLayout constraintLayout = findViewById(R.id.preview_display_layout);
        constraintLayout.removeAllViewsInLayout();
        constraintLayout.addView(glSurfaceView);
        glSurfaceView.setVisibility(View.VISIBLE);
        constraintLayout.requestLayout();

        hands.setResultListener(handsResult -> {

            TextView recognizedGesture = findViewById(R.id.recognizedGesture);

            glSurfaceView.setRenderData(handsResult);
            glSurfaceView.requestRender();

            logWristLandmark(handsResult, /*showPixelValues=*/ false);

            runOnUiThread(() -> {
                if (!captureFlag) {
                    lastGesture = HandGesture.UNDEFINED;
                    lastGesture = GestureDetect.handGestureCalculator(handsResult.multiHandLandmarks(), lastGesture);
                    try {
                        recognizedGesture.setText(getEmoji(GestureDetect.gestureEmojis.get(lastGesture)));
                        curGesture = (String) recognizedGesture.getText();
                    } catch (Exception e) {
                        recognizedGesture.setText("");
                        e.printStackTrace();
                    }

                    recognizedGesture.setTextColor(Color.parseColor("#FFFFFF"));
                    recognizedGesture.invalidate();
                    recognizedGesture.requestLayout();
                    recognizedGesture.bringToFront();
                    Log.i(TAG, "Camera activation");

                    SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
                    Set<String> selectedGesturesHex = sharedPrefs.getStringSet("emoji_pref", null);
                    Set<Integer> selectedGesturesInt = new HashSet<>();
                    try {
                        selectedGesturesHex.forEach(x -> selectedGesturesInt.add(
                                Integer.parseInt(x.replaceFirst("0x", ""), 16)));
                    } catch (Exception e) {
                        e.printStackTrace();
                    }


                    if (selectedGesturesInt.contains(GestureDetect.gestureEmojis.get(lastGesture))) {
                        captureFlag = true;
                        new CountDownTimer(3000, 1000) {
                            public void onTick(long millisUntilFinished) {
                                timer.setVisibility(View.VISIBLE);
                                timer.setText(String.valueOf(1 + millisUntilFinished / 1000));
                                timer.setTextColor(Color.parseColor("#FFFFFF"));
                                timer.invalidate();
                                timer.requestLayout();
                                timer.bringToFront();
                                counter++;
                            }

                            public void onFinish() {
                                capturePhoto();
                                counter = 0;
                                timer.setVisibility(View.GONE);
                                lastGesture = HandGesture.UNDEFINED;
                                new CountDownTimer(2000, 1000) {
                                    public void onTick(long l) {
                                        Log.i(TAG, "extra timer is called");

                                    }

                                    public void onFinish() {
                                        Log.i(TAG, "extra timer is called");
                                        captureFlag = false;
                                    }
                                }.start();
                            }
                        }.start();
                    }
                }
            });


        });
    }

    private void startCamera() {
        cameraInput.start(
                this,
                hands.getGlContext(),
                cameraFaceMediapipe, // CameraInput.CameraFacing.FRONT or ...BACK
                glSurfaceView.getWidth(),
                glSurfaceView.getHeight());

    cameraProviderFuture = ProcessCameraProvider.getInstance(this);

    // required for taking pictures
    imageCapture = new ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build();


    cameraProviderFuture.addListener(() -> {
      try {
        ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
        startCameraHelper(cameraProvider);
      } catch (ExecutionException | InterruptedException e) {
        e.printStackTrace();
      }
    }, getExecutor());
  }

    @SuppressLint("RestrictedApi")
    private void startCameraHelper(ProcessCameraProvider cameraProvider) {
        cameraProvider.unbindAll();
        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(cameraFaceCameraX)
                .build();

        Preview preview;
        try {
            // REFLECTION! We are accessing the Preview object that is created by mediapipe, 2 layers of wrappers and all declared private...
            // https://stackoverflow.com/questions/1196192/how-to-read-the-value-of-a-private-field-from-a-different-class-in-java
            Field f1 = cameraInput.getClass().getDeclaredField("cameraHelper"); //potential NoSuchFieldException
            f1.setAccessible(true);
            CameraXPreviewHelper cameraHelper = (CameraXPreviewHelper) f1.get(cameraInput);
            assert cameraHelper != null;
            Field f2 = cameraHelper.getClass().getDeclaredField("preview"); //potential NoSuchFieldException
            f2.setAccessible(true);
            preview = (Preview) f2.get(cameraHelper);
            assert preview != null;
            Log.e(TAG, "Reflection works, accessing the Preview: " + preview);
        } catch (Exception e) {
            e.printStackTrace();
            preview = (new androidx.camera.core.Preview.Builder()).build();
        }

        cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture);
    }

    private void stopCurrentPipeline() {
        if (cameraInput != null) {
            cameraInput.setNewFrameListener(null);
            cameraInput.close();
        }
        if (glSurfaceView != null) {
            glSurfaceView.setVisibility(View.GONE);
        }
        if (hands != null) {
            hands.close();
        }
    }

    private void logWristLandmark(HandsResult result, boolean showPixelValues) {
        if (result.multiHandLandmarks().isEmpty()) {
            return;
        }
        NormalizedLandmark wristLandmark =
                result.multiHandLandmarks().get(0).getLandmarkList().get(HandLandmark.WRIST);
        // For Bitmaps, show the pixel values. For texture inputs, show the normalized coordinates.
        if (showPixelValues) {
            int width = result.inputBitmap().getWidth();
            int height = result.inputBitmap().getHeight();
            Log.i(
                    TAG,
                    String.format(
                            "MediaPipe Hand wrist coordinates (pixel values): x=%f, y=%f",
                            wristLandmark.getX() * width, wristLandmark.getY() * height));
        } else {
            Log.i(
                    TAG,
                    String.format(
                            "MediaPipe Hand wrist normalized coordinates (value range: [0, 1]): x=%f, y=%f",
                            wristLandmark.getX(), wristLandmark.getY()));
        }
        if (result.multiHandWorldLandmarks().isEmpty()) {
            return;
        }
        Landmark wristWorldLandmark =
                result.multiHandWorldLandmarks().get(0).getLandmarkList().get(HandLandmark.WRIST);
        Log.i(
                TAG,
                String.format(
                        "MediaPipe Hand wrist world coordinates (in meters with the origin at the hand's"
                                + " approximate geometric center): x=%f m, y=%f m, z=%f m",
                        wristWorldLandmark.getX(), wristWorldLandmark.getY(), wristWorldLandmark.getZ()));
      }


    ///////////////////////////////
    //// CAMERA: TAKE PICTURE ////
    /////////////////////////////


    private enum InputSource {
        UNKNOWN,
        CAMERA,
    }

  private void capturePhoto() {
    long unixTime = System.currentTimeMillis()/1000;
    String timestamp = Long.toString(unixTime);

    ContentValues contentValues = new ContentValues();
    contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, timestamp);
    contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg");
    _btn_map_depot.setVisibility(View.VISIBLE);

    imageCapture.takePicture(
            new ImageCapture.OutputFileOptions.Builder(
                    getContentResolver(),
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    contentValues
            ).build(),
            getExecutor(),
            new ImageCapture.OnImageSavedCallback() {
              @Override
              public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                Toast.makeText(MainActivity.this, "Photo has been saved successfully to " + MediaStore.Images.Media.EXTERNAL_CONTENT_URI.getPath(), Toast.LENGTH_SHORT).show();
              }

              @Override
              public void onError(@NonNull ImageCaptureException exception) {
                Toast.makeText(MainActivity.this, "Error saving photo: " + exception.getMessage(), Toast.LENGTH_SHORT).show();
              }

            }

    );
  }


  private Button _btn_map_depot;
  private ImageView _iv;
  private Button _btn_save_img;
  private Button _btn_save_cen;

  private void assignViews() {
    _btn_map_depot = (Button) findViewById(R.id.btn_map_depot);
    _iv = (ImageView) findViewById(R.id.iv);
    _btn_save_img = (Button) findViewById(R.id.btn_save_img);
    _btn_save_cen = (Button) findViewById(R.id.btn_save_cen);
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    Bitmap bmp = null;
      if(data == null){
          _btn_map_depot.setVisibility(View.INVISIBLE);
          _btn_save_cen.setVisibility(View.INVISIBLE);
          _btn_save_img.setVisibility(View.INVISIBLE);
          captureFlag = false;
          return;
      }
    if (resultCode == RESULT_OK) {
      Uri uri = data.getData();
      Log.e("uri", uri.toString());
      ContentResolver cr = this.getContentResolver();
      try {
        bmp = BitmapFactory.decodeStream(cr.openInputStream(uri));
        bmp = adjustPhotoRotation(bmp, 0);
        Bitmap bmp_save = DrawingUtils.drawTextToLeftBottom(this, bmp, curGesture, 40, Color.RED, 20, 20);
        /* 将Bitmap设定到ImageView */
        _iv.setImageBitmap(bmp_save);
        this.bmp_save = bmp_save;
        _btn_map_depot.setVisibility(View.INVISIBLE);
        captureFlag = true;
      } catch (FileNotFoundException e) {
        Log.e("Exception", e.getMessage(), e);
      }


    }
    super.onActivityResult(requestCode, resultCode, data);
  }

  Bitmap adjustPhotoRotation(Bitmap bm, final int orientationDegree) {
    Matrix m = new Matrix();
    m.setRotate(orientationDegree, (float) bm.getWidth() / 2, (float) bm.getHeight() / 2);
    try {
      Bitmap bm1 = Bitmap.createBitmap(bm, 0, 0, bm.getWidth(), bm.getHeight(), m, true);
      return bm1;
    } catch (OutOfMemoryError ex) {
    }
    return null;

  }

}