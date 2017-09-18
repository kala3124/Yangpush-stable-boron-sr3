/*
 * Copyright (c) 2015 Cisco Systems and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.yangpush.impl;

import java.util.Collection;
import java.util.Collections;

import org.opendaylight.controller.md.sal.common.api.data.AsyncDataBroker;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataChangeEvent;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.dom.api.DOMDataBroker;
import org.opendaylight.controller.md.sal.dom.api.DOMDataChangeListener;
import org.opendaylight.controller.md.sal.dom.api.DOMMountPointService;
import org.opendaylight.controller.md.sal.dom.api.DOMRpcProviderService;
import org.opendaylight.controller.sal.core.api.Broker;
import org.opendaylight.controller.sal.core.api.Provider;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.network.topology.topology.topology.types.TopologyNetconf;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NetworkTopology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yangpush.rpc.YangpushRpcImpl;
import org.opendaylight.yangpush.subscription.YangpushSubscriptionEngine;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class is Binding Independent version of yangpushProvider
 * and provide the implementation of yangpush application.
 *
 * BI - It uses a neutral data DOM format for data and API calls,
 *      which is independent of generated Java language bindings.
 *
 * BA - It uses code generated both at development time and at runtime.
 *
 *  Here in the BI implementation, we used the Qname to access the DOM nodes
 *  e.g. ((MapEntryNode) topology).getChild(new NodeIdentifier(Node.QNAME)).
 *
 *  While in BA implementation we can directly use the generated bindings
 *  from the yang model to access the nodes.
 *
 *  One can follow the following link to understand the difference between BI and BA :
 *  https://ask.opendaylight.org/question/998/binding-independent-and-binding-aware-difference/
 */
public class YangpushDomProvider implements Provider, AutoCloseable, DOMDataChangeListener {

    private static final Logger LOG = LoggerFactory.getLogger(YangpushDomProvider.class);
    public static final YangInstanceIdentifier NETCONF_TOPO_IID;

    static {
        final org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.InstanceIdentifierBuilder builder =
                org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.builder();
        builder
        .node(NetworkTopology.QNAME)
        .node(Topology.QNAME)
        .nodeWithKey(Topology.QNAME, QName.create(Topology.QNAME, "topology-id"), TopologyNetconf.QNAME.getLocalName());

        NETCONF_TOPO_IID = builder.build();
    }

    private DOMMountPointService mountPointService = null;
    private DOMDataBroker globalDomDataBroker = null;
    private YangpushRpcImpl yangpushRpcImpl = null;
    private YangpushSubscriptionEngine subEngine = null;

    /**
     * This method initializes DomDataBroker and Mountpoint service.
     * This services needed throughout the lifetime of the yangpush
     * application and registers its RPC implementation and Data change
     * Listener with the MD-SAL.
     */
    @Override
    public void onSessionInitiated(final Broker.ProviderSession providerSession) {
        // get the DOM versions of MD-SAL services
        this.globalDomDataBroker = providerSession.getService(DOMDataBroker.class);
        this.mountPointService = providerSession.getService(DOMMountPointService.class);

        this.subEngine = YangpushSubscriptionEngine.getInstance();
        this.subEngine.setDataBroker(globalDomDataBroker);
        this.subEngine.createPushUpdateDataStore();
        //this.subEngine.createPushUpdateOdlEventNs();

        final DOMRpcProviderService service = providerSession.getService(DOMRpcProviderService.class);
        yangpushRpcImpl = new YangpushRpcImpl(service,this.mountPointService, this.globalDomDataBroker);

        final YangInstanceIdentifier nodeIid = YangInstanceIdentifier.builder(NETCONF_TOPO_IID).node(Node.QNAME).build();

        LOG.info("yangpushDomProvider is registered");

        this.globalDomDataBroker.registerDataChangeListener(LogicalDatastoreType.OPERATIONAL,
                nodeIid,
                this,
                AsyncDataBroker.DataChangeScope.SUBTREE);
    }


    @Override
    public Collection<ProviderFunctionality> getProviderFunctionality() {
        // Deprecated, not using
        return Collections.emptySet();
    }

    @Override
    public void close() {
        this.globalDomDataBroker = null;
        this.mountPointService = null;
    }

    /**
     * This method is yangpush's Data Change Listener on the Netconf Topology
     * namespace. It registers at the root of the Netconf Topology subtree for
     * changes in the entire subtree. At this point the method only logs the
     * data change to demonstrate the basic design pattern. A real application
     * would use the the data contained in the data change event to, for
     * example, maintain paths to connected netconf nodes.
     *
     * @param change Data change event
     */
    @Override
    public void onDataChanged(final AsyncDataChangeEvent<YangInstanceIdentifier, NormalizedNode<?, ?>> change) {
        // TODO: Method need to be implemented. The data change has to
        // be handled in the same way as in yangpushProvider.
        LOG.trace("data changed: {}", change);
    }
}
