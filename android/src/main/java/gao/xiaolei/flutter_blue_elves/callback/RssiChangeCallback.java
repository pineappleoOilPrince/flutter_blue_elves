package gao.xiaolei.flutter_blue_elves.callback;

public interface RssiChangeCallback {//设备rssi变化之后的回调

    void rssiChangeCallback(String id,int newRssi);
}
