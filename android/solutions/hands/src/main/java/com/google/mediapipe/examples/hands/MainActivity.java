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
import android.content.ContentValues;
import android.graphics.Color;
import android.os.Bundle;

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

import android.os.CountDownTimer;
import android.provider.MediaStore;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;
// ContentResolver dependency
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mediapipe.components.CameraXPreviewHelper;
import com.google.mediapipe.formats.proto.LandmarkProto;
import com.google.mediapipe.formats.proto.LandmarkProto.Landmark;
import com.google.mediapipe.formats.proto.LandmarkProto.NormalizedLandmark;
import com.google.mediapipe.solutioncore.CameraInput;
import com.google.mediapipe.solutioncore.SolutionGlSurfaceView;
import com.google.mediapipe.solutions.hands.HandLandmark;
import com.google.mediapipe.solutions.hands.Hands;
import com.google.mediapipe.solutions.hands.HandsOptions;
import com.google.mediapipe.solutions.hands.HandsResult;

import java.lang.reflect.Field;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;


/** Main activity of MediaPipe Hands app. */
public class MainActivity extends AppCompatActivity implements PopupMenu.OnMenuItemClickListener {
  private static final String TAG = "MainActivity";

  private Hands hands;
  // Run the pipeline and the model inference on GPU or CPU.
  private static final boolean RUN_ON_GPU = true;

  private enum InputSource {
    UNKNOWN,
    CAMERA,
  }

  // Dictionary mapping icons with gestures
  private static final EnumMap<HandGesture,Integer> gestureEmojis= new EnumMap<>(Map.of(
          HandGesture.VICTORY, 0x270C,
          HandGesture.HORNS, 0x1F918,
          HandGesture.LOVE, 0x1F91F,
          HandGesture.INDEX, 0x261D,
          HandGesture.OK, 0x1f44c,
          HandGesture.MIDDLE, 0x1f595,
          HandGesture.CALL, 0x1F919,
          HandGesture.THUMBS, 0x1F44D,
          HandGesture.FIST, 0x270A
  ));


  private InputSource inputSource = InputSource.UNKNOWN;

  // the selfie camera will be shown on start-up
  private CameraInput.CameraFacing cameraFaceMediapipe = CameraInput.CameraFacing.FRONT;
  private int cameraFaceCameraX = CameraSelector.LENS_FACING_FRONT;

  // Live camera demo UI and camera components.
  private CameraInput cameraInput;

  private SolutionGlSurfaceView<HandsResult> glSurfaceView;

  // Gesture pausing between recognition and shot
  private TextView timer;

  // Emoji view for activationGesture
  private TextView activationEmoji;

  // Counter var for previous to the shot
  public int counter;

  // Last Gesture registered
  public HandGesture lastGesture;

  // Activation Gesture for shot. Default VICTORY HAND
  public HandGesture activationGesture= HandGesture.VICTORY;

  // Denotes activation of the counter previous to the shot
  public static boolean captureFlag=false;

  // init settings screen
  public PrefScreen prefFragment = new PrefScreen();


  private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);
    Objects.requireNonNull(getSupportActionBar()).hide();
    setupLiveDemoUiComponents();

  }



  private void replaceFragment(Fragment fragment) {

    // used to open the settings screen
    FragmentManager supportFragmentManager = getSupportFragmentManager();

    supportFragmentManager.beginTransaction()
            .replace(android.R.id.content, fragment)
            .setReorderingAllowed(true)
            .addToBackStack(null)
            .commit();

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

    FloatingActionButton btn = findViewById(R.id.gestureButton);
    btn.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {

        //TODO: hide main view
//        PopupMenu popup = new PopupMenu(MainActivity.this, v);
//        popup.setOnMenuItemClickListener( MainActivity.this);
//        popup.inflate(R.menu.menu_gestures);
//        popup.show();
        replaceFragment(prefFragment);
      }
    });

    timer= (TextView) findViewById(R.id.timer);
    activationEmoji= (TextView) findViewById(R.id.gestureEmoji);
    activationEmoji.setVisibility(View.VISIBLE);
    activationEmoji.setText(getEmoji(0x270C));
    activationEmoji.invalidate();
    activationEmoji.requestLayout();
    activationEmoji.bringToFront();


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

  @Override
  public boolean onMenuItemClick(MenuItem item) {
    Toast.makeText(this, "Selected gesture for shot: " +item.getTitle(), Toast.LENGTH_SHORT).show();
    switch (item.getItemId()) {
      case R.id.victory:
        activationGesture= HandGesture.VICTORY;
        activationEmoji.setText(getEmoji(gestureEmojis.get(HandGesture.VICTORY)));
        return true;
      case R.id.index:
        activationGesture= HandGesture.INDEX;
        activationEmoji.setText(getEmoji(gestureEmojis.get(HandGesture.INDEX)));
        return true;
      case R.id.horns:
        activationGesture= HandGesture.HORNS;
        activationEmoji.setText(getEmoji(gestureEmojis.get(HandGesture.HORNS)));
        return true;
      case R.id.ok:
        activationGesture= HandGesture.OK;
        activationEmoji.setText(getEmoji(gestureEmojis.get(HandGesture.OK)));
        return true;
      case R.id.fist:
        activationGesture= HandGesture.FIST;
        activationEmoji.setText(getEmoji(gestureEmojis.get(HandGesture.FIST)));
        return true;
      case R.id.call:
        activationGesture= HandGesture.CALL;
        activationEmoji.setText(getEmoji(gestureEmojis.get(HandGesture.CALL)));
        return true;
      case R.id.love:
        activationGesture= HandGesture.LOVE;
        activationEmoji.setText(getEmoji(gestureEmojis.get(HandGesture.LOVE)));
        return true;
      case R.id.middle:
        activationGesture= HandGesture.MIDDLE;
        activationEmoji.setText(getEmoji(gestureEmojis.get(HandGesture.MIDDLE)));
        return true;
      default:
        return false;
    }
  }

  // Unicode emoji to String
  private String getEmoji(int unicode){
    return new String(Character.toChars(unicode));
  }


  /** Sets up core workflow for streaming mode. */
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
    FrameLayout frameLayout = findViewById(R.id.preview_display_layout);
    frameLayout.removeAllViewsInLayout();
    frameLayout.addView(glSurfaceView);
    glSurfaceView.setVisibility(View.VISIBLE);
    frameLayout.requestLayout();


    TextView recognizedGesture = findViewById(R.id.recognizedGesture);

    hands.setResultListener( handsResult -> {

      glSurfaceView.setRenderData(handsResult);
      glSurfaceView.requestRender();

      logWristLandmark(handsResult, /*showPixelValues=*/ false);

      runOnUiThread(() -> {
        if(!captureFlag) {
          lastGesture= HandGesture.UNDEFINED;
          String gestureString = handGestureCalculator(handsResult.multiHandLandmarks());
          recognizedGesture.setText(gestureString);
          recognizedGesture.setTextColor(Color.parseColor("#FFFFFF"));
          recognizedGesture.invalidate();
          recognizedGesture.requestLayout();
          recognizedGesture.bringToFront();
          Log.i(TAG, "Camera activation");


          if (lastGesture == activationGesture) {
            captureFlag=true;
            new CountDownTimer(3000, 1000) {
              public void onTick(long millisUntilFinished) {
                timer.setVisibility(View.VISIBLE);
//                    recognizedGesture.setVisibility(View.GONE);
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
//                    recognizedGesture.setVisibility(View.VISIBLE);

                lastGesture= HandGesture.UNDEFINED;
                new CountDownTimer(2000, 1000) {
                  public void onTick(long l) {
                    Log.i(TAG, "extra timer is called");

                  }
                  public void onFinish() {
                    Log.i(TAG, "extra timer is called");
                    captureFlag=false;
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

    // required for taking pictures
    imageCapture = new ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build();

    cameraProviderFuture = ProcessCameraProvider.getInstance(this);
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
    }
    catch(Exception e) {
      e.printStackTrace();
      preview =  (new androidx.camera.core.Preview.Builder()).build();
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

  ////////////////////////////
  //// GESTURE DETECTION ////
  //////////////////////////

  private String handGestureCalculator(List<LandmarkProto.NormalizedLandmarkList> multiHandLandmarks) {
    if (multiHandLandmarks.isEmpty()) {
      return "No hand deal";
    }
    boolean thumbIsOpen = false;
    boolean firstFingerIsOpen = false;
    boolean secondFingerIsOpen = false;
    boolean thirdFingerIsOpen = false;
    boolean fourthFingerIsOpen = false;

    //FIXME: something is wrong with the calculation I think
    // Original implementation from github gist, has problems depending on which side of the hand face the camera
    for (LandmarkProto.NormalizedLandmarkList landmarks : multiHandLandmarks) {

      List<NormalizedLandmark> landmarkList = landmarks.getLandmarkList();
      float pseudoFixKeyPoint = landmarkList.get(2).getX();
      if (pseudoFixKeyPoint < landmarkList.get(9).getX()) {
        if (landmarkList.get(3).getX() < pseudoFixKeyPoint && landmarkList.get(4).getX() < pseudoFixKeyPoint) {
          thumbIsOpen = true;
        }
      }
      if (pseudoFixKeyPoint > landmarkList.get(9).getX()) {
        if (landmarkList.get(3).getX() > pseudoFixKeyPoint && landmarkList.get(4).getX() > pseudoFixKeyPoint) {
          thumbIsOpen = false;
        }
      }
      Log.d(TAG, "pseudoFixKeyPoint == " + pseudoFixKeyPoint + "\nlandmarkList.get(2).getX() == " + landmarkList.get(2).getX()
              + "\nlandmarkList.get(4).getX() = " + landmarkList.get(4).getX());
      pseudoFixKeyPoint = landmarkList.get(6).getY();
      if (landmarkList.get(7).getY() < pseudoFixKeyPoint && landmarkList.get(8).getY() < landmarkList.get(7).getY()) {
        firstFingerIsOpen = true;
      }
      pseudoFixKeyPoint = landmarkList.get(10).getY();
      if (landmarkList.get(11).getY() < pseudoFixKeyPoint && landmarkList.get(12).getY() < landmarkList.get(11).getY()) {
        secondFingerIsOpen = true;
      }
      pseudoFixKeyPoint = landmarkList.get(14).getY();
      if (landmarkList.get(15).getY() < pseudoFixKeyPoint && landmarkList.get(16).getY() < landmarkList.get(15).getY()) {
        thirdFingerIsOpen = true;
      }
      pseudoFixKeyPoint = landmarkList.get(18).getY();
      if (landmarkList.get(19).getY() < pseudoFixKeyPoint && landmarkList.get(20).getY() < landmarkList.get(19).getY()) {
        fourthFingerIsOpen = true;
      }

      // TODO: writing this in a nested fashion might be better, right now reaching "On the Phone" is difficult
      //  The order probably should be 1st -> 2nd -> 3rd -> 4th -> thumb (last because it is the most complex here)
      /* Hand gesture recognition
      * First = Index finger
      *  Second = Middle finger
      *  Third = Ring finger
      *  Fourth = Pinky
      *
      *  All gestures are represented by standard emojis, their strings correspond to the emoji names
      * */
      if (firstFingerIsOpen && secondFingerIsOpen && !thirdFingerIsOpen && !fourthFingerIsOpen && !thumbIsOpen) {
        lastGesture= HandGesture.VICTORY;
        return getEmoji(gestureEmojis.get(HandGesture.VICTORY));
      } else if (firstFingerIsOpen && !secondFingerIsOpen && !thirdFingerIsOpen && fourthFingerIsOpen && !thumbIsOpen) {
        lastGesture= HandGesture.HORNS;
        return getEmoji(gestureEmojis.get(HandGesture.HORNS));
      } else if (thumbIsOpen && firstFingerIsOpen && !secondFingerIsOpen && !thirdFingerIsOpen && fourthFingerIsOpen) {
        lastGesture= HandGesture.LOVE;
        return getEmoji(gestureEmojis.get(HandGesture.LOVE));
      } else if (!fourthFingerIsOpen && firstFingerIsOpen && !secondFingerIsOpen && !thirdFingerIsOpen && !thumbIsOpen){
        lastGesture= HandGesture.INDEX;
        return getEmoji(gestureEmojis.get(HandGesture.INDEX));
      } else if (!firstFingerIsOpen && secondFingerIsOpen && thirdFingerIsOpen && fourthFingerIsOpen && isThumbNearFirstFinger(landmarkList.get(4), landmarkList.get(8))) {
        lastGesture= HandGesture.OK;
        return getEmoji(gestureEmojis.get(HandGesture.OK)); // open fingers have to be stretched
      } else if (!firstFingerIsOpen && secondFingerIsOpen && !thirdFingerIsOpen && !fourthFingerIsOpen) { // thumb state doesn't matter
        lastGesture= HandGesture.MIDDLE;
        return getEmoji(gestureEmojis.get(HandGesture.MIDDLE));
      } else if (!firstFingerIsOpen && !secondFingerIsOpen && !thirdFingerIsOpen && fourthFingerIsOpen && thumbIsOpen) {
        lastGesture= HandGesture.CALL;
        return getEmoji(gestureEmojis.get(HandGesture.CALL)); // Barely works

        // This one does not have a fitting emoji
//      } else if (thumbIsOpen && firstFingerIsOpen && !secondFingerIsOpen && !thirdFingerIsOpen && !fourthFingerIsOpen) {
//        return "The L";

      } else if (!fourthFingerIsOpen && thumbIsOpen && !firstFingerIsOpen && !secondFingerIsOpen && !thirdFingerIsOpen && isThumbNearFirstFinger(landmarkList.get(4), landmarkList.get(8))) {
        lastGesture= HandGesture.THUMBS;
        return getEmoji(gestureEmojis.get(HandGesture.THUMBS)); // Barely works
      } else if (!thumbIsOpen && !firstFingerIsOpen && !secondFingerIsOpen && !thirdFingerIsOpen && !fourthFingerIsOpen ) {
        lastGesture= HandGesture.FIST;
        return getEmoji(gestureEmojis.get(HandGesture.FIST));
      } else {
        lastGesture= HandGesture.UNDEFINED;
        String info = "thumbIsOpen " + thumbIsOpen + " firstFingerIsOpen " + firstFingerIsOpen
                + " secondFingerIsOpen " + secondFingerIsOpen +
                " thirdFingerIsOpen " + thirdFingerIsOpen + " fourthFingerIsOpen " + fourthFingerIsOpen;
        Log.d(TAG, "handGestureCalculator: == " + info);
        return "unrecognized gesture";
      }
    }
    return "___";
  }

  private boolean isThumbNearFirstFinger(LandmarkProto.NormalizedLandmark point1, LandmarkProto.NormalizedLandmark point2) {
    double distance = getEuclideanDistanceAB(point1.getX(), point1.getY(), point2.getX(), point2.getY());
    return distance < 0.1;
  }

  private double getEuclideanDistanceAB(double a_x, double a_y, double b_x, double b_y) {
    double dist = Math.pow(a_x - b_x, 2) + Math.pow(a_y - b_y, 2);
    return Math.sqrt(dist);
  }

  private String getMultiHandLandmarksDebugString(List<LandmarkProto.NormalizedLandmarkList> multiHandLandmarks) {
    if (multiHandLandmarks.isEmpty()) {
      return "No hand landmarks";
    }
    StringBuilder multiHandLandmarksStr = new StringBuilder("Number of hands detected: " + multiHandLandmarks.size() + "\n");
    int handIndex = 0;
    for (LandmarkProto.NormalizedLandmarkList landmarks : multiHandLandmarks) {
      multiHandLandmarksStr.append("\t#Hand landmarks for hand[").append(handIndex).append("]: ").append(landmarks.getLandmarkCount()).append("\n");
      int landmarkIndex = 0;
      for (NormalizedLandmark landmark : landmarks.getLandmarkList()) {
        multiHandLandmarksStr.append("\t\tLandmark [").append(landmarkIndex).append("]: (").append(landmark.getX()).append(", ").append(landmark.getY()).append(", ").append(landmark.getZ()).append(")\n");
        ++landmarkIndex;
      }
      ++handIndex;
    }
    return multiHandLandmarksStr.toString();
  }

  ///////////////////////////////
  //// CAMERA: TAKE PICTURE ////
  /////////////////////////////

  private ImageCapture imageCapture;
//  private VideoCapture videoCapture;

  private void capturePhoto() {
    long unixTime = System.currentTimeMillis()/1000;
    String timestamp = Long.toString(unixTime);

    ContentValues contentValues = new ContentValues();
    contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, timestamp);
    contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg");

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

}
