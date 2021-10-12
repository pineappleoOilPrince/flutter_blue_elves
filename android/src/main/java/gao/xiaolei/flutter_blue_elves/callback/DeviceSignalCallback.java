package gao.xiaolei.flutter_blue_elves.callback;
public interface DeviceSignalCallback {
    void signalCallback(short type,String id,String uuid,boolean isSuccess,byte[] data);
}
