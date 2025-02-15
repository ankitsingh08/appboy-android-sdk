package com.braze.ui.support;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.view.DisplayCutoutCompat;
import androidx.core.view.WindowInsetsCompat;

import com.braze.enums.inappmessage.Orientation;
import com.braze.support.BrazeLogger;

public class ViewUtils {
  private static final String TAG = BrazeLogger.getBrazeLogTag(ViewUtils.class);
  private static final int TABLET_SMALLEST_WIDTH_DP = 600;

  public static void removeViewFromParent(View view) {
    if (view != null) {
      if (view.getParent() instanceof ViewGroup) {
        final ViewGroup parent = (ViewGroup) view.getParent();
        parent.removeView(view);
        BrazeLogger.d(TAG, "Removed view: " + view + "\nfrom parent: " + parent);
      }
    }
  }

  public static void setFocusableInTouchModeAndRequestFocus(View view) {
    try {
      view.setFocusableInTouchMode(true);
      view.requestFocus();
    } catch (Exception e) {
      BrazeLogger.e(TAG, "Caught exception while setting view to focusable in touch mode and requesting focus.", e);
    }
  }

  public static double convertDpToPixels(Context context, double valueInDp) {
    double density = context.getResources().getDisplayMetrics().density;
    return valueInDp * density;
  }

  public static boolean isRunningOnTablet(Activity activity) {
    return activity.getResources().getConfiguration().smallestScreenWidthDp
        >= TABLET_SMALLEST_WIDTH_DP;
  }

  /**
   * Safely calls {@link Activity#setRequestedOrientation(int)}
   */
  public static void setActivityRequestedOrientation(@NonNull Activity activity, int requestedOrientation) {
    try {
      activity.setRequestedOrientation(requestedOrientation);
    } catch (Exception e) {
      BrazeLogger.e(TAG, "Failed to set requested orientation " + requestedOrientation + " for activity class: " + activity.getLocalClassName(), e);
    }
  }

  public static void setHeightOnViewLayoutParams(View view, int height) {
    if (view == null) {
      BrazeLogger.w(TAG, "Cannot set height on null view.");
      return;
    }
    ViewGroup.LayoutParams layoutParams = view.getLayoutParams();
    layoutParams.height = height;
    view.setLayoutParams(layoutParams);
  }

  /**
   * Checks if the device is in night mode. In Android 10, this corresponds
   * to "Dark Theme" being enabled by the user.
   */
  public static boolean isDeviceInNightMode(Context context) {
    int nightModeFlags = context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
    return nightModeFlags == Configuration.UI_MODE_NIGHT_YES;
  }

  /**
   * @return Whether the current screen orientation (e.g. {@link Configuration#ORIENTATION_LANDSCAPE})
   * matches the preferred orientation (e.g. {@link Orientation#LANDSCAPE}.
   */
  public static boolean isCurrentOrientationValid(int currentScreenOrientation, Orientation preferredOrientation) {
    if (currentScreenOrientation == Configuration.ORIENTATION_LANDSCAPE
        && preferredOrientation == Orientation.LANDSCAPE) {
      BrazeLogger.d(TAG, "Current and preferred orientation are landscape.");
      return true;
    } else if (currentScreenOrientation == Configuration.ORIENTATION_PORTRAIT
        && preferredOrientation == Orientation.PORTRAIT) {
      BrazeLogger.d(TAG, "Current and preferred orientation are portrait.");
      return true;
    } else {
      BrazeLogger.d(TAG, "Current orientation " + currentScreenOrientation
          + " and preferred orientation " + preferredOrientation + " don't match");
      return false;
    }
  }

  /**
   * @return The maximum of the display cutout left inset and the system window left inset.
   */
  public static int getMaxSafeLeftInset(@NonNull WindowInsetsCompat windowInsets) {
    if (windowInsets.getDisplayCutout() != null) {
      final DisplayCutoutCompat displayCutout = windowInsets.getDisplayCutout();
      return Math.max(displayCutout.getSafeInsetLeft(), windowInsets.getInsets(WindowInsetsCompat.Type.systemBars()).left);
    } else {
      // The max inset is just the system value since the display cutout does not exist
      return windowInsets.getInsets(WindowInsetsCompat.Type.systemBars()).left;
    }
  }

  /**
   * @return The maximum of the display cutout right inset and the system window right inset.
   */
  public static int getMaxSafeRightInset(@NonNull WindowInsetsCompat windowInsets) {
    if (windowInsets.getDisplayCutout() != null) {
      final DisplayCutoutCompat displayCutout = windowInsets.getDisplayCutout();
      return Math.max(displayCutout.getSafeInsetRight(), windowInsets.getInsets(WindowInsetsCompat.Type.systemBars()).right);
    } else {
      // The max inset is just the system value since the display cutout does not exist
      return windowInsets.getInsets(WindowInsetsCompat.Type.systemBars()).right;
    }
  }

  /**
   * @return The maximum of the display cutout top inset and the system window top inset.
   */
  public static int getMaxSafeTopInset(@NonNull WindowInsetsCompat windowInsets) {
    if (windowInsets.getDisplayCutout() != null) {
      final DisplayCutoutCompat displayCutout = windowInsets.getDisplayCutout();
      return Math.max(displayCutout.getSafeInsetTop(), windowInsets.getInsets(WindowInsetsCompat.Type.systemBars()).top);
    } else {
      // The max inset is just the system value since the display cutout does not exist
      return windowInsets.getInsets(WindowInsetsCompat.Type.systemBars()).top;
    }
  }

  /**
   * @return The maximum of the display cutout bottom inset and the system window bottom inset.
   */
  public static int getMaxSafeBottomInset(@NonNull WindowInsetsCompat windowInsets) {
    if (windowInsets.getDisplayCutout() != null) {
      final DisplayCutoutCompat displayCutout = windowInsets.getDisplayCutout();
      return Math.max(displayCutout.getSafeInsetBottom(), windowInsets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom);
    } else {
      // The max inset is just the system value since the display cutout does not exist
      return windowInsets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom;
    }
  }

  /**
   * Detects if this device is currently in touch mode given a {@link View}.
   */
  public static boolean isDeviceNotInTouchMode(View view) {
    return !view.isInTouchMode();
  }
}
