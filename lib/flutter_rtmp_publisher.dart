import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

enum RtmpLiveViewCameraPosition {
  front,
  back
}


class RtmpStatus {
  final int tex; // for internal purpose only
  final int width;
  final int height;
  final int fps;
  final bool isStreaming;
  final bool isStreamingPaused;
  final RtmpLiveViewCameraPosition cameraPosition;
  final String rtmpUrl;
  final String streamName;
  final int cameraWidth;
  final int cameraHeight;

  double get aspectRatio => height != 0 ? width / height : 1.0;

  RtmpStatus._({this.tex, this.width, this.height, this.fps, this.isStreaming, this.isStreamingPaused, this.cameraPosition, this.rtmpUrl, this.streamName, this.cameraWidth, this.cameraHeight});

  RtmpStatus updateWith({int tex, int width, int height, int fps, bool isStreaming, bool isStreamingPaused, RtmpLiveViewCameraPosition cameraPosition, String rtmpUrl, String streamName, int cameraWidth, int cameraHeight}) {
    return RtmpStatus._(
      tex: tex ?? this.tex,
      width: width ?? this.width,
      height: height ?? this.height,
      fps: fps ?? this.fps,
      isStreaming: isStreaming ?? this.isStreaming,
      isStreamingPaused: isStreamingPaused ?? this.isStreamingPaused,
      cameraPosition: cameraPosition ?? this.cameraPosition,
      rtmpUrl: rtmpUrl ?? this.rtmpUrl,
      streamName: streamName ?? this.streamName,
      cameraWidth: cameraWidth ?? this.cameraWidth,
      cameraHeight: cameraHeight ?? this.cameraHeight);
  }
}

class RtmpLiveViewController {
  static const MethodChannel _channel = const MethodChannel('jp.espresso3389.flutter_rtmp_publisher');
  static bool _fxInitialized = false;

  StreamSubscription<dynamic> _sub;
  String _rtmpUrlConnectingTo;
  String _streamNameConnectingTo;
  Orientation _lastOrientation;

  final status = ValueNotifier<RtmpStatus>(null);

  void dispose() {
    status?.dispose();
    _sub?.cancel();
    _sub = null;
    close();
  }

  Future close() async {
    if (status.value == null)
      return;
    final tmp = status.value.tex;
    status.value = null;
    await _channel.invokeMethod('close', { 'tex': tmp });
  }

  Future _initTex() async {
    if (!_fxInitialized) {
      await _channel.invokeMethod('initFramework');
    }
    if (status.value == null)
      status.value = RtmpStatus._(tex: await _channel.invokeMethod('alloc'));
  }

  Future initialize({@required int width, @required int height, @required int fps, @required RtmpLiveViewCameraPosition cameraPosition, bool restartPreview = true}) async {

    await _initTex();

    status.value = status.value.updateWith(
      width: width, height: height, fps: fps,
      isStreaming: false, isStreamingPaused: false,
      cameraPosition: cameraPosition);

    _sub = EventChannel('jp.espresso3389.flutter_rtmp_publisher.instance-${status.value.tex}').receiveBroadcastStream().listen((data) {
      if (data is String) {
        print('RtmpLiveViewController: state changed: $data');
        switch(data) {
          case 'connected':
            status.value = status.value.updateWith(
              isStreaming: true, isStreamingPaused: false,
              rtmpUrl: _rtmpUrlConnectingTo, streamName: _streamNameConnectingTo);
            break;
          case 'failedToConnect':
            status.value = status.value.updateWith(isStreaming: false, isStreamingPaused: false);
            break;
          case 'disconnected':
            status.value = status.value.updateWith(isStreaming: false, isStreamingPaused: false);
            break;
          case 'paused':
            status.value = status.value.updateWith(isStreamingPaused: true);
            break;
          case 'resumed':
            status.value = status.value.updateWith(isStreamingPaused: false);
            break;
          default:
            print('Unknown status: $data');
        }
      } else if (data is Map) {
        switch (data['name'] as String)
        {
          case 'cameraSize':
            status.value = status.value.updateWith(cameraWidth: data['width'], cameraHeight: data['height']);
            break;
          case 'camera':
            status.value = status.value.updateWith(cameraPosition: data['camera'] == 'back' ? RtmpLiveViewCameraPosition.back : RtmpLiveViewCameraPosition.front);
            break;
          default:
            print('Unknown data: ${data['name']}');
        }
      }
    });

    await _channel.invokeMethod('initCaptureConfig', {
      'tex': status.value.tex,
      'width': status.value.width,
      'height': status.value.height,
      'fps': status.value.fps,
      'camera': status.value.cameraPosition == RtmpLiveViewCameraPosition.back ? 'back' : 'front'
    });

    if (restartPreview)
      await _startPreview();
  }

  // FIXME: What's the definition of pause??
  Future pause() async {
    _checkParams();
    await _channel.invokeMethod('pause', { 'tex': status.value.tex });
  }

  // FIXME: What's the definition of resume??
  Future resume() async {
    _checkParams();
    await _channel.invokeMethod('resume', { 'tex': status.value.tex });
  }

  // FIXME: Android anyway does not implement this :(
  Future _startPreview() async {
    _checkParams();
    await _channel.invokeMethod('startPreview', { 'tex': status.value.tex });
  }

  // FIXME: Android anyway does not implement this :(
  Future _stopPreview() async {
    _checkParams();
    await _channel.invokeMethod('stopPreview', { 'tex': status.value.tex });
  }

  Future setCamera(RtmpLiveViewCameraPosition cameraPosition) async {
    _checkParams();
    await _channel.invokeMethod('setCamera', { 'tex': status.value.tex, 'camera': cameraPosition == RtmpLiveViewCameraPosition.back ? 'back' : 'front' });
  }

  Future swapCamera() async {
    _checkParams();
    await setCamera(status.value.cameraPosition == RtmpLiveViewCameraPosition.back ? RtmpLiveViewCameraPosition.front : RtmpLiveViewCameraPosition.back);
  }

  Future connect({@required String rtmpUrl, @required String streamName}) async {
    _checkParams();
    _rtmpUrlConnectingTo = rtmpUrl;
    _streamNameConnectingTo = streamName;
    await _channel.invokeMethod('connect', {
      'tex': status.value.tex,
      'url': rtmpUrl,
      'name': streamName
    });
  }

  Future disconnect() async {
    _checkParams();
    await _channel.invokeMethod('disconnect', { 'tex': status.value.tex });
  }

  void _checkParams() {
    final s = status.value;
    if (status.value == null || s.width == null || s.height == null || s.fps == null)
      throw Exception('The instance not initialized.');
  }

  // unlike pause, it is app-life-cycle related func.
  static Future _statePaused() async {
    try {
      await _channel.invokeMethod('paused');
    } catch (e) {
    }
  }

  // unlike resume, it is app-life-cycle related func.
  static Future _stateResumed() async {
    try {
      await _channel.invokeMethod('resumed');
    } catch (e) {
    }
  }

  // helper method
  void _orientationChanged(Orientation orientation) {
    if (_lastOrientation == orientation)
      return;
    _lastOrientation = orientation;
    _channel.invokeMethod('orientation', { 'tex': status.value.tex, 'orientation': orientation.index });
  }
}

class RtmpLiveView extends StatefulWidget {
  @override
  _RtmpLiveViewState createState() => _RtmpLiveViewState();

  final RtmpLiveViewController controller;

  RtmpLiveView({Key key, @required this.controller}) : super(key: key);
}

class _RtmpLiveViewState extends State<RtmpLiveView> with WidgetsBindingObserver {
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.paused) {
      RtmpLiveViewController._statePaused();
    } else if (state == AppLifecycleState.resumed) {
      RtmpLiveViewController._stateResumed();
    }
  }

  @override
  Widget build(BuildContext context) {
    return ValueListenableBuilder<RtmpStatus>(
      valueListenable: widget.controller.status,
      builder: (context, status, child) => status != null
      ? Texture(textureId: status.tex)
      : Container()
    );
  }
}
