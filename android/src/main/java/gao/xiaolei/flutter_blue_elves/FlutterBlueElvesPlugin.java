package gao.xiaolei.flutter_blue_elves;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelUuid;
import android.provider.Settings;
import android.util.SparseArray;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import gao.xiaolei.flutter_blue_elves.callback.ConnectStateCallback;
import gao.xiaolei.flutter_blue_elves.callback.DeviceSignalCallback;
import gao.xiaolei.flutter_blue_elves.callback.DiscoverServiceCallback;
import gao.xiaolei.flutter_blue_elves.callback.MtuChangeCallback;
import gao.xiaolei.flutter_blue_elves.util.MyScanRecord;
import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.embedding.engine.plugins.activity.ActivityAware;
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding;
import io.flutter.plugin.common.BinaryMessenger;
import io.flutter.plugin.common.EventChannel;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.common.PluginRegistry;

/**
 * FlutterBlueElvesPlugin
 */
public class FlutterBlueElvesPlugin implements FlutterPlugin, MethodCallHandler, ActivityAware {
    private final String CHANNEL_METHOD = "flutter_blue_elves/method";
    private final String EVENT_CHANNEL = "flutter_blue_elves/event";
    private Context context;
    private Activity activity;
    private AlertDialog.Builder alertBuilder;//android弹窗
    private Handler mHandler = new Handler();//为了开启定时任务
    private BluetoothManager mBluetoothManager;//本地蓝牙管理
    private BluetoothAdapter mBluetoothAdapter;//本地蓝牙适配器
    private EventChannel.EventSink mySink;//flutter中订阅了要接收我发出的消息的观察者们
    private Map<String, BluetoothDevice> scanDeviceCaches = new HashMap<>();//用于缓存扫描过的设备对象
    private Map<String, BluetoothDevice> hideConnectedDeviceCaches = new HashMap<>();//用于缓存其他应用连接的设备对象
    private boolean scanIsAllowDuplicates = false;//判断扫描设备时是否运行返回重复设备
    private boolean isScanFix=false;//判断此次扫描是不是修复性的扫描
    private String currentFixDeviceId;//当前扫描的目标设备Id
    private long fixScanStartTime;//修复扫描的开始时间
    private int fixScanConnectTimeout;//修复扫描的超时时间
    private Set<String> alreadyScanDeviceMacAddress = new HashSet<>();//用于储存扫描过的设备mac地址
    private Map<String, Device> devicesMap = new HashMap<>();//用于储存设备对象的map
    private static final int REQUEST_CODE_LOCATION_PERMISSION = 2;//申请定位权限的requestCode
    private static final int REQUEST_CODE_LOCATION_SETTINGS = 3;//开启定位服务的requestCode
    private static final int REQUEST_CODE_OPEN_BLUETOOTH = 4;//开启蓝牙服务的requestCode
    private static final int REQUEST_CODE_BLUE_PERMISSION = 5;//申请蓝牙权限的requestCode

    /// The MethodChannel that will the communication between Flutter and native Android
    ///
    /// This local reference serves to register the plugin with the Flutter Engine and unregister it
    /// when the Flutter Engine is detached from the Activity
    private MethodChannel channel;

    @Override
    public void onAttachedToEngine(@NonNull FlutterPluginBinding flutterPluginBinding) {
        stepUp(flutterPluginBinding.getBinaryMessenger(),flutterPluginBinding.getApplicationContext(),null);
    }

    public void stepUp(BinaryMessenger binaryMessenger,Context mContext,Activity mActivity){
        channel = new MethodChannel(binaryMessenger, CHANNEL_METHOD);
        channel.setMethodCallHandler(this);
        context = mContext;
        activity=mActivity;
        mBluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);//调用系统服务获取蓝牙管理对象
        mBluetoothAdapter = mBluetoothManager.getAdapter();//获取本地蓝牙适配器
        new EventChannel(binaryMessenger, EVENT_CHANNEL).setStreamHandler(new EventChannel.StreamHandler() {//创建对flutter的消息发送channel
            @Override
            public void onListen(Object o, EventChannel.EventSink eventSink) {//当flutter中有人要订阅我们发送的消息时就会调用这个函数
                //System.out.println("添加了一个观察者");
                mySink = eventSink;//设置观察者
            }

            @Override
            public void onCancel(Object o) {//当flutter中有人要取消订阅我们发送的消息时就会调用这个函数
                //System.out.println("删除了一个观察者");
                mySink = null;//将观察者移除
            }
        });
    }

    // This static function is optional and equivalent to onAttachedToEngine. It supports the old
    // pre-Flutter-1.12 Android projects. You are encouraged to continue supporting
    // plugin registration via this function while apps migrate to use the new Android APIs
    // post-flutter-1.12 via https://flutter.dev/go/android-project-migration.
    //
    // It is encouraged to share logic between onAttachedToEngine and registerWith to keep
    // them functionally equivalent. Only one of onAttachedToEngine or registerWith will be called
    // depending on the user's project. onAttachedToEngine or registerWith must both be defined
    // in the same class.
    public static void registerWith(PluginRegistry.Registrar registrar) {
        gao.xiaolei.flutter_blue_elves.FlutterBlueElvesPlugin instance=new gao.xiaolei.flutter_blue_elves.FlutterBlueElvesPlugin();
        instance.stepUp(registrar.messenger(),registrar.context(),registrar.activity());
    }

    @Override
    public void onMethodCall(@NonNull MethodCall call, @NonNull Result result) {
        switch (call.method) {
            case "startScan"://如果是扫描设备
                Map<String, Object> scanParamsMap = call.arguments();
                scanDevices((boolean) scanParamsMap.get("isAllowDuplicates"), (int) scanParamsMap.get("timeout"),false,null);
                result.success(null);
                break;
            case "stopScan"://如果是停止扫描
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
                    stopScan21();
                else
                    stopScan18();
                result.success(null);
                break;
            case "getHideConnected"://如果是获取其他应用连接上的设备信息
                result.success(getHideConnectedDevice());
                break;
            case "connect"://如果是连接设备
                Map<String, Object> connectParamsMap = call.arguments();
                String connectDeviceId = (String) connectParamsMap.get("id") ;
                boolean isFromScan=(boolean)connectParamsMap.get("isFromScan") ;
                BluetoothDevice cache = isFromScan ? scanDeviceCaches.remove(connectDeviceId):hideConnectedDeviceCaches.remove(connectDeviceId);//从扫描结果或者隐藏结果中去连接只能执行一次
                if (cache != null) {//如果这个id存在的话
                    Device toConnectDevice = new Device(context, mHandler,cache, mConnectStateCallback, mDeviceSignalCallback, myDiscoverServiceCallback,mtuChangeCallback);
                    devicesMap.put(connectDeviceId,toConnectDevice);
                    int timeout=(int) connectParamsMap.get("timeout");
                    if(toConnectDevice.isInBleCache())//如果在蓝牙堆栈里就可以直接连接
                        toConnectDevice.connectDevice(timeout);
                    else//如果不在蓝牙堆栈里就要先扫描再连接
                        scanDevices(false,timeout ,true,connectDeviceId);
                    result.success(true);
                } else result.success(false);
                break;
            case "reConnect"://如果是重连设备
                Map<String, Object> reConnectParamsMap = call.arguments();
                String reConnectDeviceId = (String) reConnectParamsMap.get("id") ;
                Device reConnectDevice=devicesMap.get(reConnectDeviceId);
                if (reConnectDevice != null) {//如果这个id存在的话
                    int timeout=(int) reConnectParamsMap.get("timeout");
                    if(reConnectDevice.isInBleCache())
                        reConnectDevice.connectDevice(timeout);
                    else//如果不在蓝牙堆栈里就要先扫描再连接
                        scanDevices(false,timeout ,true,reConnectDeviceId);
                    result.success(true);
                } else result.success(false);
                break;
            case "disConnect"://如果是断开设备连接
                Map<String, Object> disConnectParamsMap = call.arguments();
                String disConnectDeviceId = (String) disConnectParamsMap.get("id") ;
                Device disConnectDevice=devicesMap.get(disConnectDeviceId);
                if (disConnectDevice != null) {//如果这个id存在的话
                    disConnectDevice.initiativeDisConnect();
                    result.success(true);
                } else result.success(false);
                break;
            case "discoverService": //如果是去发现服务
                Map<String, Object> discoverServiceParamsMap = call.arguments();
                Device toDiscoverServiceDevice = devicesMap.get( (String)discoverServiceParamsMap.get("id"));
                if (toDiscoverServiceDevice != null) {//如果这个id存在的话
                    toDiscoverServiceDevice.discoverService();
                    result.success(true);
                } else result.success(false);
                break;
            case "setNotify"://开启或者关闭notify
                Map<String, Object> setNotifyParamsMap = call.arguments();
                Device setNotifyDevice = devicesMap.get( (String)setNotifyParamsMap.get("id"));
                if (setNotifyDevice != null) {
                    result.success(setNotifyDevice.setNotifyCharacteristic((String) setNotifyParamsMap.get("serviceUuid"), (String) setNotifyParamsMap.get("characteristicUuid"), (boolean) setNotifyParamsMap.get("isEnable")));
                } else result.success(false);
                break;
            case "readData"://使用读特征值向设备读取数据
                Map<String, Object> readDataParamsMap = call.arguments();
                Device readDataDevice = devicesMap.get( (String)readDataParamsMap.get("id"));
                if (readDataDevice != null) {//如果这个id存在的话
                    readDataDevice.readDataToDevice((String) readDataParamsMap.get("serviceUuid"), (String) readDataParamsMap.get("characteristicUuid"));
                    result.success(true);
                } else result.success(false);
                break;
            case "writeData"://使用写特征值向设备发送数据
                Map<String, Object> writeDataParamsMap = call.arguments();
                Device writeDataDevice = devicesMap.get( (String)writeDataParamsMap.get("id"));
                if (writeDataDevice != null) {//如果这个id存在的话
                    writeDataDevice.writeDataToDevice((String) writeDataParamsMap.get("serviceUuid"), (String) writeDataParamsMap.get("characteristicUuid"), (boolean) writeDataParamsMap.get("isNoResponse"), (byte[]) writeDataParamsMap.get("data"));
                    result.success(true);
                } else result.success(false);
                break;
            case "readDescriptorData"://使用特征值的描述向设备读取数据
                Map<String, Object> readDescriptorDataParamsMap = call.arguments();
                Device readDescriptorDataDevice = devicesMap.get( (String)readDescriptorDataParamsMap.get("id"));
                if (readDescriptorDataDevice != null) {//如果这个id存在的话
                    readDescriptorDataDevice.readDescriptorDataToDevice((String) readDescriptorDataParamsMap.get("serviceUuid"), (String) readDescriptorDataParamsMap.get("characteristicUuid"),(String) readDescriptorDataParamsMap.get("descriptorUuid"));
                    result.success(true);
                } else result.success(false);
                break;
            case "writeDescriptorData"://使用特征值的描述向设备写入数据
                Map<String, Object> writeDescriptorDataParamsMap = call.arguments();
                Device writeDescriptorDataDevice = devicesMap.get( (String)writeDescriptorDataParamsMap.get("id"));
                if (writeDescriptorDataDevice != null) {//如果这个id存在的话
                    writeDescriptorDataDevice.writeDescriptorDataToDevice((String) writeDescriptorDataParamsMap.get("serviceUuid"), (String) writeDescriptorDataParamsMap.get("characteristicUuid"), (String) writeDescriptorDataParamsMap.get("descriptorUuid"), (byte[]) writeDescriptorDataParamsMap.get("data"));
                    result.success(true);
                } else result.success(false);
                break;
            case "requestMtu"://如果是修改Mtu
                if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP){
                    Map<String, Object> requestMtuDataParamsMap = call.arguments();
                    Device requestMtuDevice = devicesMap.get((String)requestMtuDataParamsMap.get("id"));
                    if (requestMtuDevice != null) //如果这个id存在的话
                        result.success(requestMtuDevice.requestMtu((Integer) requestMtuDataParamsMap.get("newMtu")));
                    else result.success(false);
                }else result.success(false);
                break;
            case "checkBlueLackWhat"://如果是检查缺少什么权限和功能
                result.success(checkBlueLackWhat());
                break;
            case "applyBluetoothPermission"://如果是获取蓝牙权限
                applyBluePermission();
                break;
            case "applyLocationPermission"://如果是获取蓝牙定位权限
                applyLocalPermission();
                break;
            case "openLocationService"://如果是开启蓝牙定位功能
                openLocationService();
                break;
            case "openBluetoothService"://如果是开启蓝牙功能
                openBluetoothService();
                break;
            case "destroy"://如果是销毁设备对象
                Map<String, Object> destroyParamsMap = call.arguments();
                String destroyDeviceId = (String)destroyParamsMap.get("id");
                Device destroyDevice=devicesMap.remove(destroyDeviceId);
                if (destroyDevice != null) {//如果这个id存在的话
                    destroyDevice.initiativeDisConnect();//与设备断开连接并销毁连接资源
                    result.success(true);
                } else result.success(false);
                break;
            default://如果没有此方法
                result.notImplemented();
                break;
        }
    }

    @Override
    public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
        channel.setMethodCallHandler(null);
    }

    private final ConnectStateCallback mConnectStateCallback = new ConnectStateCallback() {//设备连接状态有改变时就会调用这个函数

        @Override
        public void connectSuccess(String id) {
            Map<String, Object> result = new HashMap<>(2);
            result.put("eventName", "connected");
            result.put("id", id);
            sendSuccessMsgToEventChannel(result);//通知上层设备连接成功
        }

        @Override
        public void connectTimeout(String id) {
            Map<String, Object> result = new HashMap<>(2);
            result.put("eventName", "connectTimeout");
            result.put("id", id);
            sendSuccessMsgToEventChannel(result);//通知上层设备连接超时
        }

        @Override
        public void disConnected(String id, boolean isInitiative) {
            Map<String, Object> result = new HashMap<>(2);
            result.put("id", id);
            result.put("eventName",isInitiative?"initiativeDisConnected":"disConnected");
            sendSuccessMsgToEventChannel(result);
        }
    };

    private final DiscoverServiceCallback myDiscoverServiceCallback = (id, serviceUuid, characteristic) -> {
        Map<String, Object> result = new HashMap<>(4);
        result.put("eventName", "discoverService");
        result.put("id", id);
        result.put("serviceUuid", serviceUuid);
        result.put("characteristic", characteristic);
        sendSuccessMsgToEventChannel(result);//通知上层发现服务
    };

    private final DeviceSignalCallback mDeviceSignalCallback= (type, id, uuid, isSuccess, data) -> {
        Map<String, Object> result = new HashMap<>(6);
        result.put("eventName", "deviceSignal");
        result.put("type", type);
        result.put("id", id);
        result.put("uuid", uuid);
        result.put("isSuccess", isSuccess);
        result.put("data", data);
        sendSuccessMsgToEventChannel(result);
    };

    private final MtuChangeCallback mtuChangeCallback= (id, isSuccess, newMtu) -> {
        Map<String, Object> result = new HashMap<>(4);
        result.put("eventName", "mtuChange");
        result.put("id", id);
        result.put("isSuccess", isSuccess);
        result.put("newMtu", newMtu);
        sendSuccessMsgToEventChannel(result);
    };

    /**
     * 扫描设备
     */
    private void scanDevices(boolean isAllowDuplicates,int timeout,boolean isScanFix,String fixTarget){
        if(isScanFix) {//如果是修复扫描
            currentFixDeviceId = fixTarget;
            fixScanConnectTimeout=timeout;
            fixScanStartTime=System.currentTimeMillis();
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
            startScan21(isAllowDuplicates, timeout,isScanFix);
        else
            startScan18(isAllowDuplicates, timeout,isScanFix);
    }

    /**
     * 获取被其他应用连接了的设备信息
     */
    private List<Map<String,Object>> getHideConnectedDevice(){
        List<BluetoothDevice> hideConnectedDevices=mBluetoothManager.getConnectedDevices(BluetoothProfile.GATT);
        hideConnectedDeviceCaches.clear();
        List<Map<String,Object>> result=new LinkedList<>();
        for (int i = 0,length=hideConnectedDevices.size(); i <length ; i++) {
            BluetoothDevice device=hideConnectedDevices.get(i);
            if(!devicesMap.containsKey(device.getAddress())){//没有出现在缓存中的才返回
                hideConnectedDeviceCaches.put(device.getAddress(),device);
                Map<String,Object> deviceMsg=new HashMap<>(4);
                deviceMsg.put("id",device.getAddress());
                deviceMsg.put("name",device.getName());
                deviceMsg.put("macAddress",device.getAddress());
                ParcelUuid[] uuidArray=device.getUuids();
                List<String> uuids;
                if(uuidArray!=null){
                    uuids=new ArrayList<>(uuidArray.length);
                    for (int j = 0; j < uuidArray.length; j++) {
                        uuids.add(uuidArray[j].toString());
                    }
                }else
                    uuids=new ArrayList<>(0);
                deviceMsg.put("uuids",uuids);
                result.add(deviceMsg);
            }
        }
        return result;
    }

    /**
     * 开始去扫描设备,android低于21
     */
    private void startScan18(boolean isAllowDuplicates, int timeout,boolean isFix) {
        scanIsAllowDuplicates = isAllowDuplicates;
        isScanFix=isFix;
        alreadyScanDeviceMacAddress.clear();
        mHandler.removeCallbacks(scanTimeoutCallback18);//先停止定时任务
        mBluetoothAdapter.stopLeScan(mScanCallback18);//先停止扫描
        mBluetoothAdapter.startLeScan(mScanCallback18);//开始扫描
        if (timeout > 0||isFix)
            mHandler.postDelayed(scanTimeoutCallback18, isFix?(timeout>0?timeout:10000):timeout);//设置到达最大扫描时间之后的回调
    }

    /**
     * 停止扫描设备,android低于21
     */
    private void stopScan18() {
        mHandler.removeCallbacks(scanTimeoutCallback18);//先停止定时任务
        mBluetoothAdapter.stopLeScan(mScanCallback18);//停止扫描
    }

    /**
     * startScan18扫描结果的回调函数
     * startleScan和stopLeScan的callback对象要一样，不然无法停止扫描
     */
    private BluetoothAdapter.LeScanCallback mScanCallback18 = (device, rssi, scanRecord) -> {
        if(!isScanFix){
            MyScanRecord myScanRecord=MyScanRecord.parseFromBytes(scanRecord);
            List<ParcelUuid> uuidList=myScanRecord.getServiceUuids();//有可能是null
            ParcelUuid[] uuidArray = null;
            if(uuidList!=null) {
                uuidArray = new ParcelUuid[uuidList.size()];
                uuidList.toArray(uuidArray);
            }
            SparseArray<byte[]> manufacturerSpecificDataArray=myScanRecord.getManufacturerSpecificData();
            Map<Integer,byte[]> manufacturerSpecificData=null;
            if(manufacturerSpecificDataArray.size()>0){
                manufacturerSpecificData=new HashMap<>(manufacturerSpecificDataArray.size());
                for(int i=0,length=manufacturerSpecificDataArray.size();i<length;i++){
                    manufacturerSpecificData.put(manufacturerSpecificDataArray.keyAt(i),manufacturerSpecificDataArray.valueAt(i));
                }
            }
            handleScanResult(device, rssi, scanRecord,uuidArray,myScanRecord.getDeviceName(),manufacturerSpecificData);
        }else
            handleFixScanResult(device);

    };

    /**
     * startScan18扫描时间到了之后要调用的函数
     */
    private Runnable scanTimeoutCallback18 = () -> {
        mBluetoothAdapter.stopLeScan(mScanCallback18);
        if(!isScanFix){//如果是正常扫描
            Map<String, Object> result = new HashMap<>(1);
            result.put("eventName", "scanTimeout");
            sendSuccessMsgToEventChannel(result);//通知上层扫描时间已经到了
        }else{
            if(fixScanConnectTimeout>0)//只有设置了超时时间才要通知上层目标设备连接超时
                mConnectStateCallback.connectTimeout(currentFixDeviceId);
            else mConnectStateCallback.disConnected(currentFixDeviceId,false);//如果没有设置超时时间则通知上层连接失败
        }
    };

    /**
     * 开始去扫描设备,android高于21
     */
    @RequiresApi(21)
    private void startScan21(boolean isAllowDuplicates, int timeout,boolean isFix) {
        scanIsAllowDuplicates = isAllowDuplicates;
        isScanFix=isFix;
        alreadyScanDeviceMacAddress.clear();
        mHandler.removeCallbacks(getScanTimeoutCallback21());//先停止定时任务
        BluetoothLeScanner scanner = mBluetoothAdapter.getBluetoothLeScanner();
        scanner.stopScan(getScanCallback21());//先停止扫描
        scanner.startScan(getScanCallback21());
        if (timeout > 0||isFix)
            mHandler.postDelayed(getScanTimeoutCallback21(), isFix?(timeout>0?timeout:10000):timeout);//设置到达最大扫描时间之后的回调
    }

    /**
     * 停止扫描设备,android高于21
     */
    @RequiresApi(21)
    private void stopScan21() {
        mHandler.removeCallbacks(getScanTimeoutCallback21());//先停止定时任务
        BluetoothLeScanner scanner = mBluetoothAdapter.getBluetoothLeScanner();
        scanner.stopScan(getScanCallback21());//停止扫描
    }

    private Runnable scanTimeoutCallback21;
    /**
     * startScan21扫描时间到了之后要调用的函数
     */
    @RequiresApi(21)
    private Runnable getScanTimeoutCallback21 (){
        if(scanTimeoutCallback21==null){
            scanTimeoutCallback21= () -> {
                BluetoothLeScanner scanner = mBluetoothAdapter.getBluetoothLeScanner();
                scanner.stopScan(getScanCallback21());//停止扫描
                if(!isScanFix){//如果是正常扫描
                    Map<String, Object> result = new HashMap<>(1);
                    result.put("eventName", "scanTimeout");
                    sendSuccessMsgToEventChannel(result);//通知上层扫描时间已经到了
                }else{
                    if(fixScanConnectTimeout>0)//只有设置了超时时间才要通知上层目标设备连接超时
                        mConnectStateCallback.connectTimeout(currentFixDeviceId);
                    else mConnectStateCallback.disConnected(currentFixDeviceId,false);//如果没有设置超时时间则通知上层连接失败
                }
            };
        }
        return scanTimeoutCallback21;
    }

    private ScanCallback scanCallback21;

    /**
     * 获取android高于21的蓝牙扫描回调函数，不存在则创建
     */
    @RequiresApi(21)
    private ScanCallback getScanCallback21() {
        if (scanCallback21 == null) {
            scanCallback21 = new ScanCallback() {

                @Override
                public void onScanResult(int callbackType, ScanResult result) {
                    super.onScanResult(callbackType, result);
                    if(!isScanFix){
                        ScanRecord scanRecord=result.getScanRecord();
                        List<ParcelUuid> uuidList=scanRecord.getServiceUuids();//有可能是null
                        ParcelUuid[] uuidArray = null;
                        if(uuidList!=null) {
                            uuidArray = new ParcelUuid[uuidList.size()];
                            uuidList.toArray(uuidArray);
                        }
                        SparseArray<byte[]> manufacturerSpecificDataArray=scanRecord.getManufacturerSpecificData();
                        Map<Integer,byte[]> manufacturerSpecificData=null;
                        if(manufacturerSpecificDataArray.size()>0){
                            manufacturerSpecificData=new HashMap<>(manufacturerSpecificDataArray.size());
                            for(int i=0,length=manufacturerSpecificDataArray.size();i<length;i++){
                                manufacturerSpecificData.put(manufacturerSpecificDataArray.keyAt(i),manufacturerSpecificDataArray.valueAt(i));
                            }
                        }
                        handleScanResult(result.getDevice(), result.getRssi(),scanRecord.getBytes(),uuidArray,scanRecord.getDeviceName(),manufacturerSpecificData);
                    }else
                        handleFixScanResult(result.getDevice());
                }

                @Override
                public void onBatchScanResults(List<ScanResult> results) {
                    super.onBatchScanResults(results);

                }

                @Override
                public void onScanFailed(int errorCode) {
                    super.onScanFailed(errorCode);
                    //System.out.println("扫描到错误");
                }
            };
        }
        return scanCallback21;
    }

    /**
     * 修复扫描扫描到一个设备之后要处理的函数
     */
    private void handleFixScanResult(BluetoothDevice device) {
        if (alreadyScanDeviceMacAddress.add(device.getAddress())) {//如果设备没有重复
            Device currentDevice=devicesMap.get(device.getAddress());
            if(device.getAddress().equals(currentFixDeviceId)){//如果是目标设备
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
                    stopScan21();//停止扫描以及扫描定时
                else
                    stopScan18();//停止扫描以及扫描定时
                long timeLeft=fixScanConnectTimeout>0?(fixScanConnectTimeout-(System.currentTimeMillis()-fixScanStartTime)):0;//计算除去扫描所用的时间还剩多少时间用于连接设备
                currentDevice.updateBleDevice(device);//替换掉里面的设备对象
                currentDevice.connectDevice((int) timeLeft);
            }else if(currentDevice!=null&&!currentDevice.isInBleCache())//如果不是目标设备但是也在蓝牙cache中并且之前的对象没有被蓝牙堆栈缓存
                currentDevice.updateBleDevice(device);//替换掉里面的设备对象
        }
    }

    /**
     * 扫描到一个设备之后要处理的函数
     */
    private void handleScanResult(BluetoothDevice device, int rssi, byte[] scanRecord,ParcelUuid[] uuidArray,String localName,Map<Integer,byte[]> manufacturerSpecificData) {
        if (scanIsAllowDuplicates || alreadyScanDeviceMacAddress.add(device.getAddress())) {//如果允许重复或者说设备没有重复
            List<String> uuids;
            if(uuidArray!=null){
                uuids = new ArrayList<>(uuidArray.length);
                for (int i = 0, length = uuidArray.length; i < length; i++) {
                    uuids.add(uuidArray[i].toString());
                }
            }else uuids = new ArrayList<>(0);
            scanDeviceCaches.put(device.getAddress(), device);//将扫描结果缓存起来
            Map<String, Object> result = new HashMap<>(9);
            result.put("eventName", "scanResult");
            result.put("id", device.getAddress());
            result.put("name", device.getName());
            result.put("localName", localName);
            result.put("macAddress", device.getAddress());
            result.put("rssi", rssi);
            result.put("uuids", uuids);
            result.put("manufacturerSpecificData",manufacturerSpecificData);
            result.put("scanRecord", scanRecord);
            sendSuccessMsgToEventChannel(result);//将扫描到的结果发给上层
        }
    }


    /**
     * 检查需要使用蓝牙缺什么权限和功能
     */
    private List<Integer> checkBlueLackWhat() {
        List<Integer> lackArray = new ArrayList<>(3);
        if (!checkHaveLocalPermission())//如果没有开启定位权限
            lackArray.add(0);
        if (!isLocationEnable(this.context))//如果没有开启定位功能
            lackArray.add(1);
        if (!mBluetoothAdapter.isEnabled())//如果没有打开蓝牙
            lackArray.add(2);
        if(!checkHaveBluePermission())//如果没有授予蓝牙权限
            lackArray.add(3);
        return lackArray;
    }


    /**
     * 判断用户是否有授予蓝牙权限,适配android12
     */
    private boolean checkHaveBluePermission() {
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            return ContextCompat.checkSelfPermission(context,
                    Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED&&ContextCompat.checkSelfPermission(context,
                    Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
        else return true;
    }

    /**
     * 申请定蓝牙权限
     */
    private void applyBluePermission() {
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S){//android12才要申请这个
            String[] strings=new String[]{Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT};
            ActivityCompat.requestPermissions(activity, strings, REQUEST_CODE_BLUE_PERMISSION);
        }
    }

    /**
     * 判断用户是否有授予定位权限
     */
    private boolean checkHaveLocalPermission() {
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            return ContextCompat.checkSelfPermission(context,
                    Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED&&ContextCompat.checkSelfPermission(context,
                    Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        else return ContextCompat.checkSelfPermission(context,
                Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }


    /**
     * 申请定位权限
     */
    private void applyLocalPermission() {
        String[] strings;
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            strings = new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION};
        else strings =new String[]{Manifest.permission.ACCESS_COARSE_LOCATION};
        ActivityCompat.requestPermissions(activity, strings, REQUEST_CODE_LOCATION_PERMISSION);
    }

    /**
     * 判断是否已经打开位置定位的功能
     */
    private boolean isLocationEnable(Context context) {
        LocationManager locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        boolean networkProvider = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
        boolean gpsProvider = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
        if (networkProvider || gpsProvider) return true;
        return false;
    }

    /**
     * 申请打开位置定位功能
     */
    private void openLocationService() {
        Intent locationIntent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
        this.activity.startActivityForResult(locationIntent, REQUEST_CODE_LOCATION_SETTINGS);
    }

    /**
     * 申请打开蓝牙
     */
    private void openBluetoothService() {
        Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        //intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);//如果试图从非activity的非正常途径启动一个activity，比如从一个service中启动一个activity，则intent必须要添加FLAG_ACTIVITY_NEW_TASK标记,但是加了的话就不会等待返回结果
        activity.startActivityForResult(intent, REQUEST_CODE_OPEN_BLUETOOTH);//此方法用来打开一个新的activity来让让用户确认是否要打开蓝牙
    }

    @Override
    public void onAttachedToActivity(@NonNull ActivityPluginBinding binding) {
        this.activity = binding.getActivity();
        alertBuilder = new AlertDialog.Builder(activity);
        binding.addActivityResultListener((requestCode, resultCode, data) -> {
            Map<String, Object> result = new HashMap<>(1);
            switch (requestCode) {
                case REQUEST_CODE_LOCATION_SETTINGS://如果是开启定位功能的返回结果
                    if (isLocationEnable(this.context)) //如果开启了定位服务
                        result.put("eventName", "allowLocationFunction");
                    else
                        result.put("eventName", "denyLocationFunction");
                    sendSuccessMsgToEventChannel(result);//告知上层用户是否同意开启位置功能
                    return true;
                case REQUEST_CODE_OPEN_BLUETOOTH://如果是开启蓝牙功能的返回结果
                    if (resultCode == Activity.RESULT_OK) //用户同意开启了蓝牙功能
                        result.put("eventName", "allowOpenBluetooth");
                    else
                        result.put("eventName", "denyOpenBluetooth");
                    sendSuccessMsgToEventChannel(result);
                    return true;
            }
            return false;
        });
        binding.addRequestPermissionsResultListener((requestCode, permissions, grantResults) -> {
            Map<String, Object> result = new HashMap<>(1);
            boolean isAllow=grantResults.length > 0 ? true : false;
            for(int i=0;i<grantResults.length;i++){
                if(grantResults[i]!= PackageManager.PERMISSION_GRANTED){
                    isAllow=false;
                    break;
                }
            }
            switch (requestCode) {
                case REQUEST_CODE_LOCATION_PERMISSION://如果是申请位置权限的结果
                    result.put("eventName", isAllow ? "allowLocationPermission" : "denyLocationPermission");
                    sendSuccessMsgToEventChannel(result);//告知上层用户是否同意授予位置权限
                    return true;
                case REQUEST_CODE_BLUE_PERMISSION://如果是申请蓝牙权限的结果
                    result.put("eventName", isAllow ? "allowBluetoothPermission" : "denyBluetoothPermission");
                    sendSuccessMsgToEventChannel(result);//告知上层用户是否同意授予蓝牙权限
                    return true;
            }
            return false;
        });
    }

    @Override
    public void onDetachedFromActivityForConfigChanges() {

    }

    @Override
    public void onReattachedToActivityForConfigChanges(@NonNull ActivityPluginBinding binding) {

    }

    @Override
    public void onDetachedFromActivity() {

    }


    /**
     * 向观察者们发送成功消息
     */
    private void sendSuccessMsgToEventChannel(Object msg) {
        if (mySink != null)
            runOnMainThread(() -> {
                mySink.success(msg);
            });
    }

    /**
     * 向观察者们轮询发送失败消息
     */
    private void sendFailMsgToEventChannel(String errCode, String errMsg, Object errDetail) {
        if (mySink != null)
            runOnMainThread(() -> {
                mySink.error(errCode, errMsg, errDetail);
            });
    }

    /**
     * 判断当前线程是否是主线程
     */
    private boolean isMainThread() {
        return Looper.myLooper() == Looper.getMainLooper();
    }

    /**
     * 将目标代码快运行在主线程中
     */
    private void runOnMainThread(Runnable runnable) {
        if (isMainThread()) {
            runnable.run();
        } else {
            if (mHandler != null) {
                mHandler.post(runnable);
            }
        }
    }
}