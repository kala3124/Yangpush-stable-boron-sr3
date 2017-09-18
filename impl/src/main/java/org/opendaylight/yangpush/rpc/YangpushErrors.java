/*
 * Copyright © 2015 Copyright (c) 2015 cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.yangpush.rpc;

/**
 * This class defines error strings used in yang-push project.
 *
 * @author Ambika.Tripathy
 *
 */
public class YangpushErrors {
    private static final String errorString[] ={
            "Input missing or null",
            "Period is missing in Input or value is absent",
            "Stream is missing in Input or value is absent",
            "Invalid filter in Input",
            "Node_Name absent in Input or value is absent",
            "Input is not a instance of Contaier Node",
            "Node not found in ODL topology",
            "Node has no capability to support datastore push create-subscription",
            "YANG-PUSH capability version missmatch",
            "Invalid Period for subscription",
            "Error in subscription filter",
            "Subscription not possible, due to resource unavailability",
            "Subscription id error in RPC input",
            "Node has no capability to support datastore push establish-subscription",
    };

    public static enum errors{
        input_error,
        input_period_error,
        input_stream_error,
        input_filter_error,
        input_node_error,
        input_not_instatce_of,
        node_not_found_error,
        node_capability_error,
        node_capability_version_error,
        period_error,
        filter_error,
        subscription_creation_error,
        input_sub_id_error,
        max_error_count,
        subscription_establish_error,
    };

    /**
     * Method returns a string quuivanr
     * @param id
     * @return
     */
    public static String printError(errors id) {
        return errorString[id.hashCode()];
    }
}
