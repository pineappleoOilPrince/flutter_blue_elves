//
//  Device.m
//
//
//  Created by Johnny Li on 2021/10/7.
//

#import <Foundation/Foundation.h>
#import "Device.h"
#import "FlutterBlueElvesPlugin.h"
#import<CoreBluetooth/CoreBluetooth.h>

@interface Device()<CBPeripheralDelegate>
@property(nonatomic,assign,readwrite)NSString* identifier;
@property(nonatomic,assign,readwrite)short state;
@property(strong,nonatomic,readwrite)CBPeripheral * peripheral;//连接的蓝牙设备对象
@property(strong,nonatomic,readwrite)CBCentralManager *centralManager;//本地蓝牙管理
@property(assign,nonatomic,readwrite)BOOL isConnectedDevice;
@property(assign,nonatomic,readwrite)BOOL isInitiativeDisConnect;
@property(strong,nonatomic,readwrite)FlutterBlueElvesPlugin * pluginInstance;
@property(strong,nonatomic,readwrite)NSMutableDictionary<NSString*,NSNumber*> * serviceFinishCharacteristicsCount;//储存服务已经完成的特征值数量
@end

@implementation Device

-(instancetype)init:(NSString *)identifier centralManager:(CBCentralManager *)centralManager peripheral:(CBPeripheral *)peripheral pluginInstance:(FlutterBlueElvesPlugin*)pluginInstance{
    self = [super init];
    self.identifier=identifier;
    self.state=0;
    self.peripheral=peripheral;
    [self.peripheral setDelegate:self];
    self.centralManager=centralManager;
    self.serviceFinishCharacteristicsCount=[NSMutableDictionary new];
    self.isConnectedDevice=NO;
    self.isInitiativeDisConnect=NO;
    self.pluginInstance=pluginInstance;
    return self;
}

-(void)destroy{
    [self initiativeDisConnect];
    self.centralManager = nil;
    self.pluginInstance=nil;
    self.peripheral=nil;
    [self.serviceFinishCharacteristicsCount removeAllObjects];
}

-(void)connectDevice:(int)timeout{
    if(self.state==0){
        self.state=1;
        self.isConnectedDevice=NO;
        self.isInitiativeDisConnect=NO;
        [self.centralManager connectPeripheral:self.peripheral options:nil];//连接设备
        if(timeout>0)
            [self performSelector:@selector(connectTimeoutCallback) withObject:nil afterDelay:timeout];//设置到达最大连接时间后还没连上设备时调用的函数
    }
}

-(void)initiativeDisConnect{
    if(self.state==2){
        self.isInitiativeDisConnect=YES;
        [self.centralManager cancelPeripheralConnection:self.peripheral];//断开连接
    }
}

-(void)discoverService{
    [self.serviceFinishCharacteristicsCount removeAllObjects];
    [self.peripheral discoverServices:nil];//发现服务
}

-(BOOL)setNotifyCharacteristic:(NSString *) serviceUuid characteristicUuid:(NSString *) characteristicUuid isEnable:(BOOL) isEnable{
    CBCharacteristic * characteristic=[self getBlueCharacteristic:serviceUuid characteristicUuid:characteristicUuid];
    if(characteristic==nil)
        return false;
    [self.peripheral setNotifyValue:isEnable forCharacteristic:characteristic];
    return true;//开启notify的通知
}

-(void)readDataToDevice:(NSString *)serviceUuid characteristicUuid:(NSString *)characteristicUuid {
    CBCharacteristic * characteristic=[self getBlueCharacteristic:serviceUuid characteristicUuid:characteristicUuid];
    if(characteristic!=nil)
        [self.peripheral readValueForCharacteristic:characteristic];
}

-(void)writeDataToDevice:(NSString *)serviceUuid characteristicUuid:(NSString *)characteristicUuid isNoResponse:(BOOL) isNoResponse data:(NSData *) data{
    CBCharacteristic * characteristic=[self getBlueCharacteristic:serviceUuid characteristicUuid:characteristicUuid];
    if(characteristic!=nil)//如果设置了CBCharacteristicWriteWithResponse，则写入数据之后不会进入写入回调
        [self.peripheral writeValue:data forCharacteristic:characteristic type:isNoResponse? CBCharacteristicWriteWithResponse:CBCharacteristicWriteWithResponse];
}

-(void)readDescriptorDataToDevice:(NSString *)serviceUuid characteristicUuid:(NSString *)characteristicUuid descriptorUuid:(NSString *)descriptorUuid{
    CBDescriptor * descriptor=[self getBlueDescriptor:serviceUuid characteristicUuid:characteristicUuid descriptorUuid:descriptorUuid];
    if(descriptor!=nil)
        [self.peripheral readValueForDescriptor:descriptor];
}

-(void)writeDescriptorDataToDevice:(NSString *)serviceUuid characteristicUuid:(NSString *)characteristicUuid descriptorUuid:(NSString *)descriptorUuid data:(NSData *) data{
    CBDescriptor * descriptor=[self getBlueDescriptor:serviceUuid characteristicUuid:characteristicUuid descriptorUuid:descriptorUuid];
    if(descriptor!=nil)
        [self.peripheral writeValue:data forDescriptor:descriptor];
}

//通知该设备对象连接状态更新了
- (void)notifyConnectState:(short)newState {
    if(newState==0){//如果连接断开
        NSLog(@"设备连接断开");
        self.state=0;
    }else if(newState==1){//如果设备连接成功
        self.isConnectedDevice=YES;
        NSLog(@"设备连接成功");
        [NSObject cancelPreviousPerformRequestsWithTarget:self selector:@selector(connectTimeoutCallback) object:nil];//取消最大连接时间的定时任务
        self.state=2;
    }else//如果设备连接失败
        NSLog(@"设备连接失败");
}

// 发现外设的服务后的回调
- (void)peripheral:(nonnull CBPeripheral *)peripheral didDiscoverServices:(nullable NSError *)error {
    if (error) {
        NSLog(@"设备获取服务失败");
        return;
    }
    for (CBService *service in peripheral.services) {
        [self.serviceFinishCharacteristicsCount setValue:0 forKey:[service.UUID UUIDString]];
        [peripheral discoverCharacteristics:nil forService:service];//去发现这个服务的特征
    }
}

//发现服务特征值之后的回调
-(void)peripheral:(CBPeripheral *)peripheral didDiscoverCharacteristicsForService:(nonnull CBService *)service error:(nullable NSError *)error{
    if (error) {
        NSLog(@"设备服务特征值获取失败");
        return;
    }
    for (CBCharacteristic *characteristic in service.characteristics) {
        [peripheral discoverDescriptorsForCharacteristic:characteristic];//去发现这个服务的特征值
    }
}

//发现服务特征值描述之后的回调
-(void)peripheral:(CBPeripheral *)peripheral didDiscoverDescriptorsForCharacteristic:(nonnull CBCharacteristic *)characteristic error:(nullable NSError *)error{
    if (error) {
        NSLog(@"设备服务特征值描述获取失败");
        return;
    }
    CBService *service=characteristic.service;//获取服务对象
    NSString *serviceUuid=[service.UUID UUIDString];
    int serviceCurrentFinishCharacteristicCount=[self.serviceFinishCharacteristicsCount objectForKey:serviceUuid].intValue+1;
    [self.serviceFinishCharacteristicsCount setValue:[NSNumber numberWithInt:serviceCurrentFinishCharacteristicCount] forKey:serviceUuid];//将该服务已经完成的特征值数量加一
    if(serviceCurrentFinishCharacteristicCount==service.characteristics.count){//如果该服务的特征值的描述已经全部发现完成
        NSMutableArray<NSMutableDictionary<NSString*,id>*> *characteristicArray=[[NSMutableArray alloc]initWithCapacity:service.characteristics.count];
        for (CBCharacteristic * eachCharacteristic in service.characteristics) {//处理服务的每个特征值
            NSMutableDictionary<NSString*,id>* characteristicInfo=[[NSMutableDictionary alloc]initWithCapacity:3];
            [characteristicInfo setValue:[eachCharacteristic.UUID UUIDString] forKey:@"uuid"];
            [characteristicInfo setValue:[NSNumber numberWithLong:eachCharacteristic.properties] forKey:@"properties"];
            NSMutableArray<NSMutableDictionary<NSString*,id>*> * descriptorsArray=[[NSMutableArray alloc]initWithCapacity:eachCharacteristic.descriptors.count];
            for (CBDescriptor* eachDescriptor in characteristic.descriptors) {//处理特征值的每个描述
                NSMutableDictionary<NSString*,id>* descriptorInfo=[[NSMutableDictionary alloc]initWithCapacity:1];
                [descriptorInfo setValue:[eachDescriptor.UUID UUIDString] forKey:@"uuid"];
                [descriptorsArray addObject:descriptorInfo];
            }
            [characteristicInfo setValue:descriptorsArray forKey:@"descriptors"];
            [characteristicArray addObject:characteristicInfo];
        }
        [self.pluginInstance discoverService:self.identifier serviceUuid:serviceUuid characteristics:characteristicArray];//将服务的特征值已经描述返回给上层
    }
}


//使用特征值读设备或者设备notify通知数据的回调函数
-(void)peripheral:(CBPeripheral *)peripheral didUpdateValueForCharacteristic:(nonnull CBCharacteristic *)characteristic error:(nullable NSError *)error{
    if (error)
        [self.pluginInstance signalCallback:5 deviceId:self.identifier uuid:[characteristic.UUID UUIDString] isSuccess:NO data:nil];
    else[self.pluginInstance signalCallback:5 deviceId:self.identifier uuid:[characteristic.UUID UUIDString] isSuccess:YES data:characteristic.value];
}

//使用特征值写设备之后的回调函数
-(void)peripheral:(CBPeripheral *)peripheral didWriteValueForCharacteristic:(nonnull CBCharacteristic *)characteristic error:(nullable NSError *)error{
    if (error)
        [self.pluginInstance signalCallback:1 deviceId:self.identifier uuid:[characteristic.UUID UUIDString] isSuccess:NO data:nil];
    else[self.pluginInstance signalCallback:1 deviceId:self.identifier uuid:[characteristic.UUID UUIDString] isSuccess:YES data:characteristic.value];
}

//使用特征值描述读之后的回调函数
-(void)peripheral:(CBPeripheral *)peripheral didUpdateValueForDescriptor:(nonnull CBDescriptor *)descriptor error:(nullable NSError *)error{
    if (error)
        [self.pluginInstance signalCallback:3 deviceId:self.identifier uuid:[descriptor.UUID UUIDString] isSuccess:NO data:nil];
    else
        [self.pluginInstance signalCallback:3 deviceId:self.identifier uuid:[descriptor.UUID UUIDString] isSuccess:YES data:[self handelDescriptorValue:descriptor.value]];
}

//使用特征值描述写之后的回调函数
-(void)peripheral:(CBPeripheral *)peripheral didWriteValueForDescriptor:(nonnull CBDescriptor *)descriptor error:(nullable NSError *)error{
    if (error)
        [self.pluginInstance signalCallback:4 deviceId:self.identifier uuid:[descriptor.UUID UUIDString] isSuccess:NO data:nil];
    else
        [self.pluginInstance signalCallback:4 deviceId:self.identifier uuid:[descriptor.UUID UUIDString] isSuccess:YES data:[self handelDescriptorValue:descriptor.value]];
}


-(CBDescriptor *)getBlueDescriptor:(NSString *)serviceUuid characteristicUuid:(NSString *)characteristicUuid descriptorUuid:(NSString *)descriptorUuid{
    CBCharacteristic * characteristic=[self getBlueCharacteristic:serviceUuid characteristicUuid:characteristicUuid];
    if(characteristic==nil)
        return nil;
    CBDescriptor * result=nil;
    NSArray<CBDescriptor *>* descriptors=[characteristic descriptors];
    for (int i=0; i<[descriptors count]; i++) {
        CBDescriptor * item=descriptors[i];
        if([descriptorUuid isEqualToString:[item.UUID UUIDString]]){
            result=item;
            break;
        }
    }
    return result;
}
-(CBCharacteristic *)getBlueCharacteristic:(NSString *)serviceUuid characteristicUuid:(NSString *)characteristicUuid{
    CBService * service=[self getBlueService:serviceUuid];
    if(service==nil)
        return nil;
    CBCharacteristic * result=nil;
    NSArray<CBCharacteristic *>*characteristics=[service characteristics];
    for (int i=0; i<[characteristics count]; i++) {
        CBCharacteristic * item=characteristics[i];
        if([characteristicUuid isEqualToString:[item.UUID UUIDString]]){
            result=item;
            break;
        }
    }
    return result;
}

-(CBService *)getBlueService:(NSString *)serviceUuid{
    NSArray<CBService *> *services=[self.peripheral services];
    CBService * result=nil;
    for (int i=0; i<[services count]; i++) {
        CBService * item=services[i];
        if([serviceUuid isEqualToString:[item.UUID UUIDString]]){
            result=item;
            break;
        }
    }
    return result;
}

-(void)connectTimeoutCallback{
    [self.centralManager cancelPeripheralConnection:self.peripheral];//断开连接
    self.state=0;
    [self.pluginInstance connectTimeout:self.identifier];
}

//因为特征值描述返回的数据类型不一定是nsdata的,所以要统一处理一下返回成nsdata类型
-(NSData*) handelDescriptorValue:(id)value{
    if([value isKindOfClass:[NSData class]])
        return value;
    else if ([value isKindOfClass:[NSString class]])
        return [value dataUsingEncoding:NSUTF8StringEncoding];
    else if([value isKindOfClass:[NSNumber class]])
        return [NSData dataWithBytes:&value length:sizeof(value)];
    else return [NSData data];
}
@end

