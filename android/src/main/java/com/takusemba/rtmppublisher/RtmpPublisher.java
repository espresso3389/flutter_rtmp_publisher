package com.takusemba.rtmppublisher;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.opengl.EGL14;
import android.opengl.EGLContext;
import android.opengl.GLSurfaceView;
import android.widget.LinearLayout;

import io.flutter.plugin.common.PluginRegistry;

public class RtmpPublisher implements SurfaceTexture.OnFrameAvailableListener,
  CameraSurfaceRenderer.OnRendererStateChangedListener {

  private PluginRegistry.Registrar registrar;
  private GLSurfaceView glView;
  private CameraSurfaceRenderer renderer;
  private Streamer streamer;
  private CameraClient camera;
  private Camera.Size cameraSize;
  private CameraCallback cameraCallback;

  public static abstract class CameraCallback {
    public abstract void onCameraSizeDetermined(int width, int height);
  }

  public RtmpPublisher(PluginRegistry.Registrar registrar,
                       GLSurfaceView glView,
                       CameraMode mode,
                       PublisherListener listener,
                       CameraCallback cameraCallback) {
    this.registrar = registrar;
    this.glView = glView;
    this.camera = new CameraClient(registrar.context(), mode);
    this.cameraCallback = cameraCallback;
    this.streamer = new Streamer();
    this.streamer.setMuxerListener(listener);

    glView.setEGLContextClientVersion(2);
    renderer = new CameraSurfaceRenderer();
    renderer.addOnRendererStateChangedLister(streamer.getVideoHandlerListener());
    renderer.addOnRendererStateChangedLister(this);

    glView.setRenderer(renderer);
    glView.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
  }

  public void release() {
    stopPublishing();

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
    glView.queueEvent(new Runnable() {
      @Override
      public void run() {
        // EGL14.eglGetCurrentContext() should be called from glView thread.
        final EGLContext context = EGL14.eglGetCurrentContext();
        glView.post(new Runnable() {
          @Override
          public void run() {
            // back to main thread
            streamer.startStreaming(context, width, height, audioBitrate, videoBitrate);
          }
        });
      }
    });
  }

  public void stopPublishing() {
    if (streamer.isStreaming()) {
      streamer.stopStreaming();
    }
  }

  public boolean isPublishing() {
    return streamer.isStreaming();
  }

  public void pause() {

  }

  public void resume() {
  }

  public void onResume() {
    if (isCameraOperating)
      return;
    Camera.Parameters params = camera.open();
    cameraSize = params.getPreviewSize();

    // FIXME:
    if (!glView.isAttachedToWindow()) {
      registrar.activity().addContentView(glView, new LinearLayout.LayoutParams(cameraSize.width, cameraSize.height));
    } else {
      glView.setLayoutParams(new LinearLayout.LayoutParams(cameraSize.width, cameraSize.height));
    }

    cameraCallback.onCameraSizeDetermined(cameraSize.width, cameraSize.height);

    glView.onResume();
    glView.queueEvent(new Runnable() {
      @Override
      public void run() {
        renderer.setCameraPreviewSize(cameraSize.width, cameraSize.height);
      }
    });
    isCameraOperating = true;
  }

  public void onPause() {
    if (isCameraOperating) {
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

  @Override
  public void onSurfaceCreated(SurfaceTexture surfaceTexture) {
    onResume();
    surfaceTexture.setDefaultBufferSize(cameraSize.width, cameraSize.height);
    surfaceTexture.setOnFrameAvailableListener(this);
    camera.startPreview(surfaceTexture);
  }

  @Override
  public void onFrameDrawn(int textureId, float[] transform, long timestamp) {
    // no-op
  }

  @Override
  public void onFrameAvailable(SurfaceTexture surfaceTexture) {
    glView.requestRender();
  }
}
