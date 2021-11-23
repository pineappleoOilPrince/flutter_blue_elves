package gao.xiaolei.flutter_blue_elves.callback;

public interface MtuChangeCallback {//设备mtu被修改之后的回调

    void mtuChangeCallback(String id,boolean isSuccess,int newMtu);
}