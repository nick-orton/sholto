package com.nickorton.sholto.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.nickorton.sholto.audio.Cue
import com.nickorton.sholto.engine.RoundConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsRepositoryTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `settings and user presets persist across repository instances`() =
        runTest {
            val file = tempFolder.newFile("settings.preferences_pb")
            val scope1 = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + SupervisorJob())
            val repo1 =
                SettingsRepository(
                    PreferenceDataStoreFactory.create(scope = scope1) { file },
                    newId = { "preset-1" },
                )

            assertEquals(TimerSettings(), repo1.settings.first())
            assertEquals(emptyList<Preset>(), repo1.userPresets.first())

            repo1.updateSettings {
                it
                    .withRoundLength(150_000L)
                    .withWarningLength(15_000L)
                    .withRestLength(45_000L)
                    .withRounds(8)
                    .withGetReadyLength(5_000L)
                    .withSound(Cue.Warning, on = false)
                    .copy(tapBehavior = TapBehavior.Stop, vibration = false, keepScreenOn = false)
            }
            val saved = repo1.savePreset("  Bag   work  ")
            assertEquals("preset-1", saved.id)
            assertEquals("Bag work", saved.name)
            scope1.cancel()

            // Simulate app restart with a fresh DataStore reading the same file.
            val scope2 = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + SupervisorJob())
            val repo2 = SettingsRepository(PreferenceDataStoreFactory.create(scope = scope2) { file })
            val restored = repo2.settings.first()
            assertEquals(RoundConfig(150_000L, 15_000L, 45_000L, 8, 5_000L), restored.round)
            assertEquals(TapBehavior.Stop, restored.tapBehavior)
            assertEquals(Cue.entries.toSet() - Cue.Warning, restored.sounds)
            assertEquals(false, restored.vibration)
            assertEquals(false, restored.keepScreenOn)
            assertEquals(listOf(saved), repo2.userPresets.first())
            scope2.cancel()
        }

    @Test
    fun `apply, rename and delete presets`() =
        runTest {
            val file = tempFolder.newFile("presets.preferences_pb")
            var nextId = 1
            val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + SupervisorJob())
            val repo =
                SettingsRepository(
                    PreferenceDataStoreFactory.create(scope = scope) { file },
                    newId = { "id-${nextId++}" },
                )

            repo.applyPreset(Presets.builtIn.first { it.id == "amateur" })
            assertEquals(
                3,
                repo.settings
                    .first()
                    .round.rounds,
            )

            repo.updateSettings { it.withRounds(5) }
            val p = repo.savePreset("Five rounds")
            repo.renamePreset(p.id, "5 rounds")
            assertEquals(
                "5 rounds",
                repo.userPresets
                    .first()
                    .single()
                    .name,
            )

            assertThrows(IllegalArgumentException::class.java) {
                kotlinx.coroutines.runBlocking { repo.savePreset("Pro") }
            }

            repo.deletePreset(p.id)
            assertEquals(emptyList<Preset>(), repo.userPresets.first())
            // Deleting a preset leaves the current settings untouched.
            assertEquals(
                5,
                repo.settings
                    .first()
                    .round.rounds,
            )
            scope.cancel()
        }

    @Test
    fun `preset codec skips damaged lines and clamps out-of-range values`() {
        val raw =
            listOf(
                "good\t180000\t30000\t60000\t6\tSix rounds",
                "clamped\t5000\t5000\t600000\t200\tClamped",
                "bad-warning\t60000\t90000\t30000\t3\tImpossible",
                "garbage",
            ).joinToString("\n")
        val decoded = PresetCodec.decode(raw)
        assertEquals(
            listOf(
                Preset("good", "Six rounds", 180_000L, 30_000L, 60_000L, 6),
                Preset("clamped", "Clamped", 30_000L, 5_000L, 5 * 60_000L, 99),
            ),
            decoded,
        )
    }
}
