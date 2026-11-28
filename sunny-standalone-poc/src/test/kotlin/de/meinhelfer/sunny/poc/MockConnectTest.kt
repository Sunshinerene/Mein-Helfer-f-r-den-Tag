package de.meinhelfer.sunny.poc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class MockConnectTest {
    private val tasks = SyntheticMeinHelfer.descriptors[0]
    private val appointments = SyntheticMeinHelfer.descriptors[1]
    private val adapter = RecordingAdapter()
    private val connect = MockConnect(adapter = adapter)

    @Test
    fun catalogExposesVersionedOfflineSyntheticDescriptors() {
        assertEquals(
            listOf("meinhelfer.today.tasks", "meinhelfer.today.appointments"),
            connect.capabilities().map { it.id },
        )
        assertEquals(
            listOf(TestPermission.ReadTodayTasks, TestPermission.ReadTodayAppointments),
            connect.capabilities().map { it.permission },
        )
        for (capability in connect.capabilities()) {
            assertEquals(1, capability.version)
            assertEquals(CapabilityOwner.MeinHelferSynthetic, capability.owner)
            assertEquals(Sensitivity.Synthetic, capability.sensitivity)
            assertEquals(InputSchema.TodayV1, capability.inputSchema)
            assertTrue(capability.offline)
            assertTrue(capability.help.isNotBlank())
            assertTrue(capability.dependencies.isEmpty())
        }
    }

    @Test
    fun duplicateIdIsRejectedEvenWithDifferentVersion() {
        assertThrows(IllegalArgumentException::class.java) {
            CapabilityRegistry(listOf(tasks, tasks.copy(version = 2)))
        }
    }

    @Test
    fun unknownCapabilityCannotBeGrantedOrRead() {
        val unknown = "meinhelfer.today.unknown"
        assertFalse(connect.grantForTest(unknown, 1))
        assertFalse(connect.isGranted(unknown, 1))
        assertEquals(
            ConnectResult.Rejected(Rejection.UnknownCapability),
            connect.request(ConnectRequest(unknown, 1, validInput())),
        )
        assertEquals(0, adapter.calls.size)
    }

    @Test
    fun incompatibleVersionCannotBeGrantedAndDoesNotUseOtherGrant() {
        assertTrue(connect.grantForTest(tasks.id, tasks.version))
        assertFalse(connect.grantForTest(tasks.id, 2))
        assertFalse(connect.isGranted(tasks.id, 2))
        assertEquals(
            ConnectResult.Rejected(Rejection.IncompatibleVersion),
            connect.request(ConnectRequest(tasks.id, 2, validInput())),
        )
        assertEquals(0, adapter.calls.size)
    }

    @Test
    fun malformedInputIsRejectedWithGrantWithoutCallingAdapter() {
        assertTrue(connect.grantForTest(tasks.id, tasks.version))
        val invalid = listOf(
            emptyMap(),
            mapOf("day" to "today"),
            mapOf("limit" to "1"),
            validInput() + ("extra" to "fixture"),
            validInput() + ("day" to "tomorrow"),
            validInput() + ("limit" to "0"),
            validInput() + ("limit" to "6"),
            validInput() + ("limit" to " 1"),
            validInput() + ("limit" to "01"),
            validInput() + ("limit" to "１"),
            validInput() + ("limit" to "9".repeat(4096)),
        )

        for (input in invalid) {
            assertEquals(
                ConnectResult.Rejected(Rejection.MalformedInput),
                connect.request(ConnectRequest(tasks.id, tasks.version, input)),
            )
        }
        assertEquals(0, adapter.calls.size)
    }

    @Test
    fun bothCapabilitiesDenyByDefaultWithoutCallingAdapter() {
        assertFalse(connect.isGranted(tasks.id, tasks.version))
        assertFalse(connect.isGranted(appointments.id, appointments.version))
        assertEquals(ConnectResult.Rejected(Rejection.Unauthorized), read(tasks))
        assertEquals(ConnectResult.Rejected(Rejection.Unauthorized), read(appointments))
        assertEquals(0, adapter.calls.size)
    }

    @Test
    fun taskGrantAllowsOnlySelectedBoundedFixture() {
        assertTrue(connect.grantForTest(tasks.id, tasks.version))
        assertTrue(connect.isGranted(tasks.id, tasks.version))
        assertEquals(ConnectResult.Read(TodayPayload.Tasks(listOf(SampleTask.TestList))), read(tasks, "1"))
        assertEquals(ConnectResult.Rejected(Rejection.Unauthorized), read(appointments))
        assertEquals(listOf(OutputSchema.TodayTasksV1 to TodayQuery(1)), adapter.calls)
    }

    @Test
    fun appointmentGrantReturnsOnlyFixedAppointmentsWithinLimit() {
        assertTrue(connect.grantForTest(appointments.id, appointments.version))
        assertEquals(
            ConnectResult.Read(TodayPayload.Appointments(listOf(SampleAppointment.Morning))),
            read(appointments, "1"),
        )
        assertEquals(
            ConnectResult.Read(TodayPayload.Appointments(SampleAppointment.entries)),
            read(appointments, "5"),
        )
        assertEquals(
            listOf(
                OutputSchema.TodayAppointmentsV1 to TodayQuery(1),
                OutputSchema.TodayAppointmentsV1 to TodayQuery(5),
            ),
            adapter.calls,
        )
    }

    @Test
    fun revokeBlocksSubsequentAccessAndPreservesOtherGrant() {
        connect.grantForTest(tasks.id, tasks.version)
        connect.grantForTest(appointments.id, appointments.version)
        assertTrue(read(tasks) is ConnectResult.Read)

        connect.revokeForTest(tasks.id, tasks.version)

        assertFalse(connect.isGranted(tasks.id, tasks.version))
        assertEquals(ConnectResult.Rejected(Rejection.Unauthorized), read(tasks))
        assertEquals(1, adapter.calls.size)
        assertTrue(read(appointments) is ConnectResult.Read)
        assertEquals(2, adapter.calls.size)
    }

    @Test
    fun resetClearsEveryGrantAndNewSessionStartsWithoutGrants() {
        connect.grantForTest(tasks.id, tasks.version)
        connect.grantForTest(appointments.id, appointments.version)
        val newSession = MockConnect(adapter = adapter)
        assertEquals(ConnectResult.Rejected(Rejection.Unauthorized), newSession.request(request(tasks)))

        connect.reset()

        assertEquals(ConnectResult.Rejected(Rejection.Unauthorized), read(tasks))
        assertEquals(ConnectResult.Rejected(Rejection.Unauthorized), read(appointments))
        assertEquals(0, adapter.calls.size)
    }

    @Test
    fun rejectsAdapterPayloadWithWrongType() {
        val wrongType = MockConnect(adapter = TodayAdapter { _, _ ->
            TodayPayload.Appointments(listOf(SampleAppointment.Morning))
        })
        wrongType.grantForTest(tasks.id, tasks.version)
        assertEquals(ConnectResult.Rejected(Rejection.InvalidResponse), wrongType.request(request(tasks)))
    }

    @Test
    fun rejectsAdapterPayloadLargerThanRequestedLimit() {
        val oversized = MockConnect(adapter = TodayAdapter { _, _ -> TodayPayload.Tasks(SampleTask.entries) })
        oversized.grantForTest(tasks.id, tasks.version)
        assertEquals(ConnectResult.Rejected(Rejection.InvalidResponse), oversized.request(request(tasks, "1")))
    }

    private fun read(capability: CapabilityDescriptor, limit: String = "3") =
        connect.request(request(capability, limit))

    private fun request(capability: CapabilityDescriptor, limit: String = "3") =
        ConnectRequest(capability.id, capability.version, validInput(limit))

    private fun validInput(limit: String = "3") = mapOf("day" to "today", "limit" to limit)

    private class RecordingAdapter : TodayAdapter {
        val calls = mutableListOf<Pair<OutputSchema, TodayQuery>>()

        override fun read(schema: OutputSchema, query: TodayQuery): TodayPayload {
            calls.add(schema to query)
            return SyntheticMeinHelfer.read(schema, query)
        }
    }
}
