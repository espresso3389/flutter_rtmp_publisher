package com.takusemba.rtmppublisher;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.util.Size;
import android.view.Surface;
import android.view.WindowManager;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

class CameraClient {

  private Context context;
  private CameraManager cameraManager;
  private CameraDevice camera;
  private CameraMode mode;
  private SurfaceTexture surfaceTexture;
  private CameraCaptureSession captureSession;
  private CameraCharacteristics chars;
  private StreamConfigurationMap confMap;
  private int cameraOrientation;
  private boolean cameraOpened;
  private int requestedWidth;
  private int requestedHeight;
  private Size resultSize;
  private float[] rotationMatrix;

  boolean isCameraWidthHeightSwapped() { return rotationMatrix != null && rotationMatrix[0] == 0; }
  int getResultWidth() { return isCameraWidthHeightSwapped() ? resultSize.getHeight() : resultSize.getWidth(); }
  int getResultHeight() { return isCameraWidthHeightSwapped() ? resultSize.getWidth() : resultSize.getHeight(); }

  CameraClient(Context context) {
    this.context = context;
    this.cameraManager = (CameraManager)context.getSystemService(Context.CAMERA_SERVICE);
    this.mode = CameraMode.BACK;
    this.requestedWidth = 640;
    this.requestedHeight = 480;
    this.cameraOpened = false;
  }

  void open(CameraMode newMode, int width, int height) {
    initCamera(newMode);
    if (camera == null) {
      throw new IllegalStateException("camera not found");
    }

    this.requestedWidth = width;
    this.requestedHeight = height;
    resultSize = calculatePreviewSize(width, height);

    CaptureRequest.Builder builder = createCaptureRequestBuilder();
    if (surfaceTexture != null)
      startPreview(surfaceTexture, builder);
  }

  void swap() {
    open(mode.swap(), requestedWidth, requestedHeight);
  }

  private boolean initCamera(CameraMode newMode) {
    final int facing = newMode == CameraMode.BACK ? CameraCharacteristics.LENS_FACING_BACK : CameraCharacteristics.LENS_FACING_FRONT;
    try {
      for (String cameraId : cameraManager.getCameraIdList()) {
        chars = cameraManager.getCameraCharacteristics(cameraId);
        if (chars.get(CameraCharacteristics.LENS_FACING) == facing) {
          mode = newMode;
          return initCameraWith(cameraId);
        }
      }
    } catch (CameraAccessException e) {
      e.printStackTrace();
    }
    return false;
  }

  private boolean initCameraWith(String cameraId) throws CameraAccessException {
    close();
    final CameraClient cc = this;
    cameraManager.openCamera(cameraId, new CameraDevice.StateCallback() {
      @Override
      public void onOpened(@NonNull CameraDevice camera) {
        cc.camera = camera;
      }

      @Override
      public void onDisconnected(@NonNull CameraDevice camera) {
        cc.close();
      }

      @Override
      public void onError(@NonNull CameraDevice camera, int error) {
        cc.close();
      }
    }, null);

    confMap = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
    cameraOrientation = chars.get(CameraCharacteristics.SENSOR_ORIENTATION);
    cameraOpened = true;
    return true;
  }

  CameraMode getCameraMode() {
    return mode;
  }

  void startPreview(SurfaceTexture surfaceTexture, CaptureRequest.Builder builder) {
    this.surfaceTexture = surfaceTexture;
    try {
      surfaceTexture.setDefaultBufferSize(resultSize.getWidth(), resultSize.getHeight());
      Surface surface = new Surface(surfaceTexture);
      builder.addTarget(surface);
      final CaptureRequest captureRequest = builder.build();

      List<Surface> surfaces = new ArrayList<Surface>();
      surfaces.add(surface);
      camera.createCaptureSession(surfaces, new CameraCaptureSession.StateCallback() {
        @Override
        public void onConfigured(@NonNull CameraCaptureSession session) {
          try {
            captureSession = session;
            session.setRepeatingRequest(captureRequest, null, null);
          } catch (CameraAccessException e) {
            e.printStackTrace();
          }
        }

        @Override
        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
        }
      }, null);

    } catch (CameraAccessException e) {
      e.printStackTrace();
    }
  }

  void close() {
    if (captureSession != null) {
      captureSession.close();
      captureSession = null;
    }
    if (camera != null) {
      camera.close();
      camera = null;
    }
    cameraOpened = false;
  }

  private CaptureRequest.Builder createCaptureRequestBuilder() {
    CaptureRequest.Builder builder = camera.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);

    if (doesSupportFocusModeContinuousVideo()) {
      builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO);
    }
    builder.set(CaptureRequest.CONTROL_CAPTURE_INTENT, CaptureRequest.CONTROL_CAPTURE_INTENT_VIDEO_RECORD);

    rotationMatrix = setRotation();
    return builder;
  }

  private boolean doesSupportFocusModeContinuousVideo() {
    final int[] focusModes = chars.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES);
    for (int i = 0; i < focusModes.length; i++) {
      if (focusModes[i] == CameraCharacteristics.CONTROL_AF_MODE_CONTINUOUS_VIDEO) {
        return true;
      }
    }
    return false;
  }

  public void onOrientationChanged(int orientation) {
    if (camera != null)
      rotationMatrix = setRotation();
  }

  private int getDeviceRotation() {
    WindowManager windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
    return windowManager.getDefaultDisplay().getRotation();
  }


  private Size calculatePreviewSize(int requestedWidth, int requestedHeight) {
    final Size[] sizes = confMap.getOutputSizes(SurfaceTexture.class);
    Size previewSize = null;
    for (Size size : sizes) {
      if (size.getWidth() == requestedWidth && size.getHeight() == requestedHeight) {
        previewSize = size;
        break;
      }
    }
    if (previewSize == null) {
      for (Size size : sizes) {
        if (size.getWidth() == requestedWidth && size.getHeight() >= requestedHeight) {
          previewSize = size;
          break;
        }
      }
    }
    if (previewSize == null) {
      for (Size size : sizes) {
        if (size.getHeight() == requestedHeight && size.getWidth() >= requestedWidth) {
          previewSize = size;
          break;
        }
      }
    }
    return previewSize;
  }

  private float[] setRotation() {
    int rotation = getDeviceRotation();
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
    boolean whflipped = degrees == 90 || degrees == 270;

    float[] transform =  new float[] {
      0, 0, 0, 0,
      0, 0, 0, 0,
      0, 0, 1, 0, // FIXME: Should we use z??
      0, 0, 0, 1
    };


    //Matrix matrix = new Matrix();
    //matrix.postRotate(

    final int texWidth = whflipped ? requestedHeight : requestedWidth;
    final int texHeight = whflipped ? requestedWidth : requestedHeight;
    final int xInd = whflipped ? 1 : 0;
    final int yInd = whflipped ? 4 : 5;
    if (resultSize.getWidth() / texWidth < resultSize.getHeight() / texHeight) {
      transform[yInd]  *= (float)resultSize.getWidth() * texHeight / texWidth / resultSize.getHeight();
    } else {
      transform[xInd] *= (float)resultSize.getHeight() * texWidth / texHeight / resultSize.getWidth();
    }

    return transform;
  }
}
