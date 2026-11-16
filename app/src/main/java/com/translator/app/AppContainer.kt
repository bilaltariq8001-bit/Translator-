package com.translator.app

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.translator.app.core.offline.MlKitOfflinePacks
import com.translator.app.core.offline.OfflineFallbackDataSource
import com.translator.app.core.offline.OfflinePacks
import com.translator.app.core.offline.canTranslate
import androidx.room.Room
import com.translator.app.core.data.DataStoreSettingsStore
import com.translator.app.core.data.HistoryRepository
import com.translator.app.core.data.AppDatabase
import com.translator.app.core.data.RoomHistoryRepository
import com.translator.app.core.data.SettingsStore
import com.translator.app.core.data.TranslateTextUseCase
import com.translator.app.core.ai.AiChatDataSource
import com.translator.app.core.ai.AiChatSource
import com.translator.app.core.ai.AiInsightsDataSource
import com.translator.app.core.ai.AiInsightsSource
import com.translator.app.core.ai.AiPhotoDataSource
import com.translator.app.core.ai.OcrFirstPhotoSource
import com.translator.app.core.ai.FallbackToolSource
import com.translator.app.core.ai.PhrasebookToolSource
import com.translator.app.core.ai.MlKitPhotoTextReader
import com.translator.app.core.ai.OcrPhotoDataSource
import com.translator.app.core.ai.AiPhotoSource
import com.translator.app.core.ai.AiToolSource
import com.translator.app.core.ai.AiToolsDataSource
import com.translator.app.core.ai.AiTranslationDataSource
import com.translator.app.core.ai.FirebaseAiTextGenerator
import com.translator.app.core.ai.SwitchingTranslationDataSource
import com.translator.app.core.network.OkHttpTranslateApi
import com.translator.app.core.network.RemoteTranslationDataSource
import com.translator.app.core.network.TranslationDataSource
import kotlinx.coroutines.flow.first
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** Manual constructor injection: every layer can be replaced by a fake in tests. */
class AppContainer(context: Context) {
    /** Small synchronous values: consent result, analytics choice, launch count. */
    val syncPrefs = com.translator.app.core.util.SyncPrefs(context)
    val launchNumber: Int = syncPrefs.nextLaunchNumber()
    init {
        // Analytics is a no-op until Firebase is set up and the person has agreed.
        com.translator.app.core.analytics.Analytics.install(com.translator.app.core.analytics.FirebaseAnalyticsSink(context))
    }

    val settings: SettingsStore = DataStoreSettingsStore(context)
    private val database = Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "translator.db").addMigrations(com.translator.app.core.data.MIGRATION_1_2, com.translator.app.core.data.MIGRATION_2_3)
        // Only when an OLDER app version is installed over data saved by a newer one: start fresh rather than crash at launch.
        .fallbackToDestructiveMigrationOnDowngrade().build()
    val history: HistoryRepository = RoomHistoryRepository(database.historyDao())
    val chats: com.translator.app.core.data.ChatRepository = com.translator.app.core.data.RoomChatRepository(database.chatDao())

    // Connect timeout 10 s; each translate attempt is bounded to 15 s inside RemoteTranslationDataSource.
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val standardTranslation = RemoteTranslationDataSource(OkHttpTranslateApi(httpClient))
    private val aiGenerator = FirebaseAiTextGenerator()
    private val aiTranslation = AiTranslationDataSource(aiGenerator)

    // AI translation (Firebase AI Logic) only when the user opted in and allows cloud features.
    private val onlineTranslation = com.translator.app.core.network.CachingTranslationDataSource(
        SwitchingTranslationDataSource(
            aiTranslation, standardTranslation,
            // On-device packs are tried first (further out); the AI only steps in when none can translate this pair.
            fallbackToAi = { r -> !offlinePacks.canTranslate(r.sourceLang, r.targetLang) },
        ),
    )

    /** Opens the connection to the translation service ahead of the first request, so that request skips the handshake. */
    fun warmUpTranslation() = standardTranslation.warmUp()

    // Downloaded language packs translate with no internet, and back up the online service when it cannot be reached.
    val offlinePacks: OfflinePacks = MlKitOfflinePacks()
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    fun isOnline(): Boolean {
        val caps = connectivity?.activeNetwork?.let(connectivity::getNetworkCapabilities) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    // Ads, consent and remote config. Nothing in here starts the ads SDK; the startup flow does that after consent.
    val ads = com.translator.app.core.ads.AdsController(context, syncPrefs, ::isOnline)

    val translationDataSource: TranslationDataSource = OfflineFallbackDataSource(onlineTranslation, offlinePacks, ::isOnline)

    val translateText = TranslateTextUseCase(translationDataSource, history)

    // Every AI feature goes through the one Firebase AI Logic generator.
    val aiInsights: AiInsightsSource = AiInsightsDataSource(aiGenerator)
    // Photo: read on the phone first (so the translation can be painted onto the photo); the AI model reads what the phone cannot.
    val aiPhoto: AiPhotoSource = OcrFirstPhotoSource(OcrPhotoDataSource(MlKitPhotoTextReader(), translationDataSource), AiPhotoDataSource(aiGenerator))
    val aiChat: AiChatSource = AiChatDataSource(aiGenerator)
    // Travel phrases fall back to a built-in phrasebook plus the normal translator when AI is not set up.
    val aiTools: AiToolSource = FallbackToolSource(AiToolsDataSource(aiGenerator), PhrasebookToolSource(translationDataSource))

    /** Delete my data: history, favorites and every setting on this phone. */
    suspend fun deleteAllData() {
        history.clear()
        chats.clear()
        settings.clearAll()
        // Analytics stays OFF until the person opts in again (the first-run screen asks again); the UMP answer itself stays.
        syncPrefs.analyticsChoice = 0
        com.translator.app.core.consent.applyAnalytics(syncPrefs)
    }
}
