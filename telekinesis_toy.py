import sys
import os
import pprint

from pox.core import core
import pox.openflow.libopenflow_01 as of
from pox.lib.util import dpid_to_str
from pox.lib.util import str_to_bool
import time
import pox.lib.packet as pkt
from pox.lib.addresses import IPAddr, EthAddr

print 'HELLO! This is a toy example of Telekinesis controller.'

log = core.getLogger()

def dpidToSwitchname(vid):
    temp = vid.replace('-','')
    temp = temp.split('0')
    sname = 's'+temp[-1]
    return sname

def createPktoutmsg():
    arp_packet1 = pkt.arp(protosrc=IPAddr("10.0.0.2"), protodst=IPAddr("10.0.0.2"), hwsrc=EthAddr("00:00:00:00:00:12"),opcode = pkt.arp.REPLY)
    eth_packet1 = pkt.ethernet()
    eth_packet1.src = EthAddr("00:00:00:00:00:12")
    eth_packet1.dst = EthAddr("00:00:00:00:00:01")
    eth_packet1.type = pkt.ethernet.ARP_TYPE
    eth_packet1.set_payload(arp_packet1)

    arp_packet2 = pkt.arp(protosrc=IPAddr("10.0.0.1"), protodst=IPAddr("10.0.0.1"), hwsrc=EthAddr("00:00:00:00:00:11"),opcode = pkt.arp.REPLY)
    eth_packet2 = pkt.ethernet()
    eth_packet2.src = EthAddr("00:00:00:00:00:11")
    eth_packet2.dst = EthAddr("00:00:00:00:00:02")
    eth_packet2.type = pkt.ethernet.ARP_TYPE
    eth_packet2.set_payload(arp_packet2)

    # packet_out msg
    msg1 = of.ofp_packet_out()
    msg1.data = eth_packet1.pack()
    msg1.actions.append(of.ofp_action_output(port = int(2)))

    msg2 = of.ofp_packet_out()
    msg2.data = eth_packet2.pack()
    msg2.actions.append(of.ofp_action_output(port = int(1)))

    return msg1, msg2

class LearningSwitch (object):
    def __init__ (self, connection):
        self.connection = connection

        # We want to hear PacketIn messages, so we listen
        # to the connection
        connection.addListeners(self)

    def _handle_PacketIn (self, event):
        print '*** Warning: not expect PacketIn ', dpid_to_str(event.connection.dpid)
        packet = event.parsed


    def dropBcast(self):
        self.connection.send( of.ofp_flow_mod(priority=0))

    def addFlow(self, rle):
        for temp in rle:
            msg = of.ofp_flow_mod()
            msg.priority = int(temp[0])
            msg.match.dl_dst = EthAddr(temp[3])
            msg.actions.append(of.ofp_action_output(port = int(temp[4])))
            self.connection.send(msg)

    def addFlowRewrite(self, rle):
        for temp in rle:
            msg = of.ofp_flow_mod()
            msg.priority = 50001
            msg.match.dl_type = pkt.ethernet.IP_TYPE
            msg.match.nw_src = IPAddr(temp[0])
            msg.match.nw_dst = IPAddr(temp[1])
            msg.actions.append(of.ofp_action_dl_addr.set_src(temp[2]))
            msg.actions.append(of.ofp_action_dl_addr.set_dst(temp[3]))
            msg.actions.append(of.ofp_action_output(port = int(temp[4])))
            self.connection.send(msg)

    def dummyInject(self, sname, eedpid):
        msg1,msg2 = createPktoutmsg()
        print '*** Send dummy msg1 for '+sname
        core.openflow.sendToDPID(eedpid, msg1)

        print '*** Send dummy msg2 for '+sname
        core.openflow.sendToDPID(eedpid, msg2)


class versionflip_control (object):
    """
    Waits for OpenFlow switches to connect and inject dummy pkts to them.
    """
    def __init__ (self):
        core.openflow.addListeners(self)
        self.dpids = []
        self.events = []
        self.switches = []

    def _handle_ConnectionUp (self, event):
        log.debug("Connection %s" % (event.connection,))

        edpidstr = dpid_to_str(event.connection.dpid)
        edpid = event.connection.dpid

        self.dpids.append(edpid)
        self.events.append(event.connection)
        lswitch = LearningSwitch(event.connection)
        self.switches.append(lswitch)
        switchname = dpidToSwitchname(edpidstr)
        lswitch.dropBcast()

        time.sleep(4)
        print '*** Add rules in', switchname
        #lswitch.addFlow([['50001',600,0,'00:00:00:00:00:01',2], [50001,600,0,'00:00:00:00:00:02',1]])
        lswitch.addFlowRewrite([['10.0.0.1','10.0.0.2','00:00:00:00:00:11','00:00:00:00:00:02',1], ['10.0.0.2','10.0.0.1','00:00:00:00:00:12','00:00:00:00:00:01',2]])
        self.startSwitch()

    def startSwitch(self):
        for i in self.events:
            edpidstr = dpid_to_str(i.dpid)
            switchname = dpidToSwitchname(edpidstr)
            self.switches[0].dummyInject(switchname, i.dpid)


def launch ():
    core.registerNew(versionflip_control)
