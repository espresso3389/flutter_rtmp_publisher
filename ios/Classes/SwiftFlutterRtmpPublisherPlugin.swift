import Flutter
import UIKit
import AVFoundation
import VideoToolbox

public class SwiftFlutterRtmpPublisherPlugin: NSObject, FlutterPlugin {
  public static func register(with registrar: FlutterPluginRegistrar) {
    let channel = FlutterMethodChannel(name: "flutter_rtmp_publisher", binaryMessenger: registrar.messenger())
    let instance = SwiftFlutterRtmpPublisherPlugin(registrar: registrar)
    registrar.addMethodCallDelegate(instance, channel: channel)
  }
  
  init(registrar: FlutterPluginRegistrar) {
    self.registrar = registrar
  }
  
  let registrar: FlutterPluginRegistrar
  var instances: [Int64:Haishin] = [:]
  
  public func handle(_ call: FlutterMethodCall, result: @escaping FlutterResult) {
    do {
      if call.method == "initAVFoundation" {
        try Haishin.initAVFoundation()
        result(nil)
        return
      } else if call.method == "alloc" {
        let h = Haishin(registrar: registrar)
        let tex = registrar.textures().register(h)
        instances[tex] = h
        h.tex = tex
        result(tex)
        return
      } else if call.method == "initCaptureConfig" {
        guard let args = call.arguments as! NSDictionary? else { result(nil); return }
        guard let width = args["width"] as! NSNumber? else { result(nil); return }
        guard let height = args["height"] as! NSNumber? else { result(nil); return }
        guard let fps = args["fps"] as! NSNumber? else { result(nil); return }
        guard let camera = args["camera"] as! NSString? else { result(nil); return }
        try getHaishin(call).initStream(width: width.intValue, height: height.intValue, fps: fps.intValue, camera: camera as String)
        result(nil)
        return
      } else if call.method == "connect" {
        guard let args = call.arguments as! NSDictionary? else { result(nil); return }
        guard let url = args["url"] as! NSString? else { result(nil); return }
        guard let name = args["name"] as! NSString? else { result(nil); return }
        result(try getHaishin(call).onair(rtmpUrl: url as String, streamName: name as String))
        return
      } else if call.method == "disconnect" {
        let _ = try getHaishin(call).disconnect()
        result(nil)
        return
      } else if call.method == "pause" {
        try getHaishin(call).pause()
        result(nil)
        return
      } else if call.method == "resume" {
        try getHaishin(call).resume()
        result(nil)
        return
      } else if call.method == "paused" {
        try getHaishin(call).paused()
        result(nil)
        return
      } else if call.method == "resumed" {
        try getHaishin(call).resumed()
        result(nil)
        return
      } else if call.method == "startPreview" {
        try getHaishin(call).startPreview()
        result(nil)
        return
      } else if call.method == "stopPreview" {
        try getHaishin(call).stopPreview()
        result(nil)
        return
      } else if call.method == "close" {
        let inst = try getHaishin(call)
        inst.close()
        instances[inst.tex] = nil
        result(nil)
        return
      } else {
        print("SwiftFlutterRtmpPublisherPlugin.handle: unknown command: \(call.method)")
        result(nil) // unknown
      }
    } catch {
      print("SwiftFlutterRtmpPublisherPlugin.handle: \(error)")
      result(nil)
    }
  }
  
  enum HaishinError : Error {
    case InvalidArgument
    case InvalidInstance
    case InvalidInstanceId
  }
  
  func getHaishin(_ call: FlutterMethodCall) throws -> Haishin {
    guard let args = call.arguments as! NSDictionary? else { throw HaishinError.InvalidArgument }
    guard let tex = args["tex"] as! NSNumber? else { throw HaishinError.InvalidArgument }
    guard tex.int64Value >= 0 else { throw HaishinError.InvalidInstanceId }
    guard let inst = instances[tex.int64Value] else { throw HaishinError.InvalidInstance }
    guard inst.tex == tex.int64Value else { throw HaishinError.InvalidInstanceId }
    return inst
  }
}

class Haishin : NSObject {
  init(registrar: FlutterPluginRegistrar) {
    self.registrar = registrar
  }
  
  let registrar: FlutterPluginRegistrar
  var tex: Int64 = -1
  var rtmpConnection: RTMPConnection?
  var rtmpStream: RTMPStream?
  
  let _lastFrame = AtomicReference<CVPixelBuffer?>(initialValue: nil)
  
  var session: AVCaptureSession? = nil
  var currentStream: NetStream? = nil
  
  var streamName: String = "Untitled"
  
  var orientation: AVCaptureVideoOrientation = .portrait
  var position: AVCaptureDevice.Position = .back
  
  public static func initAVFoundation() throws {
    let session = AVAudioSession.sharedInstance()
    try session.setPreferredSampleRate(44_100)
    // https://stackoverflow.com/questions/51010390/avaudiosession-setcategory-swift-4-2-ios-12-play-sound-on-silent
    if #available(iOS 10.0, *) {
      try session.setCategory(.playAndRecord, mode: .default, options: [.defaultToSpeaker, .allowBluetooth])
    } else {
      session.perform(NSSelectorFromString("setCategory:withOptions:error:"), with: AVAudioSession.Category.playAndRecord, with:  [AVAudioSession.CategoryOptions.allowBluetooth])
    }
    try session.setMode(AVAudioSession.Mode.default)
    try session.setActive(true)
  }
  
  public func close() {
    stopPreview();
    rtmpConnection?.close()
    rtmpStream?.close();
    rtmpStream?.dispose()
    rtmpStream = nil
    rtmpConnection = nil
  }
  
  // camera: "back" or "front"
  public func initStream(width: Int, height: Int, fps: Int, camera: String) {
    close()
    
    rtmpConnection = RTMPConnection()
    let sampleRate:Double = 44_100
    
    let session = AVAudioSession.sharedInstance()
    do {
      try session.setPreferredSampleRate(44_100)
      // https://stackoverflow.com/questions/51010390/avaudiosession-setcategory-swift-4-2-ios-12-play-sound-on-silent
      if #available(iOS 10.0, *) {
        try session.setCategory(.playAndRecord, mode: .default, options: [.allowBluetooth])
      } else {
        session.perform(NSSelectorFromString("setCategory:withOptions:error:"), with: AVAudioSession.Category.playAndRecord, with:  [AVAudioSession.CategoryOptions.allowBluetooth])
      }
      try session.setActive(true)
    } catch {
    }
    
    rtmpStream = RTMPStream(connection: rtmpConnection!)
    
    rtmpStream!.captureSettings = [
      "fps": fps, // FPS
      "sessionPreset": AVCaptureSession.Preset.medium.rawValue, // input video width/height
      "continuousAutofocus": true, // use camera autofocus mode
      "continuousExposure": true, //  use camera exposure mode
      "preferredVideoStabilizationMode": AVCaptureVideoStabilizationMode.auto.rawValue
    ]
    rtmpStream!.audioSettings = [
      "muted": false, // mute audio
      "bitrate": RTMPStream.defaultAudioBitrate, // 32 * 1024,
      "sampleRate": sampleRate,
    ]
    rtmpStream!.videoSettings = [
      "width": width, // video output width
      "height": height, // video output height
      "bitrate": RTMPStream.defaultVideoBitrate, //  width * height / 1440 * 1024
      // "dataRateLimits": [160 * 1024 / 8, 1], optional kVTCompressionPropertyKey_DataRateLimits property
      "profileLevel": kVTProfileLevel_H264_Baseline_3_1, // H264 Profile require "import VideoToolbox"
      "maxKeyFrameIntervalDuration": 2, // key frame / sec
    ]
    // "0" means the same of input
    rtmpStream!.recorderSettings = [
      AVMediaType.audio: [
        AVFormatIDKey: Int(kAudioFormatMPEG4AAC),
        AVSampleRateKey: 0,
        AVNumberOfChannelsKey: 0,
        // AVEncoderBitRateKey: 128000,
      ],
      AVMediaType.video: [
        AVVideoCodecKey: AVVideoCodecH264,
        AVVideoHeightKey: 0,
        AVVideoWidthKey: 0,
        /*
         AVVideoCompressionPropertiesKey: [
         AVVideoMaxKeyFrameIntervalDurationKey: 2,
         AVVideoProfileLevelKey: AVVideoProfileLevelH264Baseline30,
         AVVideoAverageBitRateKey: 512000
         ]
         */
      ],
    ]
    
    // 2nd arguemnt set false
    rtmpStream!.attachAudio(AVCaptureDevice.default(for: AVMediaType.audio), automaticallyConfiguresApplicationAudioSession: false) { error in
      print(error)
    }
    
    // Screen capture
    //rtmpStream.attachScreen(ScreenCaptureSession(shared: UIApplication.shared))
    
    let cameraPos = camera == "back" ? AVCaptureDevice.Position.back : AVCaptureDevice.Position.front
    rtmpStream!.attachCamera(DeviceUtil.device(withPosition: cameraPos)) { error in
      print(error)
    }
  }
  
  public func stopPreview() {
    attachStream(nil)
  }
  
  public func startPreview() {
    attachStream(rtmpStream)
  }
  
  public func onair(rtmpUrl: String, streamName: String) -> Bool {
    guard rtmpConnection != nil && rtmpStream != nil else {
      return false
    }
    rtmpConnection!.addEventListener(Event.RTMP_STATUS, selector: #selector(rtmpStatusHandler), observer: self)
    print("Conencting to URL=\(rtmpUrl),NAME=\(streamName)")
    rtmpConnection!.connect(rtmpUrl)
    self.streamName = streamName
    return true
  }
  
  public func pause() {
    rtmpStream?.pause();
  }
  
  public func resume() {
    rtmpStream?.resume();
  }
    
  public func paused() {
    // TODO: Implement some
  }
  
  public func resumed() {
    // TODO: Implement some
  }
  
  public func disconnect() -> Bool {
    guard rtmpConnection != nil && rtmpStream != nil else {
      return false
    }
    rtmpConnection!.removeEventListener(Event.RTMP_STATUS, selector: #selector(rtmpStatusHandler), observer: self)
    rtmpConnection!.close()
    return true
  }
  
  open func attachStream(_ stream: NetStream?) {
    guard let stream: NetStream = stream else {
      session?.stopRunning()
      session = nil
      currentStream = nil
      return
    }
    
    stream.mixer.session.beginConfiguration()
    session = stream.mixer.session
    orientation = stream.mixer.videoIO.orientation
    stream.mixer.session.commitConfiguration()
    
    stream.lockQueue.async {
      stream.mixer.videoIO.drawable = self
      self.currentStream = stream
      stream.mixer.startRunning()
    }
  }
  
  @objc
  func rtmpStatusHandler(_ notification: Notification) {
    let e = Event.from(notification)
    if let data: ASObject = e.data as? ASObject, let code: String = data["code"] as? String {
      switch code {
      case RTMPConnection.Code.connectSuccess.rawValue:
        rtmpStream!.publish(streamName)
      default:
        break
      }
    }
  }
}

extension Haishin : FlutterTexture {
  public func copyPixelBuffer() -> Unmanaged<CVPixelBuffer>? {
    let val = _lastFrame.getAndSet(newValue: nil)
    return val != nil ? Unmanaged<CVPixelBuffer>.passRetained(val!) : nil
  }
}

extension Haishin : NetStreamDrawable {
  
  func draw(image: CIImage) {
    var cvPixBuf: CVPixelBuffer? = nil
    if #available(iOS 10.0, *) {
      cvPixBuf = image.pixelBuffer
    }
    if cvPixBuf == nil {
      // TODO: Implement logic for iOS 9.X or ...
    }
    let _ = self._lastFrame.getAndSet(newValue: cvPixBuf)
    self.registrar.textures().textureFrameAvailable(self.tex)
  }
}
