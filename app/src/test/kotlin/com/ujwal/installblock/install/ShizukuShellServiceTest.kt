// SPDX-License-Identifier: GPL-3.0-or-later
package com.ujwal.installblock.install

import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ShizukuShellServiceTest {

    private fun checkSafe(vararg cmd: String): Boolean =
        ShizukuShellService.isSafeCommand(arrayOf(*cmd))

    @Test
    fun allowsSafePmInstallCommand() {
        assertTrue(
            checkSafe("pm", "install", "-r", "--user", "0", "/data/local/tmp/ib_test.apk"),
        )
    }

    @Test
    fun allowsSafePmUninstallCommand() {
        assertTrue(checkSafe("pm", "uninstall", "com.example.distraction"))
        assertTrue(checkSafe("pm", "uninstall", "--user", "0", "com.example.distraction"))
        assertTrue(checkSafe("cmd", "package", "uninstall", "com.example.distraction"))
        assertTrue(checkSafe("cmd", "package", "uninstall", "--user", "0", "com.example.distraction"))
    }

    @Test
    fun allowsSafePmUninstallKeepDataCommand() {
        assertTrue(checkSafe("pm", "uninstall", "-k", "com.example.distraction"))
        assertTrue(checkSafe("pm", "uninstall", "-k", "--user", "0", "com.example.distraction"))
        assertTrue(checkSafe("cmd", "package", "uninstall", "-k", "com.example.distraction"))
        assertTrue(checkSafe("cmd", "package", "uninstall", "-k", "--user", "0", "com.example.distraction"))
    }

    @Test
    fun allowsSafePmClearCommand() {
        assertTrue(checkSafe("pm", "clear", "com.example.distraction"))
        assertTrue(checkSafe("pm", "clear", "--user", "0", "com.example.distraction"))
    }

    @Test
    fun allowsSafeCmdPackageClearCommand() {
        assertTrue(checkSafe("cmd", "package", "clear", "com.example.distraction"))
        assertTrue(checkSafe("cmd", "package", "clear", "--user", "0", "com.example.distraction"))
    }

    @Test
    fun allowsSafeInstallExistingCommands() {
        assertTrue(checkSafe("pm", "install-existing", "com.example.distraction"))
        assertTrue(checkSafe("pm", "install-existing", "--user", "0", "com.example.distraction"))
        assertTrue(checkSafe("cmd", "package", "install-existing", "com.example.distraction"))
        assertTrue(checkSafe("cmd", "package", "install-existing", "--user", "0", "com.example.distraction"))
    }

    @Test
    fun allowsSafePmListPackagesCommands() {
        assertTrue(checkSafe("pm", "list", "packages"))
        assertTrue(checkSafe("pm", "list", "packages", "-u"))
    }

    @Test
    fun allowsSafeDpmSetActiveAdminCommand() {
        assertTrue(
            checkSafe(
                "dpm",
                "set-active-admin",
                "--user",
                "current",
                "com.example.distraction/com.ujwal.installblock.placeholder.BlockAdmin",
            ),
        )
        assertTrue(
            checkSafe(
                "dpm",
                "set-active-admin",
                "--user",
                "0",
                "com.example.distraction/com.ujwal.installblock.placeholder.BlockAdmin",
            ),
        )
    }

    @Test
    fun allowsSafeDpmRemoveActiveAdminCommand() {
        assertTrue(
            checkSafe(
                "dpm",
                "remove-active-admin",
                "--user",
                "current",
                "com.example.distraction/com.ujwal.installblock.placeholder.BlockAdmin",
            ),
        )
    }

    @Test
    fun rejectsArbitraryCommands() {
        assertFalse(checkSafe("rm", "-rf", "/data/local/tmp"))
        assertFalse(checkSafe("cat", "/etc/hosts"))
        assertFalse(checkSafe("reboot"))
    }

    @Test
    fun rejectsShellInjectionMetacharacters() {
        assertFalse(checkSafe("pm", "uninstall", "--user", "0", "com.example.app;rm -rf /"))
        assertFalse(checkSafe("pm", "uninstall", "--user", "0", "com.example.app|cat"))
        assertFalse(checkSafe("pm", "uninstall", "--user", "0", "com.example.app&reboot"))
        assertFalse(checkSafe("pm", "uninstall", "--user", "0", "com.example.app`id`"))
        assertFalse(checkSafe("pm", "uninstall", "--user", "0", "com.example.app$(id)"))
        assertFalse(checkSafe("pm", "uninstall", "--user", "0", "com.example.app\nrm"))
        assertFalse(checkSafe("pm", "install", "-r", "--user", "0", "/data/local/tmp/test.apk>out"))
        assertFalse(checkSafe("pm", "clear", "com.example.app;rm -rf /"))
        assertFalse(checkSafe("pm", "install-existing", "--user", "0", "com.example.app;rm -rf /"))
        assertFalse(checkSafe("cmd", "package", "clear", "--user", "0", "com.example.app|cat"))
        assertFalse(checkSafe("cmd", "package", "install-existing", "--user", "0", "com.example.app&reboot"))
    }

    @Test
    fun rejectsMalformedPackageNames() {
        assertFalse(checkSafe("pm", "uninstall", "--user", "0", "invalid..pkg"))
        assertFalse(checkSafe("pm", "uninstall", "--user", "0", "../evil"))
        assertFalse(checkSafe("pm", "uninstall", "--user", "0", "123.numeric.start"))
        assertFalse(checkSafe("pm", "clear", "invalid..pkg"))
        assertFalse(checkSafe("pm", "install-existing", "--user", "0", "invalid..pkg"))
        assertFalse(checkSafe("cmd", "package", "clear", "--user", "0", "../evil"))
        assertFalse(checkSafe("cmd", "package", "install-existing", "--user", "0", "../evil"))
    }

    @Test
    fun rejectsInvalidAdminComponents() {
        assertFalse(checkSafe("dpm", "set-active-admin", "--user", "current", "no-slash-here"))
        assertFalse(checkSafe("dpm", "set-active-admin", "--user", "current", "com.app/"))
        assertFalse(checkSafe("dpm", "set-active-admin", "--user", "current", "/com.admin"))
    }
}
