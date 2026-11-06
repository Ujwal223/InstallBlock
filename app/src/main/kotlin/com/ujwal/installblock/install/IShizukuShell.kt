// SPDX-License-Identifier: GPL-3.0-or-later
package com.ujwal.installblock.install

import android.os.Binder
import android.os.IBinder
import android.os.IInterface
import android.os.Parcel

interface IShizukuShell : IInterface {
    fun execute(command: Array<String>): String
    fun installApk(bytes: ByteArray, packageName: String): String

    abstract class Stub : Binder(), IShizukuShell {
        init {
            attachInterface(this, DESCRIPTOR)
        }

        override fun asBinder(): IBinder = this

        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            requireNotNull(reply)
            if (code == INTERFACE_TRANSACTION) {
                reply.writeString(DESCRIPTOR)
                return true
            }
            return when (code) {
                TRANSACTION_EXECUTE -> {
                    data.enforceInterface(DESCRIPTOR)
                    val command = data.createStringArray() ?: emptyArray()
                    val result = execute(command)
                    reply.writeNoException()
                    reply.writeString(result)
                    true
                }
                TRANSACTION_INSTALL_APK -> {
                    data.enforceInterface(DESCRIPTOR)
                    val bytes = data.createByteArray() ?: ByteArray(0)
                    val packageName = data.readString() ?: ""
                    val result = installApk(bytes, packageName)
                    reply.writeNoException()
                    reply.writeString(result)
                    true
                }
                else -> super.onTransact(code, data, reply, flags)
            }
        }

        companion object {
            private const val DESCRIPTOR = "com.ujwal.installblock.install.IShizukuShell"
            private const val TRANSACTION_EXECUTE = IBinder.FIRST_CALL_TRANSACTION
            private const val TRANSACTION_INSTALL_APK = IBinder.FIRST_CALL_TRANSACTION + 1

            fun asInterface(binder: IBinder): IShizukuShell =
                if (binder.queryLocalInterface(DESCRIPTOR) is IShizukuShell) {
                    binder.queryLocalInterface(DESCRIPTOR) as IShizukuShell
                } else {
                    Proxy(binder)
                }
        }

        private class Proxy(private val binder: IBinder) : IShizukuShell {
            override fun asBinder(): IBinder = binder

            override fun execute(command: Array<String>): String {
                val data = Parcel.obtain()
                val reply = Parcel.obtain()
                return try {
                    data.writeInterfaceToken(DESCRIPTOR)
                    data.writeStringArray(command)
                    binder.transact(TRANSACTION_EXECUTE, data, reply, 0)
                    reply.readException()
                    reply.readString() ?: error("Empty Shizuku service response")
                } finally {
                    data.recycle()
                    reply.recycle()
                }
            }

            override fun installApk(bytes: ByteArray, packageName: String): String {
                val data = Parcel.obtain()
                val reply = Parcel.obtain()
                return try {
                    data.writeInterfaceToken(DESCRIPTOR)
                    data.writeByteArray(bytes)
                    data.writeString(packageName)
                    binder.transact(TRANSACTION_INSTALL_APK, data, reply, 0)
                    reply.readException()
                    reply.readString() ?: error("Empty Shizuku service response")
                } finally {
                    data.recycle()
                    reply.recycle()
                }
            }
        }
    }
}
