package com.braze.ui.contentcards.handlers;

import android.os.Parcelable;

import androidx.annotation.NonNull;

import com.appboy.models.cards.Card;
import com.braze.events.ContentCardsUpdatedEvent;
import com.braze.ui.contentcards.ContentCardsFragment;

import java.util.List;

/**
 * An interface to handle card updates for the ContentCards. Handles the
 * sorting for {@link ContentCardsUpdatedEvent}'s in the {@link ContentCardsFragment}.
 */
public interface IContentCardsUpdateHandler extends Parcelable {
  /**
   * Handles a {@link ContentCardsUpdatedEvent} and returns a list of cards for rendering.
   * Each {@link ContentCardsUpdatedEvent} will contain
   * the full list of cards from either the cache or from a network request.
   *
   * @param event the {@link ContentCardsUpdatedEvent} update.
   * @return a list of cards to be rendered in the Content Cards from this {@link ContentCardsUpdatedEvent}
   */
  @NonNull
  List<Card> handleCardUpdate(ContentCardsUpdatedEvent event);
}
