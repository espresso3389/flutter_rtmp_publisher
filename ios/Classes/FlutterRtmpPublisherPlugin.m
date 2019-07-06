#import "FlutterRtmpPublisherPlugin.h"
#import <flutter_rtmp_publisher/flutter_rtmp_publisher-Swift.h>

@implementation FlutterRtmpPublisherPlugin
+ (void)registerWithRegistrar:(NSObject<FlutterPluginRegistrar>*)registrar {
  [SwiftFlutterRtmpPublisherPlugin registerWithRegistrar:registrar];
}
@end
