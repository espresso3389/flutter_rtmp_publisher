import 'dart:async';
import 'package:flutter/services.dart' as prefix0;
import 'package:rxdart/rxdart.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

enum RtmpLiveViewCameraPosition {
  front,
  back
}

class RtmpLiveViewController {
  static const MethodChannel _channel = const MethodChannel('jp.espresso3389.flutter_rtmp_publisher');
  static bool _fxInitialized = false;

  var _subject = BehaviorSubject<String>();
  int _tex = -1;
  int _width;
  int _height;
  int _fps;
  bool _isStreaming = false;
  RtmpLiveViewCameraPosition _camera = RtmpLiveViewCameraPosition.back;
  StreamSubscription<dynamic> _sub;

  bool get isStreaming => _isStreaming;

  int get width => _width;
  int get height => _height;
  int get fps => _fps;
  RtmpLiveViewCameraPosition get camera => _camera;

  void dispose() {
    _subject?.close();
    _subject = null;
    _sub?.cancel();
    _sub = null;
    close();
  }

  Future close() async {
    if (_tex == -1)
      return;
    final tmp = _tex;
    _tex = -1;
    _width = null;
    _height = null;
    _fps = null;
    await _channel.invokeMethod('close', { 'tex': tmp });
  }

  Future _initTex() async {
    if (!_fxInitialized) {
      await _channel.invokeMethod('initFramework');
    }
    if (_tex == -1)
      _tex = await _channel.invokeMethod('alloc');
  }

  Future initialize({@required int width, @required int height, @required int fps, @required RtmpLiveViewCameraPosition camera, bool restartPreview = true}) async {

    if (_tex == -1)
      await _initTex();

    _width = width;
    _height = height;
    _fps = fps;
    _camera = camera;

    _sub = EventChannel('jp.espresso3389.flutter_rtmp_publisher.instance-$_tex').receiveBroadcastStream().listen((data) {
      if (data is String) {
        print('RtmpLiveViewController: state changed: $data');
        switch(data) {
          case 'connected':
            _isStreaming = true;
            _subject?.sink?.add('connected');
            break;
          case 'failedToConnect':
            _isStreaming = false; // basically we don't need this
            _subject?.sink?.add('failedToConnect');
            break;
          case 'disconnected':
            _isStreaming = false;
            _subject?.sink?.add('disconnected');
            break;
          case 'paused':
            _subject?.sink?.add('paused');
            break;
          case 'resumed':
            _subject?.sink?.add('resumed');
            break;
        }
      }
    });

    await _channel.invokeMethod('initCaptureConfig', {
      'tex': _tex,
      'width': _width,
      'height': _height,
      'fps': _fps,
      'camera': _camera == RtmpLiveViewCameraPosition.back ? 'back' : 'front'
    });

    _subject.sink.add('config');

    if (restartPreview)
      await _startPreview();
  }

  // FIXME: What's the definition of pause??
  Future pause() async {
    _checkParams();
    await _channel.invokeMethod('pause', { 'tex': _tex });
  }

  // FIXME: What's the definition of resume??
  Future resume() async {
    _checkParams();
    await _channel.invokeMethod('resume', { 'tex': _tex });
  }

  // FIXME: Android anyway does not implement this :(
  Future _startPreview() async {
    _checkParams();
    await _channel.invokeMethod('startPreview', { 'tex': _tex });
  }

  // FIXME: Android anyway does not implement this :(
  Future _stopPreview() async {
    _checkParams();
    await _channel.invokeMethod('stopPreview', { 'tex': _tex });
  }

  Future connect({@required String rtmpUrl, @required String name}) async {
    _checkParams();
    await _channel.invokeMethod('connect', {
      'tex': _tex,
      'url': rtmpUrl,
      'name': name
    });
  }

  Future disconnect() async {
    _checkParams();
    await _channel.invokeMethod('disconnect', { 'tex': _tex });
  }

  void _checkParams() {
    if (_tex == -1 || _width == null || _height == null || _fps == null)
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
}

class RtmpLiveView extends StatefulWidget {
  @override
  _RtmpLiveViewState createState() => _RtmpLiveViewState();

  RtmpLiveViewController controller;

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
    return StreamBuilder(
      stream: widget.controller._subject.stream,
      builder: (context, snapshot) => widget.controller._tex >= 0
      ? Texture(textureId: widget.controller._tex)
      : Container()
    );
  }
}
