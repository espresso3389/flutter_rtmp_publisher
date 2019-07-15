// The code is based on Android-MediaCodec-Examples/ExtractMpegFramesTest.java
// https://github.com/PhilLab/Android-MediaCodec-Examples/blob/master/ExtractMpegFramesTest.java
package jp.espresso3389.video_player;

import android.graphics.SurfaceTexture;
import android.opengl.GLES11Ext;
import android.os.Build;
import android.os.Handler;
import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.ByteOrder;

import android.opengl.GLES20;
import android.opengl.Matrix;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;
import javax.microedition.khronos.egl.EGLSurface;

import static android.content.ContentValues.TAG;

public class SurfaceTextureForwarder implements SurfaceTexture.OnFrameAvailableListener {
  private STextureRender mTextureRender;
  private SurfaceTexture mSurfaceTextureSrc;
  private EGL10 mEgl;
  private int mTextureId;
  private EGLDisplay mEGLDisplay = EGL10.EGL_NO_DISPLAY;
  private EGLContext mEGLContext = EGL10.EGL_NO_CONTEXT;
  private EGLSurface mEGLSurface = EGL10.EGL_NO_SURFACE;

  private SurfaceTexture mSurfaceTextureDest;

  public SurfaceTextureForwarder(SurfaceTexture surfaceTextureDest) {
    mEgl = (EGL10) EGLContext.getEGL();
    eglSetup();

    int[] textures = new int[1];
    GLES20.glGenTextures(1, textures, 0);
    mTextureId = textures[0];

    mSurfaceTextureSrc = new SurfaceTexture(mTextureId);

    // Copy-n-pasted from Flutter's code
    // https://github.com/flutter/engine/blob/30639ee7b6a3c0ea6afa7ffcdbd11254b069431f/shell/platform/android/io/flutter/embedding/engine/renderer/FlutterRenderer.java#L108
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      // The callback relies on being executed on the UI thread (unsynchronised read of mNativeView
      // and also the engine code check for platform thread in Shell::OnPlatformViewMarkTextureFrameAvailable),
      // so we explicitly pass a Handler for the current thread.
      mSurfaceTextureSrc.setOnFrameAvailableListener(this, new Handler());
    } else {
      // Android documentation states that the listener can be called on an arbitrary thread.
      // But in practice, versions of Android that predate the newer API will call the listener
      // on the thread where the SurfaceTexture was constructed.
      mSurfaceTextureSrc.setOnFrameAvailableListener(this);
    }

    mSurfaceTextureDest = surfaceTextureDest;
}

  public SurfaceTexture getSurfaceTexture() {
    return mSurfaceTextureSrc;
  }

  public void setupDestSurface(int width, int height) {
    if (width <= 0 || height <= 0) {
      throw new IllegalArgumentException();
    }

    mSurfaceTextureDest.setDefaultBufferSize(width, height);
    recreateEglSurface();

    mTextureRender = new STextureRender(mTextureId);
    mTextureRender.surfaceCreated();

    mSurfaceTextureSrc.updateTexImage();
  }

  public interface OnFrameDrawn {
    void onFrameDrawn(int textureId, SurfaceTexture surfaceTexture);
  }
  private OnFrameDrawn onFrameDrawn;

  public void setOnFrameDrawn(OnFrameDrawn onFrameDrawn) {
    this.onFrameDrawn = onFrameDrawn;
  }

  /**
   * Create bitmap from a frame.
   *
   * @param invert if set, render the image with Y inverted (0,0 in top left)
   */
  public boolean copyToSurface(boolean invert) {
    makeCurrent();
    mTextureRender.drawFrame(mSurfaceTextureSrc, invert);
    mEgl.eglSwapBuffers(mEGLDisplay, mEGLSurface);
    return true;
  }

  @Override
  public void onFrameAvailable(SurfaceTexture surfaceTexture) {
    if (mEGLSurface == EGL10.EGL_NO_SURFACE) {
      Log.e(TAG, "onFrameAvailable: no destination surface size available yet.");
      return;
    }
    mSurfaceTextureSrc.updateTexImage();
    copyToSurface(false);
    if (onFrameDrawn != null) {
      onFrameDrawn.onFrameDrawn(mTextureId, mSurfaceTextureSrc);
    }
  }

  EGLConfig[] eglConfigs;

  /**
   * Prepares EGL.  We want a GLES 2.0 context and a surface that supports pbuffer.
   */
  private void eglSetup() {
    final int EGL_OPENGL_ES2_BIT = 0x0004;
    final int EGL_CONTEXT_CLIENT_VERSION = 0x3098;

    mEGLDisplay = mEgl.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY);
    if (mEGLDisplay == EGL10.EGL_NO_DISPLAY) {
      throw new RuntimeException("unable to get EGL14 display");
    }
    int[] version = new int[2];
    if (!mEgl.eglInitialize(mEGLDisplay, version)) {
      mEGLDisplay = null;
      throw new RuntimeException("unable to initialize EGL14");
    }

    // Configure EGL for pbuffer and OpenGL ES 2.0, 24-bit RGB.
    int[] attribList = {
      EGL10.EGL_RED_SIZE, 8,
      EGL10.EGL_GREEN_SIZE, 8,
      EGL10.EGL_BLUE_SIZE, 8,
      EGL10.EGL_ALPHA_SIZE, 8,
      EGL10.EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,
      EGL10.EGL_SURFACE_TYPE, EGL10.EGL_PBUFFER_BIT,
      EGL10.EGL_NONE
    };

    eglConfigs = new EGLConfig[1];
    int[] numConfigs = new int[1];
    if (!mEgl.eglChooseConfig(mEGLDisplay, attribList, eglConfigs, eglConfigs.length, numConfigs)) {
      throw new RuntimeException("unable to find RGB888+recordable ES2 EGL config");
    }

    // Configure context for OpenGL ES 2.0.
    int[] attrib_list = {
      EGL_CONTEXT_CLIENT_VERSION, 2,
      EGL10.EGL_NONE
    };
    mEGLContext = mEgl.eglCreateContext(mEGLDisplay, eglConfigs[0], EGL10.EGL_NO_CONTEXT, attrib_list);
    checkEglError("eglCreateContext");
    if (mEGLContext == null) {
      throw new RuntimeException("null context");
    }
  }

  public void recreateEglSurface() {
    if (mEGLSurface != EGL10.EGL_NO_SURFACE) {
      mEgl.eglMakeCurrent(mEGLDisplay, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_SURFACE,
        EGL10.EGL_NO_CONTEXT);
      mEgl.eglDestroySurface(mEGLDisplay, mEGLSurface);
    }
    mEGLSurface = mEgl.eglCreateWindowSurface(mEGLDisplay, eglConfigs[0], mSurfaceTextureDest, null);
    checkEglError("eglCreateWindowSurface");
    if (mEGLSurface == null) {
      throw new RuntimeException("surface was null");
    }

    makeCurrent();
    GLES20.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
    GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
    mEgl.eglSwapBuffers(mEGLDisplay, mEGLSurface);
  }

  /**
   * Discard all resources held by this class, notably the EGL context.
   */
  public void release() {
    mSurfaceTextureDest = null;
    if (mSurfaceTextureSrc != null)
      mSurfaceTextureSrc.setOnFrameAvailableListener(null);
    mSurfaceTextureSrc = null;
    mTextureRender = null;

    if (mEGLDisplay != EGL10.EGL_NO_DISPLAY) {
      //mEgl.eglReleaseThread();
      mEgl.eglMakeCurrent(mEGLDisplay, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_SURFACE,
        EGL10.EGL_NO_CONTEXT);

      if (mEGLSurface != EGL10.EGL_NO_SURFACE) {
        mEgl.eglDestroySurface(mEGLDisplay, mEGLSurface);
        mEGLSurface = EGL10.EGL_NO_SURFACE;
      }
      if (mEGLContext != EGL10.EGL_NO_CONTEXT) {
        mEgl.eglDestroyContext(mEGLDisplay, mEGLContext);
        mEGLContext = EGL10.EGL_NO_CONTEXT;
      }

      mEgl.eglTerminate(mEGLDisplay);
      mEGLDisplay = EGL10.EGL_NO_DISPLAY;

      int[] textures = new int[] { mTextureId };
      GLES20.glDeleteTextures(textures.length, textures, 0);
    }
  }

  /**
   * Makes our EGL context and surface current.
   */
  public void makeCurrent() {
    if (!mEgl.eglMakeCurrent(mEGLDisplay, mEGLSurface, mEGLSurface, mEGLContext)) {
      throw new RuntimeException("eglMakeCurrent failed");
    }
  }

  /**
   * Checks for EGL errors.
   */
  private void checkEglError(String msg) {
    int error;
    if ((error = mEgl.eglGetError()) != EGL10.EGL_SUCCESS) {
      throw new RuntimeException(msg + ": EGL error: 0x" + Integer.toHexString(error));
    }
  }

  public void checkGlError(String op) {
    int error;
    while ((error = GLES20.glGetError()) != GLES20.GL_NO_ERROR) {
      Log.e(TAG, op + ": glError " + error);
      throw new RuntimeException(op + ": glError " + error);
    }
  }

  /**
   * Code for rendering a texture onto a surface using OpenGL ES 2.0.
   */
  private static class STextureRender {
    private static final int FLOAT_SIZE_BYTES = 4;
    private static final int TRIANGLE_VERTICES_DATA_STRIDE_BYTES = 5 * FLOAT_SIZE_BYTES;
    private static final int TRIANGLE_VERTICES_DATA_POS_OFFSET = 0;
    private static final int TRIANGLE_VERTICES_DATA_UV_OFFSET = 3;
    private final float[] mTriangleVerticesData = {
      // X, Y, Z, U, V
      -1.0f, -1.0f, 0, 0.0f, 0.0f,
       1.0f, -1.0f, 0, 1.0f, 0.0f,
      -1.0f,  1.0f, 0, 0.0f, 1.0f,
       1.0f,  1.0f, 0, 1.0f, 1.0f,
    };

    private FloatBuffer mTriangleVertices;

    private static final String VERTEX_SHADER =
      "uniform mat4 uMVPMatrix;\n" +
        "uniform mat4 uSTMatrix;\n" +
        "attribute vec4 aPosition;\n" +
        "attribute vec4 aTextureCoord;\n" +
        "varying vec2 vTextureCoord;\n" +
        "void main() {\n" +
        "  gl_Position = uMVPMatrix * aPosition;\n" +
        "  vTextureCoord = (uSTMatrix * aTextureCoord).xy;\n" +
        "}\n";

    private static final String FRAGMENT_SHADER =
      "#extension GL_OES_EGL_image_external : require\n" +
        "precision mediump float;\n" +      // highp here doesn't seem to matter
        "varying vec2 vTextureCoord;\n" +
        "uniform samplerExternalOES sTexture;\n" +
        "void main() {\n" +
        "  gl_FragColor = texture2D(sTexture, vTextureCoord);\n" +
        "}\n";

    private float[] mMVPMatrix = new float[16];

    private int mProgram;
    private int mTextureID = -12345;
    private int muMVPMatrixHandle;
    private int muSTMatrixHandle;
    private int maPositionHandle;
    private int maTextureHandle;

    public STextureRender(int textureId) {
      mTriangleVertices = ByteBuffer.allocateDirect(
        mTriangleVerticesData.length * FLOAT_SIZE_BYTES)
        .order(ByteOrder.nativeOrder()).asFloatBuffer();
      mTriangleVertices.put(mTriangleVerticesData).position(0);

      mTextureID = textureId;

      Matrix.setIdentityM(mMVPMatrix, 0);
    }

    public int getTextureId() {
      return mTextureID;
    }

    /**
     * Draws the external texture in SurfaceTexture onto the current EGL surface.
     */
    public void drawFrame(SurfaceTexture st, boolean invert) {
      checkGlError("onDrawFrame start");
      float[] mSTMatrix = new float[16];
      st.getTransformMatrix(mSTMatrix);
      if (invert) {
        mSTMatrix[5] = -mSTMatrix[5];
        mSTMatrix[13] = 1.0f - mSTMatrix[13];
      }

      // Make the EGL surface completely transparent for our purpose
      GLES20.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
      GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

      GLES20.glUseProgram(mProgram);
      checkGlError("glUseProgram");

      GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
      GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, mTextureID);

      mTriangleVertices.position(TRIANGLE_VERTICES_DATA_POS_OFFSET);
      GLES20.glVertexAttribPointer(maPositionHandle, 3, GLES20.GL_FLOAT, false,
        TRIANGLE_VERTICES_DATA_STRIDE_BYTES, mTriangleVertices);
      checkGlError("glVertexAttribPointer maPosition");
      GLES20.glEnableVertexAttribArray(maPositionHandle);
      checkGlError("glEnableVertexAttribArray maPositionHandle");

      mTriangleVertices.position(TRIANGLE_VERTICES_DATA_UV_OFFSET);
      GLES20.glVertexAttribPointer(maTextureHandle, 2, GLES20.GL_FLOAT, false,
        TRIANGLE_VERTICES_DATA_STRIDE_BYTES, mTriangleVertices);
      checkGlError("glVertexAttribPointer maTextureHandle");
      GLES20.glEnableVertexAttribArray(maTextureHandle);
      checkGlError("glEnableVertexAttribArray maTextureHandle");

      GLES20.glUniformMatrix4fv(muMVPMatrixHandle, 1, false, mMVPMatrix, 0);
      GLES20.glUniformMatrix4fv(muSTMatrixHandle, 1, false, mSTMatrix, 0);

      GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
      checkGlError("glDrawArrays");

      GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0);
    }

    /**
     * Initializes GL state.  Call this after the EGL surface has been created and made current.
     */
    public void surfaceCreated() {
      mProgram = createProgram(VERTEX_SHADER, FRAGMENT_SHADER);
      if (mProgram == 0) {
        throw new RuntimeException("failed creating program");
      }

      maPositionHandle = GLES20.glGetAttribLocation(mProgram, "aPosition");
      checkLocation(maPositionHandle, "aPosition");
      maTextureHandle = GLES20.glGetAttribLocation(mProgram, "aTextureCoord");
      checkLocation(maTextureHandle, "aTextureCoord");

      muMVPMatrixHandle = GLES20.glGetUniformLocation(mProgram, "uMVPMatrix");
      checkLocation(muMVPMatrixHandle, "uMVPMatrix");
      muSTMatrixHandle = GLES20.glGetUniformLocation(mProgram, "uSTMatrix");
      checkLocation(muSTMatrixHandle, "uSTMatrix");

      GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, mTextureID);
      checkGlError("glBindTexture mTextureID");

      GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
      GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
      GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
      GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
      checkGlError("glTexParameter");
    }

    /**
     * Replaces the fragment shader.  Pass in null to reset to default.
     */
    public void changeFragmentShader(String fragmentShader) {
      if (fragmentShader == null) {
        fragmentShader = FRAGMENT_SHADER;
      }
      GLES20.glDeleteProgram(mProgram);
      mProgram = createProgram(VERTEX_SHADER, fragmentShader);
      if (mProgram == 0) {
        throw new RuntimeException("failed creating program");
      }
    }

    private int loadShader(int shaderType, String source) {
      int shader = GLES20.glCreateShader(shaderType);
      checkGlError("glCreateShader type=" + shaderType);
      GLES20.glShaderSource(shader, source);
      GLES20.glCompileShader(shader);
      int[] compiled = new int[1];
      GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0);
      if (compiled[0] == 0) {
        Log.e(TAG, "Could not compile shader " + shaderType + ":");
        Log.e(TAG, " " + GLES20.glGetShaderInfoLog(shader));
        GLES20.glDeleteShader(shader);
        shader = 0;
      }
      return shader;
    }

    private int createProgram(String vertexSource, String fragmentSource) {
      int vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexSource);
      if (vertexShader == 0) {
        return 0;
      }
      int pixelShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource);
      if (pixelShader == 0) {
        return 0;
      }

      int program = GLES20.glCreateProgram();
      if (program == 0) {
        Log.e(TAG, "Could not create program");
      }
      GLES20.glAttachShader(program, vertexShader);
      checkGlError("glAttachShader");
      GLES20.glAttachShader(program, pixelShader);
      checkGlError("glAttachShader");
      GLES20.glLinkProgram(program);
      int[] linkStatus = new int[1];
      GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0);
      if (linkStatus[0] != GLES20.GL_TRUE) {
        Log.e(TAG, "Could not link program: ");
        Log.e(TAG, GLES20.glGetProgramInfoLog(program));
        GLES20.glDeleteProgram(program);
        program = 0;
      }
      return program;
    }

    public void checkGlError(String op) {
      int error;
      while ((error = GLES20.glGetError()) != GLES20.GL_NO_ERROR) {
        Log.e(TAG, op + ": glError " + error);
        throw new RuntimeException(op + ": glError " + error);
      }
    }

    public static void checkLocation(int location, String label) {
      if (location < 0) {
        throw new RuntimeException("Unable to locate '" + label + "' in program");
      }
    }
  }
}
