package com.appboy.unity.utils

import android.content.Intent
import android.os.Bundle
import com.appboy.events.FeedUpdatedEvent
import com.braze.events.ContentCardsUpdatedEvent
import com.braze.models.inappmessage.IInAppMessage
import com.braze.support.BrazeLogger.brazelog
import com.braze.support.BrazeLogger.getBrazeLogTag
import com.braze.support.constructJsonArray
import com.unity3d.player.UnityPlayer
import org.json.JSONObject

object MessagingUtils {
    private val TAG = getBrazeLogTag(MessagingUtils::class.java)
    private const val BRAZE_INTERNAL_GAME_OBJECT = "BrazeInternalComponent"

    enum class BrazeInternalComponentMethod(val methodName: String) {
        BEFORE_IAM_DISPLAYED("beforeInAppMessageDisplayed"),
        ON_IAM_DISMISSED("onInAppMessageDismissed"),
        ON_IAM_CLICKED("onInAppMessageClicked"),
        ON_IAM_BUTTON_CLICKED("onInAppMessageButtonClicked"),
        ON_IAM_HTML_CLICKED("onInAppMessageHTMLClicked");
    }

    fun sendInAppMessageReceivedMessage(
        unityGameObjectName: String,
        unityCallbackFunctionName: String,
        inAppMessage: IInAppMessage
    ): Boolean {
        if (unityGameObjectName.isBlank()) {
            brazelog(TAG) {
                "There is no Unity GameObject registered in the braze.xml configuration file to receive" +
                    " in app messages. Not sending the message to the Unity Player."
            }
            return false
        }
        if (unityCallbackFunctionName.isBlank()) {
            brazelog(TAG) {
                "There is no Unity callback method name registered to receive in app messages in " +
                    "the braze.xml configuration file. Not sending the message to the Unity Player."
            }
            return false
        }
        brazelog(TAG) { "Sending a message to $unityGameObjectName:$unityCallbackFunctionName." }
        UnityPlayer.UnitySendMessage(unityGameObjectName, unityCallbackFunctionName, inAppMessage.forJsonPut().toString())
        return true
    }

    @JvmStatic
    fun sendPushMessageToUnity(
        unityGameObjectName: String?,
        unityCallbackFunctionName: String?,
        pushIntent: Intent,
        pushAction: String
    ): Boolean {
        if (unityGameObjectName.isNullOrBlank()) {
            brazelog(TAG) {
                "There is no Unity GameObject registered in the braze.xml configuration file to " +
                    "receive $pushAction messages. Not sending the message to the Unity Player."
            }
            return false
        }
        if (unityCallbackFunctionName.isNullOrBlank()) {
            brazelog(TAG) {
                "There is no Unity callback method name registered to receive $pushAction messages " +
                    "in the braze.xml configuration file. Not sending the message to the Unity Player."
            }
            return false
        }
        brazelog(TAG) { "Sending a $pushAction message to $unityGameObjectName:$unityCallbackFunctionName." }
        UnityPlayer.UnitySendMessage(
            unityGameObjectName,
            unityCallbackFunctionName,
            getPushBundleExtras(pushIntent.extras).toString()
        )
        return true
    }

    fun sendFeedUpdatedEventToUnity(
        unityGameObjectName: String?,
        unityCallbackFunctionName: String?,
        feedUpdatedEvent: FeedUpdatedEvent
    ): Boolean {
        if (unityGameObjectName.isNullOrBlank()) {
            brazelog(TAG) {
                "There is no Unity GameObject registered in the braze.xml configuration " +
                    "file to receive feed updates. Not sending the message to the Unity Player."
            }
            return false
        }
        if (unityCallbackFunctionName.isNullOrBlank()) {
            brazelog(TAG) {
                "There is no Unity callback method name registered to receive feed updates in " +
                    "the braze.xml configuration file. Not sending the message to the Unity Player."
            }
            return false
        }
        val json = JSONObject()
            .put("mFeedCards", feedUpdatedEvent.feedCards.constructJsonArray())
            .put("mFromOfflineStorage", feedUpdatedEvent.isFromOfflineStorage)
        brazelog(TAG) { "Sending a feed updated event message to $unityGameObjectName:$unityCallbackFunctionName." }
        UnityPlayer.UnitySendMessage(unityGameObjectName, unityCallbackFunctionName, json.toString())
        return true
    }

    fun sendContentCardsUpdatedEventToUnity(
        unityGameObjectName: String?,
        unityCallbackFunctionName: String?,
        contentCardsUpdatedEvent: ContentCardsUpdatedEvent
    ): Boolean {
        if (unityGameObjectName.isNullOrBlank()) {
            brazelog(TAG) {
                "There is no Unity GameObject registered in the braze.xml configuration file " +
                    "to receive Content Cards updated event messages. Not sending the message to the Unity Player."
            }
            return false
        }
        if (unityCallbackFunctionName.isNullOrBlank()) {
            brazelog(TAG) {
                "There is no Unity callback method name registered to receive Content " +
                    "Cards updated event messages in the braze.xml configuration file. Not sending the message to the Unity Player."
            }
            return false
        }
        val json = JSONObject()
            .put("mContentCards", contentCardsUpdatedEvent.allCards.constructJsonArray())
            .put("mFromOfflineStorage", contentCardsUpdatedEvent.isFromOfflineStorage)
        brazelog(TAG) { "Sending a Content Cards update message to $unityGameObjectName:$unityCallbackFunctionName." }
        UnityPlayer.UnitySendMessage(unityGameObjectName, unityCallbackFunctionName, json.toString())
        return true
    }

    /**
     * Sends some structured data to the BrazeInternalComponent in C# in the Unity binding.
     */
    fun sendToBrazeInternalComponent(method: BrazeInternalComponentMethod, json: String) {
        UnityPlayer.UnitySendMessage(BRAZE_INTERNAL_GAME_OBJECT, method.methodName, json)
    }

    /**
     * De-serializes a bundle into a key value pair that can be represented as a [JSONObject].
     * Nested bundles are also converted recursively to have a single hierarchical structure.
     *
     * @param pushExtras The bundle received whenever a push notification is received, opened or deleted.
     * @return A [JSONObject] that represents this bundle in string format.
     */
    private fun getPushBundleExtras(pushExtras: Bundle?): JSONObject {
        val json = JSONObject()
        if (pushExtras == null) {
            return json
        }
        for (key in pushExtras.keySet()) {
            pushExtras[key]?.let {
                if (it is Bundle) {
                    json.put(key, getPushBundleExtras(it).toString())
                } else {
                    json.put(key, it.toString())
                }
            }
        }
        return json
    }
}
