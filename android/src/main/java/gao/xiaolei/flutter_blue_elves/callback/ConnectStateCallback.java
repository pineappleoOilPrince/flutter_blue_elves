package gao.xiaolei.flutter_blue_elves.callback;

public interface ConnectStateCallback {

    void connectSuccess(String id);//连接成功的回调

    void connectTimeout(String id);//连接超时的回调

    void disConnected(String id,boolean isInitiative);//断开连接的回调


}
