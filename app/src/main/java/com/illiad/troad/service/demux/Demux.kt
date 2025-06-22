package com.illiad.troad.service.demux

object Demux {
    val sessions: HashMap<UShort, Session> = hashMapOf()
    val bufffers: HashMap<UShort, List<DPacket>> = hashMapOf()
}