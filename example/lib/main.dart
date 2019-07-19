import 'package:flutter/material.dart';
import 'package:flutter_rtmp_publisher/flutter_rtmp_publisher.dart';
import 'package:screen_keep_on/screen_keep_on.dart';

void main() => runApp(MyApp());

class MyApp extends StatefulWidget {
  @override
  _MyAppState createState() => _MyAppState();
}

class _MyAppState extends State<MyApp> {
  final controller = RtmpLiveViewController();

  @override
  void initState() {
    super.initState();
    ScreenKeepOn.turnOn(true);
    initAsync();
  }

  @override
  void dispose() {
    ScreenKeepOn.turnOn(false);
    super.dispose();
  }

  Future initAsync() async {
    await controller.initialize(width: 640, height: 480, fps: 30, camera: RtmpLiveViewCameraPosition.back);
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      home: Scaffold(
        appBar: AppBar(
          title: const Text('Plugin example app'),
          actions: <Widget>[
            IconButton(icon: Icon(Icons.cast_connected),
            onPressed: () {
              controller.connect(rtmpUrl: 'rtmp://dev.cuminas.jp:1935/hls', name: 'test');
            },)
          ],
        ),
        body: Center(
          child: Container(
            width: 400,
            height: 400,
            child: RtmpLiveView(controller: controller)
          ),
        ),
      ),
    );
  }
}

