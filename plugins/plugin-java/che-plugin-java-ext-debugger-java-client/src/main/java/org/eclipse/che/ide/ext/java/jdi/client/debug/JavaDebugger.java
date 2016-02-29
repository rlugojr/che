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
package org.eclipse.che.ide.ext.java.jdi.client.debug;

import com.google.gwt.event.shared.EventBus;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.inject.Inject;

import org.eclipse.che.api.machine.gwt.client.events.WsAgentStateEvent;
import org.eclipse.che.api.machine.gwt.client.events.WsAgentStateHandler;
import org.eclipse.che.api.promises.client.Promise;
import org.eclipse.che.ide.api.app.AppContext;
import org.eclipse.che.ide.api.app.CurrentProject;
import org.eclipse.che.ide.api.editor.EditorPartPresenter;
import org.eclipse.che.ide.api.notification.StatusNotification;
import org.eclipse.che.ide.api.project.tree.VirtualFile;
import org.eclipse.che.ide.debug.Breakpoint;
import org.eclipse.che.ide.debug.BreakpointManager;
import org.eclipse.che.ide.debug.BreakpointStateEvent;
import org.eclipse.che.ide.debug.Debugger;
import org.eclipse.che.ide.debug.DebuggerObservable;
import org.eclipse.che.ide.debug.DebuggerObserver;
import org.eclipse.che.ide.debug.DebuggerState;
import org.eclipse.che.ide.dto.DtoFactory;
import org.eclipse.che.ide.ext.java.client.projecttree.JavaSourceFolderUtil;
import org.eclipse.che.ide.ext.java.jdi.client.JavaRuntimeExtension;
import org.eclipse.che.ide.ext.java.jdi.client.fqn.FqnResolver;
import org.eclipse.che.ide.ext.java.jdi.client.fqn.FqnResolverFactory;
import org.eclipse.che.ide.ext.java.jdi.client.marshaller.DebuggerEventListUnmarshallerWS;
import org.eclipse.che.ide.ext.java.jdi.shared.BreakPoint;
import org.eclipse.che.ide.ext.java.jdi.shared.BreakPointEvent;
import org.eclipse.che.ide.ext.java.jdi.shared.BreakpointActivatedEvent;
import org.eclipse.che.ide.ext.java.jdi.shared.DebuggerEvent;
import org.eclipse.che.ide.ext.java.jdi.shared.DebuggerEventList;
import org.eclipse.che.ide.ext.java.jdi.shared.DebuggerInfo;
import org.eclipse.che.ide.ext.java.jdi.shared.Location;
import org.eclipse.che.ide.ext.java.jdi.shared.StepEvent;
import org.eclipse.che.ide.ext.java.jdi.shared.UpdateVariableRequest;
import org.eclipse.che.ide.ext.java.jdi.shared.VariablePath;
import org.eclipse.che.ide.jseditor.client.texteditor.EmbeddedTextEditorPresenter;
import org.eclipse.che.ide.rest.AsyncRequestCallback;
import org.eclipse.che.ide.rest.DtoUnmarshallerFactory;
import org.eclipse.che.ide.rest.HTTPStatus;
import org.eclipse.che.ide.util.loging.Log;
import org.eclipse.che.ide.util.storage.LocalStorage;
import org.eclipse.che.ide.util.storage.LocalStorageProvider;
import org.eclipse.che.ide.websocket.MessageBus;
import org.eclipse.che.ide.websocket.MessageBusProvider;
import org.eclipse.che.ide.websocket.WebSocketException;
import org.eclipse.che.ide.websocket.rest.SubscriptionHandler;
import org.eclipse.che.ide.websocket.rest.exceptions.ServerException;

import javax.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.eclipse.che.ide.debug.events.DebuggerStateEvent.createConnectedStateEvent;
import static org.eclipse.che.ide.debug.events.DebuggerStateEvent.createDisconnectedStateEvent;
import static org.eclipse.che.ide.debug.events.DebuggerStateEvent.createInitializedStateEvent;
import static org.eclipse.che.ide.ext.java.jdi.shared.DebuggerEvent.BREAKPOINT;
import static org.eclipse.che.ide.ext.java.jdi.shared.DebuggerEvent.BREAKPOINT_ACTIVATED;
import static org.eclipse.che.ide.ext.java.jdi.shared.DebuggerEvent.STEP;

/**
 * Implements java debugger
 *
 * @author Mykola Morhun
 * @author Vitaly Parfonov
 * @author Artem Zatsarynnyi
 * @author Valeriy Svydenko
 * @author Dmitry Shnurenko
 * @author Anatoliy Bazko
 */
public class JavaDebugger implements Debugger, DebuggerObservable {

    private static final String LOCAL_STORAGE_DEBUGGER_KEY = "che-java-debugger";

    private final List<DebuggerObserver> debuggerListeners = new ArrayList<>();

    private       DebuggerInfo           debuggerInfo;
    private       MessageBus             messageBus;

    private final EventBus               eventBus;
    private final LocalStorageProvider   localStorageProvider;
    private final DtoFactory             dtoFactory;
    private final DtoUnmarshallerFactory dtoUnmarshallerFactory;
    private final BreakpointManager      breakpointManager;
    private final DebuggerServiceClient  service;
    private final AppContext             appContext;
    private final FqnResolverFactory     resolverFactory;

    /** Handler for processing events which is received from debugger over WebSocket connection. */
    private final SubscriptionHandler<DebuggerEventList> debuggerEventsHandler;
    private final SubscriptionHandler<Void>              debuggerDisconnectedHandler;

    /** Channel identifier to receive events from debugger over WebSocket. */
    private       String                                 debuggerEventsChannel;
    /** Channel identifier to receive event when debugger will be disconnected. */
    private       String                                 debuggerDisconnectedChannel;

    @Inject
    JavaDebugger(final EventBus eventBus,
                 final LocalStorageProvider localStorageProvider,
                 final DtoFactory dtoFactory,
                 final DtoUnmarshallerFactory dtoUnmarshallerFactory,
                 final BreakpointManager breakpointManager,
                 final DebuggerServiceClient service,
                 final MessageBusProvider messageBusProvider,
                 final AppContext appContext,
                 final FqnResolverFactory resolverFactory) {

        this.eventBus = eventBus;
        this.localStorageProvider = localStorageProvider;
        this.dtoFactory = dtoFactory;
        this.dtoUnmarshallerFactory = dtoUnmarshallerFactory;
        this.breakpointManager = breakpointManager;
        this.service = service;
        this.appContext = appContext;
        this.resolverFactory = resolverFactory;

        this.debuggerInfo = EmptyDebuggerInfo.INSTANCE;

        eventBus.addHandler(WsAgentStateEvent.TYPE, new WsAgentStateHandler() {
            @Override
            public void onWsAgentStarted(WsAgentStateEvent event) {
                messageBus = messageBusProvider.getMachineMessageBus();
                debuggerInfo = loadDebugInfo();

                if (isDebuggerConnected()) {
                    service.checkEvents(debuggerInfo.getId(), new AsyncRequestCallback<DebuggerEventList>() {
                        @Override
                        protected void onSuccess(DebuggerEventList result) {
                           startWorkingWithDebugger(debuggerInfo.getHost(), debuggerInfo.getPort());
                        }

                        @Override
                        protected void onFailure(Throwable exception) {
                            debuggerInfo = EmptyDebuggerInfo.INSTANCE;
                            preserveDebugInfo();
                        }
                    });
                }
            }

            @Override
            public void onWsAgentStopped(WsAgentStateEvent event) {
            }
        });

        this.debuggerEventsHandler = new SubscriptionHandler<DebuggerEventList>(new DebuggerEventListUnmarshallerWS(dtoFactory)) {
            @Override
            public void onMessageReceived(DebuggerEventList result) {
                onEventListReceived(result);
            }

            @Override
            public void onErrorReceived(Throwable exception) {
                try {
                    messageBus.unsubscribe(debuggerEventsChannel, this);
                } catch (WebSocketException e) {
                    Log.error(JavaDebugger.class, e);
                }

                if (exception instanceof ServerException) {
                    ServerException serverException = (ServerException)exception;
                    if (HTTPStatus.INTERNAL_ERROR == serverException.getHTTPStatus() && serverException.getMessage() != null
                        && serverException.getMessage().contains("not found")) {
                        sendOnDebuggerDisconnected(debuggerInfo.getHost(), debuggerInfo.getPort());
                    }
                }
            }
        };

        this.debuggerDisconnectedHandler = new SubscriptionHandler<Void>() {
            @Override
            protected void onMessageReceived(Void result) {
                try {
                    messageBus.unsubscribe(debuggerDisconnectedChannel, this);
                } catch (WebSocketException e) {
                    Log.error(JavaDebugger.class, e);
                }

                evaluateExpressionPresenter.closeDialog();
                sendOnDebuggerDisconnected(debuggerInfo.getHost(), debuggerInfo.getPort());
            }

            @Override
            protected void onErrorReceived(Throwable exception) {
                try {
                    messageBus.unsubscribe(debuggerDisconnectedChannel, this);
                } catch (WebSocketException e) {
                    Log.error(JavaDebugger.class, e);
                }
            }
        };

        eventBus.fireEvent(createInitializedStateEvent(this));
    }

    @Override
    public void addBreakpoint(@NotNull final VirtualFile file, final int lineNumber, final AsyncCallback<Breakpoint> callback) {
        if (isDebuggerConnected()) {
            Location location = dtoFactory.createDto(Location.class);
            location.setLineNumber(lineNumber + 1);
            final FqnResolver resolver = resolverFactory.getResolver(file.getMediaType());
            if (resolver != null) {
                location.setClassName(resolver.resolveFqn(file));
            } else {
                Log.warn(DebuggerPresenter.class, "FqnResolver is not found");
            }

            BreakPoint breakPoint = dtoFactory.createDto(BreakPoint.class);
            breakPoint.setLocation(location);
            breakPoint.setEnabled(true);
            service.addBreakpoint(debuggerInfo.getId(), breakPoint, new AsyncRequestCallback<Void>() {
                @Override
                protected void onSuccess(Void result) {
                    if (resolver != null) {
                        Breakpoint breakpoint = new Breakpoint(Breakpoint.Type.BREAKPOINT, lineNumber, file.getPath(), file, true);
                        callback.onSuccess(breakpoint);
                        sendOnBreakpointAdded();
                    }
                }

                @Override
                protected void onFailure(Throwable exception) {
                    callback.onFailure(exception);
                }
            });
        } else {
            callback.onFailure(new IllegalStateException("Debugger not attached"));
            sendOnBreakpointAdded();
        }
    }

    @Override
    public void deleteBreakpoint(@NotNull VirtualFile file, int lineNumber, final AsyncCallback<Void> callback) {
        if (isDebuggerConnected()) {
            Location location = dtoFactory.createDto(Location.class);
            location.setLineNumber(lineNumber + 1);
            FqnResolver resolver = resolverFactory.getResolver(file.getMediaType());
            if (resolver != null) {
                location.setClassName(resolver.resolveFqn(file));
            } else {
                Log.warn(JavaDebugger.class, "FqnResolver is not found");
            }

            BreakPoint breakPoint = dtoFactory.createDto(BreakPoint.class);
            breakPoint.setLocation(location);
            breakPoint.setEnabled(true);

            service.deleteBreakpoint(debuggerInfo.getId(), breakPoint, new AsyncRequestCallback<Void>() {
                @Override
                protected void onSuccess(Void result) {
                    callback.onSuccess(null);
                    sendOnBreakpointDeleted();
                }

                @Override
                protected void onFailure(Throwable exception) {
                    callback.onFailure(exception);
                }
            });
        } else {
            callback.onFailure(new IllegalStateException("Debugger not attached"));
            sendOnBreakpointDeleted();
        }
    }

    @Override
    public void deleteAllBreakpoints() {
        if (isDebuggerConnected()) {
            service.deleteAllBreakpoints(debuggerInfo.getId(), new AsyncRequestCallback<String>() {
                @Override
                protected void onSuccess(String result) {
                    breakpointManager.removeAllBreakpoints();
                    sendOnDeleteAllBreakpoints();
                }

                @Override
                protected void onFailure(Throwable exception) {
                    Log.error(JavaDebugger.class, exception);
                }
            });
        } else {
            breakpointManager.removeAllBreakpoints();
            sendOnDeleteAllBreakpoints();
        }
    }

    /**
     * Attached debugger via special host and port for current project.
     *
     * @param host host which need to connect to debugger
     * @param port port which need to connect to debugger
     */
    @Override
    public void attachDebugger(String host, int port) {
        service.connect(host, port, new AsyncRequestCallback<DebuggerInfo>(dtoUnmarshallerFactory.newUnmarshaller(DebuggerInfo.class)) {
            @Override
            public void onSuccess(DebuggerInfo result) {
                debuggerInfo = result;
                preserveDebugInfo();

                startWorkingWithDebugger(host, port);
            }

            @Override
            protected void onFailure(Throwable exception) {
                sendOnDebuggerConnectError(host, port);
            }
        });
    }

    @Override
    public void disconnectDebugger() {
        if (isDebuggerConnected()) {
            stopCheckingDebugEvents();

            service.disconnect(debuggerInfo.getId(), new AsyncRequestCallback<Void>() {
                @Override
                protected void onSuccess(Void result) {
                }

                @Override
                protected void onFailure(Throwable exception) {
                    Log.error(JavaDebugger.class, exception);
                }
            });

            invalidateDebugInfo();
            preserveDebugInfo();

            eventBus.fireEvent(createDisconnectedStateEvent(this));

            sendOnDebuggerDisconnected(debuggerInfo.getHost(), debuggerInfo.getPort());
        }
    }

    @Override
    public void stepInto() {
        breakpointManager.removeCurrentBreakpoint();

        service.stepInto(debuggerInfo.getId(), new AsyncRequestCallback<Void>() {
            @Override
            protected void onSuccess(Void result) {
                sendOnStepInto();
            }

            @Override
            protected void onFailure(Throwable exception) {
                Log.error(JavaDebugger.class, exception);
            }
        });
    }

    @Override
    public void stepOver() {
        breakpointManager.removeCurrentBreakpoint();

        service.stepOver(debuggerInfo.getId(), new AsyncRequestCallback<Void>() {
            @Override
            protected void onSuccess(Void result) {
                sendOnStepOver();
            }

            @Override
            protected void onFailure(Throwable exception) {
                Log.error(JavaDebugger.class, exception);
            }

        });
    }

    @Override
    public void stepOut() {
        breakpointManager.removeCurrentBreakpoint();

        service.stepOut(debuggerInfo.getId(), new AsyncRequestCallback<Void>() {
            @Override
            protected void onSuccess(Void result) {
                sendOnStepOut();
            }

            @Override
            protected void onFailure(Throwable exception) {
                Log.error(JavaDebugger.class, exception);
            }
        });
    }

    @Override
    public void resume() {
        breakpointManager.removeCurrentBreakpoint();

        service.resume(debuggerInfo.getId(), new AsyncRequestCallback<Void>() {
            @Override
            protected void onSuccess(Void result) {
                sendOnResume();
            }

            @Override
            protected void onFailure(Throwable exception) {
                Log.error(JavaDebugger.class, exception);
            }
        });
    }

    @Override
    public Promise<String> evaluateExpression(String expression) {
        return service.evaluateExpression(debuggerInfo.getId(), expression);
    }

    @Override
    public void changeVariableValue(List<String> path, String newValue) {
        UpdateVariableRequest updateVariableRequest = dtoFactory.createDto(UpdateVariableRequest.class);
        VariablePath variablePath = dtoFactory.createDto(VariablePath.class);
        variablePath.setPath(path);
        updateVariableRequest.setVariablePath(variablePath);
        updateVariableRequest.setExpression(newValue);

        service.setValue(debuggerInfo.getId(), updateVariableRequest, new AsyncRequestCallback<Void>() {
            @Override
            protected void onSuccess(Void result) {
                getStackFrameDump();
            }

            @Override
            protected void onFailure(Throwable throwable) {
                Log.error(JavaDebugger.class, throwable);
            }
        });
    }

    @Override
    public DebuggerState getDebuggerState() {
        return isDebuggerConnected() ? DebuggerState.CONNECTED : DebuggerState.DISCONNECTED;
    }

    @Override
    public String getVmInfo() {
        return debuggerInfo.getVmName() + " " + debuggerInfo.getVmVersion();
    }


    @Override
    public void addDebuggerObserver(DebuggerObserver debuggerObserver) {
        debuggerListeners.add(debuggerObserver);
    }

    @Override
    public void removeDebuggerObserver(DebuggerObserver debuggerObserver) {
        debuggerListeners.remove(debuggerObserver);
    }


    private void startWorkingWithDebugger(String host, int port) {
        startCheckingEvents();
        eventBus.fireEvent(createConnectedStateEvent(JavaDebugger.this));

        sendOnDebuggerConnected(host, port);
    }

    private void startCheckingEvents() {
        debuggerEventsChannel = JavaRuntimeExtension.EVENTS_CHANNEL + debuggerInfo.getId();
        try {
            messageBus.subscribe(debuggerEventsChannel, debuggerEventsHandler);
        } catch (WebSocketException e) {
            Log.error(JavaDebugger.class, e);
        }

        try {
            debuggerDisconnectedChannel = JavaRuntimeExtension.DISCONNECT_CHANNEL + debuggerInfo.getId();
            messageBus.subscribe(debuggerDisconnectedChannel, debuggerDisconnectedHandler);
        } catch (WebSocketException e) {
            Log.error(JavaDebugger.class, e);
        }
    }

    private void stopCheckingDebugEvents() {
        try {
            if (messageBus.isHandlerSubscribed(debuggerEventsHandler, debuggerEventsChannel)) {
                messageBus.unsubscribe(debuggerEventsChannel, debuggerEventsHandler);
            }

            if (messageBus.isHandlerSubscribed(debuggerDisconnectedHandler, debuggerDisconnectedChannel)) {
                messageBus.unsubscribe(debuggerDisconnectedChannel, debuggerDisconnectedHandler);
            }
        } catch (WebSocketException e) {
            Log.error(JavaDebugger.class, e);
        }
    }

    private boolean isDebuggerConnected() {
        return debuggerInfo != null && debuggerInfo != EmptyDebuggerInfo.INSTANCE;
    }

    private void invalidateDebugInfo() {
        debuggerInfo = EmptyDebuggerInfo.INSTANCE;
    }

    private void onEventListReceived(@NotNull DebuggerEventList eventList) {
        if (eventList.getEvents().size() == 0) {
            return;
        }

        VirtualFile activeFile = null;
        final EditorPartPresenter activeEditor = editorAgent.getActiveEditor();
        if (activeEditor != null) {
            activeFile = activeEditor.getEditorInput().getFile();
        }
        Location location;
        List<DebuggerEvent> events = eventList.getEvents();
        for (DebuggerEvent event : events) {
            switch (event.getType()) {
                case STEP:
                    location = ((StepEvent)event).getLocation();
                    break;
                case BREAKPOINT_ACTIVATED:
                    BreakPoint breakPoint = ((BreakpointActivatedEvent)event).getBreakPoint();
                    activateBreakpoint(breakPoint);
                    return;
                case BREAKPOINT:
                    location = ((BreakPointEvent)event).getBreakPoint().getLocation();
                    showDebuggerPanel();
                    break;
                default:
                    Log.error(DebuggerPresenter.class, "Unknown type of debugger event: " + event.getType());
                    return;
            }
            this.executionPoint = location;

            List<String> filePaths = resolveFilePathByLocation(location);

            if (activeFile == null || !filePaths.contains(activeFile.getPath())) {
                final Location finalLocation = location;
                openFile(location, filePaths, 0, new AsyncCallback<VirtualFile>() {
                    @Override
                    public void onSuccess(VirtualFile result) {
                        breakpointManager.setCurrentBreakpoint(finalLocation.getLineNumber() - 1);
                        scrollEditorToExecutionPoint((EmbeddedTextEditorPresenter)editorAgent.getActiveEditor());
                    }

                    @Override
                    public void onFailure(Throwable caught) {
                        notificationManager.notify(caught.getMessage(), StatusNotification.Status.FAIL, false);
                    }
                });
            } else {
                breakpointManager.setCurrentBreakpoint(location.getLineNumber() - 1);
                scrollEditorToExecutionPoint((EmbeddedTextEditorPresenter)activeEditor);
            }

            getStackFrameDump();
        }
    }

    /**
     * Breakpoint became active. It might happens because of different reasons:
     * <li>breakpoint was deferred and VM eventually loaded class and added it</li>
     * <li>condition triggered</li>
     * <li>etc</li>
     */
    private void activateBreakpoint(BreakPoint breakPoint) {
        Location location = breakPoint.getLocation();
        List<String> filePaths = resolveFilePathByLocation(location);
        for (String filePath : filePaths) {
            eventBus.fireEvent(
                    new BreakpointStateEvent(BreakpointStateEvent.BreakpointState.ACTIVE, filePath, location.getLineNumber() - 1));
        }
    }

    /**
     * Create file path from {@link Location}.
     *
     * @param location
     *         location of class
     * @return file path
     */
    @NotNull
    private List<String> resolveFilePathByLocation(@NotNull Location location) {
        CurrentProject currentProject = appContext.getCurrentProject();

        if (currentProject == null) {
            return Collections.emptyList();
        }

        String pathSuffix = location.getClassName().replace(".", "/") + ".java";

        List<String> sourceFolders = JavaSourceFolderUtil.getSourceFolders(currentProject);
        List<String> filePaths = new ArrayList<>(sourceFolders.size() + 1);

        for (String sourceFolder : sourceFolders) {
            filePaths.add(sourceFolder + pathSuffix);
        }
        filePaths.add(location.getClassName());

        return filePaths;
    }


    /**
     * Loads debug information from the local storage.
     */
    private DebuggerInfo loadDebugInfo() {
        LocalStorage localStorage = localStorageProvider.get();
        if (localStorage == null) {
            return EmptyDebuggerInfo.INSTANCE;
        }

        String data = localStorage.getItem(LOCAL_STORAGE_DEBUGGER_KEY);
        if (data == null || data.isEmpty()) {
            return EmptyDebuggerInfo.INSTANCE;
        }

        return dtoFactory.createDtoFromJson(data, DebuggerInfo.class);
    }

    /**
     * Preserves debug information into the local storage.
     */
    private void preserveDebugInfo() {
        LocalStorage localStorage = localStorageProvider.get();

        if (localStorage == null) {
            return;
        }

        String data;
        if (!isDebuggerConnected()) {
            data = "";
        } else {
            data = dtoFactory.toJson(debuggerInfo);
        }

        localStorage.setItem(LOCAL_STORAGE_DEBUGGER_KEY, data);
    }

    private void sendOnDebuggerConnected(String host, int port) {
        if (!debuggerListeners.isEmpty()) {
            for (DebuggerObserver observer : debuggerListeners) {
                observer.onDebuggerConnected(host, port);
            }
        }
    }

    private void sendOnDebuggerConnectError(String host, int port) {
        if (!debuggerListeners.isEmpty()) {
            for (DebuggerObserver observer : debuggerListeners) {
                observer.onDebuggerConnectError(host, port);
            }
        }
    }

    private void sendOnDebuggerDisconnected(String host, int port) {
        if (!debuggerListeners.isEmpty()) {
            for (DebuggerObserver observer : debuggerListeners) {
                observer.onDebuggerDisconnected(host, port);
            }
        }
    }

    private void sendOnStepInto() {
        if (!debuggerListeners.isEmpty()) {
            for (DebuggerObserver observer : debuggerListeners) {
                observer.onStepInto();
            }
        }
    }

    private void sendOnStepOver() {
        if (!debuggerListeners.isEmpty()) {
            for (DebuggerObserver observer : debuggerListeners) {
                observer.onStepOver();
            }
        }
    }

    private void sendOnStepOut() {
        if (!debuggerListeners.isEmpty()) {
            for (DebuggerObserver observer : debuggerListeners) {
                observer.onStepOut();
            }
        }
    }

    private void sendOnResume() {
        if (!debuggerListeners.isEmpty()) {
            for (DebuggerObserver observer : debuggerListeners) {
                observer.onResume();
            }
        }
    }

    private void sendOnBreakpointAdded() {
        if (!debuggerListeners.isEmpty()) {
            for (DebuggerObserver observer : debuggerListeners) {
                observer.onBreakpointAdded();
            }
        }
    }

    private void sendOnBreakpointDeleted() {
        if (!debuggerListeners.isEmpty()) {
            for (DebuggerObserver observer : debuggerListeners) {
                observer.onBreakpointDeleted();
            }
        }
    }

    private void sendOnDeleteAllBreakpoints() {
        if (!debuggerListeners.isEmpty()) {
            for (DebuggerObserver observer : debuggerListeners) {
                observer.onDeleteAllBreakpoints();
            }
        }
    }

}
