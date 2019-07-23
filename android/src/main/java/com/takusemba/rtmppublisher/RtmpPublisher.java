package com.takusemba.rtmppublisher;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.opengl.EGL14;
import android.opengl.EGLContext;
import android.opengl.GLSurfaceView;
import android.os.Handler;
import android.util.Log;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import io.flutter.plugin.common.PluginRegistry;

public class RtmpPublisher implements SurfaceTexture.OnFrameAvailableListener, Muxer.StatusListener,
  CameraSurfaceRenderer.OnRendererStateChangedListener, Streamer.StreamerListener {
  private Handler handler = new Handler();
  private PluginRegistry.Registrar registrar;
  private GLSurfaceView glView;
  private CameraSurfaceRenderer renderer;
  private Streamer streamer;
  private CameraClient camera;
  private CameraCallback cameraCallback;
  private RtmpPublisherListener listener;
  private int lastRotation = -1;
  private SurfaceTexture surfaceTexture;
  private long timeStatedMills;
  private int shortDisconnectionCount = 0;

  public static abstract class CameraCallback {
    public abstract void onCameraSizeDetermined(int width, int height);
  }

  public RtmpPublisher(PluginRegistry.Registrar registrar,
                       GLSurfaceView glView,
                       CameraMode mode,
                       RtmpPublisherListener listener,
                       CameraCallback cameraCallback) {
    this.registrar = registrar;
    this.glView = glView;
    // FIXME: Camera preview size fixed here :(
    this.camera = new CameraClient(registrar.context(), mode, 1920, 1080);
    this.cameraCallback = cameraCallback;
    this.streamer = new Streamer();
    this.listener = listener;
    this.streamer.setMuxerListener(this);
    this.streamer.setListener(this);

    glView.setEGLContextClientVersion(2);
    renderer = new CameraSurfaceRenderer();
    renderer.addOnRendererStateChangedLister(streamer.getVideoHandlerListener());
    renderer.addOnRendererStateChangedLister(this);

    glView.setRenderer(renderer);
    glView.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);

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
    disconnect();

    if (renderer != null) {
      renderer.pause();
      renderer = null;
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
  private int fps;
  private int audioBitrate;
  private int videoBitrate;
  private CameraMode cameraMode;
  private boolean isCameraOperating = false;
  private String rtmpUrl;

  private boolean activelyDisconnecting = false;
  private long lastDisconnectTimestamp;

  public void setCaptureConfig(int width, int height, int fps, CameraMode cameraMode, int audioBitRate, int videoBitRate) {
    this.width = width;
    this.height = height;
    this.fps = fps;
    this.cameraMode = cameraMode;
    this.audioBitrate = audioBitRate;
    this.videoBitrate = videoBitRate;
    boolean publishing = isConnected();
    if (publishing)
      disconnect();
    onActivityPause();
    onActivityResume();
    if (publishing)
      connect(rtmpUrl);
  }

  public void connect(String url) {
    activelyDisconnecting = false;
    rtmpUrl = url;
    timeStatedMills = System.currentTimeMillis();
    streamer.open(url, width, height);
  }

  public void disconnect() {
    activelyDisconnecting = true;
    if (streamer.isStreaming()) {
      streamer.stopStreaming();
    }
  }

  public boolean isConnected() {
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
    if (renderer != null && camera != null) {
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
    resizeSurface();
    callCameraCallback();
    setCameraPreviewSize();
  }

  private void callCameraCallback() {
    final int width = camera.getResultWidth();
    final int height = camera.getResultHeight();
    if (surfaceTexture != null)
      surfaceTexture.setDefaultBufferSize(width, height);
    cameraCallback.onCameraSizeDetermined(width, height);
  }

  private void setCameraPreviewSize() {
    final boolean swapped = camera.isCameraWidthHeightSwapped();
    glView.queueEvent(new Runnable() {
      @Override
      public void run() {
        renderer.setCameraPreviewSize(width, height, swapped);
      }
    });
  }

  private void resizeSurface() {
    if (!glView.isAttachedToWindow()) {
      registrar.activity().addContentView(glView, new LinearLayout.LayoutParams(camera.getResultWidth(), camera.getResultHeight()));
    } else {
      glView.setLayoutParams(new FrameLayout.LayoutParams(camera.getResultWidth(), camera.getResultHeight()));
    }
  }

  public void swapCamera() {
    camera.swap();
    doSetup();
  }

  public void setCameraMode(CameraMode mode) {
    camera.setCameraMode(mode);
    doSetup();
  }

  public CameraMode getCameraMode() {
    return camera.getCameraMode();
  }

  public void onActivityResume() {
    if (isCameraOperating)
      return;
    camera.setCameraMode(cameraMode);
    camera.open();
    doSetup();
  }

  private void doSetup() {
    if (!isCameraOperating)
      return;
    resizeSurface();
    callCameraCallback();

    glView.onResume();
    setCameraPreviewSize();
    isCameraOperating = true;
  }

  public void onActivityPause() {
    if (isCameraOperating) {
      surfaceTexture = null;
      if (camera != null) {
        camera.close();
      }
      glView.onPause();
      glView.queueEvent(new Runnable() {
        @Override
        public void run() {
          renderer.pause();
        }
      });
      if (streamer.isStreaming()) {
        streamer.stopStreaming();
      }
      isCameraOperating = false;
    }
  }

  //
  // Muxer.StatusListener
  //
  @Override
  public void onConnected() {
    glView.queueEvent(new Runnable() {
      @Override
      public void run() {
        // EGL14.eglGetCurrentContext() should be called from glView thread.
        final EGLContext context = EGL14.eglGetCurrentContext();
        glView.post(new Runnable() {
          @Override
          public void run() {
            // back to main thread
            streamer.startStreaming(context, width, height, fps, audioBitrate, videoBitrate);
          }
        });
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
    final boolean _activelyDisconnecting = activelyDisconnecting;
    disconnect();
    if (listener != null)
      listener.onDisconnected();

    final long duration = System.currentTimeMillis() - timeStatedMills;
    Log.i("RtmpPublisher", String.format("onDisconnected: duration=%d (msec.)", duration));

    activelyDisconnecting = false;

    if (_activelyDisconnecting) {
      return;
    }

    // OK, this is not intentional disconnection; we should reconnect to the server
    final long _lastDisconnectTimestamp = lastDisconnectTimestamp;
    final long now = lastDisconnectTimestamp = System.currentTimeMillis();
    final long prevDuration = now - _lastDisconnectTimestamp;

    if (prevDuration < 3000) {
      // mmm, the connection got disconnected in very short term
      shortDisconnectionCount++;
    } else {
      // relatively long connection; we'll reset the disconnection counter
      shortDisconnectionCount = 0;
    }

    if (shortDisconnectionCount > 3) {
      // connection may be so unstable, we don't try to reconnect again
      Log.i("RtmpPublisher", "Give up reconnecting!");
      return;
    }

    // OK, we'll try to reconnect again
    Log.i("RtmpPublisher", String.format("Attempting to reconnect (#%d)...", shortDisconnectionCount + 1));
    handler.post(new Runnable() {
      @Override
      public void run() {
        connect(rtmpUrl);
      }
    });
  }

  //
  // CameraSurfaceRenderer.OnRendererStateChangedListener
  //
  @Override
  public void onSurfaceCreated(SurfaceTexture surfaceTexture) {
    onActivityResume();
    surfaceTexture.setDefaultBufferSize(camera.getResultWidth(), camera.getResultHeight());
    surfaceTexture.setOnFrameAvailableListener(this);
    camera.startPreview(surfaceTexture);
    this.surfaceTexture = surfaceTexture;
  }
  @Override
  public void onFrameDrawn(int textureId, float[] transform, long timestamp) {
    // no-op
  }

  //
  // SurfaceTexture.OnFrameAvailableListener
  //
  @Override
  public void onFrameAvailable(SurfaceTexture surfaceTexture) {
    glView.requestRender();
  }

  //
  // Streamer.StreamerListener
  //
  @Override
  public void onError(String component, Exception e) {
    if (listener != null) {
      listener.onError(component, e);
    }
  }

  public interface RtmpPublisherListener {
    void onConnected();
    void onFailedToConnect();
    void onDisconnected();
    void onPaused();
    void onResumed();
    void onError(String component, Exception e);
  }
}
