package com.takusemba.rtmppublisher;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.view.Surface;
import android.view.WindowManager;

import java.io.IOException;
import java.util.List;

class CameraClient {

  private Context context;
  private Camera camera;
  private CameraMode mode;
  private SurfaceTexture surfaceTexture;
  private int cameraOrientation;
  private Camera.Size cameraSize;
  private boolean cameraWhFlipped;

  private int desiredWidth;
  private int desiredHeight;

  int getResultWidth() { return cameraSize.width; }
  int getResultHeight() { return cameraSize.height; }

  CameraClient(Context context, CameraMode mode, int desiredWidth, int desiredHeight) {
    this.context = context;
    this.mode = mode;
    this.desiredWidth = desiredWidth;
    this.desiredHeight = desiredHeight;
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
    List<Camera.Size> sizes = params.getSupportedPreviewSizes();
    for (Camera.Size size : sizes) {
      if (size.width == desiredWidth && size.height == desiredHeight) {
        params.setPreviewSize(size.width, size.height);
        isDesiredSizeFound = true;
      }
    }
    if (!isDesiredSizeFound) {
      for (Camera.Size size : sizes) {
        if (size.width == desiredWidth && size.height >= desiredHeight) {
          params.setPreviewSize(size.width, size.height);
          isDesiredSizeFound = true;
        }
      }
    }
    if (!isDesiredSizeFound) {
      for (Camera.Size size : sizes) {
        if (size.height == desiredHeight && size.width >= desiredWidth) {
          params.setPreviewSize(size.width, size.height);
          isDesiredSizeFound = true;
        }
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
    cameraSize = params.getPreviewSize();

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
    cameraWhFlipped = degrees == 90 || degrees == 270;
    camera.setDisplayOrientation(degrees);
  }
}
