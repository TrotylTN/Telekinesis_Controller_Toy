/*
 * Copyright 2016-present Open Networking Laboratory
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.onosproject.learningswitch;

import com.google.common.collect.Maps;

import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.ReferenceCardinality;
import org.onlab.packet.*;
import org.onlab.util.Tools;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.event.Event;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.DeviceId;
import org.onosproject.net.PortNumber;
import org.onosproject.net.device.DeviceService;
import org.onosproject.net.driver.DriverService;
import org.onosproject.net.flow.*;
import org.onosproject.net.flowobjective.DefaultForwardingObjective;
import org.onosproject.net.flowobjective.FlowObjectiveService;
import org.onosproject.net.flowobjective.ForwardingObjective;
import org.onosproject.net.link.LinkEvent;
import org.onosproject.net.packet.PacketContext;
import org.onosproject.net.packet.PacketPriority;
import org.onosproject.net.packet.PacketProcessor;
import org.onosproject.net.packet.PacketService;
import org.onosproject.net.topology.TopologyEvent;
import org.onosproject.net.topology.TopologyListener;
import org.onosproject.net.topology.TopologyService;
import org.onosproject.openflow.controller.*;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFFlowAdd;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFPacketOut;
import org.projectfloodlight.openflow.protocol.ver13.*;
import org.projectfloodlight.openflow.protocol.OFPortDesc;
import org.projectfloodlight.openflow.protocol.OFVersion;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.OFBufferId;
import org.projectfloodlight.openflow.types.OFPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.projectfloodlight.openflow.protocol.OFPortStatus;
import org.onosproject.provider.of.flow.impl.*;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.onlab.util.Tools.groupedThreads;
import static org.onlab.packet.ARP.OP_REPLY;

/**
 * Tutorial class used to help build a basic onos learning switch application.
 * Edit your code in the activate, deactivate, and actLikeSwitch methods.
 */
@Component(immediate = true, enabled = true)
public class LearningSwitchTutorial {
    // Instantiates the relevant services.

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected TopologyService topologyService;



    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected FlowRuleService flowRuleService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected CoreService coreService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected DeviceService deviceService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected DriverService driverService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected OpenFlowController controller;
    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected FlowObjectiveService flowObjectiveService;


    private final Logger log = LoggerFactory.getLogger(getClass());

    /*
     * Defining macTables as a concurrent map allows multiple threads and packets to
     * use the map without an issue.
     */

   // protected ExecutorService operationsService =
     //       Executors.newFixedThreadPool(32, Tools.groupedThreads("onos/flowservice", "operations-%d", log));

    private ApplicationId appId;
    private PacketProcessor processor;

    private final InternalDeviceProvider switchlistener = new InternalDeviceProvider();

    //private final TopologyListener topologyListener = new InternalTopologyListener();

    /**
     * Create a variable of the SwitchPacketProcessor class using the PacketProcessor defined above.
     * Activates the app.
     *
     * Create code to add a processor
     */
    @Activate
    protected void activate() {
        log.info("Started");
        System.out.println("Tutorial started");

        appId = coreService.getAppId("org.onosproject.learningswitch"); //equal to the name shown in pom.xml file

        //Create and processor and add it using packetService

        /*
         * Restricts packet types to IPV4 and ARP by only requesting those types
         */
        //packetService.requestPackets(DefaultTrafficSelector.builder()
          //      .matchEthType(Ethernet.TYPE_IPV4).build(), PacketPriority.REACTIVE, appId);
        //packetService.requestPackets(DefaultTrafficSelector.builder()
          //      .matchEthType(Ethernet.TYPE_ARP).build(), PacketPriority.REACTIVE, appId);

        //topologyService.addListener(topologyListener);
        controller.addListener(switchlistener);

    }

    /**
     * Deactivates the processor by removing it.
     *
     * Create code to remove the processor.
     */
    @Deactivate
    protected void deactivate() {
        log.info("Stopped");

        //Remove the processor
    }




    private class InternalDeviceProvider implements OpenFlowSwitchListener {
        Timer timer ;

        @Override
        public void switchAdded(Dpid dpid) {

            System.out.println(dpid.toString() + " is connected");

            timer = new Timer();
            timer.schedule(new RemindTask(dpid), 15000);


        }

        class RemindTask extends TimerTask {
            Dpid dpid;
            public RemindTask(Dpid dpid) {
                super();
                this.dpid = dpid;

            }
            public void run() {
                System.out.println("Applying Open flow rule to switch:");
                addFlowRule(dpid);

                System.out.println("Sending PacketOut message");
                sendOfPacketOut(dpid);
                //System.out.format("Time's up!%n");
                timer.cancel(); //Terminate the timer thread
            }
        }


        @Override
        public void switchRemoved(Dpid dpid) {
            System.out.println(dpid.toString() + " is removed");
        }

        @Override
        public void switchChanged(Dpid dpid) {
            System.out.println(dpid.toString() + " is changed");
        }

        @Override
        public void portChanged(Dpid dpid, OFPortStatus status) {
        }

        @Override
        public void receivedRoleReply(Dpid dpid, RoleState requested, RoleState response) {
        }

        private void addFlowRule(Dpid dpid) {
          // OpenFlowSwitch sw = controller.getSwitch(dpid);
//            //FlowRuleExtPayLoad flowRuleExtPayLoad = flowRule.payLoad();

 //         FlowRule flowRule = DefaultFlowRule.builder().build();


//
/*            FlowRule flowRule = DefaultFlowRule.builder()
            		.fromApp(appId)
            		.makePermanent()
            		.forDevice(DeviceId.deviceId(Dpid.uri(dpid)))
                    .withSelector(DefaultTrafficSelector.builder()
                            .matchEthType(Ethernet.TYPE_IPV4)
                            .matchIPSrc(IpPrefix.valueOf("10.0.0.1/32"))
                            .matchIPDst(IpPrefix.valueOf("10.0.0.2/32"))
                            .build())
                    .withTreatment(DefaultTrafficTreatment.builder()
                            .setEthSrc(MacAddress.valueOf("00:00:00:00:00:11"))
                            .setEthDst(MacAddress.valueOf("00:00:00:00:00:02"))
                            .setOutput(PortNumber.portNumber(1))
                           .build())
                    .withPriority(50001).build();
            OFFactory swFactory = sw.factory();
            if (swFactory.getVersion() == OFVersion.OF_13) {
            	System.out.println("Switch version is OF_13");
            }
            FlowRuleExtPayLoad flowRuleExtPayLoad = flowRule.payLoad();
            System.out.println("has Payload: " + (hasPayload(flowRuleExtPayLoad)? "yes" : "no"));
            if (hasPayload(flowRuleExtPayLoad)) {
                OFMessage msg = new ThirdPartyMessage(flowRuleExtPayLoad.payLoad());
                sw.sendMsg(msg);
                return;
            }*/


            ForwardingObjective forwardingObjective = DefaultForwardingObjective.builder()
                    .withSelector(DefaultTrafficSelector.builder()
                            .matchEthType(Ethernet.TYPE_IPV4)
                            .matchIPSrc(IpPrefix.valueOf("10.0.0.1/32"))
                            .matchIPDst(IpPrefix.valueOf("10.0.0.2/32"))
                            .build())
                    .withTreatment(DefaultTrafficTreatment.builder()
                            .setEthSrc(MacAddress.valueOf("00:00:00:00:00:11"))
                            .setEthDst(MacAddress.valueOf("00:00:00:00:00:02"))
                            .setOutput(PortNumber.portNumber(1))
                           .build())
                    .withPriority(50001)
                    .withFlag(ForwardingObjective.Flag.VERSATILE)
                    .fromApp(appId)
                    .makePermanent()
                    .add();

            ForwardingObjective forwardingObjective2 = DefaultForwardingObjective.builder()
                    .withSelector(DefaultTrafficSelector.builder()
                            .matchEthType(Ethernet.TYPE_IPV4)
                            .matchIPSrc(IpPrefix.valueOf("10.0.0.2/32"))
                            .matchIPDst(IpPrefix.valueOf("10.0.0.1/32"))
                            .build())
                    .withTreatment(DefaultTrafficTreatment.builder()
                            .setEthSrc(MacAddress.valueOf("00:00:00:00:00:12"))
                            .setEthDst(MacAddress.valueOf("00:00:00:00:00:01"))
                            .setOutput(PortNumber.portNumber(2))
                            .build())
                    .withPriority(50001)
                    .withFlag(ForwardingObjective.Flag.VERSATILE)
                    .fromApp(appId)
                    .makePermanent()
                    .add();


            try
            {
                flowObjectiveService.forward(DeviceId.deviceId(Dpid.uri(dpid)),
                        forwardingObjective);
                flowObjectiveService.forward(DeviceId.deviceId(Dpid.uri(dpid)),
                        forwardingObjective2);


            }
            catch(Exception e) {
            	System.out.println("Exception to insert flow rule");
            }

            //flowRuleService.applyFlowRules(flowRule);
            //FlowModBuilder fb = FlowModBuilder.builder(flowRule, sw.factory(),
              //      Optional.empty());
           // OFFlowAdd ofa = FlowModBuilder.builder(flowRule, sw.factory(),
             //                                       Optional.empty()).buildFlowAdd();


//            sw.sendMsg(FlowModBuilder.builder(flowRule, sw.factory(),
  //                                          Optional.empty()).buildFlowAdd());
        }


        private boolean hasPayload(FlowRuleExtPayLoad flowRuleExtPayLoad) {
            return flowRuleExtPayLoad != null &&
                    flowRuleExtPayLoad.payLoad() != null &&
                    flowRuleExtPayLoad.payLoad().length > 0;
        }


        private void sendOfPacketOut(Dpid dpid) {
        	OpenFlowSwitch sw = controller.getSwitch(dpid);
           org.projectfloodlight.openflow.types.MacAddress macAddress1 = org.projectfloodlight.openflow.types.MacAddress.of("00:00:00:00:00:12");
          // org.projectfloodlight.openflow.types.MacAddress macdst = org.projectfloodlight.openflow.types.MacAddress.of("00:00:00:00:00:02");
            IPv4Address ipAddresss1 = IPv4Address.of("10.0.0.2");
            ARP arp1 = new ARP();
            arp1.setProtocolType(ARP.PROTO_TYPE_IP)
                    .setHardwareType(ARP.HW_TYPE_ETHERNET)
                    .setProtocolAddressLength((byte) Ip4Address.BYTE_LENGTH)
                    .setHardwareAddressLength((byte) Ethernet.DATALAYER_ADDRESS_LENGTH)
                    .setSenderProtocolAddress(ipAddresss1.getBytes())
                    .setTargetProtocolAddress(ipAddresss1.getBytes())
                    .setSenderHardwareAddress(macAddress1.getBytes())
                    .setTargetHardwareAddress(MacAddress.ZERO.toBytes())
                    .setOpCode(OP_REPLY);
            Ethernet eth1 = new Ethernet();
            eth1.setEtherType(Ethernet.TYPE_ARP)
                    .setSourceMACAddress("00:00:00:00:00:12")
                    .setDestinationMACAddress("00:00:00:00:00:01")
                    .setPayload(arp1);

            OFPortDesc p1 = portDesc(2);

            org.projectfloodlight.openflow.types.MacAddress macAddress2 = org.projectfloodlight.openflow.types.MacAddress.of("00:00:00:00:00:11");
            // org.projectfloodlight.openflow.types.MacAddress macdst = org.projectfloodlight.openflow.types.MacAddress.of("00:00:00:00:00:02");
            IPv4Address ipAddresss2 = IPv4Address.of("10.0.0.1");
            ARP arp2 = new ARP();
            arp2.setProtocolType(ARP.PROTO_TYPE_IP)
                    .setHardwareType(ARP.HW_TYPE_ETHERNET)
                    .setProtocolAddressLength((byte) Ip4Address.BYTE_LENGTH)
                    .setHardwareAddressLength((byte) Ethernet.DATALAYER_ADDRESS_LENGTH)
                    .setSenderProtocolAddress(ipAddresss2.getBytes())
                    .setTargetProtocolAddress(ipAddresss2.getBytes())
                    .setSenderHardwareAddress(macAddress2.getBytes())
                    .setTargetHardwareAddress(MacAddress.ZERO.toBytes())
                    .setOpCode(OP_REPLY);
            Ethernet eth2 = new Ethernet();
            eth2.setEtherType(Ethernet.TYPE_ARP)
                    .setSourceMACAddress("00:00:00:00:00:11")
                    .setDestinationMACAddress("00:00:00:00:00:02")
                    .setPayload(arp2);

            OFPortDesc p2 = portDesc(1);


            try{
                OFPacketOut po1 =
                        packetOut(sw, eth1.serialize(), p1.getPortNo());
                sw.sendMsg(po1);
                OFPacketOut po2 =
                        packetOut(sw, eth2.serialize(), p2.getPortNo());
                sw.sendMsg(po2);

            } catch(Exception e) {

                System.out.println("Error when sending PacketOut Message");
                e.printStackTrace();
            }



        }

        private OFPortDesc portDesc(int portNum) {
            OFPortDesc.Builder builder = OFFactoryVer13.INSTANCE.buildPortDesc();
            builder.setPortNo(OFPort.of(portNum));

            return builder.build();
        }

        private OFPacketOut packetOut(OpenFlowSwitch sw, byte[] eth, OFPort out) {
            OFPacketOut.Builder builder = sw.factory().buildPacketOut();
            OFAction act = sw.factory().actions()
                    .buildOutput()
                    .setPort(out)
                    .build();
            return builder
                    .setBufferId(OFBufferId.NO_BUFFER)
                    .setInPort(OFPort.CONTROLLER)
                    .setActions(Collections.singletonList(act))
                    .setData(eth)
                    .build();
        }
    }

}
