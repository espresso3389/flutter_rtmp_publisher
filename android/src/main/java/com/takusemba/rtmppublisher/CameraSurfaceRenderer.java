package com.takusemba.rtmppublisher;

import android.graphics.SurfaceTexture;
import android.opengl.GLSurfaceView;
import android.util.Log;

import com.takusemba.rtmppublisher.gles.FullFrameRect;
import com.takusemba.rtmppublisher.gles.Texture2dProgram;

import java.util.ArrayList;
import java.util.List;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

class CameraSurfaceRenderer implements GLSurfaceView.Renderer {

  private FullFrameRect fullScreen;
  private final float[] transform = new float[16];
  private int textureId;
  private SurfaceTexture surfaceTexture;

  private boolean isSizeChanged = false;
  private boolean isSurfaceCreated = false;
  private int texWidth = -1;
  private int texHeight = -1;

  private List<OnRendererStateChangedListener> listeners = new ArrayList<>();

  void addOnRendererStateChangedLister(OnRendererStateChangedListener listener) {
    listeners.add(listener);
  }

  public interface OnRendererStateChangedListener {

    void onSurfaceCreated(SurfaceTexture surfaceTexture);

    void onFrameDrawn(int textureId, float[] transform, long timestamp);
  }

  void pause() {
    if (surfaceTexture != null) {
      surfaceTexture.release();
      surfaceTexture = null;
    }
    if (fullScreen != null) {
      fullScreen.release(false);
      fullScreen = null;
    }
    texWidth = texHeight = -1;
    isSurfaceCreated = false;
  }

  void setCameraPreviewSize(int width, int height) {
    if (width == texWidth && height == texHeight)
      return;
    Log.i("CameraSurfaceRenderer", String.format("setCameraPreviewSize: %d x %d", width, height));
    texWidth = width;
    texHeight = height;
    isSizeChanged = true;
  }

  @Override
  public void onSurfaceCreated(GL10 unused, EGLConfig config) {
    // set up texture for on-screen display.
    // note that this is not applied to the recording, because that uses a separate shader
    fullScreen = new FullFrameRect(new Texture2dProgram(Texture2dProgram.ProgramType.TEXTURE_EXT));
    textureId = fullScreen.createTextureObject();
    surfaceTexture = new SurfaceTexture(textureId);
    Log.i("CameraSurfaceRenderer", String.format("onSurfaceCreated: textureId=%d", textureId));

    if (listeners.size() > 0) {
      for (OnRendererStateChangedListener listener : listeners) {
        listener.onSurfaceCreated(surfaceTexture);
      }
    }
    isSurfaceCreated = true;
  }

  @Override
  public void onSurfaceChanged(GL10 unused, int width, int height) {
    Log.i("CameraSurfaceRenderer", String.format("onSurfaceChanged: %d x %d", width, height));
  }

  @Override
  public void onDrawFrame(GL10 unused) {
    // Latch the latest frame.
    // If there isn't anything new, we'll just re-use whatever was there before.
    surfaceTexture.updateTexImage();

    if (!isSurfaceCreated) {
      // do not update texture. just return.
      return;
    }

    if (isSizeChanged) {
      fullScreen.getProgram().setTexSize(texWidth, texHeight);
      isSizeChanged = false;
    }

    // Draw the video frame.
    surfaceTexture.getTransformMatrix(transform);
    fullScreen.drawFrame(textureId, transform);

    if (listeners.size() > 0) {
      for (OnRendererStateChangedListener listener : listeners) {
        listener.onFrameDrawn(textureId, transform, surfaceTexture.getTimestamp());
      }
    }
  }
}
