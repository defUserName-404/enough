package app.enough.limits.android.entity

import app.enough.limits.domain.Change
import app.enough.limits.domain.Config
import app.enough.limits.domain.PendingChange
import app.enough.limits.domain.Phase
import app.enough.limits.domain.Target

fun ConfigEntity.toDomain(): Config = Config(
    phase = phase.toDomain(),
    targets = targets.map { it.toDomain() },
    pending = pending.map { PendingChange(it.id, it.change.toDomain(), it.applyAt) },
    warned = warned,
    failClosedNotified = failClosedNotified,
    unsuspendable = unsuspendable,
    stuckSuspended = stuckSuspended,
    protectionMissing = protectionMissing,
    releaseFailed = releaseFailed,
)

fun Config.toEntity(): ConfigEntity = ConfigEntity(
    phase = phase.toEntity(),
    targets = targets.map { it.toEntity() },
    pending = pending.map { PendingChangeEntity(it.id, it.change.toEntity(), it.applyAt) },
    warned = warned,
    failClosedNotified = failClosedNotified,
    unsuspendable = unsuspendable,
    stuckSuspended = stuckSuspended,
    protectionMissing = protectionMissing,
    releaseFailed = releaseFailed,
)

private fun TargetEntity.toDomain() = Target(pkg, limitMin)
private fun Target.toEntity() = TargetEntity(pkg, limitMin)

private fun PhaseEntity.toDomain(): Phase = when (this) {
    PhaseEntity.SETUP -> Phase.SETUP
    PhaseEntity.ACTIVE -> Phase.ACTIVE
    PhaseEntity.RELEASING -> Phase.RELEASING
}

private fun Phase.toEntity(): PhaseEntity = when (this) {
    Phase.SETUP -> PhaseEntity.SETUP
    Phase.ACTIVE -> PhaseEntity.ACTIVE
    Phase.RELEASING -> PhaseEntity.RELEASING
}

private fun ChangeEntity.toDomain(): Change = when (this) {
    is ChangeEntity.AddOrLower -> Change.AddOrLower(target.toDomain())
    is ChangeEntity.RaiseLimit -> Change.RaiseLimit(target.toDomain())
    is ChangeEntity.Remove -> Change.Remove(pkg)
    ChangeEntity.Release -> Change.Release
}

private fun Change.toEntity(): ChangeEntity = when (this) {
    is Change.AddOrLower -> ChangeEntity.AddOrLower(target.toEntity())
    is Change.RaiseLimit -> ChangeEntity.RaiseLimit(target.toEntity())
    is Change.Remove -> ChangeEntity.Remove(pkg)
    Change.Release -> ChangeEntity.Release
}
