/*******************************************************************************
 * Copyright (c) 2012-2017 Codenvy, S.A.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Codenvy, S.A. - initial API and implementation
 *******************************************************************************/
package org.eclipse.che.ide.ui.dropdown;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.PopupPanel;
import com.google.gwt.user.client.ui.ScrollPanel;
import com.google.gwt.user.client.ui.Widget;

import org.eclipse.che.commons.annotation.Nullable;
import org.eclipse.che.ide.ui.dropdown.old.DropDownWidget;

import java.util.HashMap;
import java.util.Map;

import static com.google.gwt.user.client.ui.PopupPanel.AnimationType.ROLL_DOWN;

/**
 * Drop down list widget.
 */
public class DropDownList extends Composite {

    private static final DropDownListUiBinder     UI_BINDER = GWT.create(DropDownListUiBinder.class);
    private static final DropDownWidget.Resources resources = GWT.create(DropDownWidget.Resources.class);

    private final FlowPanel                     contentPanel;
    private final PopupPanel                    dropDownPanel;
    private final Widget                        emptyStateWidget;
    private final Map<DropDownListItem, Widget> itemsWidgets;

    @UiField
    FlowPanel listHeader;
    @UiField
    FlowPanel selectedElementName;
    @UiField
    FlowPanel dropButton;

    private DropDownListItem selectedItem;

    /** Create new drop down widget. */
    public DropDownList() {
        this(new Label("---"));
    }

    /**
     * Create new drop down widget.
     * Uses the given {@code emptyStateWidget} to display as the empty list's header.
     */
    public DropDownList(Widget emptyStateWidget) {
        this.emptyStateWidget = emptyStateWidget;
        itemsWidgets = new HashMap<>();

        initWidget(UI_BINDER.createAndBindUi(this));

        listHeader.setStyleName(resources.dropdownListCss().menuElement());

        dropButton.getElement().appendChild(resources.expansionImage().getSvg().getElement());
        dropButton.addStyleName(resources.dropdownListCss().expandedImage());

        dropDownPanel = new PopupPanel(true);
        dropDownPanel.setAnimationEnabled(true);
        dropDownPanel.setAnimationType(ROLL_DOWN);
        dropDownPanel.setWidth("350px");
        dropDownPanel.setHeight("150px");

        contentPanel = new FlowPanel();
        dropDownPanel.add(new ScrollPanel(contentPanel));

        attachEventHandlers();
    }

    private void attachEventHandlers() {
        emptyStateWidget.addDomHandler(event -> dropDownPanel.showRelativeTo(DropDownList.this), ClickEvent.getType());
        dropButton.addDomHandler(event -> dropDownPanel.showRelativeTo(DropDownList.this), ClickEvent.getType());
    }

    private void checkListEmpty() {
        if (contentPanel.getWidgetCount() == 0) {
            setHeaderWidget(emptyStateWidget);
        }
    }

    /** Set the specified widget to the list's header. */
    private void setHeaderWidget(Widget widget) {
        selectedElementName.clear();
        selectedElementName.add(widget);
    }

    /** Returns the currently selected item or {@code null} if none. */
    @Nullable
    public DropDownListItem getSelectedItem() {
        return selectedItem;
    }

    /**
     * Add the given {@code item} to the top of the list.
     *
     * @param item
     *         item to add to the list
     * @param renderer
     *         renderer provides widgets for representing the given {@code item} in the list
     */
    public void addItem(DropDownListItem item, DropDownListItemRenderer renderer) {
        final Widget headerWidget = renderer.renderHeaderWidget();
        final Widget listWidget = renderer.renderListWidget();

        headerWidget.addDomHandler(event -> dropDownPanel.showRelativeTo(DropDownList.this), ClickEvent.getType());
        setHeaderWidget(headerWidget);

        listWidget.addDomHandler(event -> {
            selectedItem = item;
            setHeaderWidget(headerWidget);
            dropDownPanel.hide();
        }, ClickEvent.getType());

        itemsWidgets.put(item, listWidget);

        contentPanel.insert(listWidget, 0);
    }

    /**
     * Shorthand for quick adding text value to the list.
     *
     * @param value
     *         text value to add to the list
     * @return added item which wraps the given {@code value}
     */
    public BaseListItem<String> addItem(String value) {
        final BaseListItem<String> item = new BaseListItem<>(value);
        final StringItemRenderer renderer = new StringItemRenderer(item);

        addItem(item, renderer);

        return item;
    }

    /** Remove item from the list. */
    public void removeItem(DropDownListItem item) {
        final Widget widget = itemsWidgets.remove(item);

        if (widget != null) {
            contentPanel.remove(widget);
        }

        // TODO: check whether another item should be set to header

        checkListEmpty();
    }

    /** Clear the list. */
    public void clear() {
        itemsWidgets.clear();
        contentPanel.clear();

        checkListEmpty();
    }

    interface DropDownListUiBinder extends UiBinder<Widget, DropDownList> {
    }
}
