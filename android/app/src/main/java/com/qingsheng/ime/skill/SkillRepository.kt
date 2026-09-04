package com.qingsheng.ime.skill

import android.content.Context
import org.json.JSONObject

class SkillRepository private constructor(context: Context) {

    private val assets = context.applicationContext.assets

    val stages: StagesData by lazy { read("stages.json") }
    val signals: SignalsData by lazy { read("signals.json") }
    val strategies: StrategiesData by lazy { read("strategies.json") }
    val examples: ExamplesData by lazy { read("examples.json") }
    val platforms: PlatformsData by lazy { read("platforms.json") }
    val mindset: MindsetData by lazy { read("mindset.json") }
    val templates: TemplateData by lazy { read("reply-templates.json") }
    val strategyLibrary: StrategyLibrary by lazy { StrategyLibrary(loadRaw("strategies-library.json")) }
    val recovery: String by lazy { loadRaw("recovery.json") }
    val autopilot: String by lazy { loadRaw("autopilot.json") }
    val audit: String by lazy { loadRaw("profile-audit.json") }

    private inline fun <reified T> read(name: String): T = parse(name) as T

    private fun parse(name: String): Any = when {
        name.startsWith("stages") -> parseStages(loadJson(name))
        name.startsWith("signals") -> parseSignals(loadJson(name))
        name.startsWith("strategies") -> parseStrategies(loadJson(name))
        name.startsWith("examples") -> parseExamples(loadJson(name))
        name.startsWith("platforms") -> parsePlatforms(loadJson(name))
        name.startsWith("mindset") -> parseMindset(loadJson(name))
        else -> parseTemplates(loadJson(name))
    }

    private fun loadJson(name: String): JSONObject =
        assets.open("skill/$name").bufferedReader(Charsets.UTF_8).use { it.readText() }
            .let { JSONObject(it) }

    private fun loadRaw(name: String): String =
        assets.open("skill/$name").bufferedReader(Charsets.UTF_8).use { it.readText() }

    private fun parseStages(json: JSONObject): StagesData {
        val list = json.optJSONArray("stages") ?: org.json.JSONArray()
        val stages = List(list.length()) { i ->
            val o = list.getJSONObject(i)
            Stage(
                id = o.getInt("id"),
                name = o.getString("name"),
                goal = o.optString("goal"),
                strategies = o.stringList("strategies"),
                taboos = o.stringList("taboos"),
                upgrade = o.optString("upgrade")
            )
        }
        return StagesData(json.optInt("defaultStage", 2), stages)
    }

    private fun parseSignals(json: JSONObject): SignalsData {
        val forced = json.optJSONArray("forcedStage") ?: org.json.JSONArray()
        val stop = json.getJSONObject("stop")
        val cold = json.getJSONObject("coldReading")
        return SignalsData(
            ioi = json.stringList("ioi"),
            iod = json.stringList("iod"),
            judgement = json.optString("judgement"),
            forcedStage = List(forced.length()) { i ->
                val o = forced.getJSONObject(i)
                ForcedStageSignal(o.stringList("keywords"), o.getInt("minStage"), o.optString("reason"))
            },
            stop = StopRule(stop.stringList("keywords"), stop.optString("action")),
            coldReading = ColdReading(cold.stringList("openers"), cold.optString("principle"))
        )
    }

    private fun parseStrategies(json: JSONObject): StrategiesData {
        val common = json.getJSONObject("common")
        val emotions = json.optJSONArray("emotions") ?: org.json.JSONArray()
        val push = json.getJSONObject("topicPush")
        return StrategiesData(
            common = CommonRules(common.stringList("principles"), common.stringList("avoid")),
            emotions = List(emotions.length()) { i ->
                val o = emotions.getJSONObject(i)
                EmotionRule(o.getString("name"), o.stringList("keywords"), o.optString("chain"), o.optString("hint"), o.stringList("avoid"))
            },
            topicPush = TopicPush(push.stringList("shallowTopics"), push.stringList("pushDirections"), push.optString("rule"))
        )
    }

    private fun parseExamples(json: JSONObject): ExamplesData {
        val arr = json.optJSONArray("examples") ?: org.json.JSONArray()
        return ExamplesData(List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            Example(o.getInt("stage"), o.getString("scenario"), o.getString("her"), o.getString("reply"), o.getString("why"))
        })
    }

    private fun parsePlatforms(json: JSONObject): PlatformsData {
        val arr = json.optJSONArray("platforms") ?: org.json.JSONArray()
        return PlatformsData(List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            Platform(o.getString("name"), o.getString("feature"), o.getString("chatStyle"), o.getString("opening"), o.stringList("taboos"))
        })
    }

    private fun parseMindset(json: JSONObject): MindsetData {
        val arr = json.optJSONArray("concepts") ?: org.json.JSONArray()
        return MindsetData(List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            Concept(o.getString("name"), o.getString("point"))
        })
    }

    private fun parseTemplates(json: JSONObject): TemplateData {
        val entries = mutableMapOf<String, StyleTemplates>()
        for (key in json.keys()) {
            val o = json.getJSONObject(key)
            entries[key] = StyleTemplates(o.stringList("natural"), o.stringList("gentle"), o.stringList("funny"))
        }
        return TemplateData(entries)
    }

    companion object {
        @Volatile private var instance: SkillRepository? = null

        fun get(context: Context): SkillRepository =
            instance ?: synchronized(this) {
                instance ?: SkillRepository(context).also { instance = it }
            }
    }
}
