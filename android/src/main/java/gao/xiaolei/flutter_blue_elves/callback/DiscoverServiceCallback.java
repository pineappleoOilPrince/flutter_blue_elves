package gao.xiaolei.flutter_blue_elves.callback;

import java.util.List;
import java.util.Map;

public interface DiscoverServiceCallback {//发现蓝牙服务和其特征值的回调
    
    void discoverService(String id, String serviceUuid, List<Map<String,Object>> characteristic);
}
