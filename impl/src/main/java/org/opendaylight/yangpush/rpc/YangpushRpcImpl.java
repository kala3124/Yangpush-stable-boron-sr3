/*
 * Copyright © 2015 Copyright (c) 2015 cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.yangpush.rpc;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.xml.transform.dom.DOMSource;

import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.controller.md.sal.dom.api.DOMDataBroker;
import org.opendaylight.controller.md.sal.dom.api.DOMDataWriteTransaction;
import org.opendaylight.controller.md.sal.dom.api.DOMMountPoint;
import org.opendaylight.controller.md.sal.dom.api.DOMMountPointService;
import org.opendaylight.controller.md.sal.dom.api.DOMNotificationService;
import org.opendaylight.controller.md.sal.dom.api.DOMRpcException;
import org.opendaylight.controller.md.sal.dom.api.DOMRpcIdentifier;
import org.opendaylight.controller.md.sal.dom.api.DOMRpcImplementation;
import org.opendaylight.controller.md.sal.dom.api.DOMRpcProviderService;
import org.opendaylight.controller.md.sal.dom.api.DOMRpcResult;
import org.opendaylight.controller.md.sal.dom.api.DOMRpcService;
import org.opendaylight.controller.md.sal.dom.spi.DefaultDOMRpcResult;

import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.event.notifications.rev161027.base.filter.FilterType;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.event.notifications.rev161027.EstablishSubscriptionInput;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.event.notifications.rev161027.EstablishSubscriptionOutput;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.event.notifications.rev161027.CreateSubscriptionInput;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.event.notifications.rev161027.ModifySubscriptionInput;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.event.notifications.rev161027.ModifySubscriptionOutput;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.event.notifications.rev161027.DeleteSubscriptionInput;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.event.notifications.rev161027.DeleteSubscriptionOutput;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.push.rev161028.PushUpdate;

import org.opendaylight.yangpush.impl.YangpushDomProvider;
import org.opendaylight.yangpush.listener.YangpushDOMNotificationListener;
import org.opendaylight.yangpush.rpc.YangpushErrors.errors;
import org.opendaylight.yangpush.subscription.YangpushSubscriptionEngine;
import org.opendaylight.yangpush.subscription.YangpushSubscriptionEngine.operations;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.AugmentationIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.PathArgument;
import org.opendaylight.yangtools.yang.data.api.schema.AnyXmlNode;
import org.opendaylight.yangtools.yang.data.api.schema.AugmentationNode;
import org.opendaylight.yangtools.yang.data.api.schema.ChoiceNode;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.LeafNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.DataContainerChild;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNodes;
import org.opendaylight.yangtools.yang.data.impl.schema.builder.api.NormalizedNodeAttrBuilder;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.CheckedFuture;
import com.google.common.util.concurrent.Futures;

import javassist.bytecode.analysis.ControlFlow.Node;

/**
 * This class implements RPC defined in ietf-yang-push@2016-10-28.yang yang
 * model. Also registers the Notification listener for the notification defined
 * in the model.
 *
 * This is the BI implementation of handling all RPC. This uses BI because
 * anyxml support is not available in BA approach. The subtree filter argument
 * handled in anyxml type.
 *
 * RPC: Create-subscription implementation details: ---------------------------
 * The basic principle of registering a subscription is:
 *
 * Uses create-subscription RPC present in ietf-datastore-push module. The input
 * of the create-subscription RPC augmented by yangpush module to add mountpoint
 * device name to input parameter.
 *
 * After parsing all the inputs, a subscription-id generated for the
 * subscription.
 *
 * Using the create-subscription defined in notification.yang, a new
 * subscription is generated for mounted device. and for each subscription
 * Notification listeners are registered.

 * RPC: Establish-subscription implementation details: ---------------------------
 * The basic principle of registering a subscription is:
 
 * Uses establish-subscription RPC present in ietf-event-notifications module. The input
 * of the establish-subscription RPC augmented by ietf-yang-push module to add mountpoint
 * device name to input parameter.
 
 * After parsing all the inputs, a subscription-id generated for the
 * subscription.
 
 * Using the establish-subscription defined in notification.yang, a new
 * subscription is generated for mounted device. and for each subscription
 * Notification listeners are registered.

 * RPC: Delete-Subscription implementation details:
 * --------------------------------- Uses delete-subscription RPC present in
 * ietf-yang-push module. The input of the delete-subscription RPC
 * augmented by yangpush module to add mountpoint device name to input
 * parameter.
 *
 * @author Ambika.Tripathy / Chandrakala Kempapura / Giles Heron
 *
 */
public class YangpushRpcImpl implements DOMRpcImplementation {

	private static final Logger LOG = LoggerFactory.getLogger(YangpushRpcImpl.class);
	public static final String NOTIFICATION_NS = "urn:ietf:params:xml:ns:netconf:notification:1.0";
	public static final String NOTIFICATION_DATE = "2008-07-14";
	public static final String I_NOTIF_NS = "urn:ietf:params:xml:ns:yang:ietf-event-notifications";
	public static final String I_NOTIF_DATE = "2016-10-27";
	public static final String I_PUSH_NS = "urn:ietf:params:xml:ns:yang:ietf-yang-push";
	public static final String I_PUSH_DATE = "2016-10-28";
	public static final String ODL_PUSH_NS = "urn:opendaylight:params:xml:ns:yang:opendaylight-yang-push";
	public static final String ODL_PUSH_DATE = "2017-07-21";

	// QName and NodeIdentifier for input args for create-subscription RPC
	// present in notification.yang (RFC5277)
	public static final NodeIdentifier I_RPC_CS_INPUT = NodeIdentifier
		.create(QName.create(NOTIFICATION_NS, NOTIFICATION_DATE, "input"));
	public static final QName I_RPC_STREAM_NAME = QName.create(NOTIFICATION_NS, NOTIFICATION_DATE, "stream");
	public static final QName I_RPC_STARTTIME_NAME = QName.create(NOTIFICATION_NS, NOTIFICATION_DATE, "starttime");
	public static final QName I_RPC_STOPTIME_NAME = QName.create(NOTIFICATION_NS, NOTIFICATION_DATE, "stoptime");
	public static final QName I_RPC_FILTER_NAME = QName.create(NOTIFICATION_NS, NOTIFICATION_DATE, "filter");

	// QNames used to construct augment leafs present in opendaylight-yang-push.yang 
	public static final QName ODL_PUSH_DEVICE_NAME = QName.create(ODL_PUSH_NS, ODL_PUSH_DATE, "device-name");
	public static final QName ODL_PUSH_DEVICE_SUB_ID = QName.create(ODL_PUSH_NS, ODL_PUSH_DATE, "device-subscription-id");

	// QNames used to construct augment leafs present in ietf-yang-push.yang
	public static final QName I_PUSH_UPDATE_TRIGGER = QName.create(I_PUSH_NS, I_PUSH_DATE, "update-trigger");
	public static final QName I_PUSH_PERIOD = QName.create(I_PUSH_NS, I_PUSH_DATE, "period");
	public static final QName I_PUSH_ANCHOR_TIME = QName.create(I_PUSH_NS, I_PUSH_DATE, "anchor-time");
	public static final QName I_PUSH_NO_SYNC_ON_START = QName.create(I_PUSH_NS, I_PUSH_DATE, "no-sync-on-start");
	public static final QName I_PUSH_DAMPENING_PERIOD = QName.create(I_PUSH_NS, I_PUSH_DATE, "dampening-period");
	public static final QName I_PUSH_EXCLUDED_CHANGE = QName.create(I_PUSH_NS, I_PUSH_DATE, "excluded-change");
	public static final QName I_PUSH_UPDATE_FILTER = QName.create(I_PUSH_NS, I_PUSH_DATE, "update-filter");
	public static final QName I_PUSH_SUBTREE_FILTER = QName.create(I_PUSH_NS, I_PUSH_DATE, "subtree-filter");
	public static final QName I_PUSH_XPATH_FILTER = QName.create(I_PUSH_NS, I_PUSH_DATE, "xpath-filter");
	public static final QName I_PUSH_SUBSCRIPTION_ID = QName.create(I_PUSH_NS, I_PUSH_DATE, "subscription-id");
	public static final QName I_PUSH_TIME_OF_UPDATE = QName.create(I_PUSH_NS, I_PUSH_DATE, "time-of-update");
	public static final QName I_PUSH_UPDATES_NOT_SENT = QName.create(I_PUSH_NS, I_PUSH_DATE, "updates-not-sent");

	// QNames used to construct input args defined in ietf-event-notifications.yang
	public static final QName I_NOTIF_SUB_ID = QName.create(I_NOTIF_NS, I_NOTIF_DATE, "subscription-id");
	public static final QName I_NOTIF_STREAM = QName.create(I_NOTIF_NS, I_NOTIF_DATE, "stream");
	public static final QName I_NOTIF_ENCODING = QName.create(I_NOTIF_NS, I_NOTIF_DATE, "encoding");
	public static final QName I_NOTIF_FILTERS = QName.create(I_NOTIF_NS, I_NOTIF_DATE, "filters");
	public static final QName I_NOTIF_FILTER = QName.create(I_NOTIF_NS, I_NOTIF_DATE, "filter");
	public static final QName I_NOTIF_FILTER_ID = QName.create(I_NOTIF_NS, I_NOTIF_DATE, "filter-id");
	public static final QName I_NOTIF_FILTER_TYPE = QName.create(I_NOTIF_NS, I_NOTIF_DATE, "filter-type");
	
	public static final QName I_NOTIF_FILTER_REF = QName.create(I_NOTIF_NS, I_NOTIF_DATE, "filter-ref");
	public static final QName I_NOTIF_STARTTIME = QName.create(I_NOTIF_NS, I_NOTIF_DATE, "startTime");
	public static final QName I_NOTIF_STOPTIME = QName.create(I_NOTIF_NS, I_NOTIF_DATE, "stopTime");

	public static final NodeIdentifier I_NOTIF_ES_OUTPUT = NodeIdentifier.create(EstablishSubscriptionOutput.QNAME);	
	public static final NodeIdentifier I_NOTIF_MS_OUTPUT = NodeIdentifier.create(ModifySubscriptionOutput.QNAME);
	public static final NodeIdentifier I_NOTIF_DS_OUTPUT = NodeIdentifier.create(DeleteSubscriptionOutput.QNAME);

	// QName and NodeIdentifier for input args for establish-subscription RPC
	// present in ietf-event-notifications.yang
	public static final NodeIdentifier I_RPC_ES_INPUT = NodeIdentifier
		.create(QName.create(I_NOTIF_NS, I_NOTIF_DATE, "input"));
	public static final QName I_RPC_ES_STREAM_NAME = QName.create(I_NOTIF_NS, I_NOTIF_DATE, "stream");
	public static final QName I_RPC_ES_STARTTIME_NAME = QName.create(I_NOTIF_NS, I_NOTIF_DATE, "starttime");
	public static final QName I_RPC_ES_STOPTIME_NAME = QName.create(I_NOTIF_NS, I_NOTIF_DATE, "stoptime");
	public static final QName I_RPC_ES_FILTER_NAME = QName.create(I_NOTIF_NS, I_NOTIF_DATE, "filter");

	// Node identifier for establish-subscription RPC present in
	// opendaylight-event-notifications.yang 
	public static final NodeIdentifier ES_STREAM_ARG = NodeIdentifier.create(I_RPC_ES_STREAM_NAME);
	public static final NodeIdentifier ES_STARTTIME_ARG = NodeIdentifier.create(I_RPC_ES_STARTTIME_NAME);
	public static final NodeIdentifier ES_STOPTIME_ARG = NodeIdentifier.create(I_RPC_ES_STOPTIME_NAME);
	public static final NodeIdentifier ES_FILTER_ARG = NodeIdentifier.create(I_RPC_ES_FILTER_NAME);
	public static final NodeIdentifier ES_DEVICE_NAME = NodeIdentifier.create(ODL_PUSH_DEVICE_NAME);
	public static final NodeIdentifier ES_SUBSCRIPTION_ID = NodeIdentifier.create(ODL_PUSH_DEVICE_SUB_ID);

	public static final NodeIdentifier I_RPC_DS_INPUT = NodeIdentifier
		.create(QName.create(I_NOTIF_NS, I_NOTIF_DATE, "input"));
	// RPCs
	public static final DOMRpcIdentifier ESTABLISH_SUBSCRIPTION_RPC = DOMRpcIdentifier
		.create(SchemaPath.create(true, QName.create(EstablishSubscriptionInput.QNAME, "establish-subscription")));
	public static final DOMRpcIdentifier CREATE_SUBSCRIPTION_RPC = DOMRpcIdentifier
		.create(SchemaPath.create(true, QName.create(CreateSubscriptionInput.QNAME, "create-subscription")));
	public static final DOMRpcIdentifier MODIFY_SUBSCRIPTION_RPC = DOMRpcIdentifier
			.create(SchemaPath.create(true, QName.create(ModifySubscriptionInput.QNAME, "modify-subscription")));
	public static final DOMRpcIdentifier DELETE_SUBSCRIPTION_RPC = DOMRpcIdentifier
			.create(SchemaPath.create(true, QName.create(DeleteSubscriptionInput.QNAME, "delete-subscription")));

	// Error messages
	public static final String ERR_INVALID_INPUT = "Invalid input";
	
	private DOMRpcProviderService service;
	private DOMMountPointService mountPointService;
	private DOMDataBroker globalDomDataBroker;
	private YangpushSubscriptionEngine yangpushSubscriptionEngine = null;

	public YangpushRpcImpl(DOMRpcProviderService service, DOMMountPointService mountPointService,
			DOMDataBroker globalDomDataBroker) {
		super();
		this.service = service;
		this.mountPointService = mountPointService;
		this.globalDomDataBroker = globalDomDataBroker;
		this.yangpushSubscriptionEngine = YangpushSubscriptionEngine.getInstance();
		registerRPCs();
	}

	/**
	 * Registers RPC present in ietf-datastore-push module.
	 * Registers RPC present in ietf-event-notifications module (establish-subscription).
	 */
	private void registerRPCs() {
		// Register RPC to DOMRpcProviderService
		service.registerRpcImplementation(this, ESTABLISH_SUBSCRIPTION_RPC, CREATE_SUBSCRIPTION_RPC, MODIFY_SUBSCRIPTION_RPC,
				DELETE_SUBSCRIPTION_RPC);
	}

	//key is device name and value is true or false based on registration status
	Map<String, YangpushDOMNotificationListener> notifyListenerRegister = new HashMap<String, YangpushDOMNotificationListener>();

	private YangpushDOMNotificationListener registerNotificationListenerPushUpdateForaNewDevice(Optional<DOMMountPoint> mountPoint, String node_name) {
		// register notification listener for PUSH-UPDATE
		// BUG: NotificationListener should be one instance or multiple??
		YangpushDOMNotificationListener listener = new YangpushDOMNotificationListener(this.globalDomDataBroker);
		final Optional<DOMNotificationService> service = mountPoint.get().getService(DOMNotificationService.class);
		QName qname = PushUpdate.QNAME;
		SchemaPath schemaPath = SchemaPath.create(true, qname);
		@SuppressWarnings("unused")
		final ListenerRegistration<YangpushDOMNotificationListener> accessTopologyListenerListenerRegistration = service
				.get().registerNotificationListener(listener, schemaPath);
		this.notifyListenerRegister.put(node_name, listener);
		return listener;
	}

	/**
	 * This method is invoked on RPC invocation of the registered method.
	 * rpc(localname) is used to invoke the correct requested method.
	 */
	@Override
	public CheckedFuture<DOMRpcResult, DOMRpcException> invokeRpc(DOMRpcIdentifier rpc, NormalizedNode<?, ?> input) {
		if(rpc.equals(ESTABLISH_SUBSCRIPTION_RPC)) {
		       LOG.debug("This is a establish subscription RPC");
                       return establishSubscriptionRpcHandler(input); 
		} else if (rpc.equals(CREATE_SUBSCRIPTION_RPC)) {
			LOG.debug("This is a create subscription RPC");
			return createSubscriptionRpcHandler(input);
		} else if (rpc.equals(MODIFY_SUBSCRIPTION_RPC)) {
			LOG.info("This is a modify subscrition RPC. Not supported ...");
		} else if (rpc.equals(DELETE_SUBSCRIPTION_RPC)) {
			deleteSubscriptionRpcHandler(input);
		} else {
			LOG.info("Unknown RPC...");
		}

		return Futures.immediateCheckedFuture((DOMRpcResult) new DefaultDOMRpcResult());
	}

	/*************************************************
	 * Section for DELETE-SUBSCRIPTION hanlder
	 *************************************************/
	/**
	 * Structure to hold input arguments for delete subscription.
	 *
	 * @author Ambika Prasad Tripathy
	 *
	 */
	final class DeleteSubscriptionRpcInput {

		public DeleteSubscriptionRpcInput() {
			this.node_name = "";
			// this.stream_name = "";
			this.subscription_id = "";
		}

		public String getNode_name() {
			return node_name;
		}

		public void setNode_name(String node_name) {
			this.node_name = node_name;
		}

		/*
		 * public String getStream_name() { return stream_name; }
		 *
		 * public void setStream_name(String stream_name) { this.stream_name =
		 * stream_name; }
		 */

		public String getSubscription_id() {
			return subscription_id;
		}

		public void setSubscription_id(String subscription_id) {
			this.subscription_id = subscription_id;
		}

		public String getError() {
			return YangpushErrors.printError(this.error);
		}

		public void setError(YangpushErrors.errors error) {
			this.error = error;
		}

		private String node_name;
		// private String stream_name;
		private String subscription_id;
		private YangpushErrors.errors error;

		public String toString() {
			return ("Delete Subscription Input paramters-> sub_id: " + subscription_id + " and Node_name:" + node_name);
		}

	}

	/**
	 * This method handles delete subscription RPC call input to the RPC is
	 * subscription-id
	 *
	 * @param input
	 */
	private void deleteSubscriptionRpcHandler(NormalizedNode<?, ?> input) {
		String error = "";
		// Parse input argument
		DeleteSubscriptionRpcInput inputData = parseDSExternalRpcInput(input, error);
		LOG.trace(inputData.toString());
		// TODO:Check existence of subscription and mount point before proceed.
		if (!this.yangpushSubscriptionEngine.isSubscriptionPresent(inputData.getSubscription_id(),
				inputData.getNode_name())) {
			LOG.error("DELETE-SUBSCRIPTION failed: Subscription info: [" + inputData.getSubscription_id() + " : "
					+ inputData.getNode_name() + "] is not present.");
			return;
		}

		// get mounted device
		final Optional<DOMMountPoint> mountPoint = getMountPoint(inputData.getNode_name(), error);
		if (!mountPoint.isPresent()) {
			LOG.error(error);
			error = null;
			LOG.error("DELETE-SUBSCRIPTION failed: Mount point:" + inputData.getNode_name() + "is not present.");
			return;
		}

		// Delete the subscription from device
		final Optional<DOMRpcService> rpcService = mountPoint.get().getService(DOMRpcService.class);
		QName uri = QName.create(I_NOTIF_NS, I_NOTIF_DATE, "delete-subscription");
		SchemaPath type = SchemaPath.create(true, uri);
		ContainerNode cn = createDeviceDSRpcInput(inputData, error);
		CheckedFuture<DOMRpcResult, DOMRpcException> result = rpcService.get().invokeRpc(type, cn);

		// update MD-SAL and subscription engine database
		this.yangpushSubscriptionEngine.updateSubscriptiontoMdSal(inputData.getSubscription_id(),
				inputData.getNode_name(), operations.delete);

		// remove the subscription from push-update listener handler
		YangpushDOMNotificationListener listener = this.notifyListenerRegister.get(inputData.getNode_name());
		listener.removeSubscriptionId(inputData.getSubscription_id());
	}

	/**
	 * Creates the Delete Subscription RPC's input container node for the
	 * device.
	 *
	 * @param inputData
	 * @param error
	 * @return
	 */
	private ContainerNode createDeviceDSRpcInput(DeleteSubscriptionRpcInput inputData, String error) {
		final ContainerNode dscn = Builders.containerBuilder().withNodeIdentifier(I_RPC_DS_INPUT)
				.withChild(ImmutableNodes.leafNode(I_NOTIF_SUB_ID, inputData.getSubscription_id())).build();

		return dscn;
	}

	/**
	 * Parses the input received from user for a delete subscription RPC. The
	 * input should container node_name and subscriptionId from the user
	 *
	 * @param input
	 * @param error
	 * @return
	 */
	private DeleteSubscriptionRpcInput parseDSExternalRpcInput(NormalizedNode<?, ?> input, String error) {
		DeleteSubscriptionRpcInput dsri = new DeleteSubscriptionRpcInput();
		ContainerNode conNode = null;
		error = "";
		if (input == null) {
			error = YangpushErrors.printError(errors.input_error);
			dsri = null;
			return dsri;
		}

		if (input instanceof ContainerNode) {
			conNode = (ContainerNode) input;
			try {
				// Decode Node_Name
				// ImmutableAugmentationNode{nodeIdentifier=AugmentationIdentifier{childNames=[(urn:opendaylight:params:xml:ns:yang:yangpush?revision=2015-01-05)node-name]},
				// value=[ImmutableLeafNode{nodeIdentifier=(urn:opendaylight:params:xml:ns:yang:yangpush?revision=2015-01-05)node-name,
				// value=test1, attributes={}}]}
				DataContainerChild<? extends PathArgument, ?> nodeName = null;
				Set<QName> childNames = new HashSet<>();
				childNames.add(ODL_PUSH_DEVICE_NAME);
				AugmentationIdentifier ai = new AugmentationIdentifier(childNames);
				Optional<DataContainerChild<? extends PathArgument, ?>> t = conNode.getChild(ai);
				if (t.isPresent()) {
					@SuppressWarnings("unchecked")
					Set<LeafNode<?>> t1 = (Set<LeafNode<?>>) t.get().getValue();
					if (!t1.isEmpty()) {
						nodeName = (LeafNode<?>) t1.toArray()[0];
						if (nodeName.getValue() != null) {
							dsri.setNode_name(nodeName.getValue().toString());
						} else {
							error = YangpushErrors.printError(errors.input_node_error);
						}
					} else {
						error = YangpushErrors.printError(errors.input_node_error);
					}
				} else {
					error = YangpushErrors.printError(errors.input_node_error);
				}

				if (!error.equals("")) {
					dsri = null;
					return dsri;
				}

				// Decode subscription-id
				DataContainerChild<? extends PathArgument, ?> subIdNode = null;
				NodeIdentifier sub_id = new NodeIdentifier(ODL_PUSH_DEVICE_SUB_ID);
				t = conNode.getChild(sub_id);
				if (t.isPresent()) {
					subIdNode = t.get();
					if (subIdNode.getValue() != null) {
						String subscription_id = subIdNode.getValue().toString();
						dsri.setSubscription_id(subscription_id);
					} else {
						error = YangpushErrors.printError(errors.input_sub_id_error);
					}
				} else {
					error = YangpushErrors.printError(errors.input_sub_id_error);
				}

				if (!error.equals("")) {
					dsri = null;
					return dsri;
				}
			} catch (Exception e) {
				LOG.error(e.toString());
			}
		} else {
			error = YangpushErrors.printError(errors.input_not_instatce_of);
			dsri = null;
		}
		return dsri;
	}
	
	
/********************************* estabilsh rpc ***********************************************/ 

	/*************************************************
	 * Section for ESTABLISH-SUBSCRIPTION hanlder
	 *************************************************/

	/**
	 * This method handles EstablishSubscription RPC call. Parse the input and
	 * based on that, establish subscription. and returns subscription-id for the
	 * subscription. If the subscription Id is -1 then there is error in
	 * subscriotion establish. Else a valid subscription id generated by this
	 * method.
	 * @return The initial guess used by
	 *
	 * Subscription-id : "-1" -> represents error in subscription
	 * Subscription-id : "yp-<integer>" -> represents the subscription id
	 * established. This subscription-id should be used for all further
	 * communication related to the subscription.
	 *
	 * @param input
	 * @return
	 */
	private CheckedFuture<DOMRpcResult, DOMRpcException> establishSubscriptionRpcHandler(NormalizedNode<?, ?> input) {
		LOG.debug("establishSubscriptionRpcHandler");
		String error = "";
		String sid = "";
		if (input.equals(null)) {
			sid = "-1";
			LOG.error(YangpushErrors.printError(errors.input_error));
			ContainerNode output = createESOutPut(sid);
			return Futures.immediateCheckedFuture((DOMRpcResult) new DefaultDOMRpcResult(output));
		}
		// Parse input arg (input data)
		EstablishSubscriptionRpcInput inputData = EsParseExternalRpcInput(input, error);
		if (inputData.equals(null)) {
			System.out.println(error);
			LOG.error(error);
			sid = "-1";
			error = null;
			ContainerNode output = createESOutPut(sid);
			return Futures.immediateCheckedFuture((DOMRpcResult) new DefaultDOMRpcResult(output));
		}

		// get mounted device
		final Optional<DOMMountPoint> mountPoint = getMountPoint(inputData.getDevice_name(), error);
		if (!mountPoint.isPresent()) {
			LOG.error(error);
			sid = "-1";
			error = null;
			ContainerNode output = createESOutPut(sid);
			return Futures.immediateCheckedFuture((DOMRpcResult) new DefaultDOMRpcResult(output));
		}

		// Register notification listener if not done for the subscription
		if (!this.notifyListenerRegister.containsKey(inputData.getDevice_name())){
			YangpushDOMNotificationListener listener = this.notifyListenerRegister.get(inputData.getDevice_name());
			//listener.insertSubscriptionId(inputData.getDevice_subscription_id());
			LOG.debug("Register to listen PUSH-UPDATE notification for New Device.");
		} else { 
			// TBD:Need to verify
			YangpushDOMNotificationListener listener = this.notifyListenerRegister.get(inputData.getDevice_name());
			//listener.insertSubscriptionId(inputData.getDevice_subscription_id());
			LOG.debug("Device already registered to listen PUSH-UPDATE notification.");
		}


		// register notification listener for PUSH-UPDATE

		final Optional<DOMRpcService> rpcService = mountPoint.get().getService(DOMRpcService.class);
		QName uri = QName.create(I_NOTIF_NS, I_NOTIF_DATE, "establish-subscription");
		SchemaPath type = SchemaPath.create(true, uri);

		ContainerNode newinput = NewcreateDeviceESRpcInput(inputData, error);

		CheckedFuture<DOMRpcResult, DOMRpcException> result = rpcService.get().invokeRpc(type, newinput);

		ContainerNode output = createESOutPut(sid);
		//error = null;

		// update MD-SAL and subscription engine database
		// TBD:Need to verify
		//this.yangpushSubscriptionEngine.updateSubscriptiontoMdSal(inputData.getSubscription_id(),inputData.getDevice_name(),operations.create);
		//this.yangpushSubscriptionEngine.updateSubscriptiontoMdSal(inputData.getDevice_subscription_id(),inputData.getDevice_name(),operations.create);

		return Futures.immediateCheckedFuture((DOMRpcResult) new DefaultDOMRpcResult(output));
		//return ;
	}


        /*
         * Creates a container node for EstablishSubscription RPC output.
         *
         * @param sid
         * @return containerNode for Establish Subscription Output
         */
        private ContainerNode createESOutPut(String sid) {
		LOG.debug("createESOutPut");
                final ContainerNode cn = Builders.containerBuilder().withNodeIdentifier(I_NOTIF_ES_OUTPUT)
			.withChild(ImmutableNodes.leafNode(I_NOTIF_SUB_ID, sid)).build();                              

                return cn;
        }


	/**
	 * This method parse the input arguments for establish-subscription RPC. The
	 * expected format of RPC input from user for establish-subscription is:
	 *
	 *
	 * <establish-subscription
	 *    xmlns="urn:ietf:params:xml:ns:yang:ietf-yang-push:1.0">
	 *    <stream>push-update</stream>
	 *    <filter netconf:type="xpath"
	 *    xmlns:ex="http://example.com/sample-data/1.0"
	 *    select="/ex:foo"/>
	 *    <period>500</period>
	 *    <encoding>encode-xml</encoding>
	 * </establish-subscription>
	 *
	 * @param input
	 *            : establish-subscription RPC input. input expects augment node
	 *            defined in ietf-yang-push.yang
	 * @param error
	 *            : returns error sting if there is any issue in input. else it
	 *            is a null string.
	 * @return
	 */
	private EstablishSubscriptionRpcInput EsParseExternalRpcInput(NormalizedNode<?, ?> input, String error) {
		LOG.debug("EsParseExternalRpcInput");
		EstablishSubscriptionRpcInput esri = new EstablishSubscriptionRpcInput();
		ContainerNode conNode = null;
		error = "";
		if (input == null) {
			error = YangpushErrors.printError(errors.input_error);
			esri = null;
			return esri;
		}

		if (input instanceof ContainerNode) {
			conNode = (ContainerNode) input;
			try {
				// Decode device_Name
				DataContainerChild<? extends PathArgument, ?> deviceName = null;
                                Set<QName> childNames = new HashSet<>();
				childNames.add(ODL_PUSH_DEVICE_NAME);
                                AugmentationIdentifier ai = new AugmentationIdentifier(childNames);
                                Optional<DataContainerChild<? extends PathArgument, ?>> t = conNode.getChild(ai);
                                if (t.isPresent()) {
					Set<LeafNode<?>> t1 = (Set<LeafNode<?>>) t.get().getValue();
                                        if (!t1.isEmpty()) {
						deviceName = (LeafNode<?>) t1.toArray()[0];
                                                if (deviceName.getValue() != null) {
							esri.setDevice_name(deviceName.getValue().toString());
						} else {
						      error = YangpushErrors.printError(errors.input_node_error);  							                                                 }
 					} else {
                                                error = YangpushErrors.printError(errors.input_node_error);
				        }
                                } else {
                                        error = YangpushErrors.printError(errors.input_node_error);
                                }

                                if (!error.equals("")) {
                                        esri = null;
                                        return esri;
                                }

				// Decode period
			   	DataContainerChild<? extends PathArgument, ?> periodNode = null;
                                NodeIdentifier updateTrigger = new NodeIdentifier(I_PUSH_UPDATE_TRIGGER);
                                t = conNode.getChild(updateTrigger);
                                if (t.isPresent()) {
					Set<LeafNode<?>> t1 = (Set<LeafNode<?>>) t.get().getValue();
                                        if (!t1.isEmpty()) {
						periodNode = (LeafNode<?>) t1.toArray()[0];
						if (periodNode.getValue() != null) {
							Long periodl = new Long(periodNode.getValue().toString());
		                                        esri.setPeriod(periodl);
						} else {
						       error = YangpushErrors.printError(errors.input_period_error);
						}
					} else {
                                                error = YangpushErrors.printError(errors.input_period_error);
                                        }
				} else {
                                        error = YangpushErrors.printError(errors.input_period_error);
                                }
				if (!error.equals("")) {
				        esri = null;
				        return esri;
	                }
				
				// Decode stream
				DataContainerChild<? extends PathArgument, ?> streamNode = null;
                                NodeIdentifier stream = new NodeIdentifier(I_NOTIF_STREAM);
                                t = conNode.getChild(stream);
                                if (t.isPresent()) {
					streamNode = t.get();
                                        if (streamNode.getValue() != null) {
						QName streamName = (QName) streamNode.getValue();
                                                String name = streamName.getLocalName();
		                                esri.setStream_name(name);
					} else {
					        error = YangpushErrors.printError(errors.input_stream_error);
					}
				} else {
				       error = YangpushErrors.printError(errors.input_stream_error);
				}

				if (!error.equals("")) {
				       esri = null;
				       return esri;
				}

				NodeIdentifier updateFilter = new NodeIdentifier(I_PUSH_UPDATE_FILTER);
                                NodeIdentifier subtreeFilter = new NodeIdentifier(I_PUSH_SUBTREE_FILTER);

				DataContainerChild<? extends PathArgument, ?> i = conNode.getChild(updateFilter).get();
                                ChoiceNode t1 = (ChoiceNode) i;
                                DataContainerChild<? extends PathArgument, ?> t2 = t1.getChild(subtreeFilter).get();
				if (t2 != null) {
					AnyXmlNode anyXmlFilter = (AnyXmlNode) t2;
                                        org.w3c.dom.Node nodeFilter = anyXmlFilter.getValue().getNode();
                                        org.w3c.dom.Document document = nodeFilter.getOwnerDocument();
                                        document.renameNode(nodeFilter, I_NOTIF_NS, "filter");
                                        DOMSource domSource = anyXmlFilter.getValue();
                                        esri.setFilter(domSource);
				} else {
				       error = "Invalid input filter. Value is NULL";
				}
			} catch (Exception e) {
			        LOG.error(e.toString());
			}
		} else {
	            error = YangpushErrors.printError(errors.input_not_instatce_of);
		    esri = null;
		}
	    return esri;
	}


	/**
	 * This method returns a container node based in input argument for
	 * establish-subscription rpc present in ietf-event-notifications.yang model. This input
	 * arguments augment
	 * nodes
	 *
	 * This containerNode will be used to establish-subscription in a mount device
	 *
	 * @param inputData
	 * @param error
	 *            : returns error sting if there is any issue in input. else it
	 *            is a null string.
	 * @return ContainerNode
	 */
	private ContainerNode NewcreateDeviceESRpcInput(EstablishSubscriptionRpcInput inputData, String error) {
		LOG.debug("***NewcreateDeviceESRpcInput***");
		// create input anyxml filter
		// CK: final NormalizedNodeAttrBuilder<NodeIdentifier, DOMSource, AnyXmlNode> anyXmlBuilder = Builders.anyXmlBuilder()
		//		.withNodeIdentifier(ES_FILTER_ARG).withValue(inputData.getFilter());
		// CK : AnyXmlNode val = anyXmlBuilder.build();
		// create input anyxml filter
		final NormalizedNodeAttrBuilder<NodeIdentifier, DOMSource, AnyXmlNode> anyXmlBuilder = Builders.anyXmlBuilder()
				.withNodeIdentifier(ES_FILTER_ARG).withValue(inputData.getFilter());
		AnyXmlNode val = anyXmlBuilder.build();

		// create augment nodes for ietf-yang-push / opendaylight-event-notifications
		Set<QName> childNames = new HashSet<>();
		childNames.add(I_PUSH_UPDATE_TRIGGER);
		childNames.add(I_PUSH_PERIOD);
		childNames.add(I_PUSH_ANCHOR_TIME);
		childNames.add(I_PUSH_NO_SYNC_ON_START);
		childNames.add(I_PUSH_DAMPENING_PERIOD);
		childNames.add(I_PUSH_EXCLUDED_CHANGE);
		childNames.add(I_PUSH_UPDATE_FILTER);
		childNames.add(I_PUSH_SUBTREE_FILTER);
		childNames.add(ODL_PUSH_DEVICE_NAME);

		AugmentationIdentifier ai = new AugmentationIdentifier(childNames);
		AugmentationNode an = Builders.augmentationBuilder().withNodeIdentifier(ai)
				.withChild(ImmutableNodes.leafNode(I_PUSH_PERIOD, inputData.getPeriod()))
				.withChild(ImmutableNodes.leafNode(I_PUSH_ANCHOR_TIME, inputData.getAnchorTime()))
				.withChild(ImmutableNodes.leafNode(I_PUSH_SUBTREE_FILTER, inputData.getSubtreeFilter()))
				.withChild(ImmutableNodes.leafNode(ODL_PUSH_DEVICE_NAME, inputData.getDevice_name())).build();

		// Create Input containerNode.
		final ContainerNode cn = Builders.containerBuilder().withNodeIdentifier(I_RPC_ES_INPUT)
				.withChild(ImmutableNodes.leafNode(ES_STREAM_ARG, inputData.getDevice_name())).withChild(val)
				.withChild(an).build();

		return cn;
	}

	/**
	 * DS to store parsed input parameters for establish-subscription RPC
	 *
	 * @author Ambika Prasad Tripathy
	 *
	 */
	final class EstablishSubscriptionRpcInput {

		public EstablishSubscriptionRpcInput() {
			this.period = new Long("0");
			this.anchor_time = new Long("0");
			this.filter = null;
			this.subtree_filter = null;
			this.stream_name = "";	
			this.device_name = "";
		}

		public Long getPeriod() {
			return period;
		}

		public void setPeriod(Long period) {
			this.period = period;
		}

		public Long getAnchorTime() {
			return anchor_time;
		}

		public void setAnchorTime(Long anchor_time) {
			this.anchor_time = anchor_time;
		}

		public DOMSource getFilter() {
			return filter;
		}

		public void setFilter(DOMSource filter) {
			this.filter = filter;
		}

		public DOMSource getSubtreeFilter() {
			return subtree_filter;
		}

		public void setSubtreeFilter(DOMSource subtree_filter) {
			this.subtree_filter = subtree_filter;
		}

		public String getStream_name() {
			return stream_name;
		}

		public void setStream_name(String stream_name) {
			this.stream_name = stream_name;
		}

		public String getDevice_name() {
			return device_name;
		}

		public void setDevice_name(final String device_name) {
			this.device_name = device_name;
		}

		public String getError() {
			return YangpushErrors.printError(this.error);
		}

		public void setError(YangpushErrors.errors error) {
			this.error = error;
		}

		private Long period;
		private Long anchor_time;
		private DOMSource filter;
		private DOMSource subtree_filter;
		private String stream_name;
		private String device_name;
		private YangpushErrors.errors error;
	}

	/********************************* end of establish rpc ***********************************************/ 

	/*************************************************
	 * Section for CREATE-SUBSCRIPTION hanlder
	 *************************************************/

	/**
	 * This method handles establishSubscription RPC call. Parse the input and
	 * based on that, creates subscription. and returns subscription-id for the
	 * subscription. If the subscription Id is -1 then there is error in
	 * subscriotion creation. Else a valid subscription id generated by this
	 * method.
	 *
	 * Subscription-id : "-1" -> represents error in subscription
	 * Subscription-id : "yp-<integer>" -> represents the subscription id
	 * created. This subscription-id should be used for all further
	 * communication related to the subscription.
	 *
	 * @param input
	 * @return
	 */
	private CheckedFuture<DOMRpcResult, DOMRpcException> createSubscriptionRpcHandler(NormalizedNode<?, ?> input) {
		String error = "";
		String sid = "";
		if (input.equals(null)) {
			sid = "-1";
			LOG.error(YangpushErrors.printError(errors.input_error));
			return Futures.immediateCheckedFuture((DOMRpcResult) new DefaultDOMRpcResult((NormalizedNode<?, ?>) null));
		}

		// Parse input arg
		CreateSubscriptionRpcInput inputData = parseExternalRpcInput(input, error);
		if (inputData.equals(null)) {
			System.out.println(error);
			LOG.error(error);
			sid = "-1";
			error = null;
			return Futures.immediateCheckedFuture((DOMRpcResult) new DefaultDOMRpcResult((NormalizedNode<?, ?>) null));
		}

		// get subscription id from subscription engine.
		sid = this.yangpushSubscriptionEngine.generateSubscriptionId();
		inputData.setSubscription_id(sid);

		//TODO: only local stuff here (not mounted devices)

		return Futures.immediateCheckedFuture((DOMRpcResult) new DefaultDOMRpcResult((NormalizedNode<?, ?>) null));
	}

	/**
	 * This method parse the input arguments for create-subscription RPC. The
	 * expected format of RPC input from user for create-subscription is:
	 *
	 *
	 * <input xmlns="urn:ietf:params:xml:ns:yang:ietf-datastore-push"> <stream>
	 * push-data</stream> <period>10</period>
	 * <subtree-filter type="subtree"> <interface-configurations xmlns=
	 * "http://cisco.com/ns/yang/Cisco-IOS-XR-ifmgr-cfg"/> </subtree-filter>
	 * <node-name xmlns="urn:opendaylight:params:xml:ns:yang:yangpush">xrvr1
	 * </node-name> 
	 * </input>
	 *
	 * @param input
	 *            : create-subscription RPC input. input expects augment node
	 *            defined in yangpush.yang
	 * @param error
	 *            : returns error sting if there is any issue in input. else it
	 *            is a null string.
	 * @return
	 */

	private CreateSubscriptionRpcInput parseExternalRpcInput(NormalizedNode<?, ?> input, String error) {
		CreateSubscriptionRpcInput csri = new CreateSubscriptionRpcInput();
		ContainerNode conNode = null;
		error = "";
		if (input == null) {
			error = YangpushErrors.printError(errors.input_error);
			csri = null;
			return csri;
		}

		if (input instanceof ContainerNode) {
			conNode = (ContainerNode) input;
			try {
				// Decode stream
				DataContainerChild<? extends PathArgument, ?> streamNode = null;
				NodeIdentifier stream = new NodeIdentifier(I_NOTIF_STREAM);
				Optional<DataContainerChild<? extends PathArgument, ?>> t = conNode.getChild(stream);
				if (t.isPresent()) {
					streamNode = t.get();
					if (streamNode.getValue() != null) {
						QName streamName = (QName) streamNode.getValue();
						String name = streamName.getLocalName();
						csri.setStream_name(name);
					} else {
						error = YangpushErrors.printError(errors.input_stream_error);
					}
				} else {
					error = YangpushErrors.printError(errors.input_stream_error);
				}

				if (!error.equals("")) {
					csri = null;
					return csri;
				}

				NodeIdentifier filtertype = new NodeIdentifier(I_NOTIF_FILTER_TYPE);
				NodeIdentifier filter = new NodeIdentifier(I_NOTIF_FILTER);

				DataContainerChild<? extends PathArgument, ?> i = conNode.getChild(filtertype).get();
				ChoiceNode t1 = (ChoiceNode) i;
				DataContainerChild<? extends PathArgument, ?> t2 = t1.getChild(filter).get();
				if (t2 != null) {
					AnyXmlNode anyXmlFilter = (AnyXmlNode) t2;
					org.w3c.dom.Node nodeFilter = anyXmlFilter.getValue().getNode();
					org.w3c.dom.Document document = nodeFilter.getOwnerDocument();
					document.renameNode(nodeFilter, NOTIFICATION_NS, "filter");
					DOMSource domSource = anyXmlFilter.getValue();
					csri.setFilter(domSource);
				} else {
					error = "Invalid input filter. Value is NULL";
				}
			} catch (Exception e) {
				LOG.error(e.toString());
			}
		} else {
			error = YangpushErrors.printError(errors.input_not_instatce_of);
			csri = null;
		}
		return csri;
	}

	/**
	 * This method returns the mount point got the mount device present in
	 * node_name argument, Is no device found, then return null.
	 *
	 * @param node_name
	 *            :
	 * @param error
	 *            : returns error sting if there is any issue in input. else it
	 *            is a null string.
	 * @return DOMMountPoint
	 */
	private Optional<DOMMountPoint> getMountPoint(String node_name, String error) {
		LOG.debug("getMountPoint");
		// to get the mountPoint of the Device
		Optional<DOMMountPoint> mountPoint = null;
		try {
			QName net_topo = QName.create("urn:TBD:params:xml:ns:yang:network-topology", "2013-10-21",
					"network-topology");
			QName topo = QName.create(net_topo, "topology");
			QName topoId = QName.create(topo, "topology-id");
			QName fromNode = QName.create(net_topo, "node");
			QName fromIdName = QName.create(fromNode, "node-id");
			// The InstanceIdentifier of our device we want to invoke the
			// subscription on
			YangInstanceIdentifier iid = YangInstanceIdentifier.builder().node(net_topo).node(topo)
					.nodeWithKey(topo, topoId, "topology-netconf").node(fromNode)
					.nodeWithKey(fromNode, fromIdName, node_name).build();

			boolean present = YangpushDomProvider.NETCONF_TOPO_IID.contains(iid);
			// The mount of the device, or more accurate: an Optional, with our
			// MountPoint
			if (present) {
				mountPoint = mountPointService.getMountPoint(iid);
				LOG.debug("getMountPoint");
			} else {
				error = "Mount point is not available for node_name = " + node_name;
			}
		} catch (Exception e) {
			throw e;
		}
		return mountPoint;
	}

	/**
	 * DS to store parsed input parameters for create-subscription RPC
	 *
	 * @author Ambika Prasad Tripathy
	 *
	 */
	final class CreateSubscriptionRpcInput {

		public CreateSubscriptionRpcInput() {
			this.filter = null;
			this.stream_name = "";
			this.subscription_id = "";
		}

		public String getStream_name() {
			return stream_name;
		}

		public void setStream_name(String stream_name) {
			this.stream_name = stream_name;
		}

		public String getSubscription_id() {
			return subscription_id;
		}

		public void setSubscription_id(String subscription_id) {
			this.subscription_id = subscription_id;
		}

		public DOMSource getFilter() {
			return filter;
		}

		public void setFilter(DOMSource filter) {
			this.filter = filter;
		}

		public String getError() {
			return YangpushErrors.printError(this.error);
		}

		public void setError(YangpushErrors.errors error) {
			this.error = error;
		}

		private String stream_name;
		private String subscription_id;
		private DOMSource filter;
		private YangpushErrors.errors error;

	}

	/*************************************************
	 * Section for MODIFY-SUBSCRIPTION hanlder
	 *************************************************/
	// TODO

	// Also need code to handle establish-subscription RPC output

	// May also need code for delete/modify-subscription RPC output

	// need to decide what to do with create-subscription
	// do we just stub that out - as won't use it to subscribe to device?
	
}
