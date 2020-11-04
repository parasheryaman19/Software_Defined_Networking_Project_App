/*
 * Copyright 2020-present Open Networking Foundation
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
package org.tenpings.app;

import org.onlab.packet.Ethernet;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.net.*;
import org.onosproject.net.flow.*;
import org.onosproject.net.host.HostService;
import org.onosproject.net.packet.*;
import org.onosproject.net.topology.TopologyService;
import org.osgi.service.component.annotations.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Skeletal ONOS application component.
 *
 * Assumptions host and destination hosts are already known at the controller.
 *
 *
 */
@Component(immediate = true, service = AppComponent.class)
public class AppComponent {

    private final Logger log = LoggerFactory.getLogger(getClass());

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected CoreService coreService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected PacketService packetService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected HostService hostService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected TopologyService topologyService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected FlowRuleService flowRuleService;

    private LocalPacketProcessor processor = new LocalPacketProcessor();

    private ApplicationId appId;

    //This is the local database of Ping Information
    //It is used to remember the ping request that have been received at the controller
    //It is implemented with a Java Map
    public Map<String, PingInfo> pingInfoDatabase = new HashMap<String, PingInfo>();

    @Activate
    protected void activate() {
        appId = coreService.registerApplication("org.tenpings.app");

        packetService.addProcessor(processor, PacketProcessor.director(2));

        installIpv4FlowRule();

        log.info("TenPings application has been started with appId {}", appId);
    }

    @Deactivate
    protected void deactivate() {
        packetService.removeProcessor(processor);
        processor = null;

        removeIpv4FlowRule();

        flowRuleService.removeFlowRulesById(appId);

        pingInfoDatabase.clear();

        log.info("TenPings application has been stopped with appId {}", appId);
    }

    /**
     * Request packet in via packet service.
     */
    private void installIpv4FlowRule() {
        TrafficSelector.Builder selector = DefaultTrafficSelector.builder();
        selector.matchEthType(Ethernet.TYPE_IPV4);
        packetService.requestPackets(selector.build(), PacketPriority.REACTIVE, appId);
    }

    /**
     * Cancel request for packet in via packet service.
     */
    private void removeIpv4FlowRule() {
        TrafficSelector.Builder selector = DefaultTrafficSelector.builder();
        selector.matchEthType(Ethernet.TYPE_IPV4);
        packetService.cancelPackets(selector.build(), PacketPriority.REACTIVE, appId);
    }

    /**
     * Packet processor responsible for forwarding packets along their paths.
     */
    private class LocalPacketProcessor implements PacketProcessor {

        @Override
        public void process(PacketContext context) {

            InboundPacket pkt = context.inPacket();
            Ethernet ethPkt = pkt.parsed();

            if (ethPkt.getEtherType() == Ethernet.TYPE_LLDP) {
                //log.info("[---TENPINGS---]: from {} ETH_TYPE: LLDP", context.inPacket().receivedFrom());
            }

            if (ethPkt.getEtherType() == Ethernet.TYPE_ARP) {
                log.info("[---TENPINGS---] from {} ETH_TYPE: ARP", context.inPacket().receivedFrom());
            }

            if (ethPkt.getEtherType() == Ethernet.TYPE_IPV4) {
                log.info("[---TENPINGS---] from {} ETH_TYPE: IPv4", context.inPacket().receivedFrom());

                //TODO-4 work inside this if condition

                //Identify the source host
                HostId idSrc = HostId.hostId(ethPkt.getSourceMAC());
                Host src = hostService.getHost(idSrc);
                if (src == null) {
                    log.error("[---TENPINGS---] source host is not known MAC {} flooded", idSrc);
                    return;
                }

                //Identify the destination host
                HostId idDst = HostId.hostId(ethPkt.getDestinationMAC());
                Host dst = hostService.getHost(idDst);
                if (dst == null) {
                    log.error("[---TENPINGS---] destination host is not known MAC {} flooded", idDst);
                    return;
                }

                //Memorize that src has pinged dst
                PingInfo pingInfo = new PingInfo(idSrc, idDst);
                String pingData = idSrc.toString()+"-"+idDst.toString();
                if (pingInfoDatabase.containsKey(pingData))
                {
                    log.error("[---TENPINGS---] this connection is already in database");
                }
                else
                {
                    pingInfoDatabase.put(idSrc.toString()+"-"+idDst.toString(), pingInfo);
                    log.info("[---TENPINGS---] this PING has been added to database");
                    //-----------------------------------------------

                    DeviceId srcDeviceId = pkt.receivedFrom().deviceId();
                    DeviceId dstDeviceId = dst.location().deviceId();

                    PortNumber srcPort = src.location().port();
                    PortNumber dstPort = dst.location().port();

                    // Are we on an edge switch that our destination is on? If so, install rule and packet out
                    if (srcDeviceId.equals(dstDeviceId)) {
                        if (!srcPort.equals(dstPort)) {

                            log.warn("[---TENPINGS---] packet received on the destination switch, rule installed");

                            installRules(context,
                                    pkt.receivedFrom().deviceId(),
                                    pkt.receivedFrom().port(),
                                    dst.location().port());

                            packetOut(context,dst.location().port());
                        }
                        return;
                    }

                    // Otherwise, get a set of paths that lead from here to the
                    // destination edge switch.
                    Set<Path> paths =
                            topologyService.getPaths(topologyService.currentTopology(),
                                    src.location().deviceId(),
                                    dst.location().deviceId());

                    if (paths.isEmpty()) {
                        // If there are no paths, flood and bail.
                        log.error("[---TENPINGS---] there is no path from source to destination");
                        return;
                    }

                    //Simple way to get the first computed path
                    Path path = paths.iterator().next();

                    log.info("[---TENPINGS---] received packet from device {} host {} to host {}",
                            pkt.receivedFrom().deviceId(),
                            idSrc,
                            idDst
                    );

                    log.info("[---TENPINGS---] path:");
                    for (Link link : path.links()) {
                        log.info("--- link {}/{}->{}/{}",
                                link.src().deviceId(),
                                link.src().port(),
                                link.dst().deviceId(),
                                link.dst().port());
                    }

                    // Install two flow rules (one per direction) to each device in the path.
                    for (int i=0; i<path.links().size(); i++) {
                        if (i == 0) {
                            log.info("[---TENPINGS---] i={} installing rules on device {} in {} out {}",
                                    i,
                                    src.location().deviceId(),
                                    src.location().port(),
                                    path.links().get(0).src().port());

                            installRules(context,
                                    pkt.receivedFrom().deviceId(),
                                    pkt.receivedFrom().port(),
                                    path.links().get(0).src().port());
                        } else {
                            log.info("[---TENPINGS---] i={} installing rules on device {} in {} out {}",
                                    i,
                                    path.links().get(i).src().deviceId(),
                                    path.links().get(i - 1).dst().port(),
                                    path.links().get(i).src().port());

                            installRules(context,
                                    path.links().get(i).src().deviceId(),
                                    path.links().get(i - 1).dst().port(),
                                    path.links().get(i).src().port());
                        }

                        if (i == path.links().size() - 1) {
                                log.info("[---TENPINGS---] i={} installing rules on device {} in {} out {}",
                                        i,
                                        dst.location().deviceId(),
                                        path.links().get(i).dst().port(),
                                        dst.location().port());

                                installRules(context,
                                        dst.location().deviceId(),
                                        path.links().get(i).dst().port(),
                                        dst.location().port());
                        }
                    }

                    //Send packet out message
                    log.info("[---TENPINGS---] sending packet out to device {}", context.inPacket().receivedFrom().deviceId());
                    packetOut(context, path.links().get(0).src().port());
                }
                
            }
        }
    }

    // Sends a packet out the specified port.
    private void packetOut(PacketContext context, PortNumber portNumber) {
        context.treatmentBuilder().setOutput(portNumber);
        context.send();
    }

    /**
     * Install a flow rule on deviceId using MAC src and dst taked from the context.
     *
     * @param context packet received within a packetin
     * @param deviceId device where to install the rule
     * @param inPort input port included in the selector
     * @param outPort output port included in the treatment
     */
    private void installRules(PacketContext context, DeviceId deviceId,
                             PortNumber inPort, PortNumber outPort) {

        //TODO-3 work inside this function
        
        Ethernet inPkt = context.inPacket().parsed();

        //Build the rule in the direct direction
        TrafficSelector selector = DefaultTrafficSelector.builder()
                .matchInPort(inPort)
                .matchEthSrc(inPkt.getSourceMAC())
                .matchEthDst(inPkt.getDestinationMAC())
                .build();

        TrafficTreatment treatment = DefaultTrafficTreatment.builder()
                .setOutput(outPort)
                .build();

        FlowRule directRule = DefaultFlowRule.builder()
                .forDevice(deviceId)
                .forTable(0)
                .withSelector(selector)
                .withTreatment(treatment)
                .withPriority(10)
                .fromApp(appId)
                .makePermanent()
                .withHardTimeout(10)
                .build();

        //Build the rule in the reverse direction
        TrafficSelector reverseSelector = DefaultTrafficSelector.builder()
                .matchInPort(outPort)
                .matchEthSrc(inPkt.getDestinationMAC())
                .matchEthDst(inPkt.getSourceMAC())
                .build();

        TrafficTreatment reverseTreatment = DefaultTrafficTreatment.builder()
                .setOutput(inPort)
                .build();

        FlowRule reverseRule = DefaultFlowRule.builder()
                .forDevice(deviceId)
                .forTable(0)
                .withSelector(reverseSelector)
                .withTreatment(reverseTreatment)
                .withPriority(10)
                .fromApp(appId)
                .makePermanent()
                .withHardTimeout(10)
                .build();

        //Install both flow rules in the data plane
        flowRuleService.applyFlowRules(directRule, reverseRule);
                          
    }

    protected String hostDatabaseToString() {
        String info = "";

        for (String key : pingInfoDatabase.keySet()) {
            info = info + pingInfoDatabase.get(key).toString() + '\n';
        }

        return info;
    }
}
