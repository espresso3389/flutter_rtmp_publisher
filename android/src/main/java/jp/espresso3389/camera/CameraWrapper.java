package jp.espresso3389.camera;

import static android.view.OrientationEventListener.ORIENTATION_UNKNOWN;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.Point;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.os.Build;
import android.util.Size;
import android.view.Display;
import android.view.OrientationEventListener;
import android.view.Surface;
import io.flutter.plugin.common.EventChannel;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.common.PluginRegistry;
import io.flutter.plugin.common.PluginRegistry.Registrar;
import io.flutter.view.FlutterView;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CameraWrapper {

  private static final int CAMERA_REQUEST_ID = 513469796;

  private static CameraManager cameraManager;
  private Camera camera;
  private Registrar registrar;
  // The code to run after requesting camera permissions.
  private Runnable cameraPermissionContinuation;
  private final OrientationEventListener orientationEventListener;
  private int currentOrientation = ORIENTATION_UNKNOWN;

  private CameraWrapper(Registrar registrar) {
    this.registrar = registrar;

    if (cameraManager == null)
      cameraManager = (CameraManager) registrar.activity().getSystemService(Context.CAMERA_SERVICE);

    orientationEventListener =
        new OrientationEventListener(registrar.activity().getApplicationContext()) {
          @Override
          public void onOrientationChanged(int i) {
            if (i == ORIENTATION_UNKNOWN) {
              return;
            }
            // Convert the raw deg angle to the nearest multiple of 90.
            currentOrientation = (int) Math.round(i / 90.0) * 90;
          }
        };

    registrar.addRequestPermissionsResultListener(new CameraRequestPermissionsListener());
  }

  public List<Map<String, Object>> getCameras() {
    List<Map<String, Object>> cameras = new ArrayList<>();
    try {
      String[] cameraNames = cameraManager.getCameraIdList();
      for (String cameraName : cameraNames) {
        HashMap<String, Object> details = new HashMap<>();
        CameraCharacteristics characteristics =
          cameraManager.getCameraCharacteristics(cameraName);
        details.put("name", cameraName);
        @SuppressWarnings("ConstantConditions")
        int sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
        details.put("sensorOrientation", sensorOrientation);

        int lensFacing = characteristics.get(CameraCharacteristics.LENS_FACING);
        switch (lensFacing) {
          case CameraMetadata.LENS_FACING_FRONT:
            details.put("lensFacing", "front");
            break;
          case CameraMetadata.LENS_FACING_BACK:
            details.put("lensFacing", "back");
            break;
          case CameraMetadata.LENS_FACING_EXTERNAL:
            details.put("lensFacing", "external");
            break;
        }
        cameras.add(details);
      }
      return cameras;
    } catch (Exception e) {
      return cameras;
    }
  }

  public Camera initialize(String cameraName, String resolutionPreset, Surface optionalRecorderSurface, final Result result) {
    if (camera != null) {
      camera.close();
    }
    camera = new Camera(cameraName, resolutionPreset, optionalRecorderSurface, result);
    orientationEventListener.enable();
    return camera;
  }

  public void dispose() {
    if (camera != null) {
      camera.dispose();
    }
    orientationEventListener.disable();
  }

  private static class CompareSizesByArea implements Comparator<Size> {
    @Override
    public int compare(Size lhs, Size rhs) {
      // We cast here to ensure the multiplications won't overflow.
      return Long.signum(
          (long) lhs.getWidth() * lhs.getHeight() - (long) rhs.getWidth() * rhs.getHeight());
    }
  }

  private class CameraRequestPermissionsListener
      implements PluginRegistry.RequestPermissionsResultListener {
    @Override
    public boolean onRequestPermissionsResult(int id, String[] permissions, int[] grantResults) {
      if (id == CAMERA_REQUEST_ID) {
        cameraPermissionContinuation.run();
        return true;
      }
      return false;
    }
  }

  public class Camera {
    private final FlutterView.SurfaceTextureEntry textureEntry;
    private CameraDevice cameraDevice;
    private CameraCaptureSession cameraCaptureSession;
    private EventChannel.EventSink eventSink;
    private int sensorOrientation;
    private boolean isFrontFacing;
    private String cameraName;
    private Size captureSize;
    private Size previewSize;
    private CaptureRequest.Builder captureRequestBuilder;
    private Size videoSize;

    public Size getCaptureSize() { return captureSize; }
    public Size getPreviewSize() { return previewSize; }
    public Size getVideoSize() { return videoSize; }

    Camera(
        final String cameraName,
        final String resolutionPreset,
        final Surface optionalRecorderSurface,
        final Result result) {

      this.cameraName = cameraName;
      this.textureEntry = registrar.textures().createSurfaceTexture();

      registerEventChannel();

      try {
        int minHeight;
        switch (resolutionPreset) {
          case "high":
            minHeight = 720;
            break;
          case "medium":
            minHeight = 480;
            break;
          case "low":
            minHeight = 240;
            break;
          default:
            throw new IllegalArgumentException("Unknown preset: " + resolutionPreset);
        }

        CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraName);
        StreamConfigurationMap streamConfigurationMap =
            characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        //noinspection ConstantConditions
        sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
        //noinspection ConstantConditions
        isFrontFacing =
            characteristics.get(CameraCharacteristics.LENS_FACING)
                == CameraMetadata.LENS_FACING_FRONT;
        computeBestCaptureSize(streamConfigurationMap);
        computeBestPreviewAndRecordingSize(streamConfigurationMap, minHeight, captureSize);

        if (cameraPermissionContinuation != null) {
          result.error("cameraPermission", "Camera permission request ongoing", null);
        }
        cameraPermissionContinuation =
            new Runnable() {
              @Override
              public void run() {
                cameraPermissionContinuation = null;
                if (!hasCameraPermission()) {
                  result.error(
                      "cameraPermission", "MediaRecorderCamera permission not granted", null);
                  return;
                }
                open(result, optionalRecorderSurface);
              }
            };
        if (hasCameraPermission()) {
          cameraPermissionContinuation.run();
        } else {
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            final Activity activity = registrar.activity();
            if (activity == null) {
              throw new IllegalStateException("No activity available!");
            }

            activity.requestPermissions(
                new String[] {Manifest.permission.CAMERA},
                CAMERA_REQUEST_ID);
          }
        }
      } catch (CameraAccessException e) {
        result.error("CameraAccess", e.getMessage(), null);
      } catch (IllegalArgumentException e) {
        result.error("IllegalArgumentException", e.getMessage(), null);
      }
    }

    private void registerEventChannel() {
      new EventChannel(
              registrar.messenger(), "espresso3389.jp/cameraEvents" + textureEntry.id())
          .setStreamHandler(
              new EventChannel.StreamHandler() {
                @Override
                public void onListen(Object arguments, EventChannel.EventSink eventSink) {
                  Camera.this.eventSink = eventSink;
                }

                @Override
                public void onCancel(Object arguments) {
                  Camera.this.eventSink = null;
                }
              });
    }

    private boolean hasCameraPermission() {
      final Activity activity = registrar.activity();
      if (activity == null) {
        throw new IllegalStateException("No activity available!");
      }

      return Build.VERSION.SDK_INT < Build.VERSION_CODES.M
          || activity.checkSelfPermission(Manifest.permission.CAMERA)
              == PackageManager.PERMISSION_GRANTED;
    }

    private void computeBestPreviewAndRecordingSize(
        StreamConfigurationMap streamConfigurationMap, int minHeight, Size captureSize) {
      Size[] sizes = streamConfigurationMap.getOutputSizes(SurfaceTexture.class);

      // Preview size and video size should not be greater than screen resolution or 1080.
      Point screenResolution = new Point();

      final Activity activity = registrar.activity();
      if (activity == null) {
        throw new IllegalStateException("No activity available!");
      }

      Display display = activity.getWindowManager().getDefaultDisplay();
      display.getRealSize(screenResolution);

      final boolean swapWH = getMediaOrientation() % 180 == 90;
      int screenWidth = swapWH ? screenResolution.y : screenResolution.x;
      int screenHeight = swapWH ? screenResolution.x : screenResolution.y;

      List<Size> goodEnough = new ArrayList<>();
      for (Size s : sizes) {
        if (minHeight <= s.getHeight()
            && s.getWidth() <= screenWidth
            && s.getHeight() <= screenHeight
            && s.getHeight() <= 1080) {
          goodEnough.add(s);
        }
      }

      Collections.sort(goodEnough, new CompareSizesByArea());

      if (goodEnough.isEmpty()) {
        previewSize = sizes[0];
        videoSize = sizes[0];
      } else {
        float captureSizeRatio = (float) captureSize.getWidth() / captureSize.getHeight();

        previewSize = goodEnough.get(0);
        for (Size s : goodEnough) {
          if ((float) s.getWidth() / s.getHeight() == captureSizeRatio) {
            previewSize = s;
            break;
          }
        }

        Collections.reverse(goodEnough);
        videoSize = goodEnough.get(0);
        for (Size s : goodEnough) {
          if ((float) s.getWidth() / s.getHeight() == captureSizeRatio) {
            videoSize = s;
            break;
          }
        }
      }
    }

    private void computeBestCaptureSize(StreamConfigurationMap streamConfigurationMap) {
      // For still image captures, we use the largest available size.
      captureSize =
          Collections.max(
              Arrays.asList(streamConfigurationMap.getOutputSizes(ImageFormat.JPEG)),
              new CompareSizesByArea());
    }

    public void open(final Result result, final Surface optionalRecorderSurface) {
      if (!hasCameraPermission()) {
        if (result != null) result.error("cameraPermission", "Camera permission not granted", null);
      } else {
        try {
          cameraManager.openCamera(
              cameraName,
              new CameraDevice.StateCallback() {
                @Override
                public void onOpened(CameraDevice cameraDevice) {
                  Camera.this.cameraDevice = cameraDevice;
                  try {
                    startPreview(optionalRecorderSurface);
                  } catch (CameraAccessException e) {
                    if (result != null) result.error("CameraAccess", e.getMessage(), null);
                    cameraDevice.close();
                    Camera.this.cameraDevice = null;
                    return;
                  }

                  if (result != null) {
                    Map<String, Object> reply = new HashMap<>();
                    reply.put("textureId", textureEntry.id());
                    reply.put("previewWidth", previewSize.getWidth());
                    reply.put("previewHeight", previewSize.getHeight());
                    result.success(reply);
                  }
                }

                @Override
                public void onClosed(CameraDevice camera) {
                  if (eventSink != null) {
                    Map<String, String> event = new HashMap<>();
                    event.put("eventType", "cameraClosing");
                    eventSink.success(event);
                  }
                  super.onClosed(camera);
                }

                @Override
                public void onDisconnected(CameraDevice cameraDevice) {
                  cameraDevice.close();
                  Camera.this.cameraDevice = null;
                  sendErrorEvent("The camera was disconnected.");
                }

                @Override
                public void onError(CameraDevice cameraDevice, int errorCode) {
                  cameraDevice.close();
                  Camera.this.cameraDevice = null;
                  String errorDescription;
                  switch (errorCode) {
                    case ERROR_CAMERA_IN_USE:
                      errorDescription = "The camera device is in use already.";
                      break;
                    case ERROR_MAX_CAMERAS_IN_USE:
                      errorDescription = "Max cameras in use";
                      break;
                    case ERROR_CAMERA_DISABLED:
                      errorDescription =
                          "The camera device could not be opened due to a device policy.";
                      break;
                    case ERROR_CAMERA_DEVICE:
                      errorDescription = "The camera device has encountered a fatal error";
                      break;
                    case ERROR_CAMERA_SERVICE:
                      errorDescription = "The camera service has encountered a fatal error.";
                      break;
                    default:
                      errorDescription = "Unknown camera error";
                  }
                  sendErrorEvent(errorDescription);
                }
              },
              null);
        } catch (CameraAccessException e) {
          if (result != null) result.error("cameraAccess", e.getMessage(), null);
        }
      }
    }

    private void startPreview(Surface optionalRecorderSurface) throws CameraAccessException {
      closeCaptureSession();

      SurfaceTexture surfaceTexture = textureEntry.surfaceTexture();
      surfaceTexture.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());
      captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);

      List<Surface> surfaces = new ArrayList<>();

      Surface previewSurface = new Surface(surfaceTexture);
      surfaces.add(previewSurface);
      captureRequestBuilder.addTarget(previewSurface);

      // used to encode video for other purpose
      if (optionalRecorderSurface != null) {
        surfaces.add(optionalRecorderSurface);
        captureRequestBuilder.addTarget(optionalRecorderSurface);
      }

      cameraDevice.createCaptureSession(
          surfaces,
          new CameraCaptureSession.StateCallback() {

            @Override
            public void onConfigured(CameraCaptureSession session) {
              if (cameraDevice == null) {
                sendErrorEvent("The camera was closed during configuration.");
                return;
              }
              try {
                cameraCaptureSession = session;
                captureRequestBuilder.set(
                    CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
                cameraCaptureSession.setRepeatingRequest(captureRequestBuilder.build(), null, null);
              } catch (CameraAccessException | IllegalStateException | IllegalArgumentException e) {
                sendErrorEvent(e.getMessage());
              }
            }

            @Override
            public void onConfigureFailed(CameraCaptureSession cameraCaptureSession) {
              sendErrorEvent("Failed to configure the camera for preview.");
            }
          },
          null);
    }

    private void sendErrorEvent(String errorDescription) {
      if (eventSink != null) {
        Map<String, String> event = new HashMap<>();
        event.put("eventType", "error");
        event.put("errorDescription", errorDescription);
        eventSink.success(event);
      }
    }

    public void closeCaptureSession() {
      if (cameraCaptureSession != null) {
        cameraCaptureSession.close();
        cameraCaptureSession = null;
      }
    }

    public void close() {
      closeCaptureSession();

      if (cameraDevice != null) {
        cameraDevice.close();
        cameraDevice = null;
      }
    }

    public void dispose() {
      close();
      textureEntry.release();
    }

    public int getMediaOrientation() {
      final int sensorOrientationOffset =
          (currentOrientation == ORIENTATION_UNKNOWN)
              ? 0
              : (isFrontFacing) ? -currentOrientation : currentOrientation;
      return (sensorOrientationOffset + sensorOrientation + 360) % 360;
    }
  }
}
