package id.haonlabs.wakemate.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import id.haonlabs.wakemate.data.repository.StationRepositoryImpl
import id.haonlabs.wakemate.data.repository.TripRepositoryImpl
import id.haonlabs.wakemate.domain.repository.StationRepository
import id.haonlabs.wakemate.domain.repository.TripRepository
import id.haonlabs.wakemate.domain.trip.TripTracker
import id.haonlabs.wakemate.service.AlertManager
import id.haonlabs.wakemate.service.AlarmAlertManager
import id.haonlabs.wakemate.service.ServiceTripTracker
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun stationRepository(impl: StationRepositoryImpl): StationRepository

    @Binds
    @Singleton
    abstract fun tripRepository(impl: TripRepositoryImpl): TripRepository

    @Binds
    abstract fun tripTracker(impl: ServiceTripTracker): TripTracker

    @Binds
    abstract fun alertManager(impl: AlarmAlertManager): AlertManager
}
