package com.nickorton.sholto.engine

/** Which hand throws a punch, relative to the stance. */
enum class Hand { Lead, Rear }

/**
 * Which side leads. It only changes punch *names* (left or right), never the
 * numbers: `3` is always the lead hook (DESIGN.md §3.5).
 */
enum class Stance(
    val lead: String,
    val rear: String,
) {
    Orthodox(lead = "Left", rear = "Right"),
    Southpaw(lead = "Right", rear = "Left"),
}

/**
 * The punch vocabulary from DESIGN.md §3.1: `1`–`6`, plus body shots
 * `1B`–`4B`. Odd numbers are lead-hand punches, even numbers rear-hand.
 *
 * @property number 1 to 6.
 * @property body Thrown to the body rather than the head.
 */
enum class Punch(
    val number: Int,
    val body: Boolean,
) {
    One(1, body = false),
    Two(2, body = false),
    Three(3, body = false),
    Four(4, body = false),
    Five(5, body = false),
    Six(6, body = false),
    OneBody(1, body = true),
    TwoBody(2, body = true),
    ThreeBody(3, body = true),
    FourBody(4, body = true),
    ;

    /** As shown on screen: `3`, or `3B` for the body. */
    val label: String get() = if (body) "${number}B" else "$number"

    val hand: Hand get() = if (number % 2 == 1) Hand.Lead else Hand.Rear

    /**
     * The punch's name for [stance], e.g. `Left hook body` for `3B` when
     * orthodox, or `Right hook body` when southpaw. The jab and cross are the
     * same in both stances, since they're named by role rather than side.
     */
    fun fullName(stance: Stance): String {
        val side = if (hand == Hand.Lead) stance.lead else stance.rear
        val base =
            when (number) {
                1 -> "Jab"
                2 -> "Cross"
                3, 4 -> "$side hook"
                else -> "$side uppercut"
            }
        return if (body) "$base body" else base
    }
}
