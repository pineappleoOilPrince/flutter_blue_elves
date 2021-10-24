import 'dart:async';
import 'dart:typed_data';

import 'package:flutter/cupertino.dart';
import 'package:flutter/material.dart';
import 'package:flutter/painting.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter_blue_elves/flutter_blue_elves.dart';
import 'dart:io';

class DeviceControl extends StatefulWidget {
  final String? _macAddress;
  final String? _name;
  final Device _device;

  const DeviceControl(this._name, this._macAddress, this._device, {Key? key})
      : super(key: key);

  @override
  State<DeviceControl> createState() => _DeviceControlState([]);
}

class _DeviceControlState extends State<DeviceControl> {
  final List<_ServiceListItem> _serviceInfos;
  final TextEditingController _sendDataTextController = TextEditingController();
  late DeviceState _deviceState;
  final List<_LogItem> _logs = [];
  late int _mtu;
  late StreamSubscription<BleService> _serviceDiscoveryStream;
  late StreamSubscription<DeviceState> _stateStream;
  late StreamSubscription<DeviceSignalResult> _deviceSignalResultStream;

  _DeviceControlState(this._serviceInfos);

  @override
  void dispose() {
    _serviceDiscoveryStream.cancel();
    _stateStream.cancel();
    _deviceSignalResultStream.cancel();
    super.dispose();
  }

  @override
  void initState() {
    super.initState();
    _mtu=widget._device.mtu;
    _serviceDiscoveryStream =
        widget._device.serviceDiscoveryStream.listen((event) {
      setState(() {
        _serviceInfos.add(_ServiceListItem(event, false));
      });
    });
    _deviceState = widget._device.state;
    if (_deviceState == DeviceState.connected) {
      widget._device.discoveryService();
    }
    _stateStream = widget._device.stateStream.listen((event) {
      if (event == DeviceState.connected) {
        setState(() {
          _mtu=widget._device.mtu;
          _serviceInfos.clear();
        });
        widget._device.discoveryService();
      }
      setState(() {
        _deviceState = event;
      });
    });
    _deviceSignalResultStream =
        widget._device.deviceSignalResultStream.listen((event) {
      String? data;
      if (event.data != null && event.data!.isNotEmpty) {
        data = "0x";
        for (int i = 0; i < event.data!.length; i++) {
          String currentStr = event.data![i].toRadixString(16).toUpperCase();
          if (currentStr.length < 2) {
            currentStr = "0" + currentStr;
          }
          data = data! + currentStr;
        }
      }
      if (event.type == DeviceSignalType.characteristicsRead ||
          event.type == DeviceSignalType.unKnown) {
        setState(() {
          _logs.insert(
              0,
              _LogItem(
                  event.uuid,
                  (event.isSuccess
                          ? "read data success signal and data:"
                          : "read data failed signal and data:") +
                      (data ?? "none"),
                  DateTime.now().toString()));
        });
      } else if (event.type == DeviceSignalType.characteristicsWrite) {
        setState(() {
          _logs.insert(
              0,
              _LogItem(
                  event.uuid,
                  (event.isSuccess
                          ? "write data success signal and data:"
                          : "write data success signal and data:") +
                      (data ?? "none"),
                  DateTime.now().toString()));
        });
      } else if (event.type == DeviceSignalType.characteristicsNotify) {
        setState(() {
          _logs.insert(0,
              _LogItem(event.uuid, data ?? "none", DateTime.now().toString()));
        });
      } else if (event.type == DeviceSignalType.descriptorRead) {
        setState(() {
          _logs.insert(
              0,
              _LogItem(
                  event.uuid,
                  (event.isSuccess
                          ? "read descriptor data success signal and data:"
                          : "read descriptor data failed signal and data:") +
                      (data ?? "none"),
                  DateTime.now().toString()));
        });
      }
    });
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text("Device control"),
        actions: [
          Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Row(
                children: [
                  TextButton(
                    child: Text("Mtu:$_mtu",
                        style: const TextStyle(color: Colors.white)),
                    onPressed: () {
                      showDialog<void>(
                        context: context,
                        builder: (BuildContext dialogContext) {
                          return SimpleDialog(
                            title: const Text('Set Mtu'),
                            children: <Widget>[
                              TextField(
                                keyboardType:TextInputType.number,
                                autofocus: true,
                                controller: _sendDataTextController,
                                decoration: const InputDecoration(
                                  hintText:
                                  "Mtu is in [23,512]",
                                ),
                              ),
                              TextButton(
                                child: const Text("request"),
                                onPressed: () {
                                  widget._device.androidRequestMtu(int.parse(_sendDataTextController.text), (isSuccess, newMtu){
                                    setState(() {
                                      _mtu=newMtu;
                                    });
                                  });
                                  _sendDataTextController.clear();
                                  Navigator.pop(dialogContext);
                                },
                              )
                            ],
                          );
                        },
                      );
                    },
                  ),
                  TextButton(
                    child: Text(
                        _deviceState == DeviceState.connected
                            ? "Disconnect"
                            : "connect",
                        style: const TextStyle(color: Colors.white)),
                    onPressed: () {
                      if (_deviceState == DeviceState.connected) {
                        widget._device.disConnect();
                      } else {
                        widget._device.connect(connectTimeout: 5000);
                      }
                    },
                  )
                ],
              )
            ],
          )
        ],
      ),
      body: ListView(children: [
        SizedBox(
          height: 200,
          child: ListView.builder(
              itemCount: _logs.length,
              itemBuilder: (context, index) {
                return Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(_logs[index]._dateTime,
                        style: const TextStyle(color: Colors.green)),
                    Text(_logs[index]._characteristic + " return:",
                        style: const TextStyle(color: Colors.grey)),
                    Text(_logs[index]._data),
                    const Padding(
                      padding: EdgeInsets.fromLTRB(0, 20, 0, 0),
                    )
                  ],
                );
              }),
        ),
        ExpansionPanelList(
            expansionCallback: (int index, bool isExpanded) {
              setState(() {
                _serviceInfos[index]._isExpanded = !isExpanded;
              });
            },
            children: _serviceInfos.map((service) {
              String serviceTitle = "Unknow Service";
              if (Platform.isAndroid) {
                //安卓才能发现到这些服务
                switch (service._serviceInfo.serviceUuid.substring(4, 8)) {
                  case "1800":
                    serviceTitle = "Generic Access";
                    break;
                  case "1801":
                    serviceTitle = "Generic Attribute";
                    break;
                }
              }
              return ExpansionPanel(
                  headerBuilder: (context, isExpanded) {
                    return Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(serviceTitle,
                            style: const TextStyle(
                                fontSize: 16, fontWeight: FontWeight.bold)),
                        FittedBox(
                          fit: BoxFit.contain,
                          child: Row(
                            children: [
                              const Text("UUID:",
                                  style: TextStyle(
                                      fontSize: 14, color: Colors.grey)),
                              Text(service._serviceInfo.serviceUuid,
                                  style: const TextStyle(fontSize: 14))
                            ],
                          ),
                        )
                      ],
                    );
                  },
                  body: Column(
                    children: service._serviceInfo.characteristics
                        .map((characteristic) {
                      String properties = "";
                      List<ElevatedButton> buttons = [];
                      if (characteristic.properties
                          .contains(CharacteristicProperties.read)) {
                        buttons.add(ElevatedButton(
                          onPressed: () {
                            widget._device.readData(
                                service._serviceInfo.serviceUuid,
                                characteristic.uuid);
                          },
                          child: const Text("Read"),
                        ));
                      }
                      if (characteristic.properties
                              .contains(CharacteristicProperties.write) ||
                          characteristic.properties.contains(
                              CharacteristicProperties.writeNoResponse)) {
                        buttons.add(ElevatedButton(
                          child: const Text("Write"),
                          onPressed: () {
                            showDialog<void>(
                              context: context,
                              builder: (BuildContext dialogContext) {
                                return SimpleDialog(
                                  title: const Text('Write'),
                                  children: <Widget>[
                                    TextField(
                                      autofocus: true,
                                      controller: _sendDataTextController,
                                      decoration: const InputDecoration(
                                        hintText:
                                            "Enter hexadecimal,such as FED10101",
                                      ),
                                    ),
                                    TextButton(
                                      child: const Text("Send"),
                                      onPressed: () {
                                        String dataStr =
                                            _sendDataTextController.text;
                                        Uint8List data =
                                            Uint8List(dataStr.length ~/ 2);
                                        for (int i = 0;
                                            i < dataStr.length ~/ 2;
                                            i++) {
                                          data[i] = int.parse(
                                              dataStr.substring(
                                                  i * 2, i * 2 + 2),
                                              radix: 16);
                                        }
                                        widget._device.writeData(
                                            service._serviceInfo.serviceUuid,
                                            characteristic.uuid,
                                            true,
                                            data);
                                        _sendDataTextController.clear();
                                        Navigator.pop(dialogContext);
                                      },
                                    )
                                  ],
                                );
                              },
                            );
                          },
                        ));
                      }
                      if (characteristic.properties
                              .contains(CharacteristicProperties.notify) ||
                          characteristic.properties
                              .contains(CharacteristicProperties.indicate)) {
                        buttons.add(ElevatedButton(
                          child: const Text("Set Notify"),
                          onPressed: () {
                            showDialog<void>(
                              context: context,
                              builder: (BuildContext dialogContext) {
                                return SimpleDialog(
                                  title: const Text('Set Notify'),
                                  children: <Widget>[
                                    SimpleDialogOption(
                                      child: const Text('Enable notify'),
                                      onPressed: () {
                                        widget._device
                                            .setNotify(
                                                service
                                                    ._serviceInfo.serviceUuid,
                                                characteristic.uuid,
                                                true)
                                            .then((value) {
                                          if (value) {
                                            Navigator.pop(dialogContext);
                                          }
                                        });
                                      },
                                    ),
                                    SimpleDialogOption(
                                      child: const Text('Disable notify'),
                                      onPressed: () {
                                        widget._device
                                            .setNotify(
                                                service
                                                    ._serviceInfo.serviceUuid,
                                                characteristic.uuid,
                                                false)
                                            .then((value) {
                                          if (value) {
                                            Navigator.pop(dialogContext);
                                          }
                                        });
                                      },
                                    ),
                                  ],
                                );
                              },
                            );
                          },
                        ));
                      }
                      for (int i = 0;
                          i < characteristic.properties.length;
                          i++) {
                        properties += characteristic.properties[i]
                            .toString()
                            .replaceAll(
                                RegExp("CharacteristicProperties."), "");
                        if (i < characteristic.properties.length - 1) {
                          properties += ",";
                        }
                      }
                      return FittedBox(
                        fit: BoxFit.contain,
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            const Text("Characteristic",
                                style: TextStyle(
                                    fontSize: 16, fontWeight: FontWeight.bold)),
                            Row(
                              children: [
                                const Text("UUID:",
                                    style: TextStyle(
                                        fontSize: 14, color: Colors.grey)),
                                Text(characteristic.uuid,
                                    style: const TextStyle(fontSize: 14)),
                              ],
                            ),
                            Row(
                              children: [
                                const Text("Properties:",
                                    style: TextStyle(
                                        fontSize: 14, color: Colors.grey)),
                                Text(properties,
                                    style: const TextStyle(fontSize: 14)),
                              ],
                            ),
                            Row(
                              children: buttons,
                            ),
                            characteristic.descriptors.isEmpty
                                ? const Padding(
                                    padding: EdgeInsets.fromLTRB(0, 10, 0, 0))
                                : FittedBox(
                                    fit: BoxFit.contain,
                                    child: Row(
                                      children: [
                                        const Padding(
                                            padding: EdgeInsets.fromLTRB(
                                                20, 0, 0, 0)),
                                        Column(
                                            crossAxisAlignment:
                                                CrossAxisAlignment.start,
                                            children: [
                                              const Text("Descriptors:",
                                                  style: TextStyle(
                                                      fontSize: 16,
                                                      fontWeight:
                                                          FontWeight.bold)),
                                              Column(
                                                crossAxisAlignment:
                                                    CrossAxisAlignment.start,
                                                children: characteristic
                                                    .descriptors
                                                    .map((descriptor) {
                                                  String descriptorType =
                                                      "UnKnown";
                                                  switch (Platform.isAndroid
                                                      ? descriptor.uuid
                                                          .substring(4, 8)
                                                      : descriptor.uuid) {
                                                    case "2900":
                                                      descriptorType =
                                                          "Characteristic Extended Properties";
                                                      break;
                                                    case "2901":
                                                      descriptorType =
                                                          "Characteristic User Description";
                                                      break;
                                                    case "2902":
                                                      descriptorType =
                                                          "Client Characteristic Configuration";
                                                      break;
                                                    case "2903":
                                                      descriptorType =
                                                          "Server Characteristic Configuration";
                                                      break;
                                                    case "2904":
                                                      descriptorType =
                                                          "Characteristic Presentation Format";
                                                      break;
                                                    case "2905":
                                                      descriptorType =
                                                          "Characteristic Aggregate Format";
                                                      break;
                                                    case "2906":
                                                      descriptorType =
                                                          "Valid Range";
                                                      break;
                                                    case "2907":
                                                      descriptorType =
                                                          "External Report Reference Descriptor";
                                                      break;
                                                    case "2908":
                                                      descriptorType =
                                                          "Report Reference Descriptor";
                                                      break;
                                                  }
                                                  return Column(
                                                    crossAxisAlignment:
                                                        CrossAxisAlignment
                                                            .start,
                                                    children: [
                                                      Text(descriptorType,
                                                          style: const TextStyle(
                                                              fontSize: 15,
                                                              fontWeight:
                                                                  FontWeight
                                                                      .bold)),
                                                      Row(
                                                        children: [
                                                          const Text("UUID:",
                                                              style: TextStyle(
                                                                  fontSize: 14,
                                                                  color: Colors
                                                                      .grey)),
                                                          Text(descriptor.uuid,
                                                              style:
                                                                  const TextStyle(
                                                                      fontSize:
                                                                          14)),
                                                        ],
                                                      ),
                                                      Row(children: [
                                                        ElevatedButton(
                                                          child: const Text(
                                                              "Read"),
                                                          onPressed: () {
                                                            widget._device.readDescriptorData(
                                                                service
                                                                    ._serviceInfo
                                                                    .serviceUuid,
                                                                characteristic
                                                                    .uuid,
                                                                descriptor
                                                                    .uuid);
                                                          },
                                                        ),
                                                        descriptorType ==
                                                                "Client Characteristic Configuration"
                                                            ? ElevatedButton(
                                                                child:
                                                                    const Text(
                                                                        "Write"),
                                                                onPressed: () {
                                                                  showDialog<
                                                                      void>(
                                                                    context:
                                                                        context,
                                                                    builder:
                                                                        (BuildContext
                                                                            dialogContext) {
                                                                      return SimpleDialog(
                                                                        title: const Text(
                                                                            'Send'),
                                                                        children: <
                                                                            Widget>[
                                                                          TextField(
                                                                            autofocus:
                                                                                true,
                                                                            controller:
                                                                                _sendDataTextController,
                                                                            decoration:
                                                                                const InputDecoration(
                                                                              hintText: "Enter hexadecimal,such as FED10101",
                                                                            ),
                                                                          ),
                                                                          TextButton(
                                                                            child:
                                                                                const Text("Send"),
                                                                            onPressed:
                                                                                () {
                                                                              String dataStr = _sendDataTextController.text;
                                                                              Uint8List data = Uint8List(dataStr.length ~/ 2);
                                                                              for (int i = 0; i < dataStr.length ~/ 2; i++) {
                                                                                data[i] = int.parse(dataStr.substring(i * 2, i * 2 + 2), radix: 16);
                                                                              }
                                                                              widget._device.writeDescriptorData(service._serviceInfo.serviceUuid, characteristic.uuid, descriptor.uuid, data);
                                                                              _sendDataTextController.clear();
                                                                              Navigator.pop(dialogContext);
                                                                            },
                                                                          )
                                                                        ],
                                                                      );
                                                                    },
                                                                  );
                                                                },
                                                              )
                                                            : const Padding(
                                                                padding:
                                                                    EdgeInsets
                                                                        .fromLTRB(
                                                                            0,
                                                                            0,
                                                                            0,
                                                                            0),
                                                              )
                                                      ]),
                                                    ],
                                                  );
                                                }).toList(),
                                              )
                                            ]),
                                      ],
                                    )),
                          ],
                        ),
                      );
                    }).toList(),
                  ),
                  isExpanded: service._isExpanded);
            }).toList())
      ]),
    );
  }
}

class _ServiceListItem {
  final BleService _serviceInfo;
  bool _isExpanded;

  _ServiceListItem(this._serviceInfo, this._isExpanded);
}

class _LogItem {
  final String _characteristic;
  final String _data;
  final String _dateTime;

  _LogItem(this._characteristic, this._data, this._dateTime);
}
