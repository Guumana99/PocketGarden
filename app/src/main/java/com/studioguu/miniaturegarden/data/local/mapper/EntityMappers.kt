package com.studioguu.miniaturegarden.data.local.mapper

import com.studioguu.miniaturegarden.data.local.entity.CollectionEntryEntity
import com.studioguu.miniaturegarden.data.local.entity.GardenProgressEntity
import com.studioguu.miniaturegarden.data.local.entity.GridObjectEntity
import com.studioguu.miniaturegarden.data.local.entity.InventorySlotEntity
import com.studioguu.miniaturegarden.data.local.entity.LetterEntity
import com.studioguu.miniaturegarden.data.local.entity.NatureNoteEntity
import com.studioguu.miniaturegarden.data.local.entity.UserProfileEntity
import com.studioguu.miniaturegarden.domain.model.CollectionRecord
import com.studioguu.miniaturegarden.domain.model.GardenProgress
import com.studioguu.miniaturegarden.domain.model.GridObject
import com.studioguu.miniaturegarden.domain.model.InventorySlot
import com.studioguu.miniaturegarden.domain.model.Letter
import com.studioguu.miniaturegarden.domain.model.LetterKind
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
    harvestCount = harvestCount,
    soilQuality = soilQuality,
    isOn = isOn,
    soilProgress = soilProgress,
    lastCropId = lastCropId,
    idleMillis = idleMillis,
    bloomColour = bloomColour,
    gift = gift,
    turned = turned,
    roofColour = roofColour,
    wallColour = wallColour,
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
    harvestCount = harvestCount,
    soilQuality = soilQuality,
    isOn = isOn,
    soilProgress = soilProgress,
    lastCropId = lastCropId,
    idleMillis = idleMillis,
    bloomColour = bloomColour,
    gift = gift,
    turned = turned,
    roofColour = roofColour,
    wallColour = wallColour,
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
    seasonStartedAt = seasonStartedAt,
    weatherSeed = weatherSeed,
)

fun GardenProgress.toEntity() = GardenProgressEntity(
    season = season.name,
    tutorialStep = tutorialStep.name,
    gardenSize = gardenSize,
    seasonStartedAt = seasonStartedAt,
    weatherSeed = weatherSeed,
)

/** Null for a letter of a kind this version does not know (written by a newer one). */
fun LetterEntity.toDomain(): Letter? = LetterKind.fromName(kind)?.let {
    Letter(letterId = letterId, kind = it, season = Season.fromName(season), sentAt = sentAt, read = read, topic = topic)
}

fun Letter.toEntity() = LetterEntity(letterId = letterId, kind = kind.name, season = season.name, sentAt = sentAt, read = read, topic = topic)

fun NatureNoteEntity.toDomain() = NatureNote(noteId = noteId, firstSeenAt = firstSeenAt, count = count, opened = opened)

fun NatureNote.toEntity() = NatureNoteEntity(noteId = noteId, firstSeenAt = firstSeenAt, count = count, opened = opened)
