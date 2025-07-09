package com.illiad.troad.service.channel

import java.io.FileDescriptor
import java.net.SocketAddress

class FildesAddress(private val fd: FileDescriptor) : SocketAddress(), Comparable<FildesAddress?> {

    val isValid: Boolean = fd.valid()

    override fun compareTo(other: FildesAddress?): Int {
        return 0
    }

    override fun hashCode(): Int {
        return fd.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        return fd.hashCode() == other.hashCode()
    }

    override fun toString(): String {
        return fdNumber(fd).toString()
    }

    private fun fdNumber(fd: FileDescriptor): Int {
        val method = fd.javaClass.getMethod("getInt$")
        return method.invoke(fd) as Int
    }
}

