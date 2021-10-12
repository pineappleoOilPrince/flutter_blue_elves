#import <Flutter/Flutter.h>

@interface FlutterBlueElvesPlugin : NSObject<FlutterPlugin>
-(instancetype)init;
-(void)connectTimeout:(NSString *)identifier;
-(void)discoverService:(NSString *)identifier serviceUuid:(NSString *)serviceUuid characteristics:(NSArray<NSDictionary<NSString*,id>*> *)characteristics;
-(void)signalCallback:(short)type deviceId:(NSString *)identifier uuid:(NSString*) uuid isSuccess:(BOOL)isSuccess data:(NSData *)data;
@end

