/**
 * Copyright (c) 2008-2012 Ardor Labs, Inc.
 *
 * This file is part of Ardor3D.
 *
 * Ardor3D is free software: you can redistribute it and/or modify it 
 * under the terms of its license which may be found in the accompanying
 * LICENSE file or at <http://www.ardor3d.com/LICENSE>.
 */

package com.ardor3d.extension.model.md3;

import java.util.ArrayList;
import java.util.List;

import com.ardor3d.scenegraph.Node;

public class Md3DataStore {

    private final Node _mainNode;

    private final List<String> _frameNames = new ArrayList<String>();

    private final List<String> _skinNames = new ArrayList<String>();

    public Md3DataStore(final Node mainNode) {
        super();
        _mainNode = mainNode;
    }

    public Node getScene() {
        return _mainNode;
    }

    public List<String> getFrameNames() {
        return _frameNames;
    }

    public int getFrameIndex(final String frameName) {
        return _frameNames.indexOf(frameName);
    }

    public List<String> getSkinNames() {
        return _skinNames;
    }
}
