/**
 * Copyright (c) 2008-2012 Ardor Labs, Inc.
 *
 * This file is part of Ardor3D.
 *
 * Ardor3D is free software: you can redistribute it and/or modify it 
 * under the terms of its license which may be found in the accompanying
 * LICENSE file or at <http://www.ardor3d.com/LICENSE>.
 */

package com.ardor3d.input.logical;

import com.ardor3d.input.ControllerEvent;
import com.ardor3d.input.ControllerInfo;
import com.ardor3d.input.ControllerWrapper;
import com.google.common.collect.PeekingIterator;

public class DummyControllerWrapper implements ControllerWrapper {
    public static final DummyControllerWrapper INSTANCE = new DummyControllerWrapper();

    PeekingIterator<ControllerEvent> empty = new PeekingIterator<ControllerEvent>() {
        @Override
        public boolean hasNext() {
            return false;
        }

        @Override
        public void remove() {}

        @Override
        public ControllerEvent peek() {
            return null;
        }

        @Override
        public ControllerEvent next() {
            return null;
        }
    };

    @Override
    public PeekingIterator<ControllerEvent> getEvents() {
        return empty;
    }

    @Override
    public void init() {
        ; // ignore, does nothing
    }

    @Override
    public int getControllerCount() {
        return 0;
    }

    @Override
    public ControllerInfo getControllerInfo(final int controllerIndex) {
        return null;
    }
}