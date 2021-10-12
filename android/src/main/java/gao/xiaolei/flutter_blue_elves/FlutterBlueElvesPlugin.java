package gao.xiaolei.flutter_blue_elves;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
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
import java.util.List;
import java.util.Map;
import java.util.Set;

import gao.xiaolei.flutter_blue_elves.callback.ConnectStateCallback;
import gao.xiaolei.flutter_blue_elves.callback.DeviceSignalCallback;
import gao.xiaolei.flutter_blue_elves.callback.DiscoverServiceCallback;
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
    private boolean scanIsAllowDuplicates = false;//判断扫描设备时是否运行返回重复设备
    private Set<String> alreadyScanDeviceMacAddress = new HashSet<>();//用于储存扫描过的设备mac地址
    private Map<String, Device> devicesMap = new HashMap<>();//用于储存设备对象的map
    private static final int REQUEST_CODE_LOCATION_PERMISSION = 2;//申请定位权限的requestCode
    private static final int REQUEST_CODE_LOCATION_SETTINGS = 3;//开启定位服务的requestCode
    private static final int REQUEST_CODE_OPEN_BLUETOOTH = 4;//开启蓝牙服务的requestCode

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
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
                    startScan21((boolean) scanParamsMap.get("isAllowDuplicates"), (int) scanParamsMap.get("timeout"));
                else
                    startScan18((boolean) scanParamsMap.get("isAllowDuplicates"), (int) scanParamsMap.get("timeout"));
                result.success(null);
                break;
            case "stopScan"://如果是停止扫描
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
                    stopScan21();
                else
                    stopScan18();
                result.success(null);
                break;
            case "connect"://如果是连接设备
                Map<String, Object> connectParamsMap = call.arguments();
                String connectDeviceId = (String) connectParamsMap.get("id") ;
                BluetoothDevice scanCache = scanDeviceCaches.remove(connectDeviceId);//从扫描结果中去连接只能执行一次
                if (scanCache != null) {//如果这个id存在的话
                    Device toConnectDevice = new Device(context, mHandler,scanCache, mConnectStateCallback, mDeviceSignalCallback, myDiscoverServiceCallback);
                    devicesMap.put(connectDeviceId,toConnectDevice);
                    toConnectDevice.connectDevice((int) connectParamsMap.get("timeout"));
                    result.success(true);
                } else result.success(false);
                break;
            case "reConnect"://如果是重连设备
                Map<String, Object> reConnectParamsMap = call.arguments();
                String reConnectDeviceId = (String) reConnectParamsMap.get("id") ;
                Device reConnectDevice=devicesMap.get(reConnectDeviceId);
                if (reConnectDevice != null) {//如果这个id存在的话
                    reConnectDevice.connectDevice((int) reConnectParamsMap.get("timeout"));
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
            case "checkBlueLackWhat"://如果是检查缺少什么权限和功能
                result.success(checkBlueLackWhat());
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
                    destroyDevice.initiativeDisConnect();//与设备断开连接
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

    /**
     * 开始去扫描设备,android低于21
     */
    private void startScan18(boolean isAllowDuplicates, int timeout) {
        scanIsAllowDuplicates = isAllowDuplicates;
        alreadyScanDeviceMacAddress.clear();
        mHandler.removeCallbacks(scanTimeoutCallback18);//先停止定时任务
        mBluetoothAdapter.stopLeScan(mScanCallback18);//先停止扫描
        mBluetoothAdapter.startLeScan(mScanCallback18);//开始扫描
        if (timeout > 0)
            mHandler.postDelayed(scanTimeoutCallback18, timeout);//设置到达最大扫描时间之后的回调
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
        handleScanResult(device, rssi, scanRecord,device.getUuids(),myScanRecord.getDeviceName(),manufacturerSpecificData);
    };

    /**
     * startScan18扫描时间到了之后要调用的函数
     */
    private Runnable scanTimeoutCallback18 = () -> {
        mBluetoothAdapter.stopLeScan(mScanCallback18);
        Map<String, Object> result = new HashMap<>(1);
        result.put("eventName", "scanTimeout");
        sendSuccessMsgToEventChannel(result);//通知上层扫描时间已经到了
    };

    /**
     * 开始去扫描设备,android高于21
     */
    @RequiresApi(21)
    private void startScan21(boolean isAllowDuplicates, int timeout) {
        scanIsAllowDuplicates = isAllowDuplicates;
        alreadyScanDeviceMacAddress.clear();
        mHandler.removeCallbacks(scanTimeoutCallback21);//先停止定时任务
        BluetoothLeScanner scanner = mBluetoothAdapter.getBluetoothLeScanner();
        scanner.stopScan(getScanCallback21());//先停止扫描
        scanner.startScan(getScanCallback21());
        if (timeout > 0)
            mHandler.postDelayed(scanTimeoutCallback21, timeout);//设置到达最大扫描时间之后的回调
    }

    /**
     * 停止扫描设备,android高于21
     */
    @RequiresApi(21)
    private void stopScan21() {
        mHandler.removeCallbacks(scanTimeoutCallback21);//先停止定时任务
        BluetoothLeScanner scanner = mBluetoothAdapter.getBluetoothLeScanner();
        scanner.stopScan(getScanCallback21());//停止扫描
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
     * startScan21扫描时间到了之后要调用的函数
     */
    private Runnable scanTimeoutCallback21 = () -> {
        mBluetoothAdapter.stopLeScan(mScanCallback18);
        Map<String, Object> result = new HashMap<>(1);
        result.put("eventName", "scanTimeout");
        sendSuccessMsgToEventChannel(result);//通知上层扫描时间已经到了
    };


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
        return lackArray;
    }


    /**
     * 判断用户是否有授予定位权限
     */
    private boolean checkHaveLocalPermission() {
        return ContextCompat.checkSelfPermission(context,
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }


    /**
     * 申请定位权限
     */
    private void applyLocalPermission() {
        String[] strings =
                {Manifest.permission.ACCESS_FINE_LOCATION};
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
            switch (requestCode) {
                case REQUEST_CODE_LOCATION_PERMISSION://如果是申请位置权限的结果
                    if (grantResults.length > 0 &&
                            grantResults[0] == PackageManager.PERMISSION_GRANTED) //用户同意了权限的话
                        result.put("eventName", "allowLocationPermission");
                    else //如果用户不同意给定位权限的话
                        result.put("eventName", "denyLocationPermission");
                    sendSuccessMsgToEventChannel(result);//告知上层用户是否同意授予位置权限
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

    private ConnectStateCallback mConnectStateCallback = new ConnectStateCallback() {//设备连接状态有改变时就会调用这个函数

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

    private DiscoverServiceCallback myDiscoverServiceCallback = (id, serviceUuid, characteristic) -> {
        Map<String, Object> result = new HashMap<>(4);
        result.put("eventName", "discoverService");
        result.put("id", id);
        result.put("serviceUuid", serviceUuid);
        result.put("characteristic", characteristic);
        sendSuccessMsgToEventChannel(result);//通知上层发现服务
    };

    private DeviceSignalCallback mDeviceSignalCallback= (type, id, uuid, isSuccess, data) -> {
        Map<String, Object> result = new HashMap<>(6);
        result.put("eventName", "deviceSignal");
        result.put("type", type);
        result.put("id", id);
        result.put("uuid", uuid);
        result.put("isSuccess", isSuccess);
        result.put("data", data);
        sendSuccessMsgToEventChannel(result);
    };


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
