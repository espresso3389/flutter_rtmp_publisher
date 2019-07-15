package jp.espresso3389.flutter_rtmp_publisher

import android.content.Context
import android.graphics.SurfaceTexture
import android.opengl.GLSurfaceView
import io.flutter.plugin.common.PluginRegistry
import android.opengl.EGL14.eglChooseConfig
import android.opengl.EGL14.EGL_OPENGL_ES2_BIT
import android.opengl.EGL14.EGL_CONTEXT_CLIENT_VERSION
import android.os.Handler
import android.widget.LinearLayout
import javax.microedition.khronos.egl.*


class FlutterGLSurfaceView(registrar: PluginRegistry.Registrar, surfaceTexture: SurfaceTexture):
  GLSurfaceView(registrar.activeContext()),
  GLSurfaceView.EGLConfigChooser,
  GLSurfaceView.EGLContextFactory,
  GLSurfaceView.EGLWindowSurfaceFactory {
  val registrar: PluginRegistry.Registrar = registrar
  val surfaceTexture: SurfaceTexture = surfaceTexture
  var egl: EGL10? = null
  var display: EGLDisplay? = null
  var context: EGLContext? = null
  var surface: EGLSurface? = null

  init {
    // Configure context for OpenGL ES 2.0.
    setEGLContextClientVersion(2)
    setEGLConfigChooser(this)
    setEGLContextFactory(this)
    setEGLWindowSurfaceFactory(this)
  }

  //
  // GLSurfaceView.EGLConfigChooser
  //
  override fun chooseConfig(egl: EGL10?, display: EGLDisplay?): EGLConfig {

    // Configure EGL for pbuffer and OpenGL ES 2.0, 24-bit RGB.
    val attribList = intArrayOf(
      EGL10.EGL_RED_SIZE, 8,
      EGL10.EGL_GREEN_SIZE, 8,
      EGL10.EGL_BLUE_SIZE, 8,
      EGL10.EGL_ALPHA_SIZE, 8,
      EGL10.EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,
      EGL10.EGL_SURFACE_TYPE, EGL10.EGL_PBUFFER_BIT,
      EGL10.EGL_NONE)

    val eglConfigs = arrayOfNulls<EGLConfig>(1)
    val numConfigs = IntArray(1)
    if (!egl!!.eglChooseConfig(display, attribList, eglConfigs, eglConfigs.size, numConfigs)) {
      throw RuntimeException("unable to find RGB888+recordable ES2 EGL config")
    }

    return eglConfigs[0]!!;
  }

  //
  // GLSurfaceView.EGLContextFactory
  //
  override fun createContext(egl: EGL10?, display: EGLDisplay?, eglConfig: EGLConfig?): EGLContext {
    // Configure context for OpenGL ES 2.0.
    val attrib_list = intArrayOf(EGL_CONTEXT_CLIENT_VERSION, 2, EGL10.EGL_NONE)
    val context = egl!!.eglCreateContext(display, eglConfig, EGL10.EGL_NO_CONTEXT, attrib_list)
    this.egl = egl
    this.display = display
    this.context = context
    return context
  }

  override fun destroyContext(egl: EGL10?, display: EGLDisplay?, context: EGLContext?) {
    egl!!.eglDestroyContext(display, context)
    this.context = null
  }

  //
  // GLSurfaceView.EGLWindowSurfaceFactory
  //
  override fun createWindowSurface(egl: EGL10?, display: EGLDisplay?, eglConfig: EGLConfig?, nativeWindow: Any?): EGLSurface {
    val surface = egl!!.eglCreateWindowSurface(display, eglConfig, surfaceTexture, null)
    this.surface = surface
    return surface
  }

  override fun destroySurface(egl: EGL10?, display: EGLDisplay?, surface: EGLSurface?) {
    egl!!.eglDestroySurface(display, surface)
    this.surface = null
  }

  public fun setSurfaceTextureSize(width: Int, height: Int) {
    surfaceTexture.setDefaultBufferSize(width, height)
  }
}
