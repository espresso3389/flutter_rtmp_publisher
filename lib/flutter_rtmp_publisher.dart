import 'dart:async';
import 'package:rxdart/rxdart.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

enum RtmpLiveViewCameraPosition {
  front,
  back
}

class RtmpLiveViewController {
  static const MethodChannel _channel = const MethodChannel('flutter_rtmp_publisher');
  static bool _avfInited = false;

  var _subject = BehaviorSubject<String>();
  int _tex = -1;
  int _width;
  int _height;
  int _fps;
  RtmpLiveViewCameraPosition _camera = RtmpLiveViewCameraPosition.back;

  int get width => _width;
  int get height => _height;
  int get fps => _fps;
  RtmpLiveViewCameraPosition get camera => _camera;

  void dispose() {
    _subject?.sink?.add('dispose');
    _subject?.close();
    _subject = null;
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
    _subject?.sink?.add('close');
    await _channel.invokeMethod('close', { 'tex': tmp });
  }

  Future _initTex() async {
    if (!_avfInited) {
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

    await _channel.invokeMethod('initCaptureConfig', {
      'tex': _tex,
      'width': _width,
      'height': _height,
      'fps': _fps,
      'camera': _camera == RtmpLiveViewCameraPosition.back ? 'back' : 'front'
    });

    _subject.sink.add('config');

    if (restartPreview)
      await startPreview();
  }

  Future pause() async {
    _checkParams();
    await _channel.invokeMethod('pause', { 'tex': _tex });
  }

  Future resume() async {
    _checkParams();
    await _channel.invokeMethod('resume', { 'tex': _tex });
  }

  Future startPreview() async {
    _checkParams();
    await _channel.invokeMethod('startPreview', { 'tex': _tex });
  }

  Future stopPreview() async {
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

  static Future _statePaused() async {
    try {
      await _channel.invokeMethod('paused');
    } catch (e) {
    }
  }

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
