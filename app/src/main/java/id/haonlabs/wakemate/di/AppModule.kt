package id.haonlabs.wakemate.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import id.haonlabs.wakemate.data.local.LineDao
import id.haonlabs.wakemate.data.local.StationDao
import id.haonlabs.wakemate.data.local.TripSessionDao
import id.haonlabs.wakemate.data.local.WakeMateDatabase
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): WakeMateDatabase =
        Room.databaseBuilder(context, WakeMateDatabase::class.java, "wakemate.db").build()

    @Provides
    fun stationDao(database: WakeMateDatabase): StationDao = database.stationDao()

    @Provides
    fun lineDao(database: WakeMateDatabase): LineDao = database.lineDao()

    @Provides
    fun tripSessionDao(database: WakeMateDatabase): TripSessionDao = database.tripSessionDao()

    @Provides
    @Singleton
    fun preferences(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("wakemate") }
}
