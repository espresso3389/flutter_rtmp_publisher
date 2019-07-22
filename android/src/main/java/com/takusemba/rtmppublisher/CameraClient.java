package com.takusemba.rtmppublisher;

import android.content.Context;
import android.graphics.SurfaceTexture;
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
import java.util.Arrays;
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
  private OnCameraReady onCameraReady;

  boolean isCameraWidthHeightSwapped() { return rotationMatrix != null && rotationMatrix[0] == 0; }

  Size getResultSize() { return isCameraWidthHeightSwapped() ? new Size(resultSize.getHeight(), resultSize.getWidth()) : resultSize; }

  public interface OnCameraReady {
    void onCameraReady(CameraClient camera);
  }


  CameraClient(Context context, OnCameraReady onCameraReady) {
    this.context = context;
    this.cameraManager = (CameraManager)context.getSystemService(Context.CAMERA_SERVICE);
    this.mode = CameraMode.BACK;
    this.requestedWidth = 640;
    this.requestedHeight = 480;
    this.cameraOpened = false;
    this.onCameraReady = onCameraReady;
  }

  void open(final CameraMode newMode, final int width, final int height) {
    final int facing = newMode == CameraMode.BACK ? CameraCharacteristics.LENS_FACING_BACK : CameraCharacteristics.LENS_FACING_FRONT;
    try {
      for (String cameraId : cameraManager.getCameraIdList()) {
        chars = cameraManager.getCameraCharacteristics(cameraId);
        if (chars.get(CameraCharacteristics.LENS_FACING) == facing) {
          close();
          final CameraClient cc = this;
          cameraManager.openCamera(cameraId, new CameraDevice.StateCallback() {
            @Override
            public void onOpened(@NonNull CameraDevice camera) {
              cc.camera = camera;
              mode = newMode;
              confMap = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
              cameraOrientation = chars.get(CameraCharacteristics.SENSOR_ORIENTATION);
              cameraOpened = true;

              requestedWidth = width;
              requestedHeight = height;
              resultSize = calculatePreviewSize(width, height);

              if (onCameraReady != null)
                onCameraReady.onCameraReady(cc);

              if (surfaceTexture != null)
                startPreview(surfaceTexture);
            }

            @Override
            public void onDisconnected(@NonNull CameraDevice camera) {
              if (camera != null)
                camera.close();
            }

            @Override
            public void onError(@NonNull CameraDevice camera, int error) {
              if (camera != null)
                camera.close();
            }
          }, null);
        }
      }
    } catch (CameraAccessException e) {
      e.printStackTrace();
    }
  }

  void swap() {
    open(mode.swap(), requestedWidth, requestedHeight);
  }

  CameraMode getCameraMode() {
    return mode;
  }

  void startPreview(SurfaceTexture surfaceTexture) {
    this.surfaceTexture = surfaceTexture;
    if (camera == null)
      return;
    try {
      surfaceTexture.setDefaultBufferSize(resultSize.getWidth(), resultSize.getHeight());
      Surface surface = new Surface(surfaceTexture);
      CaptureRequest.Builder builder = createCaptureRequestBuilder();
      builder.addTarget(surface);
      final CaptureRequest captureRequest = builder.build();

      List<Surface> surfaces = new ArrayList<Surface>();
      surfaces.add(surface);
      camera.createCaptureSession(surfaces, new CameraCaptureSession.StateCallback() {
        @Override
        public void onConfigured(@NonNull CameraCaptureSession session) {
          try {
            if (camera == null) {
              return; // ???
            }
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

  private CaptureRequest.Builder createCaptureRequestBuilder() throws CameraAccessException {
    CaptureRequest.Builder builder = camera.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);

    if (doesSupportFocusModeContinuousVideo()) {
      builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO);
    }
    builder.set(CaptureRequest.CONTROL_CAPTURE_INTENT, CaptureRequest.CONTROL_CAPTURE_INTENT_VIDEO_RECORD);

    rotationMatrix = getTextureRotationMatrix();
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
      rotationMatrix = getTextureRotationMatrix();
  }

  private int getDeviceRotation() {
    WindowManager windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
    return windowManager.getDefaultDisplay().getRotation();
  }

  /**
   * Calculate camera preview {@link SurfaceTexture} size based on camera's preview sizes.
   * @param requestedWidth
   * @param requestedHeight
   * @return
   */
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

  /**
   * Precalculated 4x4 rotation matrix used by {@link CameraClient#getRotationMatrixByDegrees} function.
   */
  private static final float[] transforms = new float[] {
    1, 0, 0, 0, 0, -1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, // 0
    1, 0, 0, 0, 0, -1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, // 90
    0, -1, 0, 0, -1, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, // 180
    -1, 0, 0, 0, 0, -1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1 // 270
  };

  /**
   * Get 4x4 rotation matrix for surface texture clockwise rotation of specified degrees
   * @param degrees
   * @return
   */
  private static float[] getRotationMatrixByDegrees(int degrees) {
    final int index = (degrees / 90) * 16;
    return Arrays.copyOfRange(transforms, index, index + 16);
  }

  /**
   * Get 4x4 rotation matrix for surface texture that is used when drawing {@link SurfaceTexture}.
   * @return
   */
  private float[] getTextureRotationMatrix() {
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

    float[] transform = getRotationMatrixByDegrees(degrees);

    final boolean whflipped = degrees == 90 || degrees == 270;
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
