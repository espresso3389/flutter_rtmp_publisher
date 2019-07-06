import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_rtmp_publisher/flutter_rtmp_publisher.dart';

void main() {
  const MethodChannel channel = MethodChannel('flutter_rtmp_publisher');

  setUp(() {
    channel.setMockMethodCallHandler((MethodCall methodCall) async {
      return '42';
    });
  });

  tearDown(() {
    channel.setMockMethodCallHandler(null);
  });

  /*
  test('getPlatformVersion', () async {
    expect(await FlutterRtmpPublisher.platformVersion, '42');
  });
  */
}
