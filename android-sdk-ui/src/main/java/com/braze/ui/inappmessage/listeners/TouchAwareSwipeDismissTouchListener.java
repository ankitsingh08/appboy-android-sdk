package com.braze.ui.inappmessage.listeners;

import android.view.MotionEvent;
import android.view.View;

/**
 * Adds touch events to the SwipeDismissTouchListener.
 */
public class TouchAwareSwipeDismissTouchListener extends SwipeDismissTouchListener {
  private ITouchListener mTouchListener;

  public interface ITouchListener {
    void onTouchStartedOrContinued();

    void onTouchEnded();
  }

  public TouchAwareSwipeDismissTouchListener(View view, DismissCallbacks callbacks) {
    this(view, null, callbacks);
  }

  public TouchAwareSwipeDismissTouchListener(View view, Object token, DismissCallbacks callbacks) {
    super(view, token, callbacks);
  }

  public void setTouchListener(ITouchListener touchListener) {
    mTouchListener = touchListener;
  }

  @Override
  public boolean onTouch(View view, MotionEvent motionEvent) {
    switch (motionEvent.getAction()) {
      case MotionEvent.ACTION_DOWN:
        if (mTouchListener != null) {
          mTouchListener.onTouchStartedOrContinued();
        }
        break;
      case MotionEvent.ACTION_UP:
      case MotionEvent.ACTION_CANCEL:
        if (mTouchListener != null) {
          mTouchListener.onTouchEnded();
        }
        break;
      default:
        break;
    }
    return super.onTouch(view, motionEvent);
  }
}
