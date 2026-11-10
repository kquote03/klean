package app.klean.feature.workout.model

import org.junit.Assert.assertEquals
import org.junit.Test

class TemplateTextTest {
    private fun strength(name: String, sets: Int, reps: Int?, kg: Double? = null) = WorkoutItem(name, WorkoutKind.STRENGTH, sets, reps, weightKg = kg)

    @Test fun parsesTheExampleFromThePlan() {
        assertEquals(
            listOf(
                strength("pushups", 3, 15),
                strength("squats", 3, 20),
                WorkoutItem("plank", WorkoutKind.TIMED, durationS = 60),
                WorkoutItem("run", WorkoutKind.CARDIO, distanceM = 5000),
            ),
            TemplateText.parse("3x15 pushups, 3×20 squats, 1 min plank, 5 km run"),
        )
    }

    @Test fun tableOfInputs() {
        val table = listOf(
            "30 pushups" to listOf(strength("pushups", 1, 30)),
            "pushups 3x12" to listOf(strength("pushups", 3, 12)),
            "5x5 squats @ 60 kg" to listOf(strength("squats", 5, 5, 60.0)),
            "45 s plank; 90 sec wall sit" to listOf(
                WorkoutItem("plank", WorkoutKind.TIMED, durationS = 45),
                WorkoutItem("wall sit", WorkoutKind.TIMED, durationS = 90),
            ),
            "800 m run" to listOf(WorkoutItem("run", WorkoutKind.CARDIO, distanceM = 800)),
            "30 min walk" to listOf(WorkoutItem("walk", WorkoutKind.CARDIO, durationS = 1800)),
            "5k run\nyoga" to listOf(
                WorkoutItem("run", WorkoutKind.CARDIO, distanceM = 5000),
                WorkoutItem("yoga", WorkoutKind.STRENGTH),
            ),
            "2.5 km swim" to listOf(WorkoutItem("swim", WorkoutKind.CARDIO, distanceM = 2500)),
            "  , ,  " to emptyList(),
        )
        table.forEach { (text, expected) -> assertEquals(text, expected, TemplateText.parse(text)) }
    }

    @Test fun formatIsTheInverseOfParse() {
        val text = "3×15 pushups, 5×5 squats @ 60 kg, 1 min plank, 45 s side plank, 5 km run, 2.5 km swim, 800 m row, 30 min walk, 20 lunges, yoga"
        val items = TemplateText.parse(text)
        assertEquals(text, TemplateText.format(items))
        assertEquals(items, TemplateText.parse(TemplateText.format(items)))
    }

    @Test fun logVerbsAreStripped() {
        assertEquals(listOf(strength("pushups", 1, 30)), TemplateText.parseLog("did 30 pushups"))
        assertEquals(listOf(WorkoutItem("run", WorkoutKind.CARDIO, distanceM = 5000)), TemplateText.parseLog("ran 5k"))
        assertEquals(listOf(WorkoutItem("run", WorkoutKind.CARDIO, distanceM = 3000)), TemplateText.parseLog("Ran 3 km"))
        assertEquals(listOf(WorkoutItem("swim", WorkoutKind.CARDIO, durationS = 1200)), TemplateText.parseLog("went for a 20 min swim"))
        assertEquals(emptyList<WorkoutItem>(), TemplateText.parseLog("gym"))
        assertEquals(listOf(strength("squats", 3, 10)), TemplateText.parseLog("done 3x10 squats today"))
    }
}
