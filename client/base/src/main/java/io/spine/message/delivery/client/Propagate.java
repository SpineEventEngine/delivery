/*
 * Copyright (c) 2000-2023 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.message.delivery.client;

public final class Propagate implements ErrorHandlingStrategy {


    public static Propagate create(){
        return new Propagate();
    }

    private Propagate(){

    }

    @Override
    public void runWithStrategy(VoidOperation operation) {
        operation.run(); // Do not catch exceptions and they will propagate.
    }

    @Override
    public <R> R runWithStrategy(OperationWithResult<R> operation) {
        return operation.run(); // Do not catch exceptions and they will propagate.
    }
}
