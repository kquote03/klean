package app.klean.shared.delegation

/**
 * Which agent actions wait for the user (plan M23). More than [MAX_RECIPIENTS_WITHOUT_APPROVAL] recipients
 * always do, in every mode, whatever the agent says; interactive and partial delegations ask for everything;
 * an agentic one acts alone otherwise.
 */
object ApprovalPolicy {
    const val MAX_RECIPIENTS_WITHOUT_APPROVAL = 5

    fun needsApproval(mode: String, action: String, recipients: List<String>): Boolean {
        val people = recipients.map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet()
        if (people.size > MAX_RECIPIENTS_WITHOUT_APPROVAL) return true
        return mode != "AGENTIC"
    }
}
