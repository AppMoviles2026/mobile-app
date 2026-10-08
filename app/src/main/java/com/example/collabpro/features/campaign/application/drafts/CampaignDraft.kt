package com.example.collabpro.features.campaign.application.drafts

import com.example.collabpro.core.domain.*
import com.example.collabpro.features.campaign.domain.model.*
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.util.UUID

data class CampaignBasics(val title: String = "", val objective: String = "", val description: String = "",
    val category: String = "", val targetAudience: String = "", val location: String = "") {
    fun request() = NewCampaign(title.trim(), objective.trim(), description.trim().ifBlank { null }, category.trim(),
        targetAudience.trim(), location.trim().ifBlank { null })
}
data class RequirementDraft(val localId: UUID = UUID.randomUUID(), val description: String = "", val mandatory: Boolean = true,
    val rule: RequirementRule = RequirementRule.MANUAL_CONFIRMATION, val expectedValue: String = "")
data class DeliverableDraft(val localId: UUID = UUID.randomUUID(), val contentType: String = "", val description: String = "",
    val quantity: String = "1", val deadline: String = "")
data class ConditionsDraft(val requirements: List<RequirementDraft> = listOf(RequirementDraft()),
    val deliverables: List<DeliverableDraft> = listOf(DeliverableDraft()), val applicationDeadline: String = "",
    val compensationType: CompensationType = CompensationType.CASH, val amount: String = "", val currency: String = "",
    val compensationDescription: String = "", val zoneId: String = ZoneId.systemDefault().id)
enum class DraftOperation { CREATE, CONDITIONS, PUBLISH, DISCARD, CLOSE }
data class CreationIntent(val request: NewCampaign, val key: IdempotencyKey, val startedAt: Instant)
data class CampaignDraft(val basics: CampaignBasics = CampaignBasics(), val conditions: ConditionsDraft = ConditionsDraft(),
    val serverId: UUID? = null, val creation: CreationIntent? = null, val pending: DraftOperation? = null,
    val conditionsSaved: Boolean = false, val step: Int = 1)

interface CampaignDraftStore {
    suspend fun load(owner: UUID): ApiResult<CampaignDraft?>
    suspend fun save(owner: UUID, draft: CampaignDraft): ApiResult<Unit>
    suspend fun clear(owner: UUID): ApiResult<Unit>
}

object CampaignDates {
    private val format = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm").withResolverStyle(ResolverStyle.STRICT)
    private val seconds = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss").withResolverStyle(ResolverStyle.STRICT)
    fun parse(text: String, zone: String): Instant? = runCatching {
        val local = LocalDateTime.parse(text.trim(), if (text.trim().length == 19) seconds else format)
        val offsets = ZoneId.of(zone).rules.getValidOffsets(local)
        if (offsets.size != 1) null else local.toInstant(offsets.single())
    }.getOrNull()
    fun display(instant: Instant, zone: String): String {
        val date = instant.atZone(ZoneId.of(zone))
        return (if (date.second == 0) format else seconds).format(date)
    }
}

object CampaignDraftValidation {
    fun basics(value: CampaignBasics): ApiFailure? {
        val body = value.request()
        val errors = linkedMapOf<String, String>()
        fun text(field: String, value: String?, max: Int, required: Boolean = true) {
            if (required && value.isNullOrBlank()) errors[field] = "Este campo es obligatorio."
            else if ((value?.length ?: 0) > max) errors[field] = "Máximo $max caracteres."
        }
        text("title", body.title, 200); text("objective", body.objective, 2000)
        text("description", body.description, 5000, false); text("category", body.category, 100)
        text("targetAudience", body.targetAudience, 2000); text("location", body.location, 150, false)
        return failure(errors)
    }
    fun conditions(value: ConditionsDraft, now: Instant): ApiResult<CampaignConditions> {
        val errors = linkedMapOf<String, String>()
        fun text(field: String, text: String, max: Int) {
            if (text.isBlank()) errors[field] = "Este campo es obligatorio."
            else if (text.trim().length > max) errors[field] = "Máximo $max caracteres."
        }
        val maxDate = Instant.parse("9999-12-31T23:59:59Z")
        val deadline = CampaignDates.parse(value.applicationDeadline, value.zoneId)
        if (deadline == null || !deadline.isAfter(now) || deadline > maxDate)
            errors["applicationDeadline"] = "Usa una fecha futura válida (aaaa-mm-dd hh:mm)."
        if (value.requirements.size !in 1..50) errors["requirements"] = "Define entre 1 y 50 requisitos."
        if (value.deliverables.size !in 1..50) errors["deliverables"] = "Define entre 1 y 50 entregables."
        val requirements = value.requirements.mapIndexed { index, item ->
            text("requirements[$index].description", item.description, 2000)
            if (item.rule != RequirementRule.MANUAL_CONFIRMATION) {
                text("requirements[$index].expectedValue", item.expectedValue, 150)
                if (item.rule == RequirementRule.AUTHORIZED_PLATFORM && item.expectedValue.trim().lowercase() !in listOf("instagram", "tiktok"))
                    errors["requirements[$index].expectedValue"] = "Selecciona Instagram o TikTok."
            }
            NewRequirement(item.description.trim(), item.mandatory, item.rule,
                if (item.rule == RequirementRule.MANUAL_CONFIRMATION) null else if (item.rule == RequirementRule.AUTHORIZED_PLATFORM)
                    item.expectedValue.trim().lowercase() else item.expectedValue.trim())
        }
        val deliverables = value.deliverables.mapIndexed { index, item ->
            text("deliverables[$index].contentType", item.contentType, 100); text("deliverables[$index].description", item.description, 2000)
            val quantity = item.quantity.toIntOrNull()
            if (quantity == null || quantity !in 1..1000) errors["deliverables[$index].quantity"] = "Cantidad de 1 a 1000."
            val date = CampaignDates.parse(item.deadline, value.zoneId)
            if (date == null || date > maxDate || (deadline != null && !date.isAfter(deadline)))
                errors["deliverables[$index].deadline"] = "Debe ser posterior al cierre de postulaciones."
            NewDeliverable(item.contentType.trim(), item.description.trim(), quantity ?: 0, date ?: Instant.EPOCH)
        }
        text("compensation.description", value.compensationDescription, 2000)
        val amount = if (value.compensationType == CompensationType.CASH) value.amount.toBigDecimalOrNull() else null
        val currency = if (value.compensationType == CompensationType.CASH) value.currency.trim().uppercase(java.util.Locale.ROOT) else null
        if (value.compensationType == CompensationType.CASH) {
            if (!value.amount.matches(Regex("[0-9]{1,10}(\\.[0-9]{1,2})?")) || amount == null || amount.signum() <= 0)
                errors["compensation.amount"] = "Monto positivo, hasta 10 enteros y 2 decimales; usa punto decimal."
            if (currency == null || currency.length != 3 || runCatching { java.util.Currency.getInstance(currency) }.isFailure)
                errors["compensation.currency"] = "Código de moneda válido, por ejemplo PEN o USD."
        }
        val failure = failure(errors)
        return if (failure != null) ApiResult.Failure(failure) else ApiResult.Success(CampaignConditions(requirements, deliverables,
            deadline!!, Compensation(value.compensationType, amount?.setScale(2), currency, value.compensationDescription.trim())))
    }
    private fun failure(errors: Map<String, String>) = if (errors.isEmpty()) null else
        ApiFailure(FailureKind.VALIDATION, "CLIENT_VALIDATION", "Revisa la información indicada.", errors)
}

fun CampaignDetails.toDraft(zone: String = ZoneId.systemDefault().id): CampaignDraft {
    val offer = summary.compensation
    return CampaignDraft(CampaignBasics(summary.title, objective, description.orEmpty(), summary.category, targetAudience, summary.location.orEmpty()),
        ConditionsDraft(requirements = requirements.map { RequirementDraft(it.id, it.description, it.mandatory, it.ruleType, it.expectedValue.orEmpty()) }.ifEmpty { listOf(RequirementDraft()) },
            deliverables = deliverables.map { DeliverableDraft(it.id, it.contentType, it.description, it.quantity.toString(), CampaignDates.display(it.deadline, zone)) }.ifEmpty { listOf(DeliverableDraft()) },
            applicationDeadline = summary.applicationDeadline?.let { CampaignDates.display(it, zone) }.orEmpty(),
            compensationType = offer?.type ?: CompensationType.CASH, amount = offer?.amount?.toPlainString().orEmpty(),
            currency = offer?.currency.orEmpty(), compensationDescription = offer?.description.orEmpty(), zoneId = zone),
        serverId = summary.id, conditionsSaved = offer != null && requirements.isNotEmpty() && deliverables.isNotEmpty(), step = 2)
}

fun CampaignDetails.matches(conditions: CampaignConditions): Boolean = summary.applicationDeadline == conditions.applicationDeadline &&
    summary.compensation?.let { it.type == conditions.compensation.type && it.currency == conditions.compensation.currency &&
        it.description == conditions.compensation.description && ((it.amount == null && conditions.compensation.amount == null) ||
        (it.amount != null && conditions.compensation.amount != null && it.amount.compareTo(conditions.compensation.amount) == 0)) } == true &&
    requirements.map { NewRequirement(it.description, it.mandatory, it.ruleType, it.expectedValue) }.groupingBy { it }.eachCount() == conditions.requirements.groupingBy { it }.eachCount() &&
    deliverables.map { NewDeliverable(it.contentType, it.description, it.quantity, it.deadline) }.groupingBy { it }.eachCount() == conditions.deliverables.groupingBy { it }.eachCount()
