package app.klean.nav

import app.klean.core.notifications.permissions.PermissionChecker
import app.klean.core.notifications.permissions.PermissionStatus

class FakePermissionChecker(
    var status: PermissionStatus = PermissionStatus(false, false, false, false, false),
    override val sdkInt: Int = 33,
) : PermissionChecker {
    override fun current(): PermissionStatus = status
}
