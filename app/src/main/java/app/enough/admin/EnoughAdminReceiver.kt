package app.enough.admin

import android.app.admin.DeviceAdminReceiver

/**
 * PINNED: do not move or rename. The OS binds device owner to the component name
 * `app.enough/.admin.EnoughAdminReceiver` (see `AdbSetup.COMPONENT`). Moving it in an
 * update drops device owner on installed phones, and getting it back needs a factory reset.
 */
class EnoughAdminReceiver : DeviceAdminReceiver()
