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

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.opengl.GLES20;
import android.util.Pair;

import androidx.appcompat.widget.AppCompatImageView;
import com.google.mediapipe.formats.proto.LandmarkProto;
import com.google.mediapipe.formats.proto.LandmarkProto.NormalizedLandmark;
import com.google.mediapipe.solutions.hands.Hands;
import com.google.mediapipe.solutions.hands.HandsResult;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;

/** An ImageView implementation for displaying {@link HandsResult}. */
public class HandsResultImageView extends AppCompatImageView {
  private static final String TAG = "HandsResultImageView";

  private static final int LEFT_HAND_CONNECTION_COLOR = Color.parseColor("#30FF30");
  private static final int RIGHT_HAND_CONNECTION_COLOR = Color.parseColor("#FF3030");
  private static final int CONNECTION_THICKNESS = 8; // Pixels
  private static final int LEFT_HAND_HOLLOW_CIRCLE_COLOR = Color.parseColor("#30FF30");
  private static final int RIGHT_HAND_HOLLOW_CIRCLE_COLOR = Color.parseColor("#FF3030");
  private static final int HOLLOW_CIRCLE_WIDTH = 5; // Pixels
  private static final int LEFT_HAND_LANDMARK_COLOR = Color.parseColor("#FF3030");
  private static final int RIGHT_HAND_LANDMARK_COLOR = Color.parseColor("#30FF30");
  private static final int LANDMARK_RADIUS = 10; // Pixels
  private Bitmap latest;

  public HandsResultImageView(Context context) {
    super(context);
    setScaleType(AppCompatImageView.ScaleType.FIT_CENTER);
  }

  /**
   * Sets a {@link HandsResult} to render.
   *
   * @param result a {@link HandsResult} object that contains the solution outputs and the input
   *     {@link Bitmap}.
   */
  public void setHandsResult(HandsResult result) {
    if (result == null) {
      return;
    }
    Bitmap bmInput = result.inputBitmap();
    int width = bmInput.getWidth();
    int height = bmInput.getHeight();
    latest = Bitmap.createBitmap(width, height, bmInput.getConfig());
    Canvas canvas = new Canvas(latest);

    canvas.drawBitmap(bmInput, new Matrix(), null);
    int numHands = result.multiHandLandmarks().size();
    for (int i = 0; i < numHands; ++i) {
      drawLandmarksOnCanvas(
          result.multiHandLandmarks().get(i).getLandmarkList(),
          result.multiHandedness().get(i).getLabel().equals("Left"),
          canvas,
          width,
          height);
    }
  }

  /** Updates the image view with the latest {@link HandsResult}. */
  public void update() {
    postInvalidate();
    if (latest != null) {
      setImageBitmap(latest);
    }
  }

  private List<Pair<Float,Float>> getSquareVertex(List<NormalizedLandmark> handLandmarkList){
    float max_X = handLandmarkList.get(0).getX();
    float min_X = handLandmarkList.get(0).getX();
    float max_Y = handLandmarkList.get(0).getX();
    float min_Y = handLandmarkList.get(0).getX();


    List<Pair<Float,Float>> vertex=new ArrayList<Pair<Float,Float>>();

    for (NormalizedLandmark landmark : handLandmarkList) {
      if (max_X < landmark.getX())
        max_X = landmark.getX();
      if (min_X > landmark.getX())
        min_X = landmark.getX();

      if (max_Y < landmark.getY())
        max_Y = landmark.getY();
      if (min_Y > landmark.getY())
        min_Y = landmark.getY();
    }

    vertex.add(new Pair<Float,Float>(min_X,min_Y));
    vertex.add(new Pair<Float,Float>(max_X,min_Y));
    vertex.add(new Pair<Float,Float>(max_X,max_Y));
    vertex.add(new Pair<Float,Float>(min_X,max_Y));

    return vertex;
  }


  private void drawLandmarksOnCanvas(
      List<NormalizedLandmark> handLandmarkList,
      boolean isLeftHand,
      Canvas canvas,
      int width,
      int height) {

    List<Pair<Float,Float>> squareVertex=getSquareVertex(handLandmarkList);
    // Draw connections.
    for (int i = 0; i < 4; i++) {
      Paint connectionPaint = new Paint();
      connectionPaint.setColor(
              isLeftHand ? LEFT_HAND_CONNECTION_COLOR : RIGHT_HAND_CONNECTION_COLOR);
      connectionPaint.setStrokeWidth(CONNECTION_THICKNESS);
      Pair<Float,Float> start = squareVertex.get(i);
      Pair<Float,Float> end = squareVertex.get((i+1)%4);
      canvas.drawLine(
              start.first * width,
              start.second * height,
              end.first * width,
              end.second * height,
              connectionPaint);
    }
    /*Paint landmarkPaint = new Paint();
    landmarkPaint.setColor(isLeftHand ? LEFT_HAND_LANDMARK_COLOR : RIGHT_HAND_LANDMARK_COLOR);
    // Draws landmarks.
    for (LandmarkProto.NormalizedLandmark landmark : handLandmarkList) {
      canvas.drawCircle(
          landmark.getX() * width, landmark.getY() * height, LANDMARK_RADIUS, landmarkPaint);
    }
    // Draws hollow circles around landmarks.
    landmarkPaint.setColor(
        isLeftHand ? LEFT_HAND_HOLLOW_CIRCLE_COLOR : RIGHT_HAND_HOLLOW_CIRCLE_COLOR);
    landmarkPaint.setStrokeWidth(HOLLOW_CIRCLE_WIDTH);
    landmarkPaint.setStyle(Paint.Style.STROKE);
    for (LandmarkProto.NormalizedLandmark landmark : handLandmarkList) {
      canvas.drawCircle(
          landmark.getX() * width,
          landmark.getY() * height,
          LANDMARK_RADIUS + HOLLOW_CIRCLE_WIDTH,
          landmarkPaint);
    }*/
  }
}
