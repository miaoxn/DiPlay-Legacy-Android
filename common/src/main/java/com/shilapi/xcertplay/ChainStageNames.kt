package com.shilapi.xcertplay

import com.shilapi.xcertplay.airplay.ChainKind
import com.shilapi.xcertplay.host.R

/**
 * Short on-screen names for the connection-chain steps, one per
 * `WIRED-CHAIN [n/16]` / `WIRELESS-CHAIN [n/12]` log step. The chain log
 * lines carry parameters and English tech wording that must not go on
 * screen as-is; these names are the display form.
 *
 * Hard constraint (feedback-path design §1.1): the table must cover every
 * step of both chains — the dot a user screenshots must line up with the
 * log number. [ChainStageNamesTest] locks the coverage; adding or removing
 * a chain step without updating this table fails the test.
 *
 * Strings use the `chain_step_` prefix (not `settings_`, so the settings
 * translation check does not apply) but are still translated into every
 * locale, like all other user-facing copy.
 */
internal object ChainStageNames {

    private val wired = mapOf<Int, Int>(
        1 to R.string.chain_step_wired_01,
        2 to R.string.chain_step_wired_02,
        3 to R.string.chain_step_wired_03,
        4 to R.string.chain_step_wired_04,
        5 to R.string.chain_step_wired_05,
        6 to R.string.chain_step_wired_06,
        7 to R.string.chain_step_wired_07,
        8 to R.string.chain_step_wired_08,
        9 to R.string.chain_step_wired_09,
        10 to R.string.chain_step_wired_10,
        11 to R.string.chain_step_wired_11,
        12 to R.string.chain_step_wired_12,
        13 to R.string.chain_step_wired_13,
        14 to R.string.chain_step_wired_14,
        15 to R.string.chain_step_wired_15,
        16 to R.string.chain_step_wired_16,
    )

    private val wireless = mapOf<Int, Int>(
        1 to R.string.chain_step_wireless_01,
        2 to R.string.chain_step_wireless_02,
        3 to R.string.chain_step_wireless_03,
        4 to R.string.chain_step_wireless_04,
        5 to R.string.chain_step_wireless_05,
        6 to R.string.chain_step_wireless_06,
        7 to R.string.chain_step_wireless_07,
        8 to R.string.chain_step_wireless_08,
        9 to R.string.chain_step_wireless_09,
        10 to R.string.chain_step_wireless_10,
        11 to R.string.chain_step_wireless_11,
        12 to R.string.chain_step_wireless_12,
    )

    fun nameRes(kind: ChainKind, step: Int): Int? = when (kind) {
        ChainKind.WIRED -> wired[step]
        ChainKind.WIRELESS -> wireless[step]
    }

    fun coveredSteps(kind: ChainKind): Set<Int> = when (kind) {
        ChainKind.WIRED -> wired.keys
        ChainKind.WIRELESS -> wireless.keys
    }
}
