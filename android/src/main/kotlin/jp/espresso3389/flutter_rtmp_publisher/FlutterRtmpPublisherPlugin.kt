package jp.espresso3389.flutter_rtmp_publisher

import android.content.Context
import android.util.LongSparseArray
import com.takusemba.rtmppublisher.PublisherListener
import com.takusemba.rtmppublisher.Streamer
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.plugin.common.PluginRegistry.Registrar
import io.flutter.view.TextureRegistry

class FlutterRtmpPublisherPlugin(
  registrar: Registrar): MethodCallHandler {

  private var registrar: Registrar = registrar
  private var textures: LongSparseArray<RtmpPublisher> = LongSparseArray()

  companion object {
    @JvmStatic
    fun registerWith(registrar: Registrar) {
      val channel = MethodChannel(registrar.messenger(), "flutter_rtmp_publisher")
      channel.setMethodCallHandler(FlutterRtmpPublisherPlugin(registrar))
    }
  }

  override fun onMethodCall(call: MethodCall, result: Result) {
    if (call.method == "alloc") {
      val tex = registrar.textures().createSurfaceTexture()
      val textureId = tex.id()
      val eventChannel = EventChannel(registrar.messenger(), "flutter_rtmp_publisher/events/$textureId")
      val rtmpPub = RtmpPublisher(registrar.context(), eventChannel, tex)
      textures.put(textureId, rtmpPub)
      result.success(textureId)
    } else if (call.method == "close") {
      val tex = call.argument<Number>("tex")!!.toLong()
      val rtmpPub = textures[tex]
      rtmpPub.close()
      textures.delete(tex)
      result.success(true)
    } else if (call.method == "initCaptureConfig") {
      val tex = call.argument<Number>("tex")!!.toLong()
      val rtmpPub = textures[tex]
      val width = call.argument<Number>("width")!!.toInt()
      val height = call.argument<Number>("height")!!.toInt()
      val fps = call.argument<Number>("fps")!!.toInt()
      val camera = if (call.argument<String>("camera") == "back") RtmpPublisher.Camera.Back else RtmpPublisher.Camera.Front
      rtmpPub.setCaptureConfig(width, height, fps, camera)
      result.success(true)
    } else if (call.method == "pause") {
      val tex = call.argument<Number>("tex")!!.toLong()
      val rtmpPub = textures[tex]
      rtmpPub.pause()
      result.success(true)
    } else if (call.method == "resume") {
      val tex = call.argument<Number>("tex")!!.toLong()
      val rtmpPub = textures[tex]
      rtmpPub.resume()
      result.success(true)
    } else if (call.method == "startPreview") {
      val tex = call.argument<Number>("tex")!!.toLong()
      val rtmpPub = textures[tex]
      rtmpPub.startPreview()
      result.success(true)
    } else if (call.method == "stopPreview") {
      val tex = call.argument<Number>("tex")!!.toLong()
      val rtmpPub = textures[tex]
      rtmpPub.stopPreview()
      result.success(true)
    } else if (call.method == "connect") {
      val tex = call.argument<Number>("tex")!!.toLong()
      val rtmpUrl = call.argument<String>("url")
      val name = call.argument<String>("name")
      val rtmpPub = textures[tex]
      rtmpPub.connect(rtmpUrl!!, name!!)
      result.success(true)
    } else if (call.method == "disconnect") {
      val tex = call.argument<Number>("tex")!!.toLong()
      val rtmpPub = textures[tex]
      rtmpPub.disconnect()
      result.success(true)
    } else if (call.method == "initAVFoundation") {
      // FIXME: name should be changed but it is suitable for some initialization
    } else {
      result.notImplemented()
    }
  }
}

class RtmpPublisher(
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

  init {
    streamer = Streamer()
    streamer?.setMuxerListener(this)
  }

  fun close() {
    streamer?.stopStreaming()
    textureEntry.release()
    eventChannel.setStreamHandler(null)
  }

  enum class Camera {
    Back,
    Front
  }

  fun setCaptureConfig(width: Int, height: Int, fps: Int, camera: Camera) {

  }

  fun pause() {
  }

  fun resume() {
  }

  fun startPreview() {
  }

  fun stopPreview() {
  }

  fun connect(rtmpUrl: String, name: String) {
  }

  fun disconnect() {
  }
}
