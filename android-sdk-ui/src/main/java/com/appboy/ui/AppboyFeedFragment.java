package com.appboy.ui;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;

import androidx.annotation.VisibleForTesting;
import androidx.core.view.GestureDetectorCompat;
import androidx.fragment.app.ListFragment;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.appboy.enums.CardCategory;
import com.appboy.events.FeedUpdatedEvent;
import com.appboy.events.IEventSubscriber;
import com.appboy.models.cards.Card;
import com.appboy.ui.adapters.AppboyListAdapter;
import com.braze.Braze;
import com.braze.support.BrazeLogger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

public class AppboyFeedFragment extends ListFragment implements SwipeRefreshLayout.OnRefreshListener {
  private static final String TAG = BrazeLogger.getBrazeLogTag(AppboyFeedFragment.class);
  private static final int NETWORK_PROBLEM_WARNING_MS = 5000;
  private static final int MAX_FEED_TTL_SECONDS = 60;
  private static final long AUTO_HIDE_REFRESH_INDICATOR_DELAY_MS = 2500L;
  @VisibleForTesting
  static final String SAVED_INSTANCE_STATE_KEY_PREVIOUS_VISIBLE_HEAD_CARD_INDEX = "PREVIOUS_VISIBLE_HEAD_CARD_INDEX";
  @VisibleForTesting
  static final String SAVED_INSTANCE_STATE_KEY_CURRENT_CARD_INDEX_AT_BOTTOM_OF_SCREEN = "CURRENT_CARD_INDEX_AT_BOTTOM_OF_SCREEN";
  @VisibleForTesting
  static final String SAVED_INSTANCE_STATE_KEY_SKIP_CARD_IMPRESSIONS_RESET = "SKIP_CARD_IMPRESSIONS_RESET";
  @VisibleForTesting
  static final String SAVED_INSTANCE_STATE_KEY_CARD_CATEGORY = "CARD_CATEGORY";

  private final Handler mMainThreadLooper = new Handler(Looper.getMainLooper());
  // Shows the network error message. This should only be executed on the Main/UI thread.
  private final Runnable mShowNetworkError = new Runnable() {
    @Override
    public void run() {
      // null checks make sure that this only executes when the constituent views are valid references.
      if (mLoadingSpinner != null) {
        mLoadingSpinner.setVisibility(View.GONE);
      }
      if (mNetworkErrorLayout != null) {
        mNetworkErrorLayout.setVisibility(View.VISIBLE);
      }
    }
  };

  private IEventSubscriber<FeedUpdatedEvent> mFeedUpdatedSubscriber;
  private AppboyListAdapter mAdapter;
  private LinearLayout mNetworkErrorLayout;
  private LinearLayout mEmptyFeedLayout;
  private ProgressBar mLoadingSpinner;
  private RelativeLayout mFeedRootLayout;
  private EnumSet<CardCategory> mCategories;
  private SwipeRefreshLayout mFeedSwipeLayout;
  private GestureDetectorCompat mGestureDetector;
  private boolean mSortEnabled = false;

  @VisibleForTesting
  boolean mSkipCardImpressionsReset = false;
  @VisibleForTesting
  int mPreviousVisibleHeadCardIndex = 0;
  @VisibleForTesting
  int mCurrentCardIndexAtBottomOfScreen = 0;

  // This view should only be in the View.VISIBLE state when the listview is not visible. This view's
  // purpose is to let the "network error" and "no card" states to have the swipe-to-refresh functionality
  // when their respective views are visible.
  private View mTransparentFullBoundsContainerView;

  @Override
  public void onAttach(final Context context) {
    super.onAttach(context);
    if (mAdapter == null) {
      mAdapter = new AppboyListAdapter(context, R.id.tag, new ArrayList<>());
      mCategories = CardCategory.getAllCategories();
    }
    mGestureDetector = new GestureDetectorCompat(context, new FeedGestureListener());
  }

  @Override
  public View onCreateView(LayoutInflater layoutInflater, ViewGroup container, Bundle savedInstanceState) {
    View view = layoutInflater.inflate(R.layout.com_appboy_feed, container, false);
    mNetworkErrorLayout = view.findViewById(R.id.com_appboy_feed_network_error);
    mLoadingSpinner = view.findViewById(R.id.com_appboy_feed_loading_spinner);
    mEmptyFeedLayout = view.findViewById(R.id.com_appboy_feed_empty_feed);
    mFeedRootLayout = view.findViewById(R.id.com_appboy_feed_root);
    mFeedSwipeLayout = view.findViewById(R.id.appboy_feed_swipe_container);
    mFeedSwipeLayout.setOnRefreshListener(this);
    mFeedSwipeLayout.setEnabled(false);
    mFeedSwipeLayout.setColorSchemeResources(R.color.com_appboy_newsfeed_swipe_refresh_color_1,
        R.color.com_appboy_newsfeed_swipe_refresh_color_2,
        R.color.com_appboy_newsfeed_swipe_refresh_color_3,
        R.color.com_appboy_newsfeed_swipe_refresh_color_4);
    mTransparentFullBoundsContainerView = view.findViewById(R.id.com_appboy_feed_transparent_full_bounds_container_view);
    return view;
  }

  @SuppressLint("InflateParams")
  @Override
  public void onViewCreated(View view, Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);
    loadFragmentStateFromSavedInstanceState(savedInstanceState);
    if (mSkipCardImpressionsReset) {
      mSkipCardImpressionsReset = false;
    } else {
      mAdapter.resetCardImpressionTracker();
      BrazeLogger.d(TAG, "Resetting card impressions.");
    }

    // Applying top and bottom padding as header and footer views allows for the top and bottom padding to be scrolled
    // away, as opposed to being a permanent frame around the feed.
    LayoutInflater inflater = LayoutInflater.from(getActivity());
    final ListView listView = getListView();
    // Inflating these views without a view root is valid since they have no parent layout information
    // that needs to be respected.
    listView.addHeaderView(inflater.inflate(R.layout.com_appboy_feed_header, null));
    listView.addFooterView(inflater.inflate(R.layout.com_appboy_feed_footer, null));

    mFeedRootLayout.setOnTouchListener((currentView, motionEvent) -> {
      // Send touch events from the background view to the gesture detector to enable margin listview scrolling
      return mGestureDetector.onTouchEvent(motionEvent);
    });

    // Enable the swipe-to-refresh view only when the user is at the head of the listview.
    listView.setOnScrollListener(new AbsListView.OnScrollListener() {
      @Override
      public void onScrollStateChanged(AbsListView absListView, int scrollState) {
      }

      @Override
      public void onScroll(AbsListView absListView, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
        mFeedSwipeLayout.setEnabled(firstVisibleItem == 0);

        // Handle read/unread cards functionality below
        if (visibleItemCount == 0) {
          // No cards/views have been loaded, do nothing
          return;
        }

        int currentVisibleHeadCardIndex = firstVisibleItem - 1;

        // Head index increased (scroll down)
        if (currentVisibleHeadCardIndex > mPreviousVisibleHeadCardIndex) {
          // Mark all cards in the gap as read
          mAdapter.batchSetCardsToRead(mPreviousVisibleHeadCardIndex, currentVisibleHeadCardIndex);
        }
        mPreviousVisibleHeadCardIndex = currentVisibleHeadCardIndex;

        // We take note of what card is at the bottom of the feed so that when this fragment is destroyed,
        // all on-screen cards have updated read indicators.
        mCurrentCardIndexAtBottomOfScreen = firstVisibleItem + visibleItemCount;
      }
    });

    // We need the transparent view to pass it's touch events to the swipe-to-refresh view. We
    // do this by consuming touch events in the transparent view.
    mTransparentFullBoundsContainerView.setOnTouchListener((currentView, motionEvent) -> {
      // Only consume events if the view is visible
      return currentView.getVisibility() == View.VISIBLE;
    });

    // Remove the previous subscriber before rebuilding a new one with our new activity.
    Braze.getInstance(getContext()).removeSingleSubscription(mFeedUpdatedSubscriber, FeedUpdatedEvent.class);
    mFeedUpdatedSubscriber = event -> {
      Activity activity = getActivity();
      // Not strictly necessary, but being defensive in the face of a lot of inconsistent behavior with
      // fragment/activity lifecycles.
      if (activity == null) {
        return;
      }

      activity.runOnUiThread(() -> {
        BrazeLogger.v(TAG, "Updating feed views in response to FeedUpdatedEvent: " + event);
        // If a FeedUpdatedEvent comes in, we make sure that the network error isn't visible. It could become
        // visible again later if we need to request a new feed and it doesn't return in time, but we display a
        // network spinner while we wait, instead of keeping the network error up.
        mMainThreadLooper.removeCallbacks(mShowNetworkError);
        mNetworkErrorLayout.setVisibility(View.GONE);

        // If there are no cards, regardless of what happens further down, we're not going to show the list view, so
        // clear the list view and change relevant visibility now.
        if (event.getCardCount(mCategories) == 0) {
          listView.setVisibility(View.GONE);
          mAdapter.clear();
        } else {
          mEmptyFeedLayout.setVisibility(View.GONE);
          mLoadingSpinner.setVisibility(View.GONE);
          mTransparentFullBoundsContainerView.setVisibility(View.GONE);
        }

        // If we got our feed from offline storage, and it was old, we asynchronously request a new one from the server,
        // putting up a spinner if the old feed was empty.
        if (event.isFromOfflineStorage() && (event.lastUpdatedInSecondsFromEpoch() + MAX_FEED_TTL_SECONDS) * 1000 < System.currentTimeMillis()) {
          BrazeLogger.i(TAG, "Feed received was older than the max time to live of " + MAX_FEED_TTL_SECONDS + " seconds, displaying it "
              + "for now, but requesting an updated view from the server.");
          Braze.getInstance(getContext()).requestFeedRefresh();
          // If we don't have any cards to display, we put up the spinner while we wait for the network to return.
          // Eventually displaying an error message if it doesn't.
          if (event.getCardCount(mCategories) == 0) {
            BrazeLogger.d(TAG, "Old feed was empty, putting up a network spinner and registering the network "
                + "error message with a delay of " + NETWORK_PROBLEM_WARNING_MS + "ms.");
            mEmptyFeedLayout.setVisibility(View.GONE);
            mLoadingSpinner.setVisibility(View.VISIBLE);
            mTransparentFullBoundsContainerView.setVisibility(View.VISIBLE);
            mMainThreadLooper.postDelayed(mShowNetworkError, NETWORK_PROBLEM_WARNING_MS);
            return;
          }
        }

        // If we get here, we know that our feed is either fresh from the cache, or came down directly from a
        // network request. Thus, an empty feed shouldn't have a network error, or a spinner, we should just
        // tell the user that the feed is empty.
        if (event.getCardCount(mCategories) == 0) {
          mLoadingSpinner.setVisibility(View.GONE);
          mEmptyFeedLayout.setVisibility(View.VISIBLE);
          mTransparentFullBoundsContainerView.setVisibility(View.VISIBLE);
        } else {
          if (mSortEnabled && event.getCardCount(mCategories) != event.getUnreadCardCount(mCategories)) {
            mAdapter.replaceFeed(sortFeedCards(event.getFeedCards(mCategories)));
          } else {
            mAdapter.replaceFeed(event.getFeedCards(mCategories));
          }
          listView.setVisibility(View.VISIBLE);
        }

        mFeedSwipeLayout.setRefreshing(false);
      });
    };
    Braze.getInstance(getContext()).subscribeToFeedUpdates(mFeedUpdatedSubscriber);

    // Once the header and footer views are set and our event handlers are ready to go, we set the adapter and hit the
    // cache for an initial feed load.
    listView.setAdapter(mAdapter);
    Braze.getInstance(getContext()).requestFeedRefreshFromCache();
  }

  /**
   * The sortFeedCards is responsible for sorting newsfeed cards depending on whether or not they have already been viewed.
   * It is only run when the the mSortEnabled is set to true and its expected behavior is to maintain the respective order of cards
   * which have the same view status.
   */
  public List<Card> sortFeedCards(List<Card> cards) {
    Collections.sort(cards, (cardOne, cardTwo) -> (cardOne.isIndicatorHighlighted() == cardTwo.isIndicatorHighlighted() ? 0 : (cardOne.isIndicatorHighlighted() ? 1 : -1)));
    return cards;
  }

  @Override
  public void onResume() {
    super.onResume();
    Braze.getInstance(getContext()).logFeedDisplayed();
  }

  @Override
  public void onDestroyView() {
    super.onDestroyView();
    // If the view is destroyed, we don't care about updating it anymore. Remove the subscription immediately.
    Braze.getInstance(getContext()).removeSingleSubscription(mFeedUpdatedSubscriber, FeedUpdatedEvent.class);
    setOnScreenCardsToRead();
  }

  @Override
  public void onPause() {
    super.onPause();
    setOnScreenCardsToRead();
  }

  /**
   * This should be called whenever the feed goes off the user's screen.
   */
  private void setOnScreenCardsToRead() {
    // Set whatever cards are on screen to read since the view is being destroyed.
    mAdapter.batchSetCardsToRead(mPreviousVisibleHeadCardIndex, mCurrentCardIndexAtBottomOfScreen);
  }

  @Override
  public void onDetach() {
    super.onDetach();
    setListAdapter(null);
  }

  /**
   * The onSaveInstanceState method gets called before an orientation change when either the fragment is
   * the current fragment or exists in the fragment manager backstack.
   */
  @Override
  public void onSaveInstanceState(Bundle outState) {
    // Save the state of this instance into the outState bundle
    outState.putInt(SAVED_INSTANCE_STATE_KEY_PREVIOUS_VISIBLE_HEAD_CARD_INDEX, mPreviousVisibleHeadCardIndex);
    outState.putInt(SAVED_INSTANCE_STATE_KEY_CURRENT_CARD_INDEX_AT_BOTTOM_OF_SCREEN, mCurrentCardIndexAtBottomOfScreen);
    outState.putBoolean(SAVED_INSTANCE_STATE_KEY_SKIP_CARD_IMPRESSIONS_RESET, mSkipCardImpressionsReset);

    if (mCategories == null) {
      mCategories = CardCategory.getAllCategories();
    }
    // An arraylist containing the ordinals of each CardCategory enum value
    ArrayList<String> cardCategoryArrayList = new ArrayList<>(mCategories.size());

    for (CardCategory cardCategory : mCategories) {
      cardCategoryArrayList.add(cardCategory.name());
    }
    outState.putStringArrayList(SAVED_INSTANCE_STATE_KEY_CARD_CATEGORY, cardCategoryArrayList);
    super.onSaveInstanceState(outState);
    // We set mSkipCardImpressionsReset to true only when onSaveInstanceState is called while the fragment
    // is visible on the screen. That happens when the fragment is being managed by the fragment manager and
    // it is not in the backstack. We do this to avoid setting the mSkipCardImpressionsReset flag when the
    // device undergoes an orientation change while the fragment is in the backstack.
    if (isVisible()) {
      mSkipCardImpressionsReset = true;
    }
  }

  /**
   * Unpacks the data from a bundle marshalled in onSaveInstanceState due to a configuration change.
   */
  @VisibleForTesting
  void loadFragmentStateFromSavedInstanceState(Bundle savedInstanceState) {
    if (savedInstanceState == null) {
      // There's no previous state to load from, so just return.
      return;
    }
    if (mCategories == null) {
      mCategories = CardCategory.getAllCategories();
    }
    mPreviousVisibleHeadCardIndex = savedInstanceState.getInt(SAVED_INSTANCE_STATE_KEY_PREVIOUS_VISIBLE_HEAD_CARD_INDEX, 0);
    mCurrentCardIndexAtBottomOfScreen = savedInstanceState.getInt(SAVED_INSTANCE_STATE_KEY_CURRENT_CARD_INDEX_AT_BOTTOM_OF_SCREEN, 0);
    mSkipCardImpressionsReset = savedInstanceState.getBoolean(SAVED_INSTANCE_STATE_KEY_SKIP_CARD_IMPRESSIONS_RESET, false);

    ArrayList<String> cardCategoryArrayList = savedInstanceState.getStringArrayList(SAVED_INSTANCE_STATE_KEY_CARD_CATEGORY);
    if (cardCategoryArrayList != null) {
      mCategories.clear();
      for (String cardCategoryString: cardCategoryArrayList) {
        mCategories.add(CardCategory.valueOf(cardCategoryString));
      }
    }
  }

  public EnumSet<CardCategory> getCategories() {
    return mCategories;
  }

  public boolean getSortEnabled() {
    return mSortEnabled;
  }

  /**
   * The setSortEnabled methods sets the mSortEnabled bool which determines whether or not on update we sort
   * newsfeed cards by their read status. Sorting is currently not done by default on requestFeedRefreshFromCache.
   */
  public void setSortEnabled(boolean sortEnabled) {
    mSortEnabled = sortEnabled;
  }

  public void setCategory(CardCategory category) {
    setCategories(EnumSet.of(category));
  }

  /**
   * Calling this method will make AppboyFeedFragment display a list of cards where each card belongs
   * to at least one of the given categories.
   * When there are no cards in those categories, this method returns an empty list.
   * When the passed in categories are null, all cards will be returned.
   * When the passed in categories are empty EnumSet, an empty list will be returned.
   *
   * @param categories an EnumSet of CardCategory. Please pass in a non-empty EnumSet of CardCategory,
   *                   or a null. An empty EnumSet is considered invalid.
   */
  public void setCategories(EnumSet<CardCategory> categories) {
    if (categories == null) {
      BrazeLogger.i(TAG, "The categories passed into setCategories are null, AppboyFeedFragment is going to display all the cards in cache.");
      mCategories = CardCategory.getAllCategories();
    } else if (categories.isEmpty()) {
      BrazeLogger.w(TAG, "The categories set had no elements and have been ignored. Please pass a valid EnumSet of CardCategory.");
      return;
    } else if (categories.equals(mCategories)) {
      return;
    } else {
      mCategories = categories;
    }
    if (Braze.getInstance(getContext()) != null) {
      Braze.getInstance(getContext()).requestFeedRefreshFromCache();
    }
  }

  /**
   * Called when the user swipes down and requests a feed refresh.
   */
  @Override
  public void onRefresh() {
    Braze.getInstance(getContext()).requestFeedRefresh();
    mMainThreadLooper.postDelayed(() -> mFeedSwipeLayout.setRefreshing(false), AUTO_HIDE_REFRESH_INDICATOR_DELAY_MS);
  }

  /**
   * This class is a custom listener to catch gestures happening outside the bounds of the listview that
   * should be fed into it.
   */
  public class FeedGestureListener extends GestureDetector.SimpleOnGestureListener {
    @Override
    public boolean onDown(MotionEvent motionEvent) {
      return true;
    }

    @Override
    public boolean onScroll(MotionEvent motionEvent, MotionEvent motionEvent2, float dx, float dy) {
      getListView().smoothScrollBy((int) dy, 0);
      return true;
    }

    @Override
    public boolean onFling(MotionEvent motionEvent, MotionEvent motionEvent2, float velocityX, float velocityY) {
      // We need to find the pixel distance of the scroll from the velocity with units (px / sec)
      // So d (px) = v (px / sec) * 1 (sec) / 1000 (ms) * deltaTimeMillis (ms)
      long deltaTimeMillis = (motionEvent2.getEventTime() - motionEvent.getEventTime()) * 2;
      int scrollDistance = (int) (velocityY * deltaTimeMillis / 1000);
      // Multiplied by 2 to get a smoother scroll effect during a fling
      getListView().smoothScrollBy(-scrollDistance, (int) (deltaTimeMillis * 2));
      return true;
    }
  }
}
