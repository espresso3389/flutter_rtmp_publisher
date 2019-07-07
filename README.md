# flutter_rtmp_publisher

The code contains modified versions of the following projects:

Project name    |Notes
----------------|------------
[HaishinKit.swift](https://github.com/shogo4405/HaishinKit.swift)|Contain full code instead of using Pod to use certain private classes such as [NetStreamDrawable](https://github.com/shogo4405/HaishinKit.swift/blob/master/Sources/Net/NetStream.swift).
[RtmpPublisher](https://github.com/TakuSemba/RtmpPublisher.git)|[Streamer](https://github.com/TakuSemba/RtmpPublisher/blob/master/rtmppublisher/src/main/java/com/takusemba/rtmppublisher/Streamer.java) class is modified to deal with Flutter's Texture and Activity related classes are removed.
[Flutter Plugins](https://github.com/flutter/plugins)|Portion of the code is derived from Flutter's [camera plugin](https://github.com/flutter/plugins/blob/master/packages/camera/lib/camera.dart).

Almost all modifications are need to use Flutter's Texture widget rather than OS's native view (UIView/Activity).
