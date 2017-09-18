/*
 * Copyright (c) 2015 Cisco Systems and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.yangpush.listener;

import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.event.notifications.rev161027.IetfEventNotificationsListener;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.push.rev161028.PushChangeUpdate;


//org/opendaylight/yang/gen/v1/urn/ietf/params/xml/ns/yang/ietf/yang/push/rev161028/PushChangeUpdate.java
// use fully-qualified name
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.event.notifications.rev161027.NotificationComplete;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.event.notifications.rev161027.ReplayComplete;


import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.push.rev161028.PushUpdate;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.event.notifications.rev161027.SubscriptionModified;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.event.notifications.rev161027.SubscriptionResumed;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.event.notifications.rev161027.SubscriptionStarted;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.event.notifications.rev161027.SubscriptionSuspended;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.event.notifications.rev161027.SubscriptionTerminated;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.opendaylight.yang.push.rev170721.PushUpdates;
//import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.opendaylight.yang.push.rev170721.push.updates.PushUpdate;

import org.opendaylight.yangpush.rpc.YangpushRpcImpl;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.data.api.schema.AnyXmlNode;
import org.opendaylight.yangtools.yang.data.api.schema.ChoiceNode;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNodes;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;


import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.controller.md.sal.dom.api.DOMDataBroker;
import org.opendaylight.controller.md.sal.dom.api.DOMDataWriteTransaction;
import org.opendaylight.controller.md.sal.dom.api.DOMNotification;
import org.opendaylight.controller.md.sal.dom.api.DOMNotificationListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class implements Notification listener for the notification defined in
 * ietf-event-notifications.yang model.
 *
 * @author Ambika.Tripathy
 *
 */
//public class YangpushDOMNotificationListener implements IetfDatastorePushListener, DOMNotificationListener {
public class YangpushDOMNotificationListener implements IetfEventNotificationsListener, DOMNotificationListener {

    private static final Logger LOG = LoggerFactory.getLogger(YangpushDOMNotificationListener.class);
    private DOMDataBroker globalDomDataBroker;
   // private String subscription_id = "";
    List<String> subscriptionList = new ArrayList<String>();

    //NodeIdentifier encoding = new NodeIdentifier(YangpushRpcImpl.I_PUSH_ENCODING);
    //NodeIdentifier contents = new NodeIdentifier(YangpushRpcImpl.I_PUSH_DATASTORECONTENTSXML);
    //NodeIdentifier subid = new NodeIdentifier(YangpushRpcImpl.I_PUSH_SUB_ID);
    //NodeIdentifier timeofevent = new NodeIdentifier(YangpushRpcImpl.I_PUSH_TIME_OF_UPDATE);

    NodeIdentifier encoding = new NodeIdentifier(YangpushRpcImpl.I_NOTIF_ENCODING);
    NodeIdentifier contents = new NodeIdentifier(YangpushRpcImpl.I_NOTIF_FILTER);
    NodeIdentifier subid = new NodeIdentifier(YangpushRpcImpl.I_NOTIF_SUB_ID);
    NodeIdentifier timeofevent = new NodeIdentifier(YangpushRpcImpl.I_PUSH_TIME_OF_UPDATE);
    // Nodes to push notification data to mdsal datastore
    //public YangInstanceIdentifier push_update_iid = null;

    public YangpushDOMNotificationListener(DOMDataBroker globalDomDataBroker) {
        this.globalDomDataBroker = globalDomDataBroker;
        //this.subscription_id = subscription_id;
        //this.push_update_iid = buildIID(subscription_id);
    }

    public void insertSubscriptionId(String subId) {
        this.subscriptionList.add(subId);
    }

    public void removeSubscriptionId(String subId) {
        this.subscriptionList.remove(subId);
    }

    /**
     * This method creates a YANG II for the subscription-id
     * used for this subscription to track. The may be multiple subscription
     * each will be identified by by the subscription-id.
     *
     * @param sub_id
     * @return
     */
    private YangInstanceIdentifier buildIID(String sub_id) {

        QName pushupdate = QName.create(YangpushRpcImpl.I_NOTIF_NS, YangpushRpcImpl.I_NOTIF_DATE, "push-update");
        org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.InstanceIdentifierBuilder builder = org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier
                .builder();
        builder.node(PushUpdates.QNAME).node(pushupdate).nodeWithKey(pushupdate,
                QName.create(pushupdate, "subscription-id"), sub_id);

        return builder.build();
    }

    /**
     * This method implements on Notification.
     * When there is a notification received by listener, that should be
     * parsed for the subscription-id and then processed.
     */
    @Override
    public void onNotification(DOMNotification notification) {
        LOG.trace("Notification received {}", notification.getBody());
        QName qname = org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.push.rev161028.PushUpdate.QNAME;
        //QName qname = org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.opendaylight.yang.push.rev170721.push.updates.PushUpdate.QNAME;
        SchemaPath schemaPath = SchemaPath.create(true, qname);
        if (notification.getType().equals(schemaPath)) {
            ContainerNode conNode = notification.getBody();
            //If the subscription-id of notification same as
            // subscription-id set for this object then proceed.
            //if (conNode.getChild(subid).get().getValue().toString().equals(subscription_id)){
            if (this.subscriptionList.contains(conNode.getChild(subid).get().getValue().toString())) {
                LOG.trace("Received push-update for subscription {}",conNode.getChild(subid).get().getValue().toString() );
                try {
                    pushUpdateHandler(notification);
                } catch (Exception e) {
                    LOG.warn(e.toString());
               }
            } else {
                LOG.error("Received subscription-id {} is not valid. Skipping the notification processing",
                conNode.getChild(subid).get().getValue().toString());
            }
        }
    }

    /**
     * This method parses the pushUpdate notification received
     * for the subscription-id and stores the data to md-sal
     * using path /push-updates/push-update/[device-subscription-id=sub_id]/
     *
     * @param notification
     * @return DOMSource for the notification
     */
    private void pushUpdateHandler(DOMNotification notification) {
        ContainerNode conNode = notification.getBody();
        ChoiceNode valueNode = null;
        AnyXmlNode anyXmlValue = null;
        DOMSource domSource = null;
        String sub_id = "";
        String timeofeventupdate = "";
        try {
            sub_id = conNode.getChild(subid).get().getValue().toString();
            timeofeventupdate = conNode.getChild(timeofevent).get().getValue().toString();
            valueNode = (ChoiceNode) conNode.getChild(encoding).get();
            anyXmlValue = (AnyXmlNode) valueNode.getChild(contents).get();
            domSource = anyXmlValue.getValue();
        } catch (Exception e) {
            LOG.warn(e.toString());
        }
        //String notificationAsString = domSourceToString(domSource);
        String notificationAsString = valueNode.getChild(contents).get().getValue().toString();
        LOG.trace("Notification recieved for sub_id :{} at : {}:\n {}", sub_id, timeofeventupdate, notificationAsString);
        storeToMdSal(sub_id, timeofeventupdate, domSource, notificationAsString);
    }

    /**
     * This method stores the pushUpdate Notification data to MD-SAL
     *
     * @param sub_id
     * @param timeofeventupdate
     * @param domSource
     * @param data
     */
    private void storeToMdSal(String dev_sub_id, String timeofeventupdate, DOMSource domSource, String data) {
       // NodeIdentifier subscriptionid = NodeIdentifer.create(QName.create(PushUpdates.QNAME, "subscription-id"));
        //NodeIdentifier devname = NodeIdentifier.create(QName.create(PushUpdates.QNAME, "device-name"));
        NodeIdentifier devsubscriptionid = NodeIdentifier.create(QName.create(PushUpdates.QNAME, "device-subscription-id"));
        NodeIdentifier timeofupdate = NodeIdentifier.create(QName.create(PushUpdates.QNAME, "time-of-update"));
        NodeIdentifier datanode = NodeIdentifier.create(QName.create(PushUpdates.QNAME, "data"));
        YangInstanceIdentifier pid = YangInstanceIdentifier.builder()
                .node(PushUpdates.QNAME)
                .node(PushUpdate.QNAME).build();

        NodeIdentifierWithPredicates p = new NodeIdentifierWithPredicates(
                QName.create(PushUpdates.QNAME, "push-update"),
                QName.create(PushUpdates.QNAME, "device-subscription-id"),
                dev_sub_id);

        MapEntryNode men = ImmutableNodes.mapEntryBuilder().withNodeIdentifier(p)
            //    .withChild(ImmutableNodes.leafNode(subscriptionid, sub_id))
            //    .withChild(ImmutableNodes.leafNode(devname, dev_name))
                .withChild(ImmutableNodes.leafNode(devsubscriptionid, dev_sub_id))
                .withChild(ImmutableNodes.leafNode(timeofupdate, timeofeventupdate))
                .withChild(ImmutableNodes.leafNode(datanode,data))
                .build();

        DOMDataWriteTransaction tx = this.globalDomDataBroker.newWriteOnlyTransaction();
        YangInstanceIdentifier yid = pid.node(new NodeIdentifierWithPredicates(PushUpdate.QNAME, men.getIdentifier().getKeyValues()));
        tx.merge(LogicalDatastoreType.CONFIGURATION, yid, men);
        LOG.trace("--DATA PATh: {}\n--DATA\n{}",yid,men);
        
	try {
            tx.submit().checkedGet();
        } catch (TransactionCommitFailedException e) {
            e.printStackTrace();
        }
    }


    @Override
    public void onReplayComplete(ReplayComplete notification) {
	    // TODO Auto-generated method stub
	   LOG.trace("Notification recieved {}", notification);
    }

    @Override
    public void onNotificationComplete(NotificationComplete notification) {
    	// TODO Auto-generated method stub
	LOG.trace("Notification recieved {}", notification);
    }

    @Override
    public void onSubscriptionModified(SubscriptionModified notification) {
        // TODO Auto-generated method stub
        LOG.trace("Notification recieved {}", notification);
    }

    @Override
    public void onSubscriptionResumed(SubscriptionResumed notification) {
        // TODO Auto-generated method stub
        LOG.trace("Notification recieved {}", notification);
    }

    //TODO: Implement onPushUpdate instead of onNotification.
    // AT present no found how to do it in BI way. Hence using onNotification.
    
    //@Override
    //public void onPushUpdate(PushUpdate notification) {
        // TODO Auto-generated method stub
    //    LOG.trace("Notification recieved {}", notification);
    //}

    //@Override
    //public void onPushChangeUpdate(PushChangeUpdate notification) {
        // TODO Auto-generated method stub
    //    LOG.trace("Notification recieved {}", notification);
    //}

    @Override
    public void onSubscriptionSuspended(SubscriptionSuspended notification) {
        // TODO Auto-generated method stub
        LOG.trace("Notification recieved {}", notification);
    }

    @Override
    public void onSubscriptionTerminated(SubscriptionTerminated notification) {
        // TODO Auto-generated method stub
        LOG.trace("Notification recieved {}", notification);
    }

    @Override
    public void onSubscriptionStarted(SubscriptionStarted notification) {
        // TODO Auto-generated method stub
        LOG.trace("Notification recieved {}", notification);
    }

    /**
     * Util method to convert a DOMSource to a string.
     *
     * @param source
     * @return String in XML format
     */
    private String domSourceToString(DOMSource source) {
        try {
            StringWriter writer = new StringWriter();
            StreamResult result = new StreamResult(writer);
            TransformerFactory tf = TransformerFactory.newInstance();
            Transformer transformer = tf.newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty(OutputKeys.STANDALONE, "yes");
            transformer.transform(source, result);
            return writer.toString();
        } catch (Exception e) {
            LOG.warn(e.toString());
        }
        return null;
    }
}
