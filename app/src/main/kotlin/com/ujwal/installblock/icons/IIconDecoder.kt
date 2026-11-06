// SPDX-License-Identifier: GPL-3.0-or-later
package com.ujwal.installblock.icons

import android.os.Binder
import android.os.IBinder
import android.os.IInterface
import android.os.Parcel

interface IIconDecoder : IInterface {
    fun decode(bytes: ByteArray): ByteArray?

    abstract class Stub : Binder(), IIconDecoder {
        init {
            attachInterface(this, DESCRIPTOR)
        }

        override fun asBinder(): IBinder = this

        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            if (code == INTERFACE_TRANSACTION) {
                reply?.writeString(DESCRIPTOR)
                return true
            }
            if (code != TRANSACTION_DECODE || reply == null) {
                return super.onTransact(code, data, reply, flags)
            }
            data.enforceInterface(DESCRIPTOR)
            val result = decode(data.createByteArray() ?: ByteArray(0))
            reply.writeNoException()
            reply.writeByteArray(result)
            return true
        }

        companion object {
            private const val DESCRIPTOR = "com.ujwal.installblock.icons.IIconDecoder"
            private const val TRANSACTION_DECODE = IBinder.FIRST_CALL_TRANSACTION

            fun asInterface(binder: IBinder): IIconDecoder =
                if (binder.queryLocalInterface(DESCRIPTOR) is IIconDecoder) {
                    binder.queryLocalInterface(DESCRIPTOR) as IIconDecoder
                } else {
                    Proxy(binder)
                }
        }

        private class Proxy(private val binder: IBinder) : IIconDecoder {
            override fun asBinder(): IBinder = binder

            override fun decode(bytes: ByteArray): ByteArray? {
                val data = Parcel.obtain()
                val reply = Parcel.obtain()
                return try {
                    data.writeInterfaceToken(DESCRIPTOR)
                    data.writeByteArray(bytes)
                    binder.transact(TRANSACTION_DECODE, data, reply, 0)
                    reply.readException()
                    reply.createByteArray()
                } finally {
                    data.recycle()
                    reply.recycle()
                }
            }
        }
    }
}
