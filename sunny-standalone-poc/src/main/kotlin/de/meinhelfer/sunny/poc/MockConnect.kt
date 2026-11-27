package de.meinhelfer.sunny.poc

internal enum class CapabilityOwner { MeinHelferSynthetic }
internal enum class TestPermission { ReadTodayTasks, ReadTodayAppointments }
internal enum class Sensitivity { Synthetic }

internal enum class InputSchema {
    TodayV1;

    fun parse(input: Map<String, String>): TodayQuery? {
        if (input.size != 2 || input["day"] != "today") return null
        val digit = input["limit"]?.singleOrNull() ?: return null
        if (digit !in '1'..'5') return null
        return TodayQuery(digit.digitToInt())
    }
}

internal enum class OutputSchema {
    TodayTasksV1,
    TodayAppointmentsV1;

    fun accepts(payload: TodayPayload, limit: Int): Boolean = when (this) {
        TodayTasksV1 -> payload is TodayPayload.Tasks && payload.items.size <= limit
        TodayAppointmentsV1 -> payload is TodayPayload.Appointments && payload.items.size <= limit
    }
}

internal data class CapabilityDescriptor(
    val id: String,
    val version: Int,
    val owner: CapabilityOwner,
    val inputSchema: InputSchema,
    val outputSchema: OutputSchema,
    val permission: TestPermission,
    val sensitivity: Sensitivity,
    val offline: Boolean,
    val title: String,
    val help: String,
    val dependencies: Set<String>,
)

internal class CapabilityRegistry(descriptors: List<CapabilityDescriptor>) {
    private val byId = descriptors.associateBy { it.id }

    init {
        require(byId.size == descriptors.size) { "Duplicate capability ID" }
    }

    fun list(): List<CapabilityDescriptor> = byId.values.toList()
    fun find(id: String): CapabilityDescriptor? = byId[id]
}

internal data class ConnectRequest(val id: String, val version: Int, val input: Map<String, String>)

internal data class TodayQuery(val limit: Int) {
    init {
        require(limit in 1..5)
    }
}

internal enum class SampleTask(val title: String, val completed: Boolean) {
    TestList("Beispielliste prüfen", false),
    TestNote("Testnotiz ordnen", true),
}

internal enum class SampleAppointment(val time: String, val title: String) {
    Morning("09:00", "Demo-Termin A"),
    Afternoon("14:30", "Demo-Termin B"),
}

internal sealed interface TodayPayload {
    data class Tasks(val items: List<SampleTask>) : TodayPayload
    data class Appointments(val items: List<SampleAppointment>) : TodayPayload
}

internal enum class Rejection {
    UnknownCapability, IncompatibleVersion, MalformedInput, Unauthorized, InvalidResponse,
}

internal sealed interface ConnectResult {
    data class Read(val payload: TodayPayload) : ConnectResult
    data class Rejected(val reason: Rejection) : ConnectResult
}

internal fun interface TodayAdapter {
    fun read(schema: OutputSchema, query: TodayQuery): TodayPayload
}

internal object SyntheticMeinHelfer : TodayAdapter {
    val descriptors = listOf(
        descriptor(
            "meinhelfer.today.tasks", OutputSchema.TodayTasksV1, TestPermission.ReadTodayTasks,
            "Aufgaben heute", "Feste Beispielaufgaben mit Status offen oder erledigt.",
        ),
        descriptor(
            "meinhelfer.today.appointments", OutputSchema.TodayAppointmentsV1, TestPermission.ReadTodayAppointments,
            "Termine heute", "Feste Beispieltermine mit Uhrzeit und Titel.",
        ),
    )

    override fun read(schema: OutputSchema, query: TodayQuery): TodayPayload = when (schema) {
        OutputSchema.TodayTasksV1 -> TodayPayload.Tasks(SampleTask.entries.take(query.limit))
        OutputSchema.TodayAppointmentsV1 -> TodayPayload.Appointments(SampleAppointment.entries.take(query.limit))
    }

    private fun descriptor(
        id: String,
        outputSchema: OutputSchema,
        permission: TestPermission,
        title: String,
        help: String,
    ) = CapabilityDescriptor(
        id = id,
        version = 1,
        owner = CapabilityOwner.MeinHelferSynthetic,
        inputSchema = InputSchema.TodayV1,
        outputSchema = outputSchema,
        permission = permission,
        sensitivity = Sensitivity.Synthetic,
        offline = true,
        title = title,
        help = help,
        dependencies = emptySet(),
    )
}

/** A grant covers exactly one registered capability version in this process-local session. */
internal class MockConnect(
    private val registry: CapabilityRegistry = CapabilityRegistry(SyntheticMeinHelfer.descriptors),
    private val adapter: TodayAdapter = SyntheticMeinHelfer,
) {
    private val grants = mutableSetOf<CapabilityDescriptor>()

    fun capabilities(): List<CapabilityDescriptor> = registry.list()

    @Synchronized
    fun grantForTest(id: String, version: Int): Boolean {
        val capability = registry.find(id)?.takeIf { it.version == version } ?: return false
        grants.add(capability)
        return true
    }

    @Synchronized
    fun revokeForTest(id: String, version: Int) {
        registry.find(id)?.takeIf { it.version == version }?.let(grants::remove)
    }

    @Synchronized
    fun isGranted(id: String, version: Int): Boolean =
        registry.find(id)?.takeIf { it.version == version } in grants

    @Synchronized
    fun reset() {
        grants.clear()
    }

    @Synchronized
    fun request(request: ConnectRequest): ConnectResult {
        val capability = registry.find(request.id) ?: return ConnectResult.Rejected(Rejection.UnknownCapability)
        if (request.version != capability.version) return ConnectResult.Rejected(Rejection.IncompatibleVersion)
        val input = capability.inputSchema.parse(request.input)
            ?: return ConnectResult.Rejected(Rejection.MalformedInput)
        if (capability !in grants) return ConnectResult.Rejected(Rejection.Unauthorized)

        val payload = adapter.read(capability.outputSchema, input)
        return if (capability.outputSchema.accepts(payload, input.limit)) {
            ConnectResult.Read(payload)
        } else {
            ConnectResult.Rejected(Rejection.InvalidResponse)
        }
    }
}
