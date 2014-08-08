/**
 * Copyright (c) 2008-2012 Ardor Labs, Inc.
 *
 * This file is part of Ardor3D.
 *
 * Ardor3D is free software: you can redistribute it and/or modify it 
 * under the terms of its license which may be found in the accompanying
 * LICENSE file or at <http://www.ardor3d.com/LICENSE>.
 */

package com.ardor3d.util.shader;

import java.io.IOException;

import com.ardor3d.util.export.InputCapsule;
import com.ardor3d.util.export.OutputCapsule;
import com.ardor3d.util.export.Savable;

/**
 * An utily class to store shader's uniform variables content.
 */
public class ShaderVariable implements Savable {
    /** Name of the uniform variable. * */
    public String name;

    /** ID of uniform. * */
    public int variableID = -1;

    /** Needs to be refreshed */
    public boolean needsRefresh = true;

    public boolean errorLogged = false;

    public boolean hasData() {
        return true;
    }

    public int getSize() {
        return 1;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((name == null) ? 0 : name.hashCode());
        result = prime * result + variableID;
        return result;
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final ShaderVariable other = (ShaderVariable) obj;
        if (variableID != -1) {
            return other.variableID == variableID;
        } else if (other.variableID != -1) {
            return other.variableID == variableID;
        } else {
            return (equals(name, other.name));
        }
    }

    // TODO replace it by java.util.Objects.equals(Object, Object)
    private boolean equals(final Object a, final Object b) {
        if (a == b) {
            return true;
        } else {
            return a != null && a.equals(b);
        }
    }

    @Override
    public void write(final OutputCapsule capsule) throws IOException {
        capsule.write(name, "name", "");
        capsule.write(variableID, "variableID", -1);
    }

    @Override
    public void read(final InputCapsule capsule) throws IOException {
        name = capsule.readString("name", "");
        variableID = capsule.readInt("variableID", -1);
    }

    @Override
    public Class<? extends ShaderVariable> getClassTag() {
        return this.getClass();
    }
}