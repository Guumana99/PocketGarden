package com.studioguu.miniaturegarden.data.local.mapper

import com.studioguu.miniaturegarden.data.local.entity.CollectionEntryEntity
import com.studioguu.miniaturegarden.data.local.entity.GridObjectEntity
import com.studioguu.miniaturegarden.data.local.entity.InventorySlotEntity
import com.studioguu.miniaturegarden.data.local.entity.UserProfileEntity
import com.studioguu.miniaturegarden.domain.model.CollectionRecord
import com.studioguu.miniaturegarden.domain.model.GridObject
import com.studioguu.miniaturegarden.domain.model.InventorySlot
import com.studioguu.miniaturegarden.domain.model.PlantStatus
import com.studioguu.miniaturegarden.domain.model.UserProfile

fun UserProfileEntity.toDomain() = UserProfile(
    coins = coins,
    playerLevel = playerLevel,
    currentXp = currentXp,
    inventoryCapacity = inventoryCapacity,
    lastUpdatedTimestamp = lastUpdatedTimestamp,
    lastMonoClockTime = lastMonoClockTime,
    signature = signature,
)

fun UserProfile.toEntity() = UserProfileEntity(
    coins = coins,
    playerLevel = playerLevel,
    currentXp = currentXp,
    inventoryCapacity = inventoryCapacity,
    lastUpdatedTimestamp = lastUpdatedTimestamp,
    lastMonoClockTime = lastMonoClockTime,
    signature = signature,
)

fun GridObjectEntity.toDomain() = GridObject(
    instanceId = instanceId,
    gridX = gridX,
    gridY = gridY,
    heightLevel = heightLevel,
    isFlipped = isFlipped,
    objectId = objectId,
    growthProgress = growthProgress,
    moistureLevel = moistureLevel,
    nutritionLevel = nutritionLevel,
    stressRate = stressRate,
    status = PlantStatus.fromName(status),
    lastUpdatedTimestamp = lastUpdatedTimestamp,
)

fun GridObject.toEntity() = GridObjectEntity(
    instanceId = instanceId,
    gridX = gridX,
    gridY = gridY,
    heightLevel = heightLevel,
    isFlipped = isFlipped,
    objectId = objectId,
    growthProgress = growthProgress,
    moistureLevel = moistureLevel,
    nutritionLevel = nutritionLevel,
    stressRate = stressRate,
    status = status.name,
    lastUpdatedTimestamp = lastUpdatedTimestamp,
)

fun InventorySlotEntity.toDomain() = InventorySlot(
    slotIndex = slotIndex,
    itemId = itemId,
    amount = amount,
)

fun InventorySlot.toEntity() = InventorySlotEntity(
    slotIndex = slotIndex,
    itemId = itemId,
    amount = amount,
)

fun CollectionEntryEntity.toDomain() = CollectionRecord(
    entryId = entryId,
    count = count,
    firstObtainedAt = firstObtainedAt,
    revealed = revealed,
)

fun CollectionRecord.toEntity() = CollectionEntryEntity(
    entryId = entryId,
    count = count,
    firstObtainedAt = firstObtainedAt,
    revealed = revealed,
)
