#!/usr/bin/python

import sys
import re
import time
import itertools as it

from mininet.net import Mininet
from mininet.node import OVSKernelSwitch, OVSSwitch, RemoteController
from mininet.cli import CLI
from mininet.log import setLogLevel
from mininet.util import dumpNodeConnections


def mac2dpid(vid, length):
  rawdpid = vid.replace(":","")
  zeros = '0'*(length-len(rawdpid))
  dpid = zeros+rawdpid
  return dpid

def remoteControllerName():
  return "remote"

def switchName(vid):
  return "s%d" % vid

def hostName(vid):
  return "h%d" % vid

def runExperiment():
	
	net = Mininet(controller=RemoteController, switch=OVSKernelSwitch)
	print "*** Creating switches"
	switches = {}
	for sid in range(3):
		switches[sid] = net.addSwitch(switchName(sid+1), dpid=mac2dpid('00:50:00:00:00:0'+str(sid+1),16), failMode='standalone')

	print "*** Creating hosts"
	hosts = {}
	for hid in range(2):
		hosts[hid] = net.addHost(hostName(hid+1), mac="00:00:00:00:00:0"+str(hid+1), disableIPv6=True)

	print "*** Creating links"
	print "*** Adding switch-to-host links"
	net.addLink(hosts[0], switches[0])
	net.addLink(hosts[1], switches[1])

	print "*** Adding switch-to-switch links"
	net.addLink(switches[0], switches[1])
	net.addLink(switches[1], switches[2])
	net.addLink(switches[2], switches[0])

        # add remote controller
        print "*** Creating controllers"
        name = remoteControllerName()
        controllers_remote = net.addController(name, controller=RemoteController,ip='127.0.0.1', port=6633)


	print "*** Starting network"
	net.build()
       
        # two legacy switches do not connect to the controller 
        for sid in range(2):
            switches[sid].start([])
        # the third switch connects to the controller
        switches[2].start([controllers_remote])

        #switches[1].cmd( 'tcpdump -e -i s1-eth2 >> tcpdump_s1-eth2 &' )
        #switches[1].cmd( 'tcpdump -e -i s2-eth2 >> tcpdump_s2-eth2 &' )
        #switches[1].cmd( 'tcpdump -e -i s1-eth3 >> tcpdump_s1-eth3 &' )
        #switches[1].cmd( 'tcpdump -e -i s2-eth3 >> tcpdump_s2-eth3 &' )

        print hosts[0].cmd( 'ping -c 10', hosts[1].IP() )

	print "*** Running CLI"
	CLI(net)

	print "*** Stopping network"
	net.stop()

def main():
	runExperiment()

if __name__ == '__main__':
	# Tell mininet to print useful information
	setLogLevel('info')
	main()
