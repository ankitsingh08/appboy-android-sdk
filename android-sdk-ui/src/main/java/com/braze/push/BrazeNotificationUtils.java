package com.braze.push;

import android.Manifest;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.appboy.BrazeInternal;
import com.appboy.Constants;
import com.appboy.enums.Channel;
import com.appboy.models.push.BrazeNotificationPayload;
import com.braze.Braze;
import com.braze.IBrazeNotificationFactory;
import com.braze.configuration.BrazeConfig;
import com.braze.configuration.BrazeConfigurationProvider;
import com.braze.enums.BrazeViewBounds;
import com.braze.images.IBrazeImageLoader;
import com.braze.push.support.HtmlUtils;
import com.braze.support.BrazeLogger;
import com.braze.support.IntentUtils;
import com.braze.support.JsonUtils;
import com.braze.support.PermissionUtils;
import com.braze.support.StringUtils;
import com.braze.ui.BrazeDeeplinkHandler;
import com.braze.ui.actions.UriAction;
import com.braze.ui.support.UriUtils;

import org.json.JSONObject;

public class BrazeNotificationUtils {
  private static final String TAG = BrazeLogger.getBrazeLogTag(BrazeNotificationUtils.class);
  private static final String SOURCE_KEY = "source";

  private enum BrazeNotificationBroadcastType {
    OPENED, RECEIVED, DELETED
  }

  /**
   * @deprecated Please see {@link Constants#BRAZE_PUSH_INTENT_NOTIFICATION_OPENED}. Deprecated since 6/25/21
   */
  @Deprecated
  public static final String APPBOY_NOTIFICATION_OPENED_SUFFIX = ".intent.APPBOY_NOTIFICATION_OPENED";
  /**
   * @deprecated Please {@link Constants#BRAZE_PUSH_INTENT_NOTIFICATION_RECEIVED}. Deprecated since 6/25/21
   */
  @Deprecated
  public static final String APPBOY_NOTIFICATION_RECEIVED_SUFFIX = ".intent.APPBOY_PUSH_RECEIVED";
  /**
   * @deprecated Please {@link Constants#BRAZE_PUSH_INTENT_NOTIFICATION_DELETED}. Deprecated since 6/25/21
   */
  @Deprecated
  public static final String APPBOY_NOTIFICATION_DELETED_SUFFIX = ".intent.APPBOY_PUSH_DELETED";

  /**
   * Handles a push notification click. Called by FCM/ADM receiver when a
   * Braze push notification click intent is received.
   * <p/>
   * See {@link #sendNotificationOpenedBroadcast}
   *
   * @param context Application context
   * @param intent  the internal notification clicked intent constructed in
   *                {@link #setContentIntentIfPresent}
   */
  public static void handleNotificationOpened(Context context, Intent intent) {
    try {
      Braze.getInstance(context).logPushNotificationOpened(intent);
      sendNotificationOpenedBroadcast(context, intent);
      BrazeConfigurationProvider appConfigurationProvider = new BrazeConfigurationProvider(context);
      if (appConfigurationProvider.getDoesHandlePushDeepLinksAutomatically()) {
        routeUserWithNotificationOpenedIntent(context, intent);
      } else {
        BrazeLogger.i(TAG, "Not handling deep links automatically, skipping deep link handling");
      }
    } catch (Exception e) {
      BrazeLogger.e(TAG, "Exception occurred attempting to handle notification opened intent.", e);
    }
  }

  /**
   * Handles a push notification deletion by the user. Called by FCM/ADM receiver when a
   * Braze push notification delete intent is received.
   * <p/>
   * See {@link NotificationCompat.Builder#setDeleteIntent(PendingIntent)}
   * <p/>
   *
   * @param context Application context
   * @param intent  the internal notification delete intent constructed in
   *                {@link #setDeleteIntent(Context, NotificationCompat.Builder, Bundle)}
   */
  public static void handleNotificationDeleted(Context context, Intent intent) {
    try {
      BrazeLogger.d(TAG, "Sending notification deleted broadcast");
      sendPushActionIntent(context, BrazeNotificationBroadcastType.DELETED, intent.getExtras());
    } catch (Exception e) {
      BrazeLogger.e(TAG, "Exception occurred attempting to handle notification delete intent.", e);
    }
  }

  /**
   * Opens any available deep links with an Intent.ACTION_VIEW intent, placing the main activity
   * on the back stack. If no deep link is available, opens the main activity.
   *
   * @param context
   * @param intent  the internal notification clicked intent constructed in
   *                {@link #setContentIntentIfPresent}
   */
  public static void routeUserWithNotificationOpenedIntent(Context context, Intent intent) {
    // get extras bundle.
    Bundle extras = intent.getBundleExtra(Constants.APPBOY_PUSH_EXTRAS_KEY);
    if (extras == null) {
      extras = new Bundle();
    }
    extras.putString(Constants.APPBOY_PUSH_CAMPAIGN_ID_KEY,
        intent.getStringExtra(Constants.APPBOY_PUSH_CAMPAIGN_ID_KEY));
    extras.putString(SOURCE_KEY, Constants.APPBOY);

    // If a deep link exists, start an ACTION_VIEW intent pointing at the deep link.
    // The intent returned from getStartActivityIntent() is placed on the back stack.
    // Otherwise, start the intent defined in getStartActivityIntent().
    String deepLink = intent.getStringExtra(Constants.APPBOY_PUSH_DEEP_LINK_KEY);
    if (!StringUtils.isNullOrBlank(deepLink)) {
      BrazeLogger.d(TAG, "Found a deep link " + deepLink);
      boolean useWebView = "true".equalsIgnoreCase(intent.getStringExtra(Constants.APPBOY_PUSH_OPEN_URI_IN_WEBVIEW_KEY));
      BrazeLogger.d(TAG, "Use webview set to: " + useWebView);

      // pass deep link and use webview values to target activity.
      extras.putString(Constants.APPBOY_PUSH_DEEP_LINK_KEY, deepLink);
      extras.putBoolean(Constants.APPBOY_PUSH_OPEN_URI_IN_WEBVIEW_KEY, useWebView);

      UriAction uriAction = BrazeDeeplinkHandler.getInstance().createUriActionFromUrlString(deepLink, extras, useWebView, Channel.PUSH);
      BrazeDeeplinkHandler.getInstance().gotoUri(context, uriAction);
    } else {
      final Intent mainActivityIntent = UriUtils.getMainActivityIntent(context, extras);
      BrazeLogger.d(TAG, "Push notification had no deep link. Opening main activity: " + mainActivityIntent);
      context.startActivity(mainActivityIntent);
    }
  }

  /**
   * @deprecated Please use {@link BrazeNotificationPayload#getAttachedAppboyExtras(Bundle)}
   */
  @Deprecated
  public static Bundle getAppboyExtrasWithoutPreprocessing(Bundle notificationExtras) {
    return BrazeNotificationPayload.getAttachedAppboyExtras(notificationExtras);
  }

  /**
   * @deprecated Please use {@link JsonUtils#parseJsonObjectIntoBundle(String)}
   */
  @Deprecated
  public static Bundle parseJSONStringDictionaryIntoBundle(String jsonStringDictionary) {
    return JsonUtils.parseJsonObjectIntoBundle(jsonStringDictionary);
  }

  /**
   * Checks the incoming FCM/ADM intent to determine whether this is a Braze push message.
   * <p/>
   * All Braze push messages must contain an extras entry with key set to "_ab" and value set to "true".
   */
  public static boolean isAppboyPushMessage(Intent intent) {
    Bundle extras = intent.getExtras();
    return extras != null && "true".equals(extras.getString(Constants.APPBOY_PUSH_APPBOY_KEY));
  }

  /**
   * Checks the intent received from FCM to determine whether this is a notification message or a
   * silent push.
   * <p/>
   * A notification message is a Braze push message that displays a notification in the
   * notification center (and optionally contains extra information that can be used directly
   * by the app).
   * <p/>
   * A silent push is a Braze push message that contains only extra information that can
   * be used directly by the app.
   */
  public static boolean isNotificationMessage(Intent intent) {
    Bundle extras = intent.getExtras();
    return extras != null && extras.containsKey(Constants.APPBOY_PUSH_TITLE_KEY) && extras.containsKey(Constants.APPBOY_PUSH_CONTENT_KEY);
  }

  /**
   * Creates and sends a broadcast message that can be listened for by the host app. The broadcast
   * message intent contains all of the data sent as part of the Braze push message. The broadcast
   * message action is <host-app-package-name>.intent.APPBOY_PUSH_RECEIVED.
   */
  public static void sendPushMessageReceivedBroadcast(Context context, Bundle notificationExtras) {
    BrazeLogger.d(TAG, "Sending push message received broadcast");
    sendPushActionIntent(context, BrazeNotificationBroadcastType.RECEIVED, notificationExtras);
  }

  /**
   * Requests a geofence refresh from Braze if appropriate based on the payload of the push notification.
   *
   * @param context
   * @param notificationExtras Notification extras as provided by FCM/ADM.
   * @return True iff a geofence refresh was requested from Appboy.
   */
  public static boolean requestGeofenceRefreshIfAppropriate(Context context, Bundle notificationExtras) {
    if (notificationExtras.containsKey(Constants.APPBOY_PUSH_SYNC_GEOFENCES_KEY)) {
      if (Boolean.parseBoolean(notificationExtras.getString(Constants.APPBOY_PUSH_SYNC_GEOFENCES_KEY))) {
        BrazeInternal.requestGeofenceRefresh(context, true);
        return true;
      } else {
        BrazeLogger.d(TAG, "Geofence sync key was false. Not syncing geofences.");
      }
    } else {
      BrazeLogger.d(TAG, "Geofence sync key not included in push payload. Not syncing geofences.");
    }
    return false;
  }

  /**
   * Creates an alarm which will issue a broadcast to cancel the notification specified by the given notificationId after the given duration.
   */
  public static void setNotificationDurationAlarm(Context context, Class<?> thisClass, int notificationId, int durationInMillis) {
    Intent cancelIntent = new Intent(context, thisClass);
    cancelIntent.setAction(Constants.APPBOY_CANCEL_NOTIFICATION_ACTION);
    cancelIntent.putExtra(Constants.APPBOY_PUSH_NOTIFICATION_ID, notificationId);

    final int flags = PendingIntent.FLAG_UPDATE_CURRENT | IntentUtils.getImmutablePendingIntentFlags();
    PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, cancelIntent, flags);
    AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
    if (durationInMillis >= Constants.APPBOY_MINIMUM_NOTIFICATION_DURATION_MILLIS) {
      BrazeLogger.d(TAG, "Setting Notification duration alarm for " + durationInMillis + " ms");
      alarmManager.set(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + durationInMillis, pendingIntent);
    }
  }

  /**
   * @deprecated Please use {@link #getNotificationId(BrazeNotificationPayload)}
   */
  @Deprecated
  public static int getNotificationId(Bundle notificationExtras) {
    BrazeNotificationPayload payload = new BrazeNotificationPayload(notificationExtras);
    return getNotificationId(payload);
  }

  /**
   * Returns an id for the new notification we'll send to the notification center.
   * Notification id is used by the Android OS to override currently active notifications with identical ids.
   * If a custom notification id is not defined in the payload, Braze derives an id value from the message's contents
   * to prevent duplication in the notification center.
   */
  public static int getNotificationId(@NonNull BrazeNotificationPayload payload) {
    if (payload == null) {
      BrazeLogger.d(TAG, "Message without extras bundle received. Using default notification id");
      return Constants.APPBOY_DEFAULT_NOTIFICATION_ID;
    }

    if (payload.getCustomNotificationId() != null) {
      int notificationId = payload.getCustomNotificationId();
      BrazeLogger.d(TAG, "Using notification id provided in the message's extras bundle: " + notificationId);
      return notificationId;
    } else {
      String messageKey = "";
      // Don't concatenate if null
      if (payload.getTitleText() != null) {
        messageKey += payload.getTitleText();
      }
      if (payload.getContentText() != null) {
        messageKey += payload.getContentText();
      }
      int notificationId = messageKey.hashCode();
      BrazeLogger.d(TAG, "Message without notification id provided in the extras "
          + "bundle received. Using a hash of the message: " + notificationId);
      return notificationId;
    }
  }

  /**
   * This method will retrieve notification priority from notificationExtras bundle if it has been set.
   * Otherwise returns the default priority.
   * <p>
   * Starting with Android O, priority is set on a notification channel and not individually on notifications.
   */
  @SuppressWarnings("deprecation") // Notification Priority usage
  public static int getNotificationPriority(Bundle notificationExtras) {
    if (notificationExtras != null && notificationExtras.containsKey(Constants.APPBOY_PUSH_PRIORITY_KEY)) {
      try {
        int notificationPriority = Integer.parseInt(notificationExtras.getString(Constants.APPBOY_PUSH_PRIORITY_KEY));
        if ((notificationPriority >= Notification.PRIORITY_MIN && notificationPriority <= Notification.PRIORITY_MAX)) {
          return notificationPriority;
        } else {
          BrazeLogger.w(TAG, "Received invalid notification priority " + notificationPriority);
        }
      } catch (NumberFormatException e) {
        BrazeLogger.e(TAG, "Unable to parse custom priority. Returning default priority of " + Notification.PRIORITY_DEFAULT, e);
      }
    }
    return Notification.PRIORITY_DEFAULT;
  }

  /**
   * This method will wake the device using a wake lock if the {@link android.Manifest.permission#WAKE_LOCK} permission is present in the
   * manifest. If the permission is not present, this does nothing. If the screen is already on,
   * and the permission is present, this does nothing. If the priority of the incoming notification
   * is min, this does nothing.
   */
  @SuppressWarnings("deprecation")
  // Deprecation warning suppressed for PowerManager.FULL_WAKE_LOCK usage. Alternative requires Activity instance which is unavailable in this context.
  // Deprecation warning suppressed for Notification.PRIORITY_MIN usage.
  public static boolean wakeScreenIfAppropriate(Context context, BrazeConfigurationProvider configurationProvider, Bundle notificationExtras) {
    // Check for the wake lock permission.
    if (!PermissionUtils.hasPermission(context, Manifest.permission.WAKE_LOCK)) {
      return false;
    }
    if (!configurationProvider.isPushWakeScreenForNotificationEnabled()) {
      return false;
    }

    // Don't wake lock if this is a minimum priority/importance notification.
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      // Get the channel for this notification
      NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
      NotificationChannel notificationChannel = getValidNotificationChannel(notificationManager, notificationExtras);

      if (notificationChannel == null) {
        BrazeLogger.d(TAG, "Not waking screen on Android O+ device, could not find notification channel.");
        return false;
      }

      int importance = getNotificationChannelImportance(notificationChannel);
      if (importance == NotificationManager.IMPORTANCE_MIN) {
        BrazeLogger.d(TAG, "Not acquiring wake-lock for Android O+ notification with importance: " + importance);
        return false;
      }
    } else {
      if (getNotificationPriority(notificationExtras) == Notification.PRIORITY_MIN) {
        return false;
      }
    }

    BrazeLogger.d(TAG, "Waking screen for notification");
    // Get the power manager for the wake lock.
    PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
    PowerManager.WakeLock wakeLock = powerManager.newWakeLock(PowerManager.FULL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP, TAG);
    // Acquire the wake lock for some negligible time, then release it. We just want to wake the screen
    // and not take up more CPU power than necessary.
    wakeLock.acquire();
    wakeLock.release();
    return true;
  }

  /**
   * Returns a custom AppboyNotificationFactory if set, else the default AppboyNotificationFactory
   */
  public static IBrazeNotificationFactory getActiveNotificationFactory() {
    IBrazeNotificationFactory customBrazeNotificationFactory = Braze.getCustomBrazeNotificationFactory();
    if (customBrazeNotificationFactory == null) {
      return BrazeNotificationFactory.getInstance();
    } else {
      return customBrazeNotificationFactory;
    }
  }

  /**
   * Checks that the notification is a story that has only just been received. If so, each
   * image within the story is put in the Braze image loader's cache.
   *
   * @param context            Application context.
   * @param notificationExtras Notification extras as provided by FCM/ADM.
   * @param appboyExtras       Bundle object containing 'extra' key value pairs defined by the client.
   */
  public static void prefetchBitmapsIfNewlyReceivedStoryPush(Context context, Bundle notificationExtras, Bundle appboyExtras) {
    if (!notificationExtras.containsKey(Constants.APPBOY_PUSH_STORY_KEY)) {
      return;
    }
    if ((notificationExtras.getBoolean(Constants.APPBOY_PUSH_STORY_IS_NEWLY_RECEIVED, false))) {
      int count = 0;
      String imageUrl = BrazeNotificationPayload.getTemplateFieldAtIndex(count, notificationExtras, Constants.APPBOY_PUSH_STORY_IMAGE_KEY_TEMPLATE);
      while (!StringUtils.isNullOrBlank(imageUrl)) {
        BrazeLogger.v(TAG, "Pre-fetching bitmap at URL: " + imageUrl);
        IBrazeImageLoader imageLoader = Braze.getInstance(context).getImageLoader();
        imageLoader.getPushBitmapFromUrl(context, appboyExtras, imageUrl, BrazeViewBounds.NOTIFICATION_ONE_IMAGE_STORY);
        count++;
        imageUrl = BrazeNotificationPayload.getTemplateFieldAtIndex(count, notificationExtras, Constants.APPBOY_PUSH_STORY_IMAGE_KEY_TEMPLATE);
      }
      notificationExtras.putBoolean(Constants.APPBOY_PUSH_STORY_IS_NEWLY_RECEIVED, false);
    }
  }

  /**
   * @deprecated Please use {@link #setTitleIfPresent(NotificationCompat.Builder, BrazeNotificationPayload)} instead.
   */
  @Deprecated
  public static void setTitleIfPresent(BrazeConfigurationProvider configurationProvider,
                                       NotificationCompat.Builder notificationBuilder,
                                       Bundle notificationExtras) {
    BrazeNotificationPayload payload = new BrazeNotificationPayload(notificationExtras);
    setTitleIfPresent(notificationBuilder, payload);
  }

  public static void setTitleIfPresent(@NonNull NotificationCompat.Builder notificationBuilder,
                                       @NonNull BrazeNotificationPayload payload) {
    BrazeLogger.d(TAG, "Setting title for notification");
    notificationBuilder.setContentTitle(HtmlUtils.getHtmlSpannedTextIfEnabled(payload.getConfigurationProvider(), payload.getTitleText()));
  }

  /**
   * @deprecated Please use {@link #setContentIfPresent(NotificationCompat.Builder, BrazeNotificationPayload)} instead.
   */
  @Deprecated
  public static void setContentIfPresent(BrazeConfigurationProvider configurationProvider,
                                         NotificationCompat.Builder notificationBuilder,
                                         Bundle notificationExtras) {
    BrazeNotificationPayload payload = new BrazeNotificationPayload(notificationExtras);
    setContentIfPresent(notificationBuilder, payload);
  }

  /**
   * Sets notification content if it exists in the payload.
   */
  public static void setContentIfPresent(@NonNull NotificationCompat.Builder notificationBuilder,
                                         @NonNull BrazeNotificationPayload payload) {
    BrazeLogger.d(TAG, "Setting content for notification");
    notificationBuilder.setContentText(HtmlUtils.getHtmlSpannedTextIfEnabled(payload.getConfigurationProvider(), payload.getContentText()));
  }

  /**
   * Sets notification ticker to the title if it exists in the notificationExtras.
   *
   * @deprecated Please use {@link #setTickerIfPresent(NotificationCompat.Builder, BrazeNotificationPayload)}
   */
  @Deprecated
  public static void setTickerIfPresent(NotificationCompat.Builder notificationBuilder,
                                        Bundle notificationExtras) {
    BrazeNotificationPayload payload = new BrazeNotificationPayload(notificationExtras);
    setTickerIfPresent(notificationBuilder, payload);
  }

  /**
   * Sets notification ticker to the title if it exists in the notificationExtras.
   */
  public static void setTickerIfPresent(@NonNull NotificationCompat.Builder notificationBuilder,
                                        @NonNull BrazeNotificationPayload payload) {
    BrazeLogger.d(TAG, "Setting ticker for notification");
    notificationBuilder.setTicker(payload.getTitleText());
  }

  /**
   * Create broadcast intent that will fire when the notification has been opened. The FCM or ADM receiver will be notified,
   * log a click, then send a broadcast to the client receiver.
   *
   * @param context
   * @param notificationBuilder
   * @param notificationExtras
   */
  public static void setContentIntentIfPresent(Context context, NotificationCompat.Builder notificationBuilder, Bundle notificationExtras) {
    try {
      PendingIntent pushOpenedPendingIntent = getPushActionPendingIntent(context, Constants.APPBOY_PUSH_CLICKED_ACTION, notificationExtras);
      notificationBuilder.setContentIntent(pushOpenedPendingIntent);
    } catch (Exception e) {
      BrazeLogger.e(TAG, "Error setting content intent.", e);
    }
  }

  public static void setDeleteIntent(Context context, NotificationCompat.Builder notificationBuilder, Bundle notificationExtras) {
    try {
      Intent pushDeletedIntent = new Intent(Constants.APPBOY_PUSH_DELETED_ACTION).setClass(context, getNotificationReceiverClass());
      if (notificationExtras != null) {
        pushDeletedIntent.putExtras(notificationExtras);
      }
      final int flags = PendingIntent.FLAG_ONE_SHOT | IntentUtils.getImmutablePendingIntentFlags();
      PendingIntent pushDeletedPendingIntent = PendingIntent.getBroadcast(context, IntentUtils.getRequestCode(), pushDeletedIntent, flags);
      notificationBuilder.setDeleteIntent(pushDeletedPendingIntent);
    } catch (Exception e) {
      BrazeLogger.e(TAG, "Error setting delete intent.", e);
    }
  }

  /**
   * Sets the icon used in the notification bar itself.
   * If a drawable defined in braze.xml is found, we use that. Otherwise, fall back to the application icon.
   *
   * @return the resource id of the small icon to be used.
   */
  public static int setSmallIcon(BrazeConfigurationProvider appConfigurationProvider, NotificationCompat.Builder notificationBuilder) {
    int smallNotificationIconResourceId = appConfigurationProvider.getSmallNotificationIconResourceId();
    if (smallNotificationIconResourceId == 0) {
      BrazeLogger.d(TAG, "Small notification icon resource was not found. Will use the app icon when "
          + "displaying notifications.");
      smallNotificationIconResourceId = appConfigurationProvider.getApplicationIconResourceId();
    } else {
      BrazeLogger.d(TAG, "Setting small icon for notification via resource id");
    }
    notificationBuilder.setSmallIcon(smallNotificationIconResourceId);
    return smallNotificationIconResourceId;
  }

  /**
   * @deprecated Please use {@link #setSetShowWhen(NotificationCompat.Builder, BrazeNotificationPayload)}
   */
  @Deprecated
  public static void setSetShowWhen(NotificationCompat.Builder notificationBuilder, Bundle notificationExtras) {
    BrazeNotificationPayload payload = new BrazeNotificationPayload(notificationExtras);
    setSetShowWhen(notificationBuilder, payload);
  }

  /**
   * This method exists to disable {@link NotificationCompat.Builder#setShowWhen(boolean)}
   * for push stories.
   */
  public static void setSetShowWhen(@NonNull NotificationCompat.Builder notificationBuilder, @NonNull BrazeNotificationPayload payload) {
    if (payload.isPushStory()) {
      BrazeLogger.d(TAG, "Set show when not supported in story push.");
      notificationBuilder.setShowWhen(false);
    }
  }

  /**
   * @deprecated Please use {@link #setLargeIconIfPresentAndSupported(NotificationCompat.Builder, BrazeNotificationPayload)}
   */
  @Deprecated
  public static boolean setLargeIconIfPresentAndSupported(Context context,
                                                          BrazeConfigurationProvider appConfigurationProvider,
                                                          NotificationCompat.Builder notificationBuilder,
                                                          Bundle notificationExtras) {
    BrazeNotificationPayload payload = new BrazeNotificationPayload(notificationExtras, null, context, appConfigurationProvider);
    return setLargeIconIfPresentAndSupported(notificationBuilder, payload);
  }

  /**
   * Set large icon. We use the large icon URL if it exists in the notificationExtras.
   * Otherwise we search for a drawable defined in braze.xml. If that doesn't exists, we do nothing.
   * <p/>
   *
   * @return whether a large icon was successfully set.
   */
  public static boolean setLargeIconIfPresentAndSupported(@NonNull NotificationCompat.Builder notificationBuilder, @NonNull BrazeNotificationPayload payload) {
    if (payload.isPushStory()) {
      BrazeLogger.d(TAG, "Large icon not supported in story push.");
      return false;
    }
    try {
      final Context context = payload.getContext();
      if (context == null) {
        BrazeLogger.d(TAG, "Cannot set large icon with null context");
        return false;
      }

      BrazeLogger.d(TAG, "Setting large icon for notification");
      String bitmapUrl = payload.getLargeIcon();
      if (bitmapUrl != null) {
        Bitmap largeNotificationBitmap = Braze.getInstance(context)
            .getImageLoader()
            .getPushBitmapFromUrl(context,
                null,
                bitmapUrl,
                BrazeViewBounds.NOTIFICATION_LARGE_ICON);
        notificationBuilder.setLargeIcon(largeNotificationBitmap);
        return true;
      }

      BrazeLogger.d(TAG, "Large icon bitmap url not present in extras. Attempting to use resource id instead.");
      final BrazeConfigurationProvider appConfigurationProvider = payload.getConfigurationProvider();
      int largeNotificationIconResourceId = appConfigurationProvider.getLargeNotificationIconResourceId();
      if (largeNotificationIconResourceId != 0) {
        Bitmap largeNotificationBitmap = BitmapFactory.decodeResource(context.getResources(), largeNotificationIconResourceId);
        notificationBuilder.setLargeIcon(largeNotificationBitmap);
        return true;
      } else {
        BrazeLogger.d(TAG, "Large icon resource id not present for notification");
      }
    } catch (Exception e) {
      BrazeLogger.e(TAG, "Error setting large notification icon", e);
    }

    BrazeLogger.d(TAG, "Large icon not set for notification");
    return false;
  }

  /**
   * @deprecated Please use {@link #setSoundIfPresentAndSupported(NotificationCompat.Builder, BrazeNotificationPayload)}
   */
  @Deprecated
  public static void setSoundIfPresentAndSupported(NotificationCompat.Builder notificationBuilder, Bundle notificationExtras) {
    BrazeNotificationPayload payload = new BrazeNotificationPayload(notificationExtras);
    setSoundIfPresentAndSupported(notificationBuilder, payload);
  }

  /**
   * Notifications can optionally include a sound to play when the notification is delivered.
   * <p/>
   * Starting with Android O, sound is set on a notification channel and not individually on notifications.
   */
  public static void setSoundIfPresentAndSupported(@NonNull NotificationCompat.Builder notificationBuilder, @NonNull BrazeNotificationPayload payload) {
    String soundUri = payload.getNotificationSound();
    if (soundUri != null) {
      if (soundUri.equals(Constants.APPBOY_PUSH_NOTIFICATION_SOUND_DEFAULT_VALUE)) {
        BrazeLogger.d(TAG, "Setting default sound for notification.");
        notificationBuilder.setDefaults(Notification.DEFAULT_SOUND);
      } else {
        BrazeLogger.d(TAG, "Setting sound for notification via uri.");
        notificationBuilder.setSound(Uri.parse(soundUri));
      }
    } else {
      BrazeLogger.d(TAG, "Sound key not present in notification extras. Not setting sound for notification.");
    }
  }

  /**
   * @deprecated Please use {@link #setSummaryTextIfPresentAndSupported(NotificationCompat.Builder, BrazeNotificationPayload)}
   */
  @Deprecated
  public static void setSummaryTextIfPresentAndSupported(NotificationCompat.Builder notificationBuilder, Bundle notificationExtras) {
    BrazeNotificationPayload payload = new BrazeNotificationPayload(notificationExtras);
    setSummaryTextIfPresentAndSupported(notificationBuilder, payload);
  }

  /**
   * Sets the subText of the notification if a summary is present in the notification extras.
   * <p/>
   * Supported on JellyBean+.
   */
  public static void setSummaryTextIfPresentAndSupported(@NonNull NotificationCompat.Builder notificationBuilder, @NonNull BrazeNotificationPayload payload) {
    String summaryText = payload.getSummaryText();
    if (summaryText != null) {
      BrazeLogger.d(TAG, "Setting summary text for notification");
      notificationBuilder.setSubText(summaryText);
    } else {
      BrazeLogger.d(TAG, "Summary text not present. Not setting summary text for notification.");
    }
  }

  /**
   * Sets the priority of the notification if a priority is present in the notification extras.
   * <p/>
   * Supported JellyBean+.
   * <p/>
   * Starting with Android O, priority is set on a notification channel and not individually on notifications.
   */
  public static void setPriorityIfPresentAndSupported(NotificationCompat.Builder notificationBuilder, Bundle notificationExtras) {
    if (notificationExtras != null) {
      BrazeLogger.d(TAG, "Setting priority for notification");
      notificationBuilder.setPriority(BrazeNotificationUtils.getNotificationPriority(notificationExtras));
    }
  }

  /**
   * @deprecated Please use
   * {@link BrazeNotificationStyleFactory#setStyleIfSupported(NotificationCompat.Builder, BrazeNotificationPayload)}
   */
  @Deprecated
  public static void setStyleIfSupported(Context context,
                                         NotificationCompat.Builder notificationBuilder,
                                         Bundle notificationExtras,
                                         Bundle appboyExtras) {
    BrazeNotificationPayload payload = new BrazeNotificationPayload(notificationExtras, null, context);
    BrazeNotificationStyleFactory.setStyleIfSupported(notificationBuilder, payload);
  }

  /**
   * @deprecated Please use {@link #setAccentColorIfPresentAndSupported(NotificationCompat.Builder, BrazeNotificationPayload)}
   */
  @Deprecated
  public static void setAccentColorIfPresentAndSupported(BrazeConfigurationProvider appConfigurationProvider,
                                                         NotificationCompat.Builder notificationBuilder,
                                                         Bundle notificationExtras) {
    BrazeNotificationPayload payload = new BrazeNotificationPayload(notificationExtras, null, null, appConfigurationProvider);
    setAccentColorIfPresentAndSupported(notificationBuilder, payload);
  }

  /**
   * Set accent color for devices on Lollipop and above. We use the push-specific accent color if it exists in the notificationExtras,
   * otherwise we search for a default set in braze.xml or don't set the color at all (and the system notification gray
   * default is used).
   * <p/>
   * Supported Lollipop+.
   */
  public static void setAccentColorIfPresentAndSupported(@NonNull NotificationCompat.Builder notificationBuilder, @NonNull BrazeNotificationPayload payload) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
      return;
    }

    if (payload.getAccentColor() != null) {
      BrazeLogger.d(TAG, "Using accent color for notification from extras bundle");
      notificationBuilder.setColor(payload.getAccentColor());
    } else {
      final BrazeConfigurationProvider configurationProvider = payload.getConfigurationProvider();
      if (configurationProvider != null) {
        BrazeLogger.d(TAG, "Using default accent color for notification");
        notificationBuilder.setColor(configurationProvider.getDefaultNotificationAccentColor());
      } else {
        BrazeLogger.d(TAG, "Cannot set default accent color for notification with null config provider");
      }
    }
  }

  /**
   * @deprecated Please use {@link #setCategoryIfPresentAndSupported(NotificationCompat.Builder, BrazeNotificationPayload)}
   */
  @Deprecated
  public static void setCategoryIfPresentAndSupported(NotificationCompat.Builder notificationBuilder, Bundle notificationExtras) {
    BrazeNotificationPayload payload = new BrazeNotificationPayload(notificationExtras);
    setCategoryIfPresentAndSupported(notificationBuilder, payload);
  }

  /**
   * Set category for devices on Lollipop and above. Category is one of the predefined notification
   * categories (see the CATEGORY_* constants in Notification)
   * that best describes a Notification. May be used by the system for ranking and filtering.
   * <p/>
   * Supported Lollipop+.
   */
  public static void setCategoryIfPresentAndSupported(@NonNull NotificationCompat.Builder notificationBuilder,
                                                      @NonNull BrazeNotificationPayload payload) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
      BrazeLogger.d(TAG, "Notification category not supported on this android version. "
          + "Not setting category for notification.");
      return;
    }

    if (payload.getNotificationCategory() != null) {
      BrazeLogger.d(TAG, "Setting category for notification");
      notificationBuilder.setCategory(payload.getNotificationCategory());
    } else {
      BrazeLogger.d(TAG, "Category not present in notification extras. Not setting category for notification.");
    }
  }

  /**
   * @deprecated Please use {@link #setVisibilityIfPresentAndSupported(NotificationCompat.Builder, BrazeNotificationPayload)}
   */
  @Deprecated
  public static void setVisibilityIfPresentAndSupported(NotificationCompat.Builder notificationBuilder, Bundle notificationExtras) {
    BrazeNotificationPayload payload = new BrazeNotificationPayload(notificationExtras);
    setVisibilityIfPresentAndSupported(notificationBuilder, payload);
  }

  /**
   * Set visibility for devices on Lollipop and above.
   * <p/>
   * Sphere of visibility of this notification, which affects how and when the SystemUI reveals the notification's presence and
   * contents in untrusted situations (namely, on the secure lockscreen). The default level, VISIBILITY_PRIVATE, behaves exactly
   * as notifications have always done on Android: The notification's icon and tickerText (if available) are shown in all situations,
   * but the contents are only available if the device is unlocked for the appropriate user. A more permissive policy can be expressed
   * by VISIBILITY_PUBLIC; such a notification can be read even in an "insecure" context (that is, above a secure lockscreen).
   * To modify the public version of this notification—for example, to redact some portions—see setPublicVersion(Notification).
   * Finally, a notification can be made VISIBILITY_SECRET, which will suppress its icon and ticker until the user has bypassed the lockscreen.
   * <p/>
   * Supported Lollipop+.
   */
  public static void setVisibilityIfPresentAndSupported(@NonNull NotificationCompat.Builder notificationBuilder, @NonNull BrazeNotificationPayload payload) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
      BrazeLogger.d(TAG, "Notification visibility not supported on this "
          + "android version. Not setting visibility for notification.");
      return;
    }

    if (payload.getNotificationVisibility() != null) {
      int visibility = payload.getNotificationVisibility();
      if (isValidNotificationVisibility(visibility)) {
        BrazeLogger.d(TAG, "Setting visibility for notification");
        notificationBuilder.setVisibility(visibility);
      } else {
        BrazeLogger.w(TAG, "Received invalid notification visibility " + visibility);
      }
    }
  }

  /**
   * @deprecated Please use {@link #setPublicVersionIfPresentAndSupported(NotificationCompat.Builder, BrazeNotificationPayload)}
   */
  @Deprecated
  public static void setPublicVersionIfPresentAndSupported(Context context,
                                                           BrazeConfigurationProvider configurationProvider,
                                                           NotificationCompat.Builder notificationBuilder,
                                                           Bundle notificationExtras) {
    BrazeNotificationPayload payload = new BrazeNotificationPayload(notificationExtras, null, context, configurationProvider);
    setPublicVersionIfPresentAndSupported(notificationBuilder, payload);
  }

  /**
   * Set the public version of the notification for notifications with private visibility.
   * <p/>
   * Supported Lollipop+.
   */
  public static void setPublicVersionIfPresentAndSupported(@NonNull NotificationCompat.Builder notificationBuilder, @NonNull BrazeNotificationPayload payload) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP
        || payload.getContext() == null
        || payload.getPublicNotificationExtras() == null) {
      return;
    }

    String notificationChannelId = BrazeNotificationUtils.getOrCreateNotificationChannelId(payload);
    if (notificationChannelId == null) {
      return;
    }

    Bundle publicNotificationExtras = JsonUtils.parseJsonObjectIntoBundle(payload.getPublicNotificationExtras());
    if (publicNotificationExtras == null) {
      return;
    }
    BrazeNotificationPayload publicPayload = new BrazeNotificationPayload(publicNotificationExtras,
        null,
        payload.getContext(),
        payload.getConfigurationProvider()
        );

    if (publicPayload.getConfigurationProvider() == null) {
      return;
    }
    NotificationCompat.Builder publicNotificationBuilder = new NotificationCompat.Builder(payload.getContext(), notificationChannelId);
    BrazeLogger.d(TAG, "Setting public version of notification");
    setContentIfPresent(publicNotificationBuilder, publicPayload);
    setTitleIfPresent(publicNotificationBuilder, publicPayload);
    setSummaryTextIfPresentAndSupported(publicNotificationBuilder, publicPayload);
    setSmallIcon(publicPayload.getConfigurationProvider(), publicNotificationBuilder);
    setAccentColorIfPresentAndSupported(publicNotificationBuilder, publicPayload);
    notificationBuilder.setPublicVersion(publicNotificationBuilder.build());
  }

  /**
   * Checks whether the given integer value is a valid Android notification visibility constant.
   */
  @TargetApi(Build.VERSION_CODES.LOLLIPOP)
  public static boolean isValidNotificationVisibility(int visibility) {
    return (visibility == Notification.VISIBILITY_SECRET || visibility == Notification.VISIBILITY_PRIVATE || visibility == Notification.VISIBILITY_PUBLIC);
  }

  /**
   * Logs a notification click with Braze if the extras passed down
   * indicate that they are from Braze and contain a campaign Id.
   * <p/>
   * A Braze session must be active to log a push notification.
   *
   * @param customContentString extra key value pairs in JSON format.
   */
  public static void logBaiduNotificationClick(Context context, String customContentString) {
    if (customContentString == null) {
      BrazeLogger.w(TAG, "customContentString was null. Doing nothing.");
      return;
    }
    try {
      JSONObject jsonExtras = new JSONObject(customContentString);
      String source = jsonExtras.optString(SOURCE_KEY, null);
      String campaignId = jsonExtras.optString(Constants.APPBOY_PUSH_CAMPAIGN_ID_KEY, null);
      if (source != null && source.equals(Constants.APPBOY) && campaignId != null) {
        Braze.getInstance(context).logPushNotificationOpened(campaignId);
      }
    } catch (Exception e) {
      BrazeLogger.e(TAG, "Caught an exception processing customContentString: " + customContentString, e);
    }
  }

  /**
   * Handles a request to cancel a push notification in the notification center. Called by FCM/ADM
   * receiver when a Braze cancel notification intent is received.
   * <p/>
   * Any existing notification in the notification center with the integer Id specified in the
   * "nid" field of the provided intent's extras is cancelled.
   * <p/>
   * If no Id is found, the defaut Braze notification Id is used.
   *
   * @param context
   * @param intent  the cancel notification intent
   */
  public static void handleCancelNotificationAction(Context context, Intent intent) {
    try {
      if (intent.hasExtra(Constants.APPBOY_PUSH_NOTIFICATION_ID)) {
        int notificationId = intent.getIntExtra(Constants.APPBOY_PUSH_NOTIFICATION_ID, Constants.APPBOY_DEFAULT_NOTIFICATION_ID);
        BrazeLogger.d(TAG, "Cancelling notification action with id: " + notificationId);
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.cancel(Constants.APPBOY_PUSH_NOTIFICATION_TAG, notificationId);
      }
    } catch (Exception e) {
      BrazeLogger.e(TAG, "Exception occurred handling cancel notification intent.", e);
    }
  }

  /**
   * Creates a request to cancel a push notification in the notification center.
   * <p/>
   * Sends an intent to the FCM/ADM receiver requesting Braze to cancel the notification with
   * the specified notification Id.
   * <p/>
   * See {@link #handleCancelNotificationAction}
   *
   * @param context
   * @param notificationId
   */
  public static void cancelNotification(Context context, int notificationId) {
    try {
      BrazeLogger.d(TAG, "Cancelling notification action with id: " + notificationId);
      Intent cancelNotificationIntent = new Intent(Constants.APPBOY_CANCEL_NOTIFICATION_ACTION).setClass(context, BrazeNotificationUtils.getNotificationReceiverClass());
      cancelNotificationIntent.setPackage(context.getPackageName());
      cancelNotificationIntent.putExtra(Constants.APPBOY_PUSH_NOTIFICATION_ID, notificationId);
      IntentUtils.addComponentAndSendBroadcast(context, cancelNotificationIntent);
    } catch (Exception e) {
      BrazeLogger.e(TAG, "Exception occurred attempting to cancel notification.", e);
    }
  }

  /**
   * @return the Class of the notification receiver used by this application.
   */
  public static Class<?> getNotificationReceiverClass() {
    if (Constants.isAmazonDevice()) {
      return BrazeAmazonDeviceMessagingReceiver.class;
    } else {
      return BrazePushReceiver.class;
    }
  }

  /**
   * Returns true if the bundle is from a push sent by Braze for uninstall tracking. Uninstall tracking push can be
   * ignored.
   *
   * @param notificationExtras A notificationExtras bundle that is passed with the push received intent when a GCM/ADM message is
   *                           received, and that Braze passes in the intent to registered receivers.
   */
  public static boolean isUninstallTrackingPush(Bundle notificationExtras) {
    try {
      if (notificationExtras != null) {
        // The ADM case where extras are flattened
        if (notificationExtras.containsKey(Constants.APPBOY_PUSH_UNINSTALL_TRACKING_KEY)) {
          return true;
        }
        // The FCM case where extras are in a separate bundle
        Bundle appboyExtras = notificationExtras.getBundle(Constants.APPBOY_PUSH_EXTRAS_KEY);
        if (appboyExtras != null) {
          return appboyExtras.containsKey(Constants.APPBOY_PUSH_UNINSTALL_TRACKING_KEY);
        }
      }
    } catch (Exception e) {
      BrazeLogger.e(TAG, "Failed to determine if push is uninstall tracking. Returning false.", e);
    }
    return false;
  }

  /**
   * Sets a notification channel on all Android O and above notifications. If not present in the extras, then a default notification channel is used.
   * <p>
   * To change the default notification channel name and description, please use {@link BrazeConfig.Builder#setDefaultNotificationChannelName(String)} and
   * {@link BrazeConfig.Builder#setDefaultNotificationChannelDescription(String)}.
   * <p>
   * The default notification channel uses the id {@link Constants#APPBOY_PUSH_DEFAULT_NOTIFICATION_CHANNEL_ID}.
   *
   * @deprecated The channel id should be set in {@link NotificationCompat.Builder#Builder(Context, String)}
   */
  @Deprecated
  @SuppressLint({"InlinedApi", "NewApi"})
  public static void setNotificationChannelIfSupported(Context context,
                                                       BrazeConfigurationProvider appConfigurationProvider,
                                                       NotificationCompat.Builder notificationBuilder,
                                                       Bundle notificationExtras) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      String channelId = getOrCreateNotificationChannelId(context, appConfigurationProvider, notificationExtras);
      notificationBuilder.setChannelId(channelId);
    }
  }

  /**
   * Returns the channel id for a valid {@link NotificationChannel}, creating one if necessary.
   * <p>
   * <ol>
   * <li>First, if {@link Constants.APPBOY_PUSH_NOTIFICATION_CHANNEL_ID_KEY} key is present in
   * {@literal notificationExtras}'s and is the id of a valid NotificationChannel, this id will
   * be returned.</li>
   * <li>Next, if the channel with id {@link Constants.APPBOY_PUSH_DEFAULT_NOTIFICATION_CHANNEL_ID} exists,
   * then {@link Constants.APPBOY_PUSH_DEFAULT_NOTIFICATION_CHANNEL_ID} will be returned.</li>
   * <li>Finally, if neither of the cases above is true, a channel with id {@link Constants.APPBOY_PUSH_DEFAULT_NOTIFICATION_CHANNEL_ID}
   * will be created and {@link Constants.APPBOY_PUSH_DEFAULT_NOTIFICATION_CHANNEL_ID} will be
   * returned.</li>
   * </ol>
   */
  @Nullable
  public static String getOrCreateNotificationChannelId(BrazeNotificationPayload payload) {
    String channelIdFromExtras = payload.getNotificationChannelId();
    String defaultChannelId = Constants.APPBOY_PUSH_DEFAULT_NOTIFICATION_CHANNEL_ID;

    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
      // If on Android < O, the channel does not really need to exist
      return channelIdFromExtras != null ? channelIdFromExtras : defaultChannelId;
    }

    final Context context = payload.getContext();
    if (context == null) {
      BrazeLogger.d(TAG, "BrazeNotificationPayload is missing a context");
      return null;
    }
    NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
    // First try to get the channel from the extras
    if (channelIdFromExtras != null) {
      if (notificationManager.getNotificationChannel(channelIdFromExtras) != null) {
        BrazeLogger.d(TAG, "Found notification channel in extras with id: " + channelIdFromExtras);
        return channelIdFromExtras;
      } else {
        BrazeLogger.d(TAG, "Notification channel from extras is invalid. No channel found with id: " + channelIdFromExtras);
      }
    }

    final BrazeConfigurationProvider appConfigurationProvider = payload.getConfigurationProvider();
    // If we get here, we need to use the default channel
    if (notificationManager.getNotificationChannel(defaultChannelId) == null) {
      // If the default doesn't exist, create it now
      BrazeLogger.d(TAG, "Appboy default notification channel does not exist on device; creating");
      NotificationChannel channel =
          new NotificationChannel(defaultChannelId,
              appConfigurationProvider.getDefaultNotificationChannelName(),
              NotificationManager.IMPORTANCE_DEFAULT);
      channel.setDescription(appConfigurationProvider.getDefaultNotificationChannelDescription());
      notificationManager.createNotificationChannel(channel);
    }

    return defaultChannelId;
  }

  /**
   * @deprecated Please use {@link #getOrCreateNotificationChannelId(BrazeNotificationPayload)}
   */
  @Nullable
  @Deprecated
  public static String getOrCreateNotificationChannelId(Context context,
                                                        BrazeConfigurationProvider appConfigurationProvider,
                                                        Bundle notificationExtras) {
    BrazeNotificationPayload payload = new BrazeNotificationPayload(notificationExtras, null, context, appConfigurationProvider);
    return getOrCreateNotificationChannelId(payload);
  }

  /**
   * @deprecated Please use {@link #setNotificationBadgeNumberIfPresent(NotificationCompat.Builder, BrazeNotificationPayload)}
   */
  @Deprecated
  public static void setNotificationBadgeNumberIfPresent(NotificationCompat.Builder notificationBuilder, Bundle notificationExtras) {
    BrazeNotificationPayload payload = new BrazeNotificationPayload(notificationExtras);
    setNotificationBadgeNumberIfPresent(notificationBuilder, payload);
  }

  /**
   * Sets the notification number, set via {@link NotificationCompat.Builder#setNumber(int)}.
   * On Android O, this number is used with notification badges.
   */
  public static void setNotificationBadgeNumberIfPresent(@NonNull NotificationCompat.Builder notificationBuilder,
                                                         @NonNull BrazeNotificationPayload payload) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
      BrazeLogger.d(TAG, "Notification badge number not supported on "
          + "this android version. Not setting badge number for notification.");
      return;
    }
    if (payload.getNotificationBadgeNumber() != null) {
      notificationBuilder.setNumber(payload.getNotificationBadgeNumber());
    }
  }

  /**
   * Handles a push story page click. Called by FCM/ADM receiver when an
   * Braze push story click intent is received.
   *
   * @param context Application context.
   * @param intent  The push story click intent.
   */
  public static void handlePushStoryPageClicked(Context context, Intent intent) {
    try {
      Braze.getInstance(context).logPushStoryPageClicked(intent.getStringExtra(Constants.APPBOY_CAMPAIGN_ID), intent.getStringExtra(Constants.APPBOY_STORY_PAGE_ID));
      String deepLink = intent.getStringExtra(Constants.APPBOY_ACTION_URI_KEY);
      if (!StringUtils.isNullOrBlank(deepLink)) {
        // Set the global deep link value to the correct action's deep link.
        intent.putExtra(Constants.APPBOY_PUSH_DEEP_LINK_KEY, intent.getStringExtra(Constants.APPBOY_ACTION_URI_KEY));
        String useWebviewString = intent.getStringExtra(Constants.APPBOY_ACTION_USE_WEBVIEW_KEY);
        if (!StringUtils.isNullOrBlank(useWebviewString)) {
          intent.putExtra(Constants.APPBOY_PUSH_OPEN_URI_IN_WEBVIEW_KEY, useWebviewString);
        }
      } else {
        // Otherwise, remove any existing deep links.
        intent.removeExtra(Constants.APPBOY_PUSH_DEEP_LINK_KEY);
      }
      BrazeNotificationUtils.sendNotificationOpenedBroadcast(context, intent);

      BrazeConfigurationProvider appConfigurationProvider = new BrazeConfigurationProvider(context);
      if (appConfigurationProvider.getDoesHandlePushDeepLinksAutomatically()) {
        BrazeNotificationUtils.routeUserWithNotificationOpenedIntent(context, intent);
      } else {
        BrazeLogger.i(TAG, "Not handling deep links automatically, skipping deep link handling");
      }
    } catch (Exception e) {
      BrazeLogger.e(TAG, "Caught exception while handling story click.", e);
    }
  }

  /**
   * Parses the notification bundle for any associated
   * ContentCards, if present. If found, the card object
   * is added to card storage.
   */
  public static void handleContentCardsSerializedCardIfPresent(BrazeNotificationPayload payload) {
    String contentCardData = payload.getContentCardSyncData();
    String contentCardDataUserId = payload.getContentCardSyncUserId();

    if (contentCardData != null && payload.getContext() != null) {
      BrazeLogger.d(TAG, "Push contains associated Content Cards card. User id: " + contentCardDataUserId + " Card data: " + contentCardData);
      BrazeInternal.addSerializedContentCardToStorage(payload.getContext(), contentCardData, contentCardDataUserId);
    }
  }

  /**
   * Checks if a push {@link Intent} contains extras found in a test push for in-app messages.
   *
   * @return True if this {@link Intent} is from an in-app message test send.
   */
  public static boolean isInAppMessageTestPush(@NonNull Intent intent) {
    return intent.hasExtra(Constants.APPBOY_PUSH_FETCH_TEST_TRIGGERS_KEY) && intent.getStringExtra(Constants.APPBOY_PUSH_FETCH_TEST_TRIGGERS_KEY).equals("true");
  }

  /**
   * Sends a push notification opened broadcast to the client broadcast receiver.
   *
   * @param context Application context
   * @param intent  The internal notification clicked intent constructed in
   *                {@link #setContentIntentIfPresent}
   */
  static void sendNotificationOpenedBroadcast(Context context, Intent intent) {
    BrazeLogger.d(TAG, "Sending notification opened broadcast");
    sendPushActionIntent(context, BrazeNotificationBroadcastType.OPENED, intent.getExtras());
  }

  /**
   * Returns an existing notification channel. The notification extras are first checked for a notification channel that exists. If not, then the default
   * Braze notification channel is returned if it exists. If neither exist on the device, then null is returned.
   * <p>
   * This method does not create a notification channel if a valid channel cannot be found.
   *
   * @param notificationExtras The extras that will be checked for a valid notification channel id.
   * @return A already created notification channel on the device, or null if one cannot be found.
   */
  @TargetApi(Build.VERSION_CODES.O)
  static NotificationChannel getValidNotificationChannel(NotificationManager notificationManager, Bundle notificationExtras) {
    if (notificationExtras == null) {
      BrazeLogger.d(TAG, "Notification extras bundle was null. Could not find a valid notification channel");
      return null;
    }
    String channelIdFromExtras = notificationExtras.getString(Constants.APPBOY_PUSH_NOTIFICATION_CHANNEL_ID_KEY, null);
    if (!StringUtils.isNullOrBlank(channelIdFromExtras)) {
      final NotificationChannel notificationChannel = notificationManager.getNotificationChannel(channelIdFromExtras);
      if (notificationChannel != null) {
        BrazeLogger.d(TAG, "Found notification channel in extras with id: " + channelIdFromExtras);
        return notificationChannel;
      } else {
        BrazeLogger.d(TAG, "Notification channel from extras is invalid, no channel found with id: " + channelIdFromExtras);
      }
    }

    final NotificationChannel defaultNotificationChannel = notificationManager.getNotificationChannel(Constants.APPBOY_PUSH_DEFAULT_NOTIFICATION_CHANNEL_ID);
    if (defaultNotificationChannel != null) {
      return defaultNotificationChannel;
    } else {
      BrazeLogger.d(TAG, "Appboy default notification channel does not exist on device.");
    }
    return null;
  }

  @TargetApi(Build.VERSION_CODES.O)
  private static int getNotificationChannelImportance(NotificationChannel notificationChannel) {
    return notificationChannel.getImportance();
  }

  /**
   * Creates a {@link PendingIntent} using the given action and extras specified.
   *
   * @param context            Application context
   * @param action             The action to set for the {@link PendingIntent}
   * @param notificationExtras The extras to set for the {@link PendingIntent}, if not null
   */
  private static PendingIntent getPushActionPendingIntent(Context context, String action, Bundle notificationExtras) {
    Intent pushActionIntent = new Intent(action).setClass(context, NotificationTrampolineActivity.class);
    if (notificationExtras != null) {
      pushActionIntent.putExtras(notificationExtras);
    }
    final int flags = PendingIntent.FLAG_ONE_SHOT | IntentUtils.getImmutablePendingIntentFlags();
    return PendingIntent.getActivity(context, IntentUtils.getRequestCode(), pushActionIntent, flags);
  }

  /**
   * Broadcasts an intent with the given action suffix. Will copy the extras from the input intent.
   *
   * @param context            Application context.
   * @param notificationExtras The extras to attach to the intent.
   */
  private static void sendPushActionIntent(Context context, BrazeNotificationBroadcastType broadcastType, Bundle notificationExtras) {
    // This is the original intent whose action
    // required a prefix of the app package name
    Intent appboySuffixedPushIntent;
    // This is the current intent whose action does
    // not require a prefix of the app package name
    Intent brazePushIntent;

    switch (broadcastType) {
      case OPENED:
        //noinspection deprecation
        appboySuffixedPushIntent = new Intent(context.getPackageName() + APPBOY_NOTIFICATION_OPENED_SUFFIX);
        brazePushIntent = new Intent(Constants.BRAZE_PUSH_INTENT_NOTIFICATION_OPENED).setPackage(context.getPackageName());
        break;
      case RECEIVED:
        //noinspection deprecation
        appboySuffixedPushIntent = new Intent(context.getPackageName() + APPBOY_NOTIFICATION_RECEIVED_SUFFIX);
        brazePushIntent = new Intent(Constants.BRAZE_PUSH_INTENT_NOTIFICATION_RECEIVED).setPackage(context.getPackageName());
        break;
      case DELETED:
        //noinspection deprecation
        appboySuffixedPushIntent = new Intent(context.getPackageName() + APPBOY_NOTIFICATION_DELETED_SUFFIX);
        brazePushIntent = new Intent(Constants.BRAZE_PUSH_INTENT_NOTIFICATION_DELETED).setPackage(context.getPackageName());
        break;
      default:
        BrazeLogger.d(TAG, "Got unknown broadcast type");
        return;
    }

    BrazeLogger.v(TAG, "Sending original Appboy broadcast receiver intent for " + broadcastType);
    sendPushActionIntent(context, appboySuffixedPushIntent, notificationExtras);
    BrazeLogger.v(TAG, "Sending Braze broadcast receiver intent for " + broadcastType);
    sendPushActionIntent(context, brazePushIntent, notificationExtras);
  }

  private static void sendPushActionIntent(@NonNull Context context, @NonNull Intent pushIntent, @Nullable Bundle notificationExtras) {
    BrazeLogger.v(TAG, "Sending push action intent: " + pushIntent);
    if (notificationExtras != null) {
      pushIntent.putExtras(notificationExtras);
    }
    IntentUtils.addComponentAndSendBroadcast(context, pushIntent);
  }
}
