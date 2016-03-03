/*******************************************************************************
 * Copyright (c) 2012-2016 Codenvy, S.A.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Codenvy, S.A. - initial API and implementation
 *******************************************************************************/
package org.eclipse.che.ide.ui;

import com.google.gwt.event.dom.client.BlurEvent;
import com.google.gwt.event.dom.client.BlurHandler;
import com.google.gwt.event.dom.client.FocusEvent;
import com.google.gwt.event.dom.client.FocusHandler;
import com.google.gwt.user.client.ui.FocusWidget;
import com.google.inject.Singleton;

import java.util.HashMap;
import java.util.Map;

/**
 * The class contains business logic which allows to track the focus for widgets.
 *
 * @author Roman Nikitenko
 */
@Singleton
public class WidgetFocusTracker {

    private Map<FocusWidget, Boolean> storage;


    /**
     * Add widget to track the focus
     *
     * @param widget
     *         the widget to track
     */
    public void subscribe(final FocusWidget widget) {
        if (storage == null) {
            storage = new HashMap<>();
        }

        storage.put(widget, false);
        widget.addFocusHandler(new FocusHandler() {
            @Override
            public void onFocus(FocusEvent event) {
                storage.put(widget, true);
            }
        });

        widget.addBlurHandler(new BlurHandler() {
            @Override
            public void onBlur(BlurEvent event) {
                storage.put(widget, false);
            }
        });
    }

    /**
     * Unsubscribe widget from tracking the focus
     *
     * @param widget
     *         the widget to unsubscribe from tracking the focus
     */
    public void unSubscribe(FocusWidget widget) {
        if (widget == null || !storage.containsKey(widget)) {
            return;
        }
        storage.remove(widget);
    }

    /** Returns is the widget in the focus */
    public boolean isWidgetFocused(FocusWidget widget) {
        return widget == null || !storage.containsKey(widget) ? false : storage.get(widget);
    }
}
