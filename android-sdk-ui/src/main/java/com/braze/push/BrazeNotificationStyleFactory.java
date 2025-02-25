package com.braze.push;

import static com.braze.IBrazeDeeplinkHandler.IntentFlagPurpose.NOTIFICATION_PUSH_STORY_PAGE_CLICK;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.RemoteViews;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.VisibleForTesting;
import androidx.core.app.NotificationCompat;

import com.appboy.Constants;
import com.appboy.models.push.BrazeNotificationPayload;
import com.appboy.ui.R;
import com.braze.Braze;
import com.braze.configuration.BrazeConfigurationProvider;
import com.braze.enums.BrazeDateFormat;
import com.braze.enums.BrazeViewBounds;
import com.braze.push.support.HtmlUtils;
import com.braze.support.BrazeImageUtils;
import com.braze.support.BrazeLogger;
import com.braze.support.DateTimeUtils;
import com.braze.support.IntentUtils;
import com.braze.support.StringUtils;
import com.braze.ui.BrazeDeeplinkHandler;

import java.util.List;
import java.util.Map;

public class BrazeNotificationStyleFactory {
  private static final String TAG = BrazeLogger.getBrazeLogTag(BrazeNotificationStyleFactory.class);
  /**
   * BigPictureHeight is set in
   * https://android.googlesource.com/platform/frameworks/base/+/6387d2f6dae27ba6e8481883325adad96d3010f4/core/res/res/layout/notification_template_big_picture.xml.
   */
  private static final int BIG_PICTURE_STYLE_IMAGE_HEIGHT = 192;
  private static final String STORY_SET_GRAVITY = "setGravity";
  private static final String STORY_SET_VISIBILITY = "setVisibility";

  /**
   * Sets the style of the notification if supported.
   * <p/>
   * If there is an image url found in the extras payload and the image can be downloaded, then
   * use the android BigPictureStyle as the notification. Else, use the BigTextStyle instead.
   * <p/>
   * Supported JellyBean+.
   */
  public static void setStyleIfSupported(@NonNull NotificationCompat.Builder notificationBuilder,
                                         @NonNull BrazeNotificationPayload payload) {
    BrazeLogger.d(TAG, "Setting style for notification");
    NotificationCompat.Style style = getNotificationStyle(notificationBuilder, payload);

    if (style != null && !(style instanceof NoOpSentinelStyle)) {
      notificationBuilder.setStyle(style);
    }
  }

  /**
   * @deprecated Please use {@link #getNotificationStyle(NotificationCompat.Builder, BrazeNotificationPayload)}
   */
  @Deprecated
  public static NotificationCompat.Style getBigNotificationStyle(Context context,
                                                                 Bundle notificationExtras,
                                                                 Bundle appboyExtras,
                                                                 NotificationCompat.Builder notificationBuilder) {
    BrazeNotificationPayload payload = new BrazeNotificationPayload(notificationExtras, null, context, null);
    return getNotificationStyle(notificationBuilder, payload);
  }

  /**
   * Returns a big style NotificationCompat.Style. If an image is present, this will be a BigPictureStyle,
   * otherwise it will be a BigTextStyle.
   */
  @Nullable
  public static NotificationCompat.Style getNotificationStyle(@NonNull NotificationCompat.Builder notificationBuilder,
                                                              @NonNull BrazeNotificationPayload payload) {
    NotificationCompat.Style style = null;

    if (payload.isPushStory() && payload.getContext() != null) {
      BrazeLogger.d(TAG, "Rendering push notification with DecoratedCustomViewStyle (Story)");
      style = getStoryStyle(notificationBuilder, payload);
    } else if (payload.isConversationalPush() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
      BrazeLogger.d(TAG, "Rendering conversational push");
      style = getConversationalPushStyle(notificationBuilder, payload);
    } else if (payload.getBigImageUrl() != null) {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && payload.isInlineImagePush()) {
        BrazeLogger.d(TAG, "Rendering push notification with custom inline image style");
        style = getInlineImageStyle(payload, notificationBuilder);
      } else {
        BrazeLogger.d(TAG, "Rendering push notification with BigPictureStyle");
        style = getBigPictureNotificationStyle(payload);
      }
    }

    // Default style is BigTextStyle.
    if (style == null) {
      BrazeLogger.d(TAG, "Rendering push notification with BigTextStyle");
      style = getBigTextNotificationStyle(payload);
    }

    return style;
  }

  /**
   * @deprecated Please use {@link #getBigTextNotificationStyle(BrazeNotificationPayload)}
   */
  @Deprecated
  public static NotificationCompat.BigTextStyle getBigTextNotificationStyle(BrazeConfigurationProvider brazeConfigurationProvider,
                                                                            Bundle notificationExtras) {
    BrazeNotificationPayload payload = new BrazeNotificationPayload(notificationExtras, null, null, brazeConfigurationProvider);
    return getBigTextNotificationStyle(payload);
  }

  /**
   * Returns a BigTextStyle notification style initialized with the content, big title, and big summary
   * specified in the notificationExtras and appboyExtras bundles.
   * <p/>
   * If summary text exists, it will be shown in the expanded notification view.
   * If a title exists, it will override the default in expanded notification view.
   */
  public static NotificationCompat.BigTextStyle getBigTextNotificationStyle(@NonNull BrazeNotificationPayload payload) {
    NotificationCompat.BigTextStyle bigTextNotificationStyle = new NotificationCompat.BigTextStyle();
    final BrazeConfigurationProvider appConfigProvider = payload.getConfigurationProvider();

    bigTextNotificationStyle.bigText(HtmlUtils.getHtmlSpannedTextIfEnabled(appConfigProvider, payload.getContentText()));
    if (payload.getBigSummaryText() != null) {
      bigTextNotificationStyle.setSummaryText(HtmlUtils.getHtmlSpannedTextIfEnabled(appConfigProvider, payload.getBigSummaryText()));
    }
    if (payload.getBigTitleText() != null) {
      bigTextNotificationStyle.setBigContentTitle(HtmlUtils.getHtmlSpannedTextIfEnabled(appConfigProvider, payload.getBigTitleText()));
    }

    return bigTextNotificationStyle;
  }

  /**
   * @deprecated Please use {@link #getStoryStyle(NotificationCompat.Builder, BrazeNotificationPayload)}
   */
  @Deprecated
  public static NotificationCompat.DecoratedCustomViewStyle getStoryStyle(Context context,
                                                                          Bundle notificationExtras,
                                                                          Bundle appboyExtras,
                                                                          NotificationCompat.Builder notificationBuilder) {
    BrazeNotificationPayload payload = new BrazeNotificationPayload(notificationExtras, null, context, new BrazeConfigurationProvider(context));
    return getStoryStyle(notificationBuilder, payload);
  }

  /**
   * Returns a {@link androidx.core.app.NotificationCompat.DecoratedCustomViewStyle} for push story.
   *
   * @param notificationBuilder Notification builder.
   * @param payload BrazeNotificationPayload
   * @return a {@link androidx.core.app.NotificationCompat.DecoratedCustomViewStyle} that describes the appearance of the push story.
   */
  public static NotificationCompat.DecoratedCustomViewStyle getStoryStyle(@NonNull NotificationCompat.Builder notificationBuilder,
                                                                          @NonNull BrazeNotificationPayload payload) {
    final Context context = payload.getContext();
    if (context == null) {
      BrazeLogger.d(TAG, "Push story page cannot render without a context");
      return null;
    }
    final List<BrazeNotificationPayload.PushStoryPage> pushStoryPages = payload.getPushStoryPages();
    int pageIndex = payload.getPushStoryPageIndex();
    BrazeNotificationPayload.PushStoryPage pushStoryPage = pushStoryPages.get(pageIndex);
    RemoteViews storyView = new RemoteViews(context.getPackageName(), R.layout.com_braze_push_story_one_image);
    if (!populatePushStoryPage(storyView, payload, pushStoryPage)) {
      BrazeLogger.w(TAG, "Push story page was not populated correctly. Not using DecoratedCustomViewStyle.");
      return null;
    }

    final Bundle notificationExtras = payload.getNotificationExtras();
    NotificationCompat.DecoratedCustomViewStyle style = new NotificationCompat.DecoratedCustomViewStyle();
    final int numPages = pushStoryPages.size();

    PendingIntent previousButtonPendingIntent = createStoryTraversedPendingIntent(context, notificationExtras, (pageIndex - 1 + numPages) % numPages);
    storyView.setOnClickPendingIntent(R.id.com_braze_story_button_previous, previousButtonPendingIntent);
    PendingIntent nextButtonPendingIntent = createStoryTraversedPendingIntent(context, notificationExtras, (pageIndex + 1) % numPages);
    storyView.setOnClickPendingIntent(R.id.com_braze_story_button_next, nextButtonPendingIntent);
    notificationBuilder.setCustomBigContentView(storyView);

    // Ensure clicks on the story don't vibrate or make noise after the story first appears
    notificationBuilder.setOnlyAlertOnce(true);
    return style;
  }

  /**
   * This method sets a fully custom {@link android.widget.RemoteViews.RemoteView} to render the
   * notification.
   * <p>
   * In the successful case, a {@link NoOpSentinelStyle} is returned.
   * In the failure case (image bitmap is null, system information not found, etc.), a
   * null style is returned.
   */
  @RequiresApi(api = Build.VERSION_CODES.M)
  @Nullable
  public static NotificationCompat.Style getInlineImageStyle(@NonNull BrazeNotificationPayload payload,
                                                             @NonNull NotificationCompat.Builder notificationBuilder) {
    final Context context = payload.getContext();
    if (context == null) {
      BrazeLogger.d(TAG, "Inline Image Push cannot render without a context");
      return null;
    }

    final String imageUrl = payload.getBigImageUrl();
    if (StringUtils.isNullOrBlank(imageUrl)) {
      BrazeLogger.d(TAG, "Inline Image Push image url invalid");
      return null;
    }
    final Bundle notificationExtras = payload.getNotificationExtras();

    // Set the image
    Bitmap largeNotificationBitmap = Braze.getInstance(context).getImageLoader()
        .getPushBitmapFromUrl(context, notificationExtras, imageUrl, BrazeViewBounds.NOTIFICATION_INLINE_PUSH_IMAGE);
    if (largeNotificationBitmap == null) {
      BrazeLogger.d(TAG, "Inline Image Push failed to get image bitmap");
      return null;
    }
    final boolean isNotificationSpaceConstrained = isRemoteViewNotificationAvailableSpaceConstrained(context);
    RemoteViews remoteView = new RemoteViews(context.getPackageName(),
        isNotificationSpaceConstrained ? R.layout.com_braze_push_inline_image_constrained : R.layout.com_braze_notification_inline_image);
    BrazeConfigurationProvider configurationProvider = new BrazeConfigurationProvider(context);

    // Set the app icon drawable
    final Icon appIcon = Icon.createWithResource(context, configurationProvider.getSmallNotificationIconResourceId());
    if (payload.getAccentColor() != null) {
      appIcon.setTint(payload.getAccentColor());
    }
    remoteView.setImageViewIcon(R.id.com_braze_inline_image_push_app_icon, appIcon);

    // Set the app name
    final PackageManager packageManager = context.getPackageManager();
    ApplicationInfo applicationInfo;
    try {
      applicationInfo = packageManager.getApplicationInfo(context.getPackageName(), 0);
    } catch (final PackageManager.NameNotFoundException e) {
      BrazeLogger.d(TAG, "Inline Image Push application info was null");
      return null;
    }

    final String applicationName = (String) packageManager.getApplicationLabel(applicationInfo);
    final CharSequence htmlSpannedAppName = HtmlUtils.getHtmlSpannedTextIfEnabled(configurationProvider, applicationName);
    remoteView.setTextViewText(R.id.com_braze_inline_image_push_app_name_text, htmlSpannedAppName);

    // Set the current time
    remoteView.setTextViewText(R.id.com_braze_inline_image_push_time_text, DateTimeUtils.formatDateNow(BrazeDateFormat.CLOCK_12_HOUR));

    // Set the text area title
    String title = notificationExtras.getString(Constants.APPBOY_PUSH_TITLE_KEY);
    remoteView.setTextViewText(R.id.com_braze_inline_image_push_title_text, HtmlUtils.getHtmlSpannedTextIfEnabled(configurationProvider, title));

    // Set the text area content
    String content = notificationExtras.getString(Constants.APPBOY_PUSH_CONTENT_KEY);
    remoteView.setTextViewText(R.id.com_braze_inline_image_push_content_text, HtmlUtils.getHtmlSpannedTextIfEnabled(configurationProvider, content));
    notificationBuilder.setCustomContentView(remoteView);

    if (isNotificationSpaceConstrained) {
      // On Android 12 and above, the custom image view we had
      // just can't render the same way so we'll fake it with a large icon
      notificationBuilder.setLargeIcon(largeNotificationBitmap);
      return new NotificationCompat.DecoratedCustomViewStyle();
    } else {
      remoteView.setImageViewBitmap(R.id.com_braze_inline_image_push_side_image, largeNotificationBitmap);
      // Since this is entirely custom, no decorated
      // style is returned to the system.
      return new NoOpSentinelStyle();
    }
  }

  /**
   * @deprecated Please use {@link #getBigPictureNotificationStyle(BrazeNotificationPayload)}
   */
  @Deprecated
  public static NotificationCompat.BigPictureStyle getBigPictureNotificationStyle(Context context,
                                                                                  Bundle notificationExtras,
                                                                                  Bundle appboyExtras) {
    BrazeNotificationPayload payload = new BrazeNotificationPayload(notificationExtras, null, context, null);
    return getBigPictureNotificationStyle(payload);
  }

  /**
   * Returns a BigPictureStyle notification style initialized with the bitmap, big title, and big summary
   * specified in the notificationExtras and appboyExtras bundles.
   * <p/>
   * If summary text exists, it will be shown in the expanded notification view.
   * If a title exists, it will override the default in expanded notification view.
   */
  public static NotificationCompat.BigPictureStyle getBigPictureNotificationStyle(@NonNull BrazeNotificationPayload payload) {
    final Context context = payload.getContext();
    if (context == null) {
      return null;
    }

    final String imageUrl = payload.getBigImageUrl();
    if (StringUtils.isNullOrBlank(imageUrl)) {
      return null;
    }

    final Bundle notificationExtras = payload.getNotificationExtras();
    Bitmap imageBitmap = Braze.getInstance(context).getImageLoader()
        .getPushBitmapFromUrl(context,
            notificationExtras,
            imageUrl,
            BrazeViewBounds.NOTIFICATION_EXPANDED_IMAGE);
    if (imageBitmap == null) {
      BrazeLogger.d(TAG, "Failed to download image bitmap for big picture notification style. Url: " + imageUrl);
      return null;
    }

    try {
      // Images get cropped differently across different screen sizes
      // Here we grab the current screen size and scale the image to fit correctly
      // Note: if the height is greater than the width it's going to look poor, so we might
      // as well let the system modify it and not complicate things by trying to smoosh it here.
      if (imageBitmap.getWidth() > imageBitmap.getHeight()) {
        int bigPictureHeightPixels = BrazeImageUtils.getPixelsFromDensityAndDp(BrazeImageUtils.getDensityDpi(context), BIG_PICTURE_STYLE_IMAGE_HEIGHT);
        // 2:1 aspect ratio
        int bigPictureWidthPixels = 2 * bigPictureHeightPixels;
        final int displayWidthPixels = BrazeImageUtils.getDisplayWidthPixels(context);
        if (bigPictureWidthPixels > displayWidthPixels) {
          bigPictureWidthPixels = displayWidthPixels;
        }

        try {
          imageBitmap = Bitmap.createScaledBitmap(imageBitmap, bigPictureWidthPixels, bigPictureHeightPixels, true);
        } catch (Exception e) {
          BrazeLogger.e(TAG, "Failed to scale image bitmap, using original.", e);
        }
      }
      if (imageBitmap == null) {
        BrazeLogger.i(TAG, "Bitmap download failed for push notification. No image will be included with the notification.");
        return null;
      }

      NotificationCompat.BigPictureStyle bigPictureNotificationStyle = new NotificationCompat.BigPictureStyle();
      bigPictureNotificationStyle.bigPicture(imageBitmap);
      setBigPictureSummaryAndTitle(bigPictureNotificationStyle, payload);

      return bigPictureNotificationStyle;
    } catch (Exception e) {
      BrazeLogger.e(TAG, "Failed to create Big Picture Style.", e);
      return null;
    }
  }

  public static NotificationCompat.MessagingStyle getConversationalPushStyle(@NonNull NotificationCompat.Builder notificationBuilder,
                                                                             @NonNull BrazeNotificationPayload payload) {
    try {
      final Map<String, BrazeNotificationPayload.ConversationPerson> conversationPersonMap = payload.getConversationPersonMap();
      BrazeNotificationPayload.ConversationPerson replyPerson = conversationPersonMap.get(payload.getConversationReplyPersonId());
      if (replyPerson == null) {
        BrazeLogger.d(TAG, "Reply person does not exist in mapping. Not rendering a style");
        return null;
      }

      NotificationCompat.MessagingStyle style = new NotificationCompat.MessagingStyle(replyPerson.getPerson());
      for (BrazeNotificationPayload.ConversationMessage message : payload.getConversationMessages()) {
        BrazeNotificationPayload.ConversationPerson person = conversationPersonMap.get(message.getPersonId());
        if (person == null) {
          BrazeLogger.d(TAG, "Message person does not exist in mapping. Not rendering a style. " + message);
          return null;
        }
        style.addMessage(message.getMessage(), message.getTimestamp(), person.getPerson());
      }
      style.setGroupConversation(conversationPersonMap.size() > 1);
      notificationBuilder.setShortcutId(payload.getConversationShortcutId());
      return style;
    } catch (Exception e) {
      BrazeLogger.e(TAG, "Failed to create conversation push style. Returning null.", e);
      return null;
    }
  }

  private static PendingIntent createStoryPageClickedPendingIntent(@NonNull Context context,
                                                                   @NonNull BrazeNotificationPayload.PushStoryPage pushStoryPage) {
    Intent storyClickedIntent = new Intent(Constants.APPBOY_STORY_CLICKED_ACTION)
        .setClass(context, NotificationTrampolineActivity.class);
    storyClickedIntent
        .setFlags(storyClickedIntent.getFlags() | BrazeDeeplinkHandler.getInstance().getIntentFlags(NOTIFICATION_PUSH_STORY_PAGE_CLICK));
    storyClickedIntent.putExtra(Constants.APPBOY_ACTION_URI_KEY, pushStoryPage.getDeeplink());
    storyClickedIntent.putExtra(Constants.APPBOY_ACTION_USE_WEBVIEW_KEY, pushStoryPage.getUseWebview());
    storyClickedIntent.putExtra(Constants.APPBOY_STORY_PAGE_ID, pushStoryPage.getStoryPageId());
    storyClickedIntent.putExtra(Constants.APPBOY_CAMPAIGN_ID, pushStoryPage.getCampaignId());
    return PendingIntent.getActivity(context, IntentUtils.getRequestCode(), storyClickedIntent, IntentUtils.getImmutablePendingIntentFlags());
  }

  private static PendingIntent createStoryTraversedPendingIntent(Context context, Bundle notificationExtras, int pageIndex) {
    Intent storyNextClickedIntent = new Intent(Constants.APPBOY_STORY_TRAVERSE_CLICKED_ACTION)
        .setClass(context, BrazeNotificationUtils.getNotificationReceiverClass());
    if (notificationExtras != null) {
      notificationExtras.putInt(Constants.APPBOY_STORY_INDEX_KEY, pageIndex);
      storyNextClickedIntent.putExtras(notificationExtras);
    }
    final int flags = PendingIntent.FLAG_ONE_SHOT | IntentUtils.getImmutablePendingIntentFlags();
    return PendingIntent.getBroadcast(context,
        IntentUtils.getRequestCode(),
        storyNextClickedIntent,
        flags);
  }

  /**
   * Adds the appropriate image, title/subtitle, and PendingIntents to the story page.
   *
   * @param view               The push story remoteView, as instantiated in the getStoryStyle method.
   * @param payload            BrazeNotificationPayload.
   * @param pushStoryPage      PushStoryPage
   * @return True if the push story page was populated correctly.
   */
  private static boolean populatePushStoryPage(@NonNull RemoteViews view,
                                               @NonNull BrazeNotificationPayload payload,
                                               @NonNull BrazeNotificationPayload.PushStoryPage pushStoryPage) {
    final Context context = payload.getContext();
    if (context == null) {
      BrazeLogger.d(TAG, "Push story page cannot render without a context");
      return false;
    }
    BrazeConfigurationProvider configurationProvider = payload.getConfigurationProvider();
    if (configurationProvider == null) {
      BrazeLogger.d(TAG, "Push story page cannot render without a configuration provider");
      return false;
    }

    final String bitmapUrl = pushStoryPage.getBitmapUrl();
    if (StringUtils.isNullOrBlank(bitmapUrl)) {
      BrazeLogger.d(TAG, "Push story page image url invalid");
      return false;
    }
    final Bundle notificationExtras = payload.getNotificationExtras();

    // Set up bitmap url
    Bitmap largeNotificationBitmap = Braze.getInstance(context).getImageLoader()
        .getPushBitmapFromUrl(context, notificationExtras, bitmapUrl, BrazeViewBounds.NOTIFICATION_ONE_IMAGE_STORY);
    if (largeNotificationBitmap == null) {
      return false;
    }
    view.setImageViewBitmap(R.id.com_braze_story_image_view, largeNotificationBitmap);

    // Set up title
    final String pageTitle = pushStoryPage.getTitle();

    // If the title is null or blank, the visibility of the container becomes GONE.
    if (!StringUtils.isNullOrBlank(pageTitle)) {
      final CharSequence pageTitleText = HtmlUtils.getHtmlSpannedTextIfEnabled(configurationProvider, pageTitle);
      view.setTextViewText(R.id.com_braze_story_text_view, pageTitleText);
      int titleGravity = pushStoryPage.getTitleGravity();
      view.setInt(R.id.com_braze_story_text_view_container, STORY_SET_GRAVITY, titleGravity);
    } else {
      view.setInt(R.id.com_braze_story_text_view_container, STORY_SET_VISIBILITY, View.GONE);
    }

    // Set up subtitle
    final String pageSubtitle = pushStoryPage.getSubtitle();

    // If the subtitle is null or blank, the visibility of the container becomes GONE.
    if (!StringUtils.isNullOrBlank(pageSubtitle)) {
      final CharSequence pageSubtitleText = HtmlUtils.getHtmlSpannedTextIfEnabled(configurationProvider, pageSubtitle);
      view.setTextViewText(R.id.com_braze_story_text_view_small, pageSubtitleText);
      int subtitleGravity = pushStoryPage.getSubtitleGravity();
      view.setInt(R.id.com_braze_story_text_view_small_container, STORY_SET_GRAVITY, subtitleGravity);
    } else {
      view.setInt(R.id.com_braze_story_text_view_small_container, STORY_SET_VISIBILITY, View.GONE);
    }

    // Set up story clicked intent
    PendingIntent storyClickedPendingIntent = createStoryPageClickedPendingIntent(context, pushStoryPage);
    view.setOnClickPendingIntent(R.id.com_braze_story_relative_layout, storyClickedPendingIntent);
    return true;
  }

  @VisibleForTesting
  static void setBigPictureSummaryAndTitle(NotificationCompat.BigPictureStyle bigPictureNotificationStyle, BrazeNotificationPayload payload) {
    final BrazeConfigurationProvider appConfigProvider = payload.getConfigurationProvider();
    if (payload.getBigSummaryText() != null) {
      bigPictureNotificationStyle.setSummaryText(HtmlUtils.getHtmlSpannedTextIfEnabled(appConfigProvider, payload.getBigSummaryText()));
    }
    if (payload.getBigTitleText() != null) {
      bigPictureNotificationStyle.setBigContentTitle(HtmlUtils.getHtmlSpannedTextIfEnabled(appConfigProvider, payload.getBigTitleText()));
    }

    // If summary is null (which we set to the subtext in setSummaryTextIfPresentAndSupported in BrazeNotificationUtils)
    // and bigSummary is null, set the summary to the message. Without this, the message would be blank in expanded mode.
    if (payload.getSummaryText() == null && payload.getBigSummaryText() == null) {
      bigPictureNotificationStyle.setSummaryText(HtmlUtils.getHtmlSpannedTextIfEnabled(appConfigProvider, payload.getContentText()));
    }
  }

  /**
   * A sentinel value used solely to denote that a style
   * should not be set on the notification builder.
   * <p>
   * Example usage would be a fully custom {@link RemoteViews}
   * that has handled rendering notification view without the
   * use of a system style. Returning null in that scenario
   * would lead to a lack of information as to whether that
   * custom rendering failed.
   */
  private static class NoOpSentinelStyle extends NotificationCompat.Style {

  }

  /**
   * On an Android 12 device and app targeting Android 12, the available space to
   * a {@link RemoteViews} notification is significantly reduced.
   */
  private static boolean isRemoteViewNotificationAvailableSpaceConstrained(Context context) {
    // Check that the device is on Android 12+ && the app is targeting Android 12+
    return (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
        && context.getApplicationContext().getApplicationInfo().targetSdkVersion >= Build.VERSION_CODES.S;
  }
}
