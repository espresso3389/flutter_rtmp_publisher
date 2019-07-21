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
    await controller.initialize(width: 1280, height: 720, fps: 30, cameraPosition: RtmpLiveViewCameraPosition.back);
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      home: Scaffold(
        appBar: AppBar(
          title: const Text('Plugin example app'),
          actions: <Widget>[
            ValueListenableBuilder<RtmpStatus>(
              valueListenable: controller.status,
              builder: (context, status, child) => IconButton(
                icon: Icon(status?.isStreaming == true ? Icons.cast_connected : Icons.cast),
                onPressed: () {
                  if (!status.isStreaming)
                    controller.connect(rtmpUrl: 'rtmp://dev.cuminas.jp:1935/hls', streamName: 'test');
                  else
                    controller.disconnect();
                }
              )
            )
          ],
        ),
        body: Container(
          margin: EdgeInsets.all(2),
          child: ValueListenableBuilder<RtmpStatus>(
            valueListenable: controller.status,
            builder: (context, status, child) => Column(
              children: <Widget>[
                AspectRatio(
                  aspectRatio: status?.aspectRatio ?? 1.0,
                  child: RtmpLiveView(controller: controller)
                ),
                SizedBox(height: 10),
                Text(status?.isStreaming == true ? '${status.rtmpUrl} : ${status.streamName}' : 'Not connected.'),
                Text(status != null ? 'Encoding: ${status.width} x ${status.height} ${status.fps} fps.' : ''),
                Text(status != null ? 'Camera: ${status.cameraWidth} x ${status.cameraHeight}' : ''),
              ]
            )
          ),
        )
      )
    );
  }
}

