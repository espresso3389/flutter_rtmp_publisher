import Flutter
import UIKit
import AVFoundation
import VideoToolbox

public class SwiftFlutterRtmpPublisherPlugin: NSObject, FlutterPlugin {
  public static func register(with registrar: FlutterPluginRegistrar) {
    let channel = FlutterMethodChannel(name: "jp.espresso3389.flutter_rtmp_publisher", binaryMessenger: registrar.messenger())
    let instance = SwiftFlutterRtmpPublisherPlugin(registrar: registrar)
    registrar.addMethodCallDelegate(instance, channel: channel)
  }
  
  init(registrar: FlutterPluginRegistrar) {
    self.registrar = registrar
  }
  
  let registrar: FlutterPluginRegistrar
  var instances: [Int64:Haishin] = [:]
  var lastOrientation: UIDeviceOrientation? = nil
  
  public func handle(_ call: FlutterMethodCall, result: @escaping FlutterResult) {
    do {
      if call.method == "initFramework" {
        try initFramework()
        result(nil)
        return
      } else if call.method == "alloc" {
        let h = Haishin(registrar: registrar)
        let tex = registrar.textures().register(h)
        instances[tex] = h
        h.tex = tex
        result(tex)
        return
      } else if call.method == "close" {
        let inst = try getHaishin(call)
        inst.close()
        instances[inst.tex] = nil
        result(nil)
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
      } else if call.method == "setCamera" {
        guard let args = call.arguments as! NSDictionary? else { result(nil); return }
        guard let camera = args["camera"] as! NSString? else { result(nil); return }
        try getHaishin(call).setCamera(camera: camera as String)
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
  
  func initFramework() throws {
    try Haishin.initAVFoundation()
    NotificationCenter.default.addObserver(self, selector: #selector(onRotation), name: UIDevice.orientationDidChangeNotification, object: nil)
  }
  
  @objc func onRotation() {
    let cur = UIDevice.current.orientation
    if lastOrientation != cur {
      lastOrientation = cur
      for tex in instances.keys {
        instances[tex]?.onOrientation(orientation: cur)
      }
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
  private var _tex: Int64 = -1
  var tex: Int64 {
    get { return _tex }
    set {
      if (_tex < 0) {
        _tex = newValue
        eventChannel = FlutterEventChannel(name: "jp.espresso3389.flutter_rtmp_publisher.instance-\(_tex)", binaryMessenger: registrar.messenger())
        eventChannel?.setStreamHandler(self)
      }
    }
  }
  
  var eventChannel: FlutterEventChannel?
  var eventSink: FlutterEventSink?
  
  var rtmpConnection: RTMPConnection?
  var rtmpStream: RTMPStream?
  
  let _lastFrame = AtomicReference<CVPixelBuffer?>(initialValue: nil)
  
  var session: AVCaptureSession? = nil
  var currentStream: NetStream? = nil

  var streamName: String = "Untitled"

  var orientation: AVCaptureVideoOrientation = .portrait // needed by NetStreamDrawble
  var devOrientation: UIDeviceOrientation = .portrait
  var position: AVCaptureDevice.Position = .back
  var previewSize: CGSize = CGSize.zero

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
    disconnect();
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
    
    previewSize = CGSize(width: width, height: height)
    setCamera(camera: camera)
    
    // emulate Android's cameraSize behavior
    let cameraSize:[String: Any?] = ["name": "cameraSize", "width": width, "height": height]
    eventSink?(cameraSize)
  }
  
  public func setCamera(camera: String) {
    let cameraPos = camera == "back" ? AVCaptureDevice.Position.back : AVCaptureDevice.Position.front
    let device = AVCaptureDevice.devices().first {
      $0.hasMediaType(.video) && $0.position == cameraPos && $0.supportsSessionPreset(AVCaptureSession.Preset.hd1920x1080)
    }
    rtmpStream!.attachCamera(device) { error in
      print(error)
    }
    let camera1:[String: Any?] = ["name": "camera", "camera": camera]
    eventSink?(camera1)
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
    eventSink?("paused")
  }
  
  public func resume() {
    rtmpStream?.resume();
    eventSink?("resume")
  }
  
  public func paused() {
    // TODO: Implement some
    print("paused not yet implemented on iOS.")
  }
  
  public func resumed() {
    // TODO: Implement some
    print("resumed not yet implemented on iOS.")
  }
  
  public func disconnect() -> Bool {
    guard rtmpConnection != nil && rtmpStream != nil else {
      return false
    }
    // NOTE: disconnect event does not fire by expcilit close call anyway
    rtmpConnection!.removeEventListener(Event.RTMP_STATUS, selector: #selector(rtmpStatusHandler), observer: self)
    rtmpConnection!.close()
    eventSink?("disconnected")
    return true
  }
  
  public func onOrientation(orientation: UIDeviceOrientation) {
    self.devOrientation = orientation
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
    //orientation = stream.mixer.videoIO.orientation
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
        eventSink?("connected")
        break
      // don't know what they mean
      case RTMPConnection.Code.callBadVersion.rawValue: break
      case RTMPConnection.Code.callFailed.rawValue: break
      case RTMPConnection.Code.callProhibited.rawValue: break
        
      // closed by the peer?
      case RTMPConnection.Code.connectClosed.rawValue:
        eventSink?("disconnected")
        break
        
      // connection failures
      case RTMPConnection.Code.connectAppshutdown.rawValue: fallthrough
      case RTMPConnection.Code.connectFailed.rawValue: fallthrough
      case RTMPConnection.Code.connectIdleTimeOut.rawValue: fallthrough
      case RTMPConnection.Code.connectInvalidApp.rawValue: fallthrough
      case RTMPConnection.Code.connectNetworkChange.rawValue: fallthrough
      case RTMPConnection.Code.connectRejected.rawValue:
        eventSink?("failedToConnect")
        break
        
      default:
        break
      }
    }
  }
}

extension Haishin : FlutterStreamHandler {
  func onListen(withArguments arguments: Any?, eventSink events: @escaping FlutterEventSink) -> FlutterError? {
    self.eventSink = events
    return nil
  }
  
  func onCancel(withArguments arguments: Any?) -> FlutterError? {
    eventSink = nil
    return nil
  }
}

extension Haishin : FlutterTexture {
  public func copyPixelBuffer() -> Unmanaged<CVPixelBuffer>? {
    let val = _lastFrame.getAndSet(newValue: nil)
    return val != nil ? Unmanaged<CVPixelBuffer>.passRetained(val!) : nil
  }
}

extension Haishin : NetStreamDrawable {
  
  func rot(image: CIImage) -> CGAffineTransform {
    switch devOrientation {
    case .portrait:
      return CGAffineTransform.identity
    case .portraitUpsideDown:
      // return image.orientationTransform(for: CGImagePropertyOrientation.down)
      return CGAffineTransform(a: -1, b: 0, c: 0, d: -1, tx: image.extent.width, ty: image.extent.height)
    case .landscapeLeft:
      // return image.orientationTransform(for: CGImagePropertyOrientation.left)
      return CGAffineTransform(a: 0, b: 1, c: -1, d: 0, tx: image.extent.height, ty: 0)
    case .landscapeRight:
      // return image.orientationTransform(for: CGImagePropertyOrientation.right)
      return CGAffineTransform(a: 0, b: -1, c: 1, d: 0, tx: 0, ty: image.extent.width)
    default:
      print("Unexpected rotation: \(devOrientation.rawValue)")
      return CGAffineTransform.identity
    }
  }
  
  func rotation(image: CIImage) -> CIImage {
    let mat = rot(image: image)
    return image.transformed(by: mat)
  }
  
  func draw(image: CIImage) {
    var cvPixBuf: CVPixelBuffer? = nil
    var subimage: CIImage? = nil
    var w: CGFloat
    var h: CGFloat
    let rotated = rotation(image: image)
    let allRect = rotated.extent
    if allRect.width != previewSize.width || allRect.height != previewSize.height {
      let scale = min(allRect.width / previewSize.width, allRect.height / previewSize.height)
      w = previewSize.width * scale
      h = previewSize.height * scale
      let rect = CGRect(
        x: allRect.minX + (allRect.width - w) / 2,
        y: allRect.minY + (allRect.height - h) / 2,
        width: w, height: h)
      
      let subimage = rotated.cropped(to: rect).transformed(by: CGAffineTransform.init(translationX: -rect.minX, y: -rect.minY))
      if #available(iOS 10.0, *) {
        cvPixBuf = subimage.pixelBuffer
      }
      if cvPixBuf == nil {
        // NOTE: Flutter accepts only BGRA32 image buffers
        // https://pub.dev/documentation/camera/latest/camzxera/ImageFormatGroup-class.html
        let options = [
          kCVPixelBufferCGImageCompatibilityKey as String: true,
          kCVPixelBufferCGBitmapContextCompatibilityKey as String: true,
          kCVPixelBufferIOSurfacePropertiesKey as String: [:]
          ] as [String : Any]
        CVPixelBufferCreate(kCFAllocatorDefault, Int(w), Int(h), kCVPixelFormatType_32BGRA, options as CFDictionary?, &cvPixBuf)
        CIContext().render(subimage, to: cvPixBuf!)
      }
    } else {
      subimage = rotated
      w = previewSize.width
      h = previewSize.height
      if #available(iOS 10.0, *) {
        cvPixBuf = subimage!.pixelBuffer
      }
    }
    let _ = self._lastFrame.getAndSet(newValue: cvPixBuf)
    self.registrar.textures().textureFrameAvailable(self.tex)
  }
}

