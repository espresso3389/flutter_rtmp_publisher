package jp.espresso3389.flutter_rtmp_publisher

import android.content.Context
import android.opengl.EGL14
import android.util.LongSparseArray
import android.view.Surface
import com.takusemba.rtmppublisher.PublisherListener
import com.takusemba.rtmppublisher.Streamer
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.plugin.common.PluginRegistry.Registrar
import io.flutter.view.TextureRegistry
import jp.espresso3389.camera.CameraWrapper

class FlutterRtmpPublisherPlugin(
  registrar: Registrar): MethodCallHandler {

  private var registrar: Registrar = registrar
  private var textures: LongSparseArray<RtmpPublisher> = LongSparseArray()
  private var cameraWrapper: CameraWrapper = CameraWrapper(registrar)

  companion object {
    @JvmStatic
    fun registerWith(registrar: Registrar) {
      val channel = MethodChannel(registrar.messenger(), "flutter_rtmp_publisher")
      channel.setMethodCallHandler(FlutterRtmpPublisherPlugin(registrar))
    }
  }

  override fun onMethodCall(call: MethodCall, result: Result) {
    when {
        call.method == "alloc" -> {
          val tex = registrar.textures().createSurfaceTexture()
          val textureId = tex.id()
          val eventChannel = EventChannel(registrar.messenger(), "flutter_rtmp_publisher/events/$textureId")
          val rtmpPub = RtmpPublisher(registrar.context(), eventChannel, tex)
          textures.put(textureId, rtmpPub)
          result.success(textureId)
        }
        call.method == "release" -> {
          val tex = call.argument<Number>("tex")!!.toLong()
          val rtmpPub = textures[tex]
          rtmpPub.close()
          textures.delete(tex)
          result.success(true)
        }
        call.method == "initCaptureConfig" -> {
          val tex = call.argument<Number>("tex")!!.toLong()
          val rtmpPub = textures[tex]
          val width = call.argument<Number>("width")!!.toInt()
          val height = call.argument<Number>("height")!!.toInt()
          val fps = call.argument<Number>("fps")!!.toInt()
          val camera = if (call.argument<String>("camera") == "back") CameraFacing.Back else CameraFacing.Front
          rtmpPub.setCaptureConfig(width, height, fps, camera, result)
        }
        call.method == "pause" -> {
          val tex = call.argument<Number>("tex")!!.toLong()
          val rtmpPub = textures[tex]
          rtmpPub.pause()
          result.success(true)
        }
        call.method == "resume" -> {
          val tex = call.argument<Number>("tex")!!.toLong()
          val rtmpPub = textures[tex]
          rtmpPub.resume(result)
        }
        call.method == "startPreview" -> {
          val tex = call.argument<Number>("tex")!!.toLong()
          val rtmpPub = textures[tex]
          rtmpPub.startPreview()
          result.success(true)
        }
        call.method == "stopPreview" -> {
          val tex = call.argument<Number>("tex")!!.toLong()
          val rtmpPub = textures[tex]
          rtmpPub.stopPreview()
          result.success(true)
        }
        call.method == "connect" -> {
          val tex = call.argument<Number>("tex")!!.toLong()
          val rtmpUrl = call.argument<String>("url")
          val name = call.argument<String>("name")
          val rtmpPub = textures[tex]
          rtmpPub.connect(rtmpUrl!!, name!!)
          result.success(true)
        }
        call.method == "disconnect" -> {
          val tex = call.argument<Number>("tex")!!.toLong()
          val rtmpPub = textures[tex]
          rtmpPub.disconnect()
          result.success(true)
        }
        call.method == "initAVFoundation" -> {
          // FIXME: name should be changed but it is suitable for some initialization
        }
        else -> result.notImplemented()
    }
  }

  enum class CameraFacing {
    Back,
    Front
  }

  inner class RtmpPublisher(
    context: Context,
    eventChannel: EventChannel,
    textureEntry: TextureRegistry.SurfaceTextureEntry): PublisherListener {
    override fun onFailedToConnect() {
      TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun onStarted() {
      TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun onDisconnected() {
      TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun onStopped() {
      TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    private var context: Context = context
    private var eventChannel: EventChannel = eventChannel
    private var textureEntry: TextureRegistry.SurfaceTextureEntry = textureEntry
    private var streamer: Streamer? = null
    private var surface: Surface? = null
    private var camera: CameraWrapper.Camera? = null
    private var width: Int = 0
    private var height: Int = 0
    private var fps: Int = 30 // FIXED
    private var cameraFacing: CameraFacing = CameraFacing.Back
    private var lastCameraFacing: CameraFacing = CameraFacing.Back

    private val BITRATE_MAGIC_DIVIDER = 13 // 720p@30fps => 2MB
    private val AUDIO_BITRATE = 128 * 1024

    init {
      streamer = Streamer()
      streamer!!.setMuxerListener(this)
      surface = Surface(textureEntry.surfaceTexture())
      textureEntry.surfaceTexture().setOnFrameAvailableListener { surfaceTexture ->
        val mat = FloatArray(16)
        surfaceTexture.getTransformMatrix(mat)
        streamer!!.videoFrameListener.onNewFrame(textureEntry.id().toInt(), mat, surfaceTexture.timestamp)
      }
    }

    fun close() {
      pause()
      streamer?.release()
      textureEntry.release()
      eventChannel.setStreamHandler(null)
    }

    fun getCameraName(cameraFacing: CameraFacing): String {
      val lensFacing = if (cameraFacing == CameraFacing.Back) "back" else "front"
      val cameras = cameraWrapper.cameras
      for (camera in cameras) {
        if (camera["lensFacing"] == lensFacing) {
          return camera["name"] as String
        }
      }
      return cameras.first()["name"] as String;
    }

    // fps ignored.
    fun setCaptureConfig(width: Int, height: Int, fps: Int, cameraFacing: CameraFacing, result: Result) {
      this.width = width
      this.height = height
      this.cameraFacing = cameraFacing
      if (streamer!!.isStreaming) {
        resume(result)
        return
      }
      result.success(null)
    }

    fun pause() {
      streamer!!.stopStreaming()
    }

    fun resume(result: Result) {
      pause()
      if (lastCameraFacing != cameraFacing) {
        camera?.close()
        camera = cameraWrapper.initialize(getCameraName(cameraFacing), "medium", surface, result)
        lastCameraFacing = cameraFacing
      }
      val videoRate = width * height * fps / BITRATE_MAGIC_DIVIDER
      streamer!!.startStreaming(EGL14.eglGetCurrentContext(), width, height, videoRate, AUDIO_BITRATE)
    }

    fun startPreview() {
    }

    fun stopPreview() {
    }

    fun connect(rtmpUrl: String, name: String) {
      streamer?.open(rtmpUrl, width, height)
    }

    fun disconnect() {
      pause()
      streamer?.release()
    }
  }

}
