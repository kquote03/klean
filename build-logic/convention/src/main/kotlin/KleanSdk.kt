object KleanSdk {
    const val COMPILE = 37
    const val MIN = 28
    const val TARGET = 37

    /** Robolectric (SDK 36 sandbox) on JDK 21 needs reflective access to jdk.internal.access. */
    val ROBOLECTRIC_JVM_ARGS = listOf(
        "--add-exports=java.base/jdk.internal.access=ALL-UNNAMED",
        "--add-opens=java.base/jdk.internal.access=ALL-UNNAMED",
    )
}
