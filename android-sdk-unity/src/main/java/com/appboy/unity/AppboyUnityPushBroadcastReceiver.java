package com.appboy.unity;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.appboy.unity.configuration.UnityConfigurationProvider;
import com.appboy.unity.utils.MessagingUtils;
import com.braze.push.BrazeNotificationUtils;
import com.braze.support.BrazeLogger;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class AppboyUnityPushBroadcastReceiver extends BroadcastReceiver {
  private static final String TAG = BrazeLogger.getBrazeLogTag(AppboyUnityPushBroadcastReceiver.class);
  private static final List<Intent> sIntentBuffer = new ArrayList<>();
  private static boolean sUnityBindingInitialized = false;
  private static boolean sReceiverInitialized = false;
  @Nullable
  private static UnityConfigurationProvider sUnityConfigurationProvider;
  @Nullable
  private static String sPushReceivedAction;
  @Nullable
  private static String sPushOpenedAction;
  @Nullable
  private static String sPushDeletedAction;

  @SuppressWarnings("deprecation") // https://jira.braze.com/browse/SDK-1689 to address
  @Override
  public void onReceive(Context context, Intent intent) {
    if (!sReceiverInitialized) {
      sUnityConfigurationProvider = new UnityConfigurationProvider(context);
      String packageName = context.getPackageName();
      sPushReceivedAction = packageName + BrazeNotificationUtils.APPBOY_NOTIFICATION_RECEIVED_SUFFIX;
      sPushOpenedAction = packageName + BrazeNotificationUtils.APPBOY_NOTIFICATION_OPENED_SUFFIX;
      sPushDeletedAction = packageName + BrazeNotificationUtils.APPBOY_NOTIFICATION_DELETED_SUFFIX;
      if (!sUnityConfigurationProvider.getDelaySendingPushMessages()) {
        // If this is false, then always send the push intents immediately
        sUnityBindingInitialized = true;
      }
      sReceiverInitialized = true;
    }
    if (sUnityBindingInitialized) {
      handleIntent(intent, sUnityConfigurationProvider);
    } else {
      BrazeLogger.i(TAG, "Adding intent to pending buffer since Unity binding is uninitialized.");
      sIntentBuffer.add(intent);
    }
  }

  /**
   * Call from the Unity binding to flush any intents
   */
  public static void onBindingInitialized() {
    sUnityBindingInitialized = true;

    if (sReceiverInitialized) {
      // Flush our existing intents
      final Iterator<Intent> it = sIntentBuffer.iterator();
      while (it.hasNext()) {
        handleIntent(it.next(), sUnityConfigurationProvider);
        it.remove();
      }
    }
  }

  public static void handleIntent(Intent intent, @NonNull UnityConfigurationProvider unityConfigurationProvider) {
    String action = intent.getAction();
    BrazeLogger.i(TAG, "Received a push broadcast intent with action: " + action);
    if (sPushReceivedAction.equals(action)) {
      String unityGameObjectName = unityConfigurationProvider.getPushReceivedGameObjectName();
      String unityCallbackFunctionName = unityConfigurationProvider.getPushReceivedCallbackMethodName();
      boolean isPushMessageSent = MessagingUtils.sendPushMessageToUnity(unityGameObjectName, unityCallbackFunctionName, intent, "push received");
      BrazeLogger.d(TAG, (isPushMessageSent ? "Successfully sent" : "Failure to send") + " push received message to Unity Player");
    } else if (sPushOpenedAction.equals(action)) {
      String unityGameObjectName = unityConfigurationProvider.getPushOpenedGameObjectName();
      String unityCallbackFunctionName = unityConfigurationProvider.getPushOpenedCallbackMethodName();
      boolean isPushMessageSent = MessagingUtils.sendPushMessageToUnity(unityGameObjectName, unityCallbackFunctionName, intent, "push opened");
      BrazeLogger.d(TAG, (isPushMessageSent ? "Successfully sent" : "Failure to send") + " push opened message to Unity Player");
    } else if (sPushDeletedAction.equals(action)) {
      String unityGameObjectName = unityConfigurationProvider.getPushDeletedGameObjectName();
      String unityCallbackFunctionName = unityConfigurationProvider.getPushDeletedCallbackMethodName();
      boolean isPushMessageSent = MessagingUtils.sendPushMessageToUnity(unityGameObjectName, unityCallbackFunctionName, intent, "push deleted");
      BrazeLogger.d(TAG, (isPushMessageSent ? "Successfully sent" : "Failure to send") + " push deleted message to Unity Player");
    }
  }
}
