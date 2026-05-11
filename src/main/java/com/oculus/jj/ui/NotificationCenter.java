package com.oculus.jj.ui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * NotificationCenter - シンプルな通知センター
 * HOROS-20240407のNSNotificationCenterをJavaで再現
 */
public class NotificationCenter {

    private static final NotificationCenter defaultCenter = new NotificationCenter();

    public static NotificationCenter defaultCenter() {
        return defaultCenter;
    }

    // HOROS-20240407でよく使われる通知名
    public static final String OsirixDCMViewSyncNotification = "OsirixDCMViewSyncNotification";
    public static final String OsirixDCMViewDidBecomeFirstResponderNotification = "OsirixDCMViewDidBecomeFirstResponderNotification";
    public static final String OsirixCloseViewerNotification = "OsirixCloseViewerNotification";
    public static final String OsirixUpdateViewNotification = "OsirixUpdateViewNotification";

    public interface NotificationObserver {
        void onNotification(String name, Object object, Map<String, Object> userInfo);
    }

    private final Map<String, List<NotificationObserver>> observers = new HashMap<>();

    public synchronized void addObserver(String name, NotificationObserver observer) {
        observers.computeIfAbsent(name, k -> new ArrayList<>()).add(observer);
    }

    public synchronized void removeObserver(NotificationObserver observer) {
        for (List<NotificationObserver> list : observers.values()) {
            list.remove(observer);
        }
    }

    public synchronized void postNotification(String name, Object object, Map<String, Object> userInfo) {
        List<NotificationObserver> list = observers.get(name);
        if (list != null) {
            for (NotificationObserver observer : new ArrayList<>(list)) {
                observer.onNotification(name, object, userInfo);
            }
        }
    }

    public void postNotification(String name, Object object) {
        postNotification(name, object, null);
    }
}
