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

import android.graphics.Color;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
// ContentResolver dependency
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.mediapipe.formats.proto.LandmarkProto;
import com.google.mediapipe.formats.proto.LandmarkProto.Landmark;
import com.google.mediapipe.formats.proto.LandmarkProto.NormalizedLandmark;
import com.google.mediapipe.solutioncore.CameraInput;
import com.google.mediapipe.solutioncore.SolutionGlSurfaceView;
import com.google.mediapipe.solutions.hands.HandLandmark;
import com.google.mediapipe.solutions.hands.Hands;
import com.google.mediapipe.solutions.hands.HandsOptions;
import com.google.mediapipe.solutions.hands.HandsResult;

import java.util.List;
import java.util.Objects;



/** Main activity of MediaPipe Hands app. */
public class MainActivity extends AppCompatActivity {
  private static final String TAG = "MainActivity";

  private Hands hands;
  // Run the pipeline and the model inference on GPU or CPU.
  private static final boolean RUN_ON_GPU = true;

  private enum InputSource {
    UNKNOWN,
    CAMERA,
  }
  private InputSource inputSource = InputSource.UNKNOWN;

  // the selfie camera will be shown on start-up
  private CameraInput.CameraFacing cameraFace = CameraInput.CameraFacing.FRONT;

  // Live camera demo UI and camera components.
  private CameraInput cameraInput;

  private SolutionGlSurfaceView<HandsResult> glSurfaceView;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);
    Objects.requireNonNull(getSupportActionBar()).hide();
    setupLiveDemoUiComponents();
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

    FloatingActionButton cameraFaceButton = findViewById(R.id.cameraFaceButton);
    cameraFaceButton.setOnClickListener(
            v -> {
              if (cameraFace == CameraInput.CameraFacing.FRONT) {
                cameraFace = CameraInput.CameraFacing.BACK;
              } else {
                cameraFace = CameraInput.CameraFacing.FRONT;
              }
              cameraInput.close();
              this.onResume();
            });

    stopCurrentPipeline();
    setupStreamingModePipeline(InputSource.CAMERA);
//    mediapipeStatus = MediapipeStatus.RUNNING;
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

    TextView recognizedGesture = findViewById(R.id.recognizedGesture);

//    String gestureString;
    hands.setResultListener(
        handsResult -> {
          logWristLandmark(handsResult, /*showPixelValues=*/ false);

          runOnUiThread(() -> {
            String gestureString = handGestureCalculator(handsResult.multiHandLandmarks());

            recognizedGesture.setText(gestureString);
            recognizedGesture.setTextColor(Color.parseColor("#FFFFFF"));
            recognizedGesture.invalidate();
            recognizedGesture.requestLayout();
            recognizedGesture.bringToFront();
            Log.i(TAG, "Gesture recognized " + gestureString);
          });



          glSurfaceView.setRenderData(handsResult);
          glSurfaceView.requestRender();
        });

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
  }

  private void startCamera() {
    cameraInput.start(
        this,
        hands.getGlContext(),
        cameraFace, // CameraInput.CameraFacing.FRONT or ...BACK
        glSurfaceView.getWidth(),
        glSurfaceView.getHeight());
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
          thumbIsOpen = true;
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
        return "victory hand";
      } else if (firstFingerIsOpen && !secondFingerIsOpen && !thirdFingerIsOpen && fourthFingerIsOpen && !thumbIsOpen) {
        return "sign of the horns";
      } else if (firstFingerIsOpen && !secondFingerIsOpen && !thirdFingerIsOpen && fourthFingerIsOpen && thumbIsOpen) {
        return "love-you gesture";
      } else if (firstFingerIsOpen && !secondFingerIsOpen && !thirdFingerIsOpen && !fourthFingerIsOpen && !thumbIsOpen){
        return "Index pointing";
      } else if (!firstFingerIsOpen && secondFingerIsOpen && thirdFingerIsOpen && fourthFingerIsOpen && isThumbNearFirstFinger(landmarkList.get(4), landmarkList.get(8))) {
        return "ok hand"; // open fingers have to be stretched
      } else if (!firstFingerIsOpen && secondFingerIsOpen && !thirdFingerIsOpen && !fourthFingerIsOpen) { // thumb state doesn't matter
        return "middle finger";
//      } else if (!firstFingerIsOpen && !secondFingerIsOpen && !thirdFingerIsOpen && fourthFingerIsOpen && thumbIsOpen) {
//        return "call me hand"; // Barely works
        // This one does not have a fitting emoji
//      } else if (thumbIsOpen && firstFingerIsOpen && !secondFingerIsOpen && !thirdFingerIsOpen && !fourthFingerIsOpen) {
//        return "The L";
//      } else if (thumbIsOpen && !firstFingerIsOpen && !secondFingerIsOpen && !thirdFingerIsOpen && !fourthFingerIsOpen && isThumbNearFirstFinger(landmarkList.get(4), landmarkList.get(8))) {
//        return "Thumbs Up Sign"; // Barely works
      } else if (!thumbIsOpen && !firstFingerIsOpen && !secondFingerIsOpen && !thirdFingerIsOpen && !fourthFingerIsOpen ) {
        return "raised fist";
      } else {
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

}
