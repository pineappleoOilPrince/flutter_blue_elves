//
//  Device.h
//
//
//  Created by Johnny Li on 2021/10/6.
//

#ifndef Device_h
#define Device_h
@class CBPeripheral;
@class CBCentralManager;
@class FlutterBlueElvesPlugin;
@interface Device:NSObject
@property(nonatomic,assign,readonly)short state;
@property(assign,nonatomic,readonly)BOOL isInitiativeDisConnect;

-(instancetype)init:(NSString *)identifier centralManager:(CBCentralManager *)centralManager peripheral:(CBPeripheral *)peripheral pluginInstance:(FlutterBlueElvesPlugin*)pluginInstance rssi:(NSNumber *) rssi;
-(void)connectDevice:(int)timeout;
-(void)initiativeDisConnect;
-(void)discoverService;
-(BOOL)setNotifyCharacteristic:(NSString *) serviceUuid characteristicUuid:(NSString *) characteristicUuid isEnable:(BOOL) isEnable;
-(void)readDataToDevice:(NSString *) serviceUuid characteristicUuid:(NSString *) characteristicUuid;
-(void)writeDataToDevice:(NSString *) serviceUuid characteristicUuid:(NSString *) characteristicUuid isNoResponse:(BOOL) isNoResponse data:(NSData *) data;
-(void)readDescriptorDataToDevice:(NSString *) serviceUuid characteristicUuid:(NSString *) characteristicUuid descriptorUuid:(NSString *) descriptorUuid;
-(void)writeDescriptorDataToDevice:(NSString *) serviceUuid characteristicUuid:(NSString *) characteristicUuid descriptorUuid:(NSString *) descriptorUuid data:(NSData *) data;
-(BOOL)watchRssi:(BOOL) isStartWatch;
- (void)notifyConnectState:(short)newState;
-(void)destroy;
@end

#endif /* Device_h */

