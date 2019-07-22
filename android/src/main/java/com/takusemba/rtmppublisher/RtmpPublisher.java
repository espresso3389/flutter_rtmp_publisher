package com.takusemba.rtmppublisher;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.opengl.EGL14;
import android.opengl.EGLContext;
import android.os.Handler;
import android.util.Size;
import android.view.WindowManager;

import io.flutter.plugin.common.PluginRegistry;

public class RtmpPublisher implements CameraClient.OnCameraReady, SurfaceTexture.OnFrameAvailableListener, Muxer.StatusListener {
  private Handler handler = new Handler();
  private PluginRegistry.Registrar registrar;
  private SurfaceTexture surfaceTextureSrc;
  private Streamer streamer;
  private CameraClient camera;
  private CameraCallback cameraCallback;
  private RtmpPublisherListener listener;
  private int lastRotation = -1;

  public static abstract class CameraCallback {
    public abstract void onCameraSizeDetermined(int width, int height);
  }

  public RtmpPublisher(PluginRegistry.Registrar registrar,
                       SurfaceTexture surfaceTextureSrc,
                       CameraMode mode,
                       RtmpPublisherListener listener,
                       CameraCallback cameraCallback) {
    this.registrar = registrar;
    this.surfaceTextureSrc = surfaceTextureSrc;
    // FIXME: Camera preview size fixed here :(
    this.camera = new CameraClient(registrar.context(), this);
    this.cameraCallback = cameraCallback;
    this.streamer = new Streamer();
    this.listener = listener;
    this.streamer.setMuxerListener(this);

    scheduleCheckOrientation();
  }

  void scheduleCheckOrientation() {
    handler.postDelayed(new Runnable() {
      @Override
      public void run() {
        checkOrientation();
      }
    }, 500);
  }

  public void release() {
    stopPublishing();

    if (surfaceTextureSrc != null) {
      //surfaceTextureSrc.setOnFrameAvailableListener(null);
      surfaceTextureSrc = null;
    }

    if (camera != null) {
      camera.close();
      camera = null;
    }
  }

  public void switchCamera() {
    camera.swap();
  }

  private int width;
  private int height;
  private int fps; // NOT USED
  private int audioBitrate;
  private int videoBitrate;
  private CameraMode cameraMode;
  private boolean isCameraOperating = false;
  private String rtmpUrl;

  public void setCaptureConfig(int width, int height, int fps, CameraMode cameraMode, int audioBitRate, int videoBitRate) {
    this.width = width;
    this.height = height;
    this.fps = fps;
    this.cameraMode = cameraMode;
    this.audioBitrate = audioBitRate;
    this.videoBitrate = videoBitRate;
    boolean publishing = isPublishing();
    if (publishing)
      stopPublishing();
    onPause();
    onResume();
    if (publishing)
      startPublishing(rtmpUrl);
  }

  public void startPublishing(String url) {
    rtmpUrl = url;
    streamer.open(url, width, height);
  }

  public void stopPublishing() {
    if (streamer.isStreaming()) {
      streamer.stopStreaming();
    }
  }

  public boolean isPublishing() {
    return streamer.isStreaming();
  }

  public boolean isPaused() { return streamer.isPaused(); }

  public void pause() {
    if (!streamer.isPaused()) {
      streamer.pause();
      if (listener != null) {
        listener.onPaused();
      }
    }
  }

  public void resume() {
    if (streamer.isPaused()) {
      streamer.resume();
      if (listener != null) {
        listener.onResumed();
      }
    }
  }

  private void checkOrientation() {
    if (camera != null) {
      int rotation = getDeviceRotation();
      if (rotation != lastRotation) {
        lastRotation = rotation;
        onOrientationChanged(rotation);
      }
    }

    scheduleCheckOrientation();
  }

  private int getDeviceRotation() {
    WindowManager windowManager = (WindowManager) registrar.activeContext().getSystemService(Context.WINDOW_SERVICE);
    return windowManager.getDefaultDisplay().getRotation();
  }

  public void onOrientationChanged(int orientation) {
    camera.onOrientationChanged(orientation);
    callCameraCallback();
  }

  private void callCameraCallback() {
    final Size size = camera.getResultSize();
    if (surfaceTextureSrc != null)
      surfaceTextureSrc.setDefaultBufferSize(size.getWidth(), size.getHeight());
    cameraCallback.onCameraSizeDetermined(size.getWidth(), size.getHeight());
  }

  public void swapCamera() {
    camera.swap();
    doSetup();
  }

  public void setCameraMode(CameraMode mode) {
    camera.open(mode, width, height);
    doSetup();
  }

  public CameraMode getCameraMode() {
    return camera.getCameraMode();
  }

  public void onResume() {
    if (isCameraOperating)
      return;
    camera.open(cameraMode, width, height);
    doSetup();
  }

  private void doSetup() {
    if (!isCameraOperating)
      return;
    callCameraCallback();
    isCameraOperating = true;

    camera.startPreview(surfaceTextureSrc);
    //surfaceTextureSrc.setOnFrameAvailableListener(this);
  }

  public void onPause() {
    if (isCameraOperating) {
      if (surfaceTextureSrc != null) {
        //surfaceTextureSrc.setOnFrameAvailableListener(null);
        surfaceTextureSrc = null;
      }
      if (camera != null) {
        camera.close();
      }
      if (streamer.isStreaming()) {
        streamer.stopStreaming();
      }
      isCameraOperating = false;
    }
  }

  /**
   * {@link CameraClient.OnCameraReady}
   * @param camera
   */
  @Override
  public void onCameraReady(CameraClient camera) {
    isCameraOperating = true;
    camera.startPreview(surfaceTextureSrc);
  }


  //
  // Muxer.StatusListener
  //
  @Override
  public void onConnected() {
    handler.post(new Runnable() {
      @Override
      public void run() {
        final EGLContext context = EGL14.eglGetCurrentContext();
        streamer.startStreaming(context, width, height, audioBitrate, videoBitrate);
      }
    });

    if (listener != null)
      listener.onConnected();
  }
  @Override
  public void onFailedToConnect() {
    if (listener != null)
      listener.onFailedToConnect();
  }
  @Override
  public void onPaused() {
    if (listener != null)
      listener.onPaused();
  }
  @Override
  public void onResumed() {
    if (listener != null)
      listener.onResumed();
  }
  @Override
  public void onDisconnected() {
    stopPublishing();
    if (listener != null)
      listener.onDisconnected();
  }

  //
  // SurfaceTexture.OnFrameAvailableListener
  //
  @Override
  public void onFrameAvailable(SurfaceTexture surfaceTexture) {
    surfaceTexture.updateTexImage();
  }

  public interface RtmpPublisherListener {
    void onConnected();
    void onFailedToConnect();
    void onDisconnected();
    void onPaused();
    void onResumed();
  }
}
