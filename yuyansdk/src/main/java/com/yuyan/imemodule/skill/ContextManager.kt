package com.yuyan.imemodule.skill

import com.yuyan.imemodule.data.AppDatabase
import com.yuyan.imemodule.data.TargetEntity
import org.json.JSONArray

data class TargetProfile(
    val id: Long,
    val name: String,
    val platform: String,
    val stage: Int,
    val facts: List<String>,
    val events: List<String>
)

class ContextManager(private val repo: SkillRepository, private val db: AppDatabase) {

    suspend fun currentTarget(): TargetEntity {
        db.targetDao().current()?.let { return it }
        val now = System.currentTimeMillis()
        val target = TargetEntity(
            name = "默认目标",
            platform = "wechat",
            stage = repo.stages.defaultStage,
            style = "NATURAL",
            createdAt = now,
            updatedAt = now
        )
        val id = db.targetDao().insert(target)
        return target.copy(id = id)
    }

    suspend fun listTargets(): List<TargetEntity> = db.targetDao().all()

    suspend fun addFact(target: TargetEntity, fact: String) {
        val facts = JSONArray(target.factsJson).also { it.put(fact) }
        db.targetDao().update(target.copy(factsJson = facts.toString(), updatedAt = System.currentTimeMillis()))
    }

    suspend fun addEvent(target: TargetEntity, event: String) {
        val events = JSONArray(target.eventsJson).also { it.put(event) }
        db.targetDao().update(target.copy(eventsJson = events.toString(), updatedAt = System.currentTimeMillis()))
    }

    suspend fun commitStage(targetId: Long, stageId: Int) {
        db.targetDao().updateStage(targetId, stageId, System.currentTimeMillis())
    }

    suspend fun recentMessages(targetId: Long, limit: Int = 10): List<String> =
        db.messageDao().recent(targetId, limit).map { it.content }

    fun profileOf(target: TargetEntity): TargetProfile = TargetProfile(
        id = target.id,
        name = target.name,
        platform = platformName(target.platform),
        stage = target.stage,
        facts = parseList(target.factsJson),
        events = parseList(target.eventsJson)
    )

    private fun parseList(json: String): List<String> = runCatching {
        val array = JSONArray(json)
        List(array.length()) { array.optString(it) }.filter { it.isNotBlank() }
    }.getOrDefault(emptyList())

    private fun platformName(key: String): String =
        repo.platforms.platforms.firstOrNull { it.name.equals(key, true) || key.contains(it.name, true) }?.name ?: key
}
