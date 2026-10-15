package com.studioguu.miniaturegarden.data.local.mapper

import com.studioguu.miniaturegarden.data.local.entity.CollectionEntryEntity
import com.studioguu.miniaturegarden.data.local.entity.GardenProgressEntity
import com.studioguu.miniaturegarden.data.local.entity.GridObjectEntity
import com.studioguu.miniaturegarden.data.local.entity.InventorySlotEntity
import com.studioguu.miniaturegarden.data.local.entity.NatureNoteEntity
import com.studioguu.miniaturegarden.data.local.entity.UserProfileEntity
import com.studioguu.miniaturegarden.domain.model.CollectionRecord
import com.studioguu.miniaturegarden.domain.model.GardenProgress
import com.studioguu.miniaturegarden.domain.model.GridObject
import com.studioguu.miniaturegarden.domain.model.InventorySlot
import com.studioguu.miniaturegarden.domain.model.NatureNote
import com.studioguu.miniaturegarden.domain.model.PlantStatus
import com.studioguu.miniaturegarden.domain.model.Season
import com.studioguu.miniaturegarden.domain.model.TutorialStep
import com.studioguu.miniaturegarden.domain.model.UserProfile
import com.studioguu.miniaturegarden.domain.model.WorkKind

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
    workKind = WorkKind.fromName(workKind),
    workStartedAt = workStartedAt,
    workEndsAt = workEndsAt,
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
    workKind = workKind.name,
    workStartedAt = workStartedAt,
    workEndsAt = workEndsAt,
)

fun InventorySlotEntity.toDomain() = InventorySlot(
    slotIndex = slotIndex,
    itemId = itemId,
    amount = amount,
    acquiredOrder = acquiredOrder,
)

fun InventorySlot.toEntity() = InventorySlotEntity(
    slotIndex = slotIndex,
    itemId = itemId,
    amount = amount,
    acquiredOrder = acquiredOrder,
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

fun GardenProgressEntity.toDomain() = GardenProgress(
    season = Season.fromName(season),
    tutorialStep = TutorialStep.fromName(tutorialStep),
    gardenSize = gardenSize,
)

fun GardenProgress.toEntity() = GardenProgressEntity(
    season = season.name,
    tutorialStep = tutorialStep.name,
    gardenSize = gardenSize,
)

fun NatureNoteEntity.toDomain() = NatureNote(noteId = noteId, firstSeenAt = firstSeenAt, count = count)

fun NatureNote.toEntity() = NatureNoteEntity(noteId = noteId, firstSeenAt = firstSeenAt, count = count)
