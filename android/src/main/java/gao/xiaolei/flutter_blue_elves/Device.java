package gao.xiaolei.flutter_blue_elves;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import androidx.annotation.RequiresApi;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import gao.xiaolei.flutter_blue_elves.callback.ConnectStateCallback;
import gao.xiaolei.flutter_blue_elves.callback.DeviceSignalCallback;
import gao.xiaolei.flutter_blue_elves.callback.DiscoverServiceCallback;
import gao.xiaolei.flutter_blue_elves.callback.MtuChangeCallback;

//封装的蓝牙设备对象
public class Device {
    private short state=0;//当前设备的状态,0代表未连接,1代表正在连接,2代表已连接
    private BluetoothGatt mBleGatt;//蓝牙连接对象
    private Context mContext;//当前应用的上下文
    private final String BLUETOOTH_NOTIFY_D="00002902-0000-1000-8000-00805f9b34fb";//此属性是用来开启通知的时获取notify特征值的描述对象,默认是这个描述uuid
    private BluetoothDevice bleDevice;//蓝牙设备对象
    private BleGattCallback mGattCallback=new BleGattCallback();
    private Handler mHandler;//为了开启定时任务
    private boolean isConnectedDevice=false;//判断是否曾经连接上设备
    private boolean isInitiativeDisConnect=false;//判断是否是手动断开连接
    private short connectRetryTimes=0;//连接重试次数
    private ConnectStateCallback connectStateCallback;//连接状态的回调
    private DeviceSignalCallback deviceSignalCallback;//设备传回数据时的回调
    private DiscoverServiceCallback discoverServiceCallback;//发现蓝牙服务之后的回调
    private MtuChangeCallback mtuChangeCallback;//mtu被修改之后的回调
    private Runnable connectTimeoutCallback=()->{//连接超时的回调
        if(mBleGatt!=null){
            mBleGatt.close();//直接将连接资源关闭，这样连接回调也不会被调用
            mBleGatt=null;
            state=0;
        }
        connectStateCallback.connectTimeout(bleDevice.getAddress());//连接超时的回调
    };

    public Device(Context mContext,Handler handler,BluetoothDevice bleDevice, ConnectStateCallback connectStateCallback, DeviceSignalCallback deviceSignalCallback, DiscoverServiceCallback discoverServiceCallback,MtuChangeCallback mtuChangeCallback) {
        this.mContext=mContext;
        this.mHandler=handler;
        this.bleDevice=bleDevice;
        this.connectStateCallback = connectStateCallback;
        this.discoverServiceCallback = discoverServiceCallback;
        this.deviceSignalCallback=deviceSignalCallback;
        this.mtuChangeCallback=mtuChangeCallback;
    }

    /**
     * 更新设备对象
     */
    public void updateBleDevice(BluetoothDevice newBleDevice){
        this.bleDevice=newBleDevice;
    }

    /**
     * 判断设备对象是否有被蓝牙堆栈缓存
     */
    public boolean isInBleCache(){
        return !(bleDevice.getType()==BluetoothDevice.DEVICE_TYPE_UNKNOWN);
    }

    /**
     * 连接设备
     */
    public void connectDevice(int connectTimeOut){
        if(state==0){//如果当前是未连接的状态
            connectRetryTimes=0;
            state=1;
            isConnectedDevice=false;//设置当前还未连接上过设备
            isInitiativeDisConnect=false;//设置如果断开连接不是手动断开的连接
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                mBleGatt = bleDevice.connectGatt(mContext, false, mGattCallback, BluetoothDevice.TRANSPORT_LE);
            else
                mBleGatt = bleDevice.connectGatt(mContext, false, mGattCallback);
            if(connectTimeOut>0)
                mHandler.postDelayed(connectTimeoutCallback, connectTimeOut);//设置到达最大连接时间之后的回调
        }
    }

    /**
     * 主动与设备断开连接
     */
    public void initiativeDisConnect(){
        if(state==2){//如果是已经连接才能去断开连接
            isInitiativeDisConnect=true;//标记当前是手动断开的
            mBleGatt.disconnect();//断开连接
        }
    }

    /**
     * 发现服务
     */
    public void discoverService(){
        mBleGatt.discoverServices();//去发现服务
    }

    /**
     * 开启或关闭notify特征值
     */
    public boolean setNotifyCharacteristic(String serviceUuid,String characteristicUuid,boolean isEnable){
        BluetoothGattCharacteristic notifyCharacteristic=getBluetoothGattCharacteristic(serviceUuid,characteristicUuid);
        if(mBleGatt == null ||notifyCharacteristic==null)
            return false;
        if (!mBleGatt.setCharacteristicNotification(notifyCharacteristic, isEnable))//开启这个notify特征值的通知功能，其实执行这一步之后就可以获得设备传来的数据了，这步不成功，也不会发送数据
            return false;
        BluetoothGattDescriptor clientConfig = notifyCharacteristic.getDescriptor(UUID.fromString(BLUETOOTH_NOTIFY_D));
        if (clientConfig == null)
            return false;
        if (isEnable)
            clientConfig.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
        else
            clientConfig.setValue(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
        return mBleGatt.writeDescriptor(clientConfig);
    }

    /**
     * 使用读特征值向设备读取数据
     */
    public void readDataToDevice(String serviceUuid,String characteristicUuid){
        BluetoothGattCharacteristic mBleGattCharacteristic = getBluetoothGattCharacteristic(serviceUuid,characteristicUuid);
        if(mBleGattCharacteristic!=null)
            mBleGatt.readCharacteristic(mBleGattCharacteristic);//读取
    }

    /**
     * 使用写特征值向设备发送数据
     */
    public void writeDataToDevice(String serviceUuid,String characteristicUuid,boolean isNoResponse,byte[] data){
        BluetoothGattCharacteristic mBleGattCharacteristic = getBluetoothGattCharacteristic(serviceUuid,characteristicUuid);
        if(mBleGattCharacteristic!=null){
            mBleGattCharacteristic.setValue(data);
            mBleGattCharacteristic.setWriteType(isNoResponse?BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE:BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);//是否是有响应写
            mBleGatt.writeCharacteristic(mBleGattCharacteristic); //发送
        }
    }

    /**
     * 使用特征值的描述向设备读取数据
     */
    public void readDescriptorDataToDevice(String serviceUuid,String characteristicUuid,String descriptorUuid){
        BluetoothGattDescriptor mBleGattDescriptor = getBluetoothGattDescriptor(serviceUuid,characteristicUuid,descriptorUuid);
        if(mBleGattDescriptor!=null)
            mBleGatt.readDescriptor(mBleGattDescriptor);//读取
    }

    /**
     * 使用特征值的描述向设备写数据
     */
    public void writeDescriptorDataToDevice(String serviceUuid,String characteristicUuid,String descriptorUuid,byte[] data){
        BluetoothGattDescriptor mBleGattDescriptor = getBluetoothGattDescriptor(serviceUuid,characteristicUuid,descriptorUuid);
        if(mBleGattDescriptor!=null){
            mBleGattDescriptor.setValue(data);
            mBleGatt.writeDescriptor(mBleGattDescriptor);//写入
        }
    }

    /**
     * 修改mtu,需要android版本大于21
     */
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public boolean requestMtu(int newMtu){
        return mBleGatt.requestMtu(newMtu);
    }

    /**
     * 根据服务UUID和特征UUID和描述UUID,获取一个描述{@link BluetoothGattDescriptor}
     */
    private BluetoothGattDescriptor getBluetoothGattDescriptor(String serviceUUID, String characteristicUUID,String descriptorUUID) {
        BluetoothGattCharacteristic characteristic=getBluetoothGattCharacteristic(serviceUUID,characteristicUUID);
        if(characteristic==null)
            return null;
        return characteristic.getDescriptor(UUID.fromString(descriptorUUID));
    }

    /**
     * 根据服务UUID和特征UUID,获取一个特征{@link BluetoothGattCharacteristic}
     */
    private BluetoothGattCharacteristic getBluetoothGattCharacteristic(String serviceUUID, String characteristicUUID) {
        BluetoothGattService service=getBluetoothGattService(serviceUUID);
        if(service==null)
            return null;
        return service.getCharacteristic(UUID.fromString(characteristicUUID));
    }

    /**
     * 根据服务UUID,获取一个服务{@link BluetoothGattService}
     */
    private BluetoothGattService getBluetoothGattService(String serviceUUID) {
        return mBleGatt.getService(UUID.fromString(serviceUUID));
    }

    /**
     * 蓝牙GATT连接及操作事件回调
     */
    private class BleGattCallback extends BluetoothGattCallback implements Serializable {
        //连接状态改变
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) { //连接成功
                isConnectedDevice=true;//标志曾经连接上设备
                mHandler.removeCallbacks(connectTimeoutCallback);//停止连接超时的定时
                state=2;//将状态修改为已连接
                connectStateCallback.connectSuccess(bleDevice.getAddress());//连接成功后的调用
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) { //断开连接
                if(mBleGatt!=null){
                    mBleGatt.close();
                    mBleGatt=null;
                }
                if(status==133&&!isConnectedDevice&&connectRetryTimes++<3) {//如果是133就要尝试去重连
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                        mBleGatt = bleDevice.connectGatt(mContext, false, mGattCallback, BluetoothDevice.TRANSPORT_LE);
                    else
                        mBleGatt = bleDevice.connectGatt(mContext, false, mGattCallback);
                }else{
                    mHandler.removeCallbacks(connectTimeoutCallback);//停止连接超时的定时
                    state=0;
                    if(isConnectedDevice)//曾经连接上才叫断开,不然就是连接失败
                        connectStateCallback.disConnected(bleDevice.getAddress(),isInitiativeDisConnect);//断开连接后的调用
                }
            }
        }

        //发现新服务
        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
                super.onServicesDiscovered(gatt, status);
                if (null != mBleGatt && status == BluetoothGatt.GATT_SUCCESS) {
                    List<BluetoothGattService> services = mBleGatt.getServices();
                    for (int i = 0; i < services.size(); i++) {//将新服务和其特征值加入Map
                        Map<String, BluetoothGattCharacteristic> charMap = new HashMap<>();
                        BluetoothGattService bluetoothGattService = services.get(i);
                        String serviceUuid = bluetoothGattService.getUuid().toString();
                        //System.out.println("发现新服务：" + serviceUuid);
                        List<BluetoothGattCharacteristic> characteristics = bluetoothGattService.getCharacteristics();
                        List<Map<String,Object>> callbackCharacteristicUuidList=new ArrayList<>(characteristics.size());//返回给flutter上层的特征值列表
                        for (int j = 0; j < characteristics.size(); j++) {
                            Map<String,Object> characteristicInfo=new HashMap<>(2);
                            BluetoothGattCharacteristic characteristic= characteristics.get(j);
                            String currentCharacteristicUuid=characteristic.getUuid().toString();
                            charMap.put(currentCharacteristicUuid, characteristic);
                            characteristicInfo.put("uuid",currentCharacteristicUuid);
                            characteristicInfo.put("properties",characteristic.getProperties());
                            callbackCharacteristicUuidList.add(characteristicInfo);
                            //System.out.println("发现新特征值：" + characteristic.getUuid().toString());
                            List<BluetoothGattDescriptor> descriptors=characteristic.getDescriptors();
                            List<Map<String,Object>> callbackDescriptorUuidList=new ArrayList<>(descriptors.size());//返回给flutter上层的特征值的描述信息列表
                            for(int k=0;k<descriptors.size();k++){
                                Map<String,Object> descriptorInfo=new HashMap<>(1);
                                BluetoothGattDescriptor descriptor=descriptors.get(k);
                                //System.out.println("发现新描述:"+descriptor.getUuid().toString());
                                descriptorInfo.put("uuid",descriptor.getUuid().toString());
                                callbackDescriptorUuidList.add(descriptorInfo);
                            }
                            characteristicInfo.put("descriptors",callbackDescriptorUuidList);
                        }
                        discoverServiceCallback.discoverService(bleDevice.getAddress(),serviceUuid,callbackCharacteristicUuidList);//发现蓝牙服务之后的回调
                    }
                }
        }

        //使用读特征值向设备读数据完成后调用的方法
        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicRead(gatt, characteristic, status);
            if (status == BluetoothGatt.GATT_SUCCESS) //如果读取数据成功了
                deviceSignalCallback.signalCallback((short) 0,bleDevice.getAddress(),characteristic.getUuid().toString(),true,characteristic.getValue());
             else//如果失败了
                deviceSignalCallback.signalCallback((short) 0,bleDevice.getAddress(),characteristic.getUuid().toString(),false,null);
        }

        //使用写特征值向设备写入数据完成后调用的方法
        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
                super.onCharacteristicWrite(gatt, characteristic, status);
                if (status == BluetoothGatt.GATT_SUCCESS) //如果写入数据成功了
                    deviceSignalCallback.signalCallback((short) 1,bleDevice.getAddress(),characteristic.getUuid().toString(),true,characteristic.getValue());
                 else //如果失败了
                    deviceSignalCallback.signalCallback((short) 1,bleDevice.getAddress(),characteristic.getUuid().toString(),false,null);

        }

        //通知数据
        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
                super.onCharacteristicChanged(gatt, characteristic);
                deviceSignalCallback.signalCallback((short) 2,bleDevice.getAddress(), characteristic.getUuid().toString(),true, characteristic.getValue());
        }

        //读取特征值描述之后调用的函数
        @Override
        public void onDescriptorRead(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            super.onDescriptorRead(gatt, descriptor, status);
            if (status == BluetoothGatt.GATT_SUCCESS) //如果读取数据成功了
                deviceSignalCallback.signalCallback((short) 3,bleDevice.getAddress(),descriptor.getUuid().toString(),true,descriptor.getValue());
             else //如果失败了
                deviceSignalCallback.signalCallback((short) 3,bleDevice.getAddress(),descriptor.getUuid().toString(),false,null);

        }

        //写入特征值描述之后调用的函数
        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            super.onDescriptorRead(gatt, descriptor, status);
            if (status == BluetoothGatt.GATT_SUCCESS) //如果读取数据成功了
                deviceSignalCallback.signalCallback((short) 4,bleDevice.getAddress(),descriptor.getUuid().toString(),true,descriptor.getValue());
             else //如果失败了
                deviceSignalCallback.signalCallback((short) 4,bleDevice.getAddress(),descriptor.getUuid().toString(),false,null);
        }

        //mtu被修改后的回调
        @Override
        @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
        public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
            super.onMtuChanged(gatt, mtu, status);
            if (status == BluetoothGatt.GATT_SUCCESS) //如果mtu修改成功了
                mtuChangeCallback.mtuChangeCallback(bleDevice.getAddress(),true,mtu);
            else //如果失败了
                mtuChangeCallback.mtuChangeCallback(bleDevice.getAddress(),false,mtu);
        }
    }

    private List<Short> getCharacteristicProperties(BluetoothGattCharacteristic characteristic){
        List<Short> result=new LinkedList<>();
        int properties=characteristic.getProperties();
        if((properties&BluetoothGattCharacteristic.PROPERTY_BROADCAST)>0)
            result.add((short) 1);
        if((properties&BluetoothGattCharacteristic.PROPERTY_READ)>0)
            result.add((short) 2);
        if((properties&BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)>0)
            result.add((short) 3);
        if((properties&BluetoothGattCharacteristic.PROPERTY_WRITE)>0)
            result.add((short) 4);
        if((properties&BluetoothGattCharacteristic.PROPERTY_NOTIFY)>0)
            result.add((short) 5);
        if((properties&BluetoothGattCharacteristic.PROPERTY_INDICATE)>0)
            result.add((short) 6);
        if((properties&BluetoothGattCharacteristic.PROPERTY_SIGNED_WRITE)>0)
            result.add((short) 7);
        if((properties&BluetoothGattCharacteristic.PROPERTY_EXTENDED_PROPS)>0)
            result.add((short) 8);
        return result;
    }


}
