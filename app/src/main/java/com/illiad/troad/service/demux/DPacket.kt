package com.illiad.troad.service.demux

import org.pcap4j.packet.Packet
import org.pcap4j.packet.TcpPacket
import org.pcap4j.packet.TcpPacket.TcpHeader
import java.util.TimerTask

class DPacket(val packet: Packet): TimerTask() {
    override fun run() {
    }

    fun getsessionId(): String{
        return ""
    }
}