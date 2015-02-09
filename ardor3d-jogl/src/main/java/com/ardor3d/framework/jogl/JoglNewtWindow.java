/**
 * Copyright (c) 2008-2012 Ardor Labs, Inc.
 *
 * This file is part of Ardor3D.
 *
 * Ardor3D is free software: you can redistribute it and/or modify it
 * under the terms of its license which may be found in the accompanying
 * LICENSE file or at <http://www.ardor3d.com/LICENSE>.
 */

package com.ardor3d.framework.jogl;

import java.util.List;
import java.util.concurrent.CountDownLatch;

import com.jogamp.nativewindow.util.Dimension;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLContext;
import com.jogamp.opengl.GLRunnable;

import com.ardor3d.annotation.MainThread;
import com.ardor3d.framework.DisplaySettings;
import com.ardor3d.framework.NativeCanvas;
import com.ardor3d.image.Image;
import com.jogamp.newt.MonitorDevice;
import com.jogamp.newt.MonitorMode;
import com.jogamp.newt.event.KeyListener;
import com.jogamp.newt.event.MouseListener;
import com.jogamp.newt.event.WindowAdapter;
import com.jogamp.newt.event.WindowEvent;
import com.jogamp.newt.event.WindowListener;
import com.jogamp.newt.opengl.GLWindow;
import com.jogamp.newt.util.MonitorModeUtil;

/**
 * Ardor3D NEWT lightweight window, NEWT "top level" component for the OpenGL rendering of Ardor3D with JOGL that
 * supports the NEWT input system directly and its abstraction in Ardor3D (com.ardor3d.input.jogl). This is the fastest
 * and the most cross-platform, reliable, memory-efficient and complete "surface" of the JogAmp backend
 */
public class JoglNewtWindow implements NativeCanvas, NewtWindowContainer {

    private final JoglCanvasRenderer _canvasRenderer;
    private boolean _inited = false;
    private boolean _isClosing = false;

    private final DisplaySettings _settings;

    private final JoglDrawerRunnable _drawerGLRunnable;

    private final GLWindow _newtWindow;

    public JoglNewtWindow(final JoglCanvasRenderer canvasRenderer, final DisplaySettings settings) {
        this(canvasRenderer, settings, true, false, false, false);
    }

    public JoglNewtWindow(final JoglCanvasRenderer canvasRenderer, final DisplaySettings settings,
            final boolean onscreen, final boolean bitmapRequested, final boolean pbufferRequested,
            final boolean fboRequested) {
        this(canvasRenderer, settings, onscreen, bitmapRequested, pbufferRequested, fboRequested, new CapsUtil());
    }

    public JoglNewtWindow(final JoglCanvasRenderer canvasRenderer, final DisplaySettings settings,
            final boolean onscreen, final boolean bitmapRequested, final boolean pbufferRequested,
            final boolean fboRequested, final CapsUtil capsUtil) {
        _newtWindow = GLWindow.create(capsUtil.getCapsForSettings(settings, onscreen, bitmapRequested,
                pbufferRequested, fboRequested));
        _drawerGLRunnable = new JoglDrawerRunnable(canvasRenderer);
        _settings = settings;
        _canvasRenderer = canvasRenderer;
        _canvasRenderer._doSwap = true;// true - do swap in renderer.
        setAutoSwapBufferMode(false);// false - doesn't swap automatically in JOGL itself
    }

    /**
     * Applies all settings not related to OpenGL (screen resolution, screen size, etc...)
     * */
    private void applySettings() {
        _newtWindow.setUndecorated(_settings.isFullScreen());
        _newtWindow.setFullscreen(_settings.isFullScreen());
        // FIXME Ardor3D does not allow to change the resolution
        /**
         * uses the filtering relying on resolution with the size to fetch only the screen mode matching with the
         * current resolution
         */
        if (_settings.isFullScreen()) {
            final MonitorDevice monitor = _newtWindow.getMainMonitor();
            List<MonitorMode> monitorModes = monitor.getSupportedModes();
            // the resolution is provided by the user
            final Dimension dimension = new Dimension(_settings.getWidth(), _settings.getHeight());
            monitorModes = MonitorModeUtil.filterByResolution(monitorModes, dimension);
            monitorModes = MonitorModeUtil.getHighestAvailableBpp(monitorModes);
            if (_settings.getFrequency() > 0) {
                monitorModes = MonitorModeUtil.filterByRate(monitorModes, _settings.getFrequency());
            } else {
                monitorModes = MonitorModeUtil.getHighestAvailableRate(monitorModes);
            }
            monitor.setCurrentMode(monitorModes.get(0));
        }
    }

    public void addKeyListener(final KeyListener keyListener) {
        _newtWindow.addKeyListener(keyListener);
    }

    public void addMouseListener(final MouseListener mouseListener) {
        _newtWindow.addMouseListener(mouseListener);
    }

    public void addWindowListener(final WindowListener windowListener) {
        _newtWindow.addWindowListener(windowListener);
    }

    public GLContext getContext() {
        return _newtWindow.getContext();
    }

    /**
     * Returns the width of the client area including insets (window decorations) in window units.
     *
     * @return width of the client area including insets (window decorations) in window units
     */
    public int getWidth() {
        return _newtWindow.getWidth() + (_newtWindow.getInsets() == null ? 0 : _newtWindow.getInsets().getTotalWidth());
    }

    /**
     * Returns the width of the client area including insets (window decorations) in pixel units.
     *
     * @return width of the client area including insets (window decorations) in pixel units
     */
    public int getWidthInPixelUnits() {
        return _newtWindow.convertToPixelUnits(new int[] { getWidth(), 0 })[0];
    }

    /**
     * Returns the width of the client area excluding insets (window decorations) in window units.
     *
     * @return width of the client area excluding insets (window decorations) in window units
     */
    public int getSurfaceWidthInWindowUnits() {
        return _newtWindow.getWidth();
    }

    /**
     * Returns the width of the client area excluding insets (window decorations) in pixel units.
     *
     * @return width of the client area excluding insets (window decorations) in pixel units
     */
    public int getSurfaceWidth() {
        return _newtWindow.getSurfaceWidth();
    }

    /**
     * Returns the height of the client area including insets (window decorations) in window units.
     *
     * @return height of the client area including insets (window decorations) in window units
     */
    public int getHeight() {
        return _newtWindow.getHeight()
                + (_newtWindow.getInsets() == null ? 0 : _newtWindow.getInsets().getTotalHeight());
    }

    /**
     * Returns the height of the client area including insets (window decorations) in pixel units.
     *
     * @return height of the client area including insets (window decorations) in pixel units
     */
    public int getHeightInPixelUnits() {
        return _newtWindow.convertToPixelUnits(new int[] { 0, getHeight() })[1];
    }

    /**
     * Returns the height of the client area excluding insets (window decorations) in window units.
     *
     * @return height of the client area excluding insets (window decorations) in window units
     */
    public int getSurfaceHeightInWindowUnits() {
        return _newtWindow.getHeight();
    }

    /**
     * Returns the height of the client area excluding insets (window decorations) in pixel units.
     *
     * @return height of the client area excluding insets (window decorations) in pixel units
     */
    public int getSurfaceHeight() {
        return _newtWindow.getSurfaceHeight();
    }

    public int getX() {
        return _newtWindow.getX();
    }

    public int getY() {
        return _newtWindow.getY();
    }

    public boolean isVisible() {
        return _newtWindow.isVisible();
    }

    public void setSize(final int width, final int height) {
        _newtWindow.setTopLevelSize(width, height);
    }

    public void setVisible(final boolean visible) {
        _newtWindow.setVisible(visible);
    }

    /**
     * Enables or disables automatic buffer swapping for this JoglNewtWindow. By default this property is set to false
     *
     * @param autoSwapBufferModeEnabled
     */
    public void setAutoSwapBufferMode(final boolean autoSwapBufferModeEnabled) {
        _newtWindow.setAutoSwapBufferMode(autoSwapBufferModeEnabled);
    }

    @Override
    @MainThread
    public void init() {
        if (_inited) {
            return;
        }

        // Set the size very early to prevent the default one from being used (typically when exiting full screen mode)
        setSize(_settings.getWidth(), _settings.getHeight());
        // Make the window visible to realize the OpenGL surface.
        setVisible(true);
        if (_newtWindow.isRealized()) {
            _newtWindow.addWindowListener(new WindowAdapter() {
                @Override
                public void windowDestroyNotify(final WindowEvent e) {
                    _isClosing = true;
                }

                // public void windowResized(final WindowEvent e) {
                // _newtWindow.invoke(true, new GLRunnable() {
                //
                // @Override
                // public boolean run(GLAutoDrawable glAutoDrawable) {
                // _canvasRenderer._camera.resize(_newtWindow.getWidth(), _newtWindow.getHeight());
                // _canvasRenderer._camera.setFrustumPerspective(_canvasRenderer._camera.getFovY(),
                // (float) _newtWindow.getWidth() / (float) _newtWindow.getHeight(),
                // _canvasRenderer._camera.getFrustumNear(),
                // _canvasRenderer._camera.getFrustumFar());
                // return true;
                // }
                // });
                // }
            });

            // Request the focus here as it cannot work when the window is not visible
            _newtWindow.requestFocus();
            applySettings();

            _canvasRenderer.setContext(getContext());

            _newtWindow.invoke(true, new GLRunnable() {
                @Override
                public boolean run(final GLAutoDrawable glAutoDrawable) {
                    _canvasRenderer.init(_settings, _canvasRenderer._doSwap);
                    return true;
                }
            });
            _inited = true;
        }
    }

    @Override
    public void draw(final CountDownLatch latch) {
        if (!_inited) {
            init();
        }

        if (/* isShowing() */isVisible()) {
            _newtWindow.invoke(true, _drawerGLRunnable);
        }
        if (latch != null) {
            latch.countDown();
        }
    }

    @Override
    public JoglCanvasRenderer getCanvasRenderer() {
        return _canvasRenderer;
    }

    @Override
    public void close() {
        _newtWindow.destroy();
    }

    @Override
    public boolean isActive() {
        return _newtWindow.hasFocus();
    }

    @Override
    public boolean isClosing() {
        return _isClosing;
    }

    @Override
    public void setVSyncEnabled(final boolean enabled) {
        _newtWindow.invoke(true, new GLRunnable() {
            @Override
            public boolean run(final GLAutoDrawable glAutoDrawable) {
                _newtWindow.getGL().setSwapInterval(enabled ? 1 : 0);
                return false;
            }
        });
    }

    @Override
    public void setTitle(final String title) {
        _newtWindow.setTitle(title);
    }

    @Override
    public void setIcon(final Image[] iconImages) {
        // FIXME supported by NEWT but not yet implemented, use System.setProperty("newt.window.icons",
        // "my-path-to-my-icon-file/icon.png");
    }

    @Override
    public void moveWindowTo(final int locX, final int locY) {
        _newtWindow.setTopLevelPosition(locX, locY);
    }

    @Override
    public GLWindow getNewtWindow() {
        return _newtWindow;
    }
}
