/*******************************************************************************
 * Copyright (c) 2014-2015 Codenvy, S.A.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Codenvy, S.A. - initial API and implementation
 *******************************************************************************/
package org.eclipse.che.ide.editor.orion.client;

import com.google.gwt.core.client.Callback;
import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.JavaScriptException;
import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.core.client.JsArrayString;
import com.google.gwt.core.client.RunAsyncCallback;
import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.LinkElement;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import org.eclipse.che.ide.api.extension.Extension;
import org.eclipse.che.ide.api.notification.NotificationManager;
import org.eclipse.che.ide.api.notification.StatusNotification;
import org.eclipse.che.ide.editor.orion.client.jso.OrionKeyBindingModule;
import org.eclipse.che.ide.editor.orion.client.jso.OrionTextThemeOverlay;
import org.eclipse.che.ide.jseditor.client.defaulteditor.EditorBuilder;
import org.eclipse.che.ide.jseditor.client.editorconfig.AutoSaveTextEditorConfiguration;
import org.eclipse.che.ide.jseditor.client.editortype.EditorType;
import org.eclipse.che.ide.jseditor.client.editortype.EditorTypeRegistry;
import org.eclipse.che.ide.jseditor.client.requirejs.RequireJsLoader;
import org.eclipse.che.ide.jseditor.client.requirejs.RequirejsErrorHandler.RequireError;
import org.eclipse.che.ide.jseditor.client.texteditor.AbstractEditorModule.EditorInitializer;
import org.eclipse.che.ide.jseditor.client.texteditor.AbstractEditorModule.InitializerCallback;
import org.eclipse.che.ide.jseditor.client.texteditor.ConfigurableTextEditor;
import org.eclipse.che.ide.jseditor.client.texteditor.EmbeddedTextEditorPresenter;
import org.eclipse.che.ide.util.loging.Log;

import javax.inject.Inject;
import java.util.logging.Level;
import java.util.logging.Logger;

@Extension(title = "Orion Editor", version = "1.1.0")
@Singleton
public class OrionEditorExtension implements Provider<OrionKeyBindingModule>{

    /** The logger. */
    private static final Logger LOG = Logger.getLogger(OrionEditorExtension.class.getSimpleName());

    /** The editor type key. */
    public static final String ORION_EDITOR_KEY = "orion";

    private final NotificationManager    notificationManager;
    private final EditorTypeRegistry     editorTypeRegistry;
    private final RequireJsLoader        requireJsLoader;
    private final OrionTextEditorFactory orionTextEditorFactory;
    private final OrionResource          orionResource;

    private boolean initFailedWarnedOnce = false;

    private OrionKeyBindingModule keyBindingModule;


    @Inject
    public OrionEditorExtension(final EditorTypeRegistry editorTypeRegistry,
                                final NotificationManager notificationManager,
                                final RequireJsLoader requireJsLoader,
                                final OrionEditorModule editorModule,
                                final OrionTextEditorFactory orionTextEditorFactory,
                                final OrionResource orionResource) {
        this.notificationManager = notificationManager;
        this.editorTypeRegistry = editorTypeRegistry;
        this.requireJsLoader = requireJsLoader;
        this.orionTextEditorFactory = orionTextEditorFactory;
        this.orionResource = orionResource;

        editorModule.setEditorInitializer(new EditorInitializer() {
            @Override
            public void initialize(final InitializerCallback callback) {
                // add code-splitting of the whole orion editor
                GWT.runAsync(new RunAsyncCallback() {
                    @Override
                    public void onSuccess() {
                        injectOrion(callback);
                    }

                    @Override
                    public void onFailure(final Throwable reason) {
                        callback.onFailure(reason);
                    }
                });
            }
        });

        // must not be delayed
        registerEditor();
        KeyMode.init();
    }

    private void injectOrion(final InitializerCallback callback) {
        final String[] scripts = new String[]{
                "built-codeEdit-10.0/code_edit/built-codeEdit-amd",
                "orion/CheContentAssistMode",
                "orion/uiUtils"
        };

        requireJsLoader.require(new Callback<JavaScriptObject[], Throwable>() {
            @Override
            public void onSuccess(final JavaScriptObject[] result) {
                requireOrion(callback);
            }

            @Override
            public void onFailure(final Throwable e) {
                if (e instanceof JavaScriptException) {
                    final JavaScriptException jsException = (JavaScriptException)e;
                    final Object nativeException = jsException.getThrown();
                    if (nativeException instanceof RequireError) {
                        final RequireError requireError = (RequireError)nativeException;
                        final String errorType = requireError.getRequireType();
                        String message = "Orion injection failed: " + errorType;
                        final JsArrayString modules = requireError.getRequireModules();
                        if (modules != null) {
                            message += modules.join(",");
                        }
                        Log.debug(OrionEditorExtension.class, message);
                    }
                }
                initializationFailed(callback, "Failed to inject Orion editor", e);
            }
        }, scripts, new String[0]);

        injectCssLink(GWT.getModuleBaseForStaticFiles() + "built-codeEdit-10.0/code_edit/built-codeEdit.css");
    }

    private static void injectCssLink(final String url) {
        final LinkElement link = Document.get().createLinkElement();
        link.setRel("stylesheet");
        link.setHref(url);
        Document.get().getHead().appendChild(link);
    }

    private void requireOrion(final InitializerCallback callback) {
        requireJsLoader.require(new Callback<JavaScriptObject[], Throwable>() {

            @Override
            public void onFailure(final Throwable reason) {
                LOG.log(Level.SEVERE, "Unable to initialize Orion ", reason);
                initializationFailed(callback, "Failed to initialize Orion editor", reason);
            }

            @Override
            public void onSuccess(final JavaScriptObject[] result) {
                //use 4th element as keybinding module
                keyBindingModule = result[3].cast();
                endConfiguration(callback);
            }
        },
         new String[]{"orion/codeEdit",
                      "orion/editor/emacs",
                      "orion/editor/vi",
                      "orion/keyBinding",
                      "che/editor/contentAssist",
                      "orion/editor/eventTarget",
                      "uiUtils"},
         new String[]{"CodeEditWidget",
                      "OrionEmacs",
                      "OrionVi",
                      "OrionKeyBinding",
                      "CheContentAssistMode",
                      "OrionEventTarget",
                      "UiUtils"});
    }

    private void endConfiguration(final InitializerCallback callback) {
        defineDefaultTheme();
        callback.onSuccess();
    }

    private void registerEditor() {
        LOG.fine("Registering Orion editor type.");
        editorTypeRegistry.registerEditorType(EditorType.fromKey(ORION_EDITOR_KEY), "Orion", new EditorBuilder() {

            @Override
            public ConfigurableTextEditor buildEditor() {
                final EmbeddedTextEditorPresenter<OrionEditorWidget> editor = orionTextEditorFactory.createTextEditor();
                editor.initialize(new AutoSaveTextEditorConfiguration(), notificationManager);
                return editor;
            }
        });
    }

    private void defineDefaultTheme() {
        // The codenvy theme uses both an orion css file and a CssResource
        orionResource.editorStyle().ensureInjected();
        OrionTextThemeOverlay.setDefaultTheme("orionCodenvy", "orion-codenvy-theme.css");
    }

    private void initializationFailed(final InitializerCallback callback, final String errorMessage, Throwable e) {
        if (initFailedWarnedOnce) {
            return;
        }
        initFailedWarnedOnce = true;
        notificationManager.notify(errorMessage, StatusNotification.Status.FAIL, true);
        LOG.log(Level.SEVERE, errorMessage + " - ", e);
        callback.onFailure(e);
    }

    @Override
    public OrionKeyBindingModule get() {
        return keyBindingModule;
    }
}
