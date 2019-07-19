package com.takusemba.rtmppublisher;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.hardware.SensorManager;
import android.util.Log;
import android.view.Display;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.WindowManager;

import java.io.IOException;

import static android.content.Context.WINDOW_SERVICE;

class CameraClient {

  private Context context;
  private Camera camera;
  private CameraMode mode;
  private SurfaceTexture surfaceTexture;
  private int cameraOrientation;
  private Camera.Size cameraSize;

  private static final int desiredHeight = 1280;
  private static final int desiredWidth = 720;

  public int getCameraOrientation() { return cameraOrientation; }

  CameraClient(Context context, CameraMode mode) {
    this.context = context;
    this.mode = mode;
  }

  Camera.Parameters open() {
    initCamera();
    if (camera == null) {
      throw new IllegalStateException("camera not found");
    }

    Camera.Parameters params = camera.getParameters();
    setParameters(params);
    return params;
  }

  void swap() {
    close();

    mode = mode.swap();
    initCamera();
    if (camera == null) {
      throw new IllegalStateException("camera not found");
    }
    startPreview(surfaceTexture);
  }

  void startPreview(SurfaceTexture surfaceTexture) {
    this.surfaceTexture = surfaceTexture;
    try {
      camera.setPreviewTexture(surfaceTexture);
      camera.startPreview();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  void close() {
    if (camera != null) {
      camera.stopPreview();
      camera.release();
      camera = null;
    }
  }

  private void initCamera() {
    Camera.CameraInfo info = new Camera.CameraInfo();

    int numCameras = Camera.getNumberOfCameras();
    for (int i = 0; i < numCameras; i++) {
      Camera.getCameraInfo(i, info);
      if (info.facing == mode.getId()) {
        camera = Camera.open(i);
        cameraOrientation = info.orientation;
        setRotation();
        break;
      }
    }
  }

  private void setParameters(Camera.Parameters params) {
    boolean isDesiredSizeFound = false;
    for (Camera.Size size : params.getSupportedPreviewSizes()) {
      if (size.width == desiredWidth && size.height == desiredHeight) {
        params.setPreviewSize(desiredWidth, desiredHeight);
        isDesiredSizeFound = true;
      }
    }

    if (!isDesiredSizeFound) {
      Camera.Size ppsfv = params.getPreferredPreviewSizeForVideo();
      if (ppsfv != null) {
        params.setPreviewSize(ppsfv.width, ppsfv.height);
      }
    }

    params.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
    params.setRecordingHint(true);

    camera.setParameters(params);

    int[] fpsRange = new int[2];
    params.getPreviewFpsRange(fpsRange);

    setRotation();
  }

  public void onOrientationChanged() {
    setRotation();
  }


  private void setRotation() {
    WindowManager windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
    int rotation = windowManager.getDefaultDisplay().getRotation();
    int degrees = 0;
    switch (rotation) {
      case Surface.ROTATION_0:
        degrees = 0;
        break;
      case Surface.ROTATION_90:
        degrees = 90;
        break;
      case Surface.ROTATION_180:
        degrees = 180;
        break;
      case Surface.ROTATION_270:
        degrees = 270;
        break;
    }

    if (mode == CameraMode.FRONT) {
      degrees = (cameraOrientation + degrees) % 360;
      degrees = (360 - degrees) % 360;
    } else {
      degrees = (cameraOrientation - degrees + 360) % 360;
    }
    camera.setDisplayOrientation(degrees);
  }
}
