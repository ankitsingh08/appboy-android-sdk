package com.appboy.unity.configuration;

import android.content.Context;

import com.appboy.unity.enums.UnityMessageType;
import com.braze.configuration.CachedConfigurationProvider;
import com.braze.support.BrazeLogger;

public class UnityConfigurationProvider extends CachedConfigurationProvider {
  private static final String TAG = UnityConfigurationProvider.class.getName();
  private static final String INAPP_SHOW_INAPP_MESSAGES_AUTOMATICALLY_KEY = "com_appboy_inapp_show_inapp_messages_automatically";
  // In App Message listener
  private static final String INAPP_LISTENER_GAME_OBJECT_NAME_KEY = "com_appboy_inapp_listener_game_object_name";
  private static final String INAPP_LISTENER_CALLBACK_METHOD_NAME_KEY = "com_appboy_inapp_listener_callback_method_name";
  // News Feed listener
  private static final String FEED_LISTENER_GAME_OBJECT_NAME_KEY = "com_appboy_feed_listener_game_object_name";
  private static final String FEED_LISTENER_CALLBACK_METHOD_NAME_KEY = "com_appboy_feed_listener_callback_method_name";
  // Push received
  private static final String PUSH_RECEIVED_GAME_OBJECT_NAME_KEY = "com_appboy_push_received_game_object_name";
  private static final String PUSH_RECEIVED_CALLBACK_METHOD_NAME_KEY = "com_appboy_push_received_callback_method_name";
  // Push opened
  private static final String PUSH_OPENED_GAME_OBJECT_NAME_KEY = "com_appboy_push_opened_game_object_name";
  private static final String PUSH_OPENED_CALLBACK_METHOD_NAME_KEY = "com_appboy_push_opened_callback_method_name";
  // Push deleted
  private static final String PUSH_DELETED_GAME_OBJECT_NAME_KEY = "com_appboy_push_deleted_game_object_name";
  private static final String PUSH_DELETED_CALLBACK_METHOD_NAME_KEY = "com_appboy_push_deleted_callback_method_name";
  // Content Cards
  private static final String CONTENT_CARDS_UPDATED_LISTENER_GAME_OBJECT_NAME_KEY = "com_appboy_content_cards_updated_listener_game_object_name";
  private static final String CONTENT_CARDS_UPDATED_LISTENER_CALLBACK_METHOD_NAME_KEY = "com_appboy_content_cards_updated_listener_callback_method_name";
  // Pending push intents
  private static final String DELAY_SENDING_PUSH_MESSAGES_KEY = "com_braze_delay_sending_push_intents";

  public UnityConfigurationProvider(Context context) {
    super(context, false);
  }

  public String getInAppMessageListenerGameObjectName() {
    return getStringValue(INAPP_LISTENER_GAME_OBJECT_NAME_KEY, null);
  }

  public String getInAppMessageListenerCallbackMethodName() {
    return getStringValue(INAPP_LISTENER_CALLBACK_METHOD_NAME_KEY, null);
  }

  public String getFeedListenerGameObjectName() {
    return getStringValue(FEED_LISTENER_GAME_OBJECT_NAME_KEY, null);
  }

  public String getFeedListenerCallbackMethodName() {
    return getStringValue(FEED_LISTENER_CALLBACK_METHOD_NAME_KEY, null);
  }

  public boolean getShowInAppMessagesAutomaticallyKey() {
    return getBooleanValue(INAPP_SHOW_INAPP_MESSAGES_AUTOMATICALLY_KEY, true);
  }

  public String getPushReceivedGameObjectName() {
    return getStringValue(PUSH_RECEIVED_GAME_OBJECT_NAME_KEY, null);
  }

  public String getPushReceivedCallbackMethodName() {
    return getStringValue(PUSH_RECEIVED_CALLBACK_METHOD_NAME_KEY, null);
  }

  public String getPushOpenedGameObjectName() {
    return getStringValue(PUSH_OPENED_GAME_OBJECT_NAME_KEY, null);
  }

  public String getPushOpenedCallbackMethodName() {
    return getStringValue(PUSH_OPENED_CALLBACK_METHOD_NAME_KEY, null);
  }

  public String getPushDeletedGameObjectName() {
    return getStringValue(PUSH_DELETED_GAME_OBJECT_NAME_KEY, null);
  }

  public String getPushDeletedCallbackMethodName() {
    return getStringValue(PUSH_DELETED_CALLBACK_METHOD_NAME_KEY, null);
  }

  public String getContentCardsUpdatedListenerGameObjectName() {
    return getStringValue(CONTENT_CARDS_UPDATED_LISTENER_GAME_OBJECT_NAME_KEY, null);
  }

  public String getContentCardsUpdatedListenerCallbackMethodName() {
    return getStringValue(CONTENT_CARDS_UPDATED_LISTENER_CALLBACK_METHOD_NAME_KEY, null);
  }

  public boolean getDelaySendingPushMessages() {
    return getBooleanValue(DELAY_SENDING_PUSH_MESSAGES_KEY, false);
  }

  public void configureListener(int messageTypeValue, String gameObject, String methodName) {
    UnityMessageType messageType = UnityMessageType.getTypeFromValue(messageTypeValue);
    if (messageType == null) {
      BrazeLogger.d(TAG, "Got bad message type " + messageTypeValue + ". Cannot configure a "
          + "listener on object " + gameObject + " for method " + methodName);
      return;
    }

    // Inform the configuration cache to set a new value
    switch (messageType) {
      case PUSH_PERMISSIONS_PROMPT_RESPONSE:
      case PUSH_TOKEN_RECEIVED_FROM_SYSTEM:
        // No Android implementation
        break;
      case PUSH_RECEIVED:
        putStringIntoRuntimeConfiguration(PUSH_RECEIVED_GAME_OBJECT_NAME_KEY, gameObject);
        putStringIntoRuntimeConfiguration(PUSH_RECEIVED_CALLBACK_METHOD_NAME_KEY, methodName);
        break;
      case PUSH_OPENED:
        putStringIntoRuntimeConfiguration(PUSH_OPENED_GAME_OBJECT_NAME_KEY, gameObject);
        putStringIntoRuntimeConfiguration(PUSH_OPENED_CALLBACK_METHOD_NAME_KEY, methodName);
        break;
      case PUSH_DELETED:
        putStringIntoRuntimeConfiguration(PUSH_DELETED_GAME_OBJECT_NAME_KEY, gameObject);
        putStringIntoRuntimeConfiguration(PUSH_DELETED_CALLBACK_METHOD_NAME_KEY, methodName);
        break;
      case IN_APP_MESSAGE:
        putStringIntoRuntimeConfiguration(INAPP_LISTENER_GAME_OBJECT_NAME_KEY, gameObject);
        putStringIntoRuntimeConfiguration(INAPP_LISTENER_CALLBACK_METHOD_NAME_KEY, methodName);
        break;
      case NEWS_FEED:
        putStringIntoRuntimeConfiguration(FEED_LISTENER_GAME_OBJECT_NAME_KEY, gameObject);
        putStringIntoRuntimeConfiguration(FEED_LISTENER_CALLBACK_METHOD_NAME_KEY, methodName);
        break;
      case CONTENT_CARDS_UPDATED:
        putStringIntoRuntimeConfiguration(CONTENT_CARDS_UPDATED_LISTENER_GAME_OBJECT_NAME_KEY, gameObject);
        putStringIntoRuntimeConfiguration(CONTENT_CARDS_UPDATED_LISTENER_CALLBACK_METHOD_NAME_KEY, methodName);
        break;
      default:
        BrazeLogger.d(TAG, "Got unhandled message type: " + messageType);
    }
  }

  private void putStringIntoRuntimeConfiguration(String key, String value) {
    getRuntimeAppConfigurationProvider().startEdit();
    getRuntimeAppConfigurationProvider().putString(key, value);
    getRuntimeAppConfigurationProvider().applyEdit();
  }
}
