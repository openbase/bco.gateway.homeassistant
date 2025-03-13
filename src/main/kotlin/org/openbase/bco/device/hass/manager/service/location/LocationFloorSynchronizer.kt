package org.openbase.bco.device.hass.manager.service.location

import org.openbase.bco.device.hass.communication.HassCommunicator
import org.openbase.bco.device.hass.manager.HassDeviceManager.Companion.ALIAS_KEY_HASS_FLOOR_ID
import org.openbase.bco.device.hass.util.contains
import org.openbase.bco.registry.remote.Registries
import org.openbase.jul.exception.CouldNotPerformException
import org.openbase.jul.extension.protobuf.IdentifiableMessage
import org.openbase.jul.schedule.SyncObject
import org.openbase.jul.storage.registry.AbstractSynchronizer
import org.openbase.type.domotic.state.ConnectionStateType
import org.openbase.type.domotic.unit.UnitConfigType.UnitConfig
import org.openbase.type.domotic.unit.location.LocationConfigType.LocationConfig.LocationType

/**
 * Synchronization for units handled by the BCO binding. This synchronization routine merely removes a thing
 * if the according unit is removed because updating the thing configuration on unit config updates is done by
 * the binding itself.
 *
 * @author [Tamino Huxohl](mailto:pleminoq@openbase.org)
 */
class LocationFloorSynchronizer(
    /**
     * @param synchronizationLock a lock used during a synchronization. This is lock should be shared with
     * other synchronization managers so that they will not interfere with each other.
     */
    synchronizationLock: SyncObject
) : AbstractSynchronizer<String, IdentifiableMessage<String, UnitConfig, UnitConfig.Builder>>(
    Registries.getUnitRegistry().getLocationUnitConfigRemoteRegistry(false), synchronizationLock
) {
    @Throws(CouldNotPerformException::class, InterruptedException::class)
    override fun activate() {
        HassCommunicator.instance
            .waitForConnectionState(ConnectionStateType.ConnectionState.State.CONNECTED)
        super.activate()
    }

    override fun afterInternalSync() {
        logger.debug("Internal sync finished!")
    }

    @Throws(CouldNotPerformException::class)
    override fun update(identifiableMessage: IdentifiableMessage<String, UnitConfig, UnitConfig.Builder>) {
        logger.trace("Synchronize update {} ...", identifiableMessage)
        // do nothing because the binding itself updates the according thing configuration
    }

    @Throws(CouldNotPerformException::class)
    override fun register(identifiableMessage: IdentifiableMessage<String, UnitConfig, UnitConfig.Builder>) {
        logger.trace("Synchronize registration {} ...", identifiableMessage)
        // do nothing because the binding itself updates the according thing configuration
    }

    @Throws(CouldNotPerformException::class)
    override fun remove(identifiableMessage: IdentifiableMessage<String, UnitConfig, UnitConfig.Builder>) {
        logger.trace("Synchronize removal {} ...", identifiableMessage)
    }

    override fun getEntries(): List<IdentifiableMessage<String, UnitConfig, UnitConfig.Builder>> =
        Registries.getUnitRegistry().getLocationUnitConfigRemoteRegistry(true).getEntries()

    override fun isSupported(identifiableMessage: IdentifiableMessage<String, UnitConfig, UnitConfig.Builder>): Boolean =
        identifiableMessage.message.locationConfig.locationType == LocationType.ZONE &&
                identifiableMessage.message.metaConfig.contains(ALIAS_KEY_HASS_FLOOR_ID)
}
