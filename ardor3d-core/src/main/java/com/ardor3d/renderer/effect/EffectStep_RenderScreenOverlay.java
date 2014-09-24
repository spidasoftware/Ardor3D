/**
 * Copyright (c) 2008-2012 Ardor Labs, Inc.
 *
 * This file is part of Ardor3D.
 *
 * Ardor3D is free software: you can redistribute it and/or modify it 
 * under the terms of its license which may be found in the accompanying
 * LICENSE file or at <http://www.ardor3d.com/LICENSE>.
 */

package com.ardor3d.renderer.effect;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

import com.ardor3d.renderer.state.RenderState;
import com.ardor3d.renderer.state.RenderState.StateType;
import com.ardor3d.renderer.state.TextureState;

public class EffectStep_RenderScreenOverlay implements EffectStep {

    private final EnumMap<StateType, RenderState> _states = new EnumMap<StateType, RenderState>(StateType.class);
    private final TextureState _texState = new TextureState();
    private final Map<String, Integer> _targetMap = new HashMap<String, Integer>();

    public EffectStep_RenderScreenOverlay() {
        _states.put(StateType.Texture, _texState);
    }

    @Override
    public void apply(final EffectManager manager) {
        // prepare our texture state
        for (final String key : _targetMap.keySet()) {
            final RenderTarget target = manager.getRenderTarget(key);
            final Integer unit = _targetMap.get(key);
            _texState.setTexture(target.getTexture(), unit.intValue());
        }

        // render a quad to the screen using our states.
        manager.renderFullScreenQuad(_states);
    }

    public TextureState getTextureState() {
        return _texState;
    }

    public EnumMap<StateType, RenderState> getEnforcedStates() {
        return _states;
    }

    public Map<String, Integer> getTargetMap() {
        return _targetMap;
    }
}
