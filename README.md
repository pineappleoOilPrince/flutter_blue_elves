# flutter_blue_elves

A flutter plugin witch includes platform-specific implementation code for Android and/or iOS to connect and control bluetooth ble device.

## Table of Contents

- [Install](#install)
- [Usage](#usage)
- [License](#license)

### Install
This project is a flutter plugin,so you use it by add dependencies in your pubspec.yaml.
```yaml
dependencies:
  flutter:
    sdk: flutter
  flutter_blue_elves: 0.0.1
```
```shell script
$ flutter pub get
```
### Usage
Import plugin module where you need use.
```dart
import 'package:flutter_blue_elves/flutter_blue_elves.dart';
```
Check bluetooth function is Ok.
```dart
///Android:
FlutterBlueElves.instance.androidCheckBlueLackWhat().then((values) {
  if(values.contains(AndroidBluetoothLack.locationPermission)){
      ///no location permission
  } 
  if(values.contains(AndroidBluetoothLack.locationFunction)){
      ///location powerOff
  } 
  if(values.contains(AndroidBluetoothLack.bluetoothFunction)){
    ///bluetooth powerOff
  } 
});

///Ios:
FlutterBlueElves.instance.iosCheckBluetoothState().then((value) {
  if(value==IosBluetoothState.unKnown){
    ///Bluetooth is not initialized
  }else if(value==IosBluetoothState.resetting){
    ///Bluetooth is resetting
  }else if(value==IosBluetoothState.unSupport){
    ///Bluetooth not support
  }else if(value==IosBluetoothState.unAuthorized){
       ///No give bluetooth permission
  }else if(value==IosBluetoothState.poweredOff){
    ///bluetooth powerOff
  }else{
    ///bluetooth is ok
  }
}
```
Turn on bluetooth function what bluetooth need.Just for android.
```dart
///apply location permission
FlutterBlueElves.instance.androidApplyLocationPermission((isOk) {
  print(isOk ? "User agrees to grant location permission" : "User does not agree to grant location permission");
});
///turn on location function
FlutterBlueElves.instance.androidOpenLocationService((isOk) {
  print(isOk ? "The user agrees to turn on the positioning function" : "The user does not agree to enable the positioning function");
});
///turn on bluetooth function
FlutterBlueElves.instance.androidOpenBluetoothService((isOk) {
  print(isOk ? "The user agrees to turn on the Bluetooth function" : "The user does not agrees to turn on the Bluetooth function");
});
```
Scan bluetooth device not connected.
```dart
///start scan,you can set scan timeout
FlutterBlueElves.instance.startScan(5000).listen((scanItem) {
  ///Use the information in the scanned object to filter the devices you want
  ///if want to connect someone,call scanItem.connect,it will return Device object
  Device device = scanItem.connect(connectTimeout: 5000);
  ///you can use this device to listen bluetooth device's state
  device.stateStream.listen((newState){
    ///newState is DeviceState type,include disconnected,disConnecting, connecting,connected, connectTimeout,initiativeDisConnected,destroyed
  }).onDone(() {
    ///if scan timeout or you stop scan,will into this
  });
});

///stop scan
FlutterBlueElves.instance.stopScan();
```

Discovery device's bluetooth service.
```dart
///use this stream to listen discovery result
device.serviceDiscoveryStream.listen((serviceItem) {
  ///serviceItem type is BleService,is readonly.It include BleCharacteristic and BleDescriptor
});
///to discovery service,witch work in connected
device.discoveryService();
```
Communicate with the device,witch work in connected.
```dart
///use this stream to listen data result
device.deviceSignalResultStream.listen((result) {
  ///result type is DeviceSignalResult,is readonly.It have DeviceSignalType attributes,witch include characteristicsRead,characteristicsWrite,characteristicsNotify,descriptorRead,descriptorWrite,unKnown.
  ///In ios you will accept unKnown,because characteristicsRead is as same as characteristicsNotify for ios.So characteristicsRead or characteristicsNotify will return unKnown.
});

///Read data from device by Characteristic.
///to read,witch work in connected
device.readData(serviceUuid,characteristicUuid);

///Write data to device by Characteristic.
///to write,witch work in connected.After my test,i find this isNoResponse is work in ios but not in android.
///In ios if you set isNoResponse,you will not receive data after write,but android will.
device.writeData(serviceUuid,characteristicUuid,isNoResponse,data);

///Read data from device by Descriptor.
device.devicereadDescriptorData(serviceUuid,characteristicUuid);

///Write data to device by Descriptor.
device.writeDescriptorData(serviceUuid,characteristicUuid,data);
```

Connect&Disconnect Device
```dart
///work in disConnected
///This connection method is direct connection, because it has been connected before, so I saved the direct connection object, and I can use this object to connect again
device.connect(5000);
///work in connected
///disconnect device
device.disConnect();
```

Destroy Device object
```dart
///if you never use this Device Object,call destroy(),witch can save space
device.destroy();
```

## License

[MIT](LICENSE) © PineappleOilPrince

