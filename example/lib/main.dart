import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter_blue_elves/flutter_blue_elves.dart';
import 'device_control.dart';
import 'dart:io';

void main() {
  runApp(const MyApp());
}

class MyApp extends StatefulWidget {
  const MyApp({Key key}) : super(key: key);

  @override
  State<MyApp> createState() => _MyAppState();
}

class _MyAppState extends State<MyApp> {
  List<AndroidBluetoothLack> _blueLack = [];
  IosBluetoothState _iosBlueState = IosBluetoothState.unKnown;
  List<ScanResult> _scanResultList = [];
  final List<_ConnectedItem> _connectedList = [];
  bool _isScaning=false;

  @override
  void initState() {
    super.initState();
    Timer.periodic(const Duration(milliseconds: 2000),
        Platform.isAndroid ? androidGetBlueLack : iosGetBlueState);
  }

  void iosGetBlueState(timer) {
    FlutterBlueElves.instance.iosCheckBluetoothState().then((value) {
      setState(() {
        _iosBlueState = value;
      });
    });
  }

  void androidGetBlueLack(timer) {
    FlutterBlueElves.instance.androidCheckBlueLackWhat().then((values) {
      setState(() {
        _blueLack = values;
      });
    });
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      home: Scaffold(
        appBar: AppBar(
          centerTitle: true,
          title: Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: Platform.isAndroid
                ? [
                    FlatButton(
                      color: _blueLack
                              .contains(AndroidBluetoothLack.locationPermission)
                          ? Colors.red
                          : Colors.green,
                      child: const Text("Permission",
                          style: TextStyle(color: Colors.black)),
                      onPressed: () {
                        if (_blueLack.contains(
                            AndroidBluetoothLack.locationPermission)) {
                          FlutterBlueElves.instance
                              .androidApplyLocationPermission((isOk) {
                            print(isOk ? "User agrees to grant location permission" : "User does not agree to grant location permission");
                          });
                        }
                      },
                    ),
                    FlatButton(
                      color: _blueLack
                              .contains(AndroidBluetoothLack.locationFunction)
                          ? Colors.red
                          : Colors.green,
                      child: const Text("GPS",
                          style: TextStyle(color: Colors.black)),
                      onPressed: () {
                        if (_blueLack
                            .contains(AndroidBluetoothLack.locationFunction)) {
                          FlutterBlueElves.instance
                              .androidOpenLocationService((isOk) {
                            print(isOk ? "The user agrees to turn on the positioning function" : "The user does not agree to enable the positioning function");
                          });
                        }
                      },
                    ),
                    FlatButton(
                      color: _blueLack
                              .contains(AndroidBluetoothLack.bluetoothFunction)
                          ? Colors.red
                          : Colors.green,
                      child: const Text("Blue",
                          style: TextStyle(color: Colors.black)),
                      onPressed: () {
                        if (_blueLack
                            .contains(AndroidBluetoothLack.bluetoothFunction)) {
                          FlutterBlueElves.instance
                              .androidOpenBluetoothService((isOk) {
                            print(isOk ? "The user agrees to turn on the Bluetooth function" : "The user does not agrees to turn on the Bluetooth function");
                          });
                        }
                      },
                    )
                  ]
                : [
                    FlatButton(
                      color: _iosBlueState == IosBluetoothState.poweredOn
                          ? Colors.green
                          : Colors.red,
                      child: Text(
                          "BlueToothState:" +
                              _iosBlueState
                                  .toString()
                                  .replaceAll(RegExp("IosBluetoothState."), ""),
                          style: const TextStyle(color: Colors.black)),
                      onPressed: () {
                        if(_iosBlueState==IosBluetoothState.unKnown){
                          showDialog<void>(
                            context: context,
                            builder: (BuildContext dialogContext) {
                              return AlertDialog(
                                title: const Text("Tip"),
                                content: Text("Bluetooth is not initialized, please wait"),
                                actions: <Widget>[
                                  FlatButton(
                                    child: Text("close"),
                                    onPressed: () => Navigator.of(context).pop(),
                                  ),
                                ],
                              );
                            },
                          );
                        }else if(_iosBlueState==IosBluetoothState.resetting){
                          showDialog<void>(
                            context: context,
                            builder: (BuildContext dialogContext) {
                              return AlertDialog(
                                title: Text("Tip"),
                                content: Text("Bluetooth is resetting, please wait"),
                                actions: <Widget>[
                                  FlatButton(
                                    child: Text("close"),
                                    onPressed: () => Navigator.of(context).pop(),
                                  ),
                                ],
                              );
                            },
                          );
                        }
                        else if(_iosBlueState==IosBluetoothState.unSupport){
                          showDialog<void>(
                            context: context,
                            builder: (BuildContext dialogContext) {
                              return AlertDialog(
                                title: Text("Tip"),
                                content: Text("The current device does not support Bluetooth, please check"),
                                actions: <Widget>[
                                  FlatButton(
                                    child: Text("close"),
                                    onPressed: () => Navigator.of(context).pop(),
                                  ),
                                ],
                              );
                            },
                          );
                        }else if(_iosBlueState==IosBluetoothState.unAuthorized){
                          showDialog<void>(
                            context: context,
                            builder: (BuildContext dialogContext) {
                              return AlertDialog(
                                title: Text("Tip"),
                                content: Text("The current app does not have Bluetooth permission, please go to the settings to grant"),
                                actions: <Widget>[
                                  FlatButton(
                                    child: Text("close"),
                                    onPressed: () => Navigator.of(context).pop(),
                                  ),
                                ],
                              );
                            },
                          );
                        }else if(_iosBlueState==IosBluetoothState.poweredOff){
                          showDialog<void>(
                            context: context,
                            builder: (BuildContext dialogContext) {
                              return AlertDialog(
                                title: Text("Tip"),
                                content: Text("Bluetooth is not currently turned on, please check"),
                                actions: <Widget>[
                                  FlatButton(
                                    child: Text("close"),
                                    onPressed: () => Navigator.of(context).pop(),
                                  ),
                                ],
                              );
                            },
                          );
                        }
                      },
                    ),
                  ],
          ),
        ),

        body: ListView.separated(
          itemCount:
              (_connectedList.isNotEmpty ? _connectedList.length + 1 : 0) +
                  (_scanResultList.isNotEmpty ? _scanResultList.length + 1 : 0),
          itemBuilder: (BuildContext context, int index) {
            if (_connectedList.isNotEmpty && index <= _connectedList.length) {
              if (index == 0) {
                return const Text("Paired device",
                    style: TextStyle(
                        color: Colors.black,
                        fontSize: 16,
                        fontWeight: FontWeight.bold));
              } else {
                _ConnectedItem currentConnected = _connectedList[index - 1];
                return Padding(
                  padding: const EdgeInsets.fromLTRB(16, 4, 16, 4),
                  child: Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      Expanded(
                        flex: 6,
                        child: Column(
                          mainAxisAlignment: MainAxisAlignment.spaceBetween,
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(currentConnected._name ?? "Unnamed device",
                                style: const TextStyle(
                                    color: Colors.black, fontSize: 16)),
                            Text(currentConnected._macAddress ?? "Unable to get mac address",
                                style: const TextStyle(
                                    color: Colors.black, fontSize: 12)),
                          ],
                        ),
                      ),
                      Expanded(
                        flex: 4,
                        child: StreamBuilder<DeviceState>(
                            initialData: DeviceState.connected,
                            stream: currentConnected._device.stateStream,
                            builder: (BuildContext context,
                                AsyncSnapshot<DeviceState> snapshot) {
                              DeviceState currentState =
                                  snapshot.connectionState ==
                                          ConnectionState.active
                                      ? snapshot.data
                                      : currentConnected._device.state;
                              return Column(children: [
                                RaisedButton(
                                  child: Text(
                                      currentState == DeviceState.connected
                                          ? "Disconnect"
                                          : "Connect"),
                                  onPressed: () {
                                    if (currentState == DeviceState.connected) {
                                      currentConnected._device.disConnect();
                                    } else {
                                      currentConnected._device
                                          .connect(connectTimeout: 5000);
                                    }
                                  },
                                ),
                                RaisedButton(
                                  child: const Text("destroy"),
                                  onPressed: () {
                                    currentConnected._device.destroy();
                                    setState(() {
                                      _connectedList.removeAt(index - 1);
                                    });
                                  },
                                ),
                                RaisedButton(
                                  child: const Text("To tap"),
                                  onPressed: () {
                                    Navigator.push(
                                      context,
                                      MaterialPageRoute(
                                        builder: (context) {
                                          return DeviceControl(
                                              currentConnected._name,
                                              currentConnected._macAddress,
                                              currentConnected._device);
                                        },
                                      ),
                                    );
                                  },
                                )
                              ]);
                            }),
                      ),
                    ],
                  ),
                );
              }
            } else {
              int scanStartIndex =
                  _connectedList.isNotEmpty ? _connectedList.length + 1 : 0;
              if (index == scanStartIndex) {
                return const Text("Scan Results",
                    style: TextStyle(
                        color: Colors.black,
                        fontSize: 16,
                        fontWeight: FontWeight.bold));
              } else {
                ScanResult currentScan =
                    _scanResultList[index - scanStartIndex - 1];
                return Padding(
                    padding: const EdgeInsets.fromLTRB(16, 4, 16, 4),
                    child: Row(
                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                      children: [
                        Expanded(
                          flex: 7,
                          child: Column(
                            mainAxisAlignment: MainAxisAlignment.spaceBetween,
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text(currentScan.name ?? "Unnamed device",
                                  style: const TextStyle(
                                      color: Colors.black, fontSize: 16)),
                              Text(
                                  currentScan.uuids.length.toString() +
                                      " Service Advertised",
                                  style: const TextStyle(
                                      color: Colors.grey, fontSize: 12)),
                              Text(currentScan.macAddress ?? "Unable to get mac address",
                                  style: const TextStyle(
                                      color: Colors.black, fontSize: 12)),
                              currentScan.localName == null
                                  ? const Padding(
                                      padding: EdgeInsets.fromLTRB(0, 0, 0, 0),
                                    )
                                  : Column(
                                      crossAxisAlignment:
                                          CrossAxisAlignment.start,
                                      children: [
                                        const Text("LocalName:",
                                            style: TextStyle(
                                                color: Colors.grey,
                                                fontSize: 12)),
                                        Text(currentScan.localName,
                                            style: const TextStyle(
                                                color: Colors.black,
                                                fontSize: 14))
                                      ],
                                    ),
                              currentScan.manufacturerSpecificData == null
                                  ? const Padding(
                                      padding: EdgeInsets.fromLTRB(0, 0, 0, 0),
                                    )
                                  : const Text("ManufacturerData:",
                                      style: TextStyle(
                                          color: Colors.grey, fontSize: 12)),
                              currentScan.manufacturerSpecificData == null
                                  ? const Padding(
                                      padding: EdgeInsets.fromLTRB(0, 0, 0, 0),
                                    )
                                  : Column(
                                      children: currentScan
                                          .manufacturerSpecificData.entries
                                          .map((entry) {
                                      String data = "";
                                      for (int byte in entry.value) {
                                        String byteStr = byte.toRadixString(16);
                                        data += byteStr.length > 1
                                            ? byteStr
                                            : ("0" + byteStr);
                                        data += ",";
                                      }
                                      data = data.replaceRange(
                                          data.length - 1, null, "");
                                      data = "[" + data + "]";
                                      int keyInt = entry.key;
                                      String keyStr = keyInt.toRadixString(16);
                                      return Text(keyStr + ":" + data,
                                          style: const TextStyle(
                                              color: Colors.black,
                                              fontSize: 12));
                                    }).toList()),
                              currentScan.row == null
                                  ? const Padding(
                                      padding: EdgeInsets.fromLTRB(0, 0, 0, 0),
                                    )
                                  : Column(
                                      crossAxisAlignment:
                                          CrossAxisAlignment.start,
                                      children: [
                                        const Text("Row:",
                                            style: TextStyle(
                                                color: Colors.grey,
                                                fontSize: 12)),
                                        Text((currentScan.row
                                            .map((byte) {
                                              String byteStr =
                                                  byte.toRadixString(16);
                                              return byteStr.length > 1
                                                  ? byteStr
                                                  : "0" + byteStr;
                                            })
                                            .toList()
                                            .toString()))
                                      ],
                                    )
                            ],
                          ),
                        ),
                        Expanded(
                          flex: 3,
                          child: Column(
                            children: [
                              RaisedButton(
                                  onPressed: () {
                                    ScanResult scanMsg = _scanResultList[
                                        index - scanStartIndex - 1];
                                    Device toConnectDevice =
                                        scanMsg.connect(connectTimeout: 5000);
                                    setState(() {
                                      _connectedList.insert(
                                          0,
                                          _ConnectedItem(
                                              toConnectDevice,
                                              scanMsg.macAddress,
                                              scanMsg.name));
                                      _scanResultList
                                          .removeAt(index - scanStartIndex - 1);
                                    });
                                    Navigator.push(
                                      context,
                                      MaterialPageRoute(
                                        builder: (context) {
                                          return DeviceControl(
                                              scanMsg.name,
                                              scanMsg.macAddress,
                                              toConnectDevice);
                                        },
                                      ),
                                    );
                                  },
                                  child: const Text("Connect")),
                              Text(currentScan.rssi.toString() + " dBm",
                                  style: const TextStyle(
                                      color: Colors.lightGreen, fontSize: 12))
                            ],
                          ),
                        )
                      ],
                    ));
              }
            }
          },
          separatorBuilder: (BuildContext context, int index) {
            if (_connectedList.isNotEmpty && index <= _connectedList.length) {
              if (index == 0 || index == _connectedList.length) {
                return const Divider(color: Colors.white);
              } else {
                return const Divider(color: Colors.grey);
              }
            } else {
              int scanStartIndex =
                  _connectedList.isNotEmpty ? _connectedList.length + 1 : 0;
              if (index == scanStartIndex ||
                  index == scanStartIndex + _scanResultList.length) {
                return const Divider(color: Colors.white);
              } else {
                return const Divider(color: Colors.grey);
              }
            }
          },
        ),
        floatingActionButton: FloatingActionButton(
          backgroundColor: _isScaning?Colors.red:Colors.blue,
          onPressed: () {
            if ((Platform.isAndroid&&_blueLack.isEmpty)||(Platform.isIOS&&_iosBlueState==IosBluetoothState.poweredOn)) {
              if(_isScaning){
                FlutterBlueElves.instance.stopScan();
              }else{
                _scanResultList = [];
                setState(() {
                  _isScaning=true;
                });
                FlutterBlueElves.instance.startScan(5000).listen((event) {
                  setState(() {
                    _scanResultList.insert(0, event);
                  });
                }).onDone(() {
                  setState(() {
                    _isScaning=false;
                  });
                });
              }
            }
          },
          tooltip: 'scan',
          child: Icon(_isScaning?Icons.stop:Icons.find_replace),
        ), // This trailing comma makes auto-formatting nicer for build methods.
      ),
    );
  }
}

class _ConnectedItem {
  final Device _device;
  final String _macAddress;
  final String _name;

  _ConnectedItem(this._device, this._macAddress, this._name);
}
