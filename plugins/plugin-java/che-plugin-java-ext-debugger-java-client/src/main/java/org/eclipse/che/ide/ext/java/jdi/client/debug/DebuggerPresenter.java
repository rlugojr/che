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

import com.google.gwt.resources.client.ImageResource;
import com.google.gwt.user.client.Timer;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.AcceptsOneWidget;
import com.google.gwt.user.client.ui.IsWidget;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;

import org.eclipse.che.api.promises.client.Operation;
import org.eclipse.che.api.promises.client.OperationException;
import org.eclipse.che.api.promises.client.PromiseError;
import org.eclipse.che.ide.api.app.AppContext;
import org.eclipse.che.ide.api.editor.EditorAgent;
import org.eclipse.che.ide.api.editor.EditorPartPresenter;
import org.eclipse.che.ide.api.event.ActivePartChangedEvent;
import org.eclipse.che.ide.api.event.ActivePartChangedHandler;
import org.eclipse.che.ide.api.event.FileEvent;
import org.eclipse.che.ide.api.notification.NotificationManager;
import org.eclipse.che.ide.api.notification.StatusNotification;
import org.eclipse.che.ide.api.parts.PartStackType;
import org.eclipse.che.ide.api.parts.WorkspaceAgent;
import org.eclipse.che.ide.api.parts.base.BasePresenter;
import org.eclipse.che.ide.api.project.node.HasStorablePath;
import org.eclipse.che.ide.api.project.node.Node;
import org.eclipse.che.ide.api.project.tree.VirtualFile;
import org.eclipse.che.ide.debug.Breakpoint;
import org.eclipse.che.ide.debug.BreakpointManager;
import org.eclipse.che.ide.debug.Debugger;
import org.eclipse.che.ide.debug.DebuggerManager;
import org.eclipse.che.ide.debug.DebuggerObserver;
import org.eclipse.che.ide.dto.DtoFactory;
import org.eclipse.che.ide.ext.java.client.project.node.JavaNodeManager;
import org.eclipse.che.ide.ext.java.client.project.node.jar.JarFileNode;
import org.eclipse.che.ide.ext.java.jdi.client.JavaRuntimeLocalizationConstant;
import org.eclipse.che.ide.ext.java.jdi.client.JavaRuntimeResources;
import org.eclipse.che.ide.ext.java.jdi.client.fqn.FqnResolver;
import org.eclipse.che.ide.ext.java.jdi.client.fqn.FqnResolverFactory;
import org.eclipse.che.ide.ext.java.jdi.client.fqn.FqnResolverObserver;
import org.eclipse.che.ide.ext.java.jdi.shared.Location;
import org.eclipse.che.ide.ext.java.jdi.shared.StackFrameDump;
import org.eclipse.che.ide.ext.java.jdi.shared.Value;
import org.eclipse.che.ide.ext.java.jdi.shared.Variable;
import org.eclipse.che.ide.ext.java.shared.JarEntry;
import org.eclipse.che.ide.jseditor.client.document.Document;
import org.eclipse.che.ide.jseditor.client.text.TextPosition;
import org.eclipse.che.ide.jseditor.client.texteditor.EmbeddedTextEditorPresenter;
import org.eclipse.che.ide.part.explorer.project.ProjectExplorerPresenter;
import org.eclipse.che.ide.project.node.FileReferenceNode;
import org.eclipse.che.ide.rest.AsyncRequestCallback;
import org.eclipse.che.ide.ui.toolbar.ToolbarPresenter;
import org.eclipse.che.ide.util.loging.Log;
import org.eclipse.che.ide.workspace.perspectives.project.ProjectPerspective;
import org.vectomatic.dom.svg.ui.SVGResource;

import javax.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;

import static org.eclipse.che.ide.api.event.FileEvent.FileOperation.OPEN;
import static org.eclipse.che.ide.api.notification.StatusNotification.Status.FAIL;
import static org.eclipse.che.ide.api.notification.StatusNotification.Status.PROGRESS;
import static org.eclipse.che.ide.api.notification.StatusNotification.Status.SUCCESS;

/**
 * The presenter provides debug java application.
 *
 * @author Vitaly Parfonov
 * @author Artem Zatsarynnyi
 * @author Valeriy Svydenko
 * @author Dmitry Shnurenko
 * @author Anatoliy Bazko
 * @author Mykola Morhun
 */
@Singleton
public class DebuggerPresenter extends BasePresenter implements DebuggerView.ActionDelegate, DebuggerObserver, FqnResolverObserver {

    private static final String TITLE = "Debug";

    private final AppContext               appContext;
    private final DtoFactory               dtoFactory;
    private final ProjectExplorerPresenter projectExplorer;
    private final JavaNodeManager          javaNodeManager;
    private final JavaRuntimeResources     javaRuntimeResources;
    private final ToolbarPresenter         debuggerToolbar;
    private final DebuggerManager          debuggerManager;
    private final FqnResolverFactory       resolverFactory;
    private final BreakpointManager        breakpointManager;

    private DebuggerView                    view;
    private EventBus                        eventBus;
    private JavaRuntimeLocalizationConstant constant;
    private WorkspaceAgent                  workspaceAgent;
    private EditorAgent                     editorAgent;
    private DebuggerVariable                selectedVariable;
    private NotificationManager             notificationManager;
    private List<DebuggerVariable>          variables;
    private Location                        executionPoint;

    @Inject
    public DebuggerPresenter(final AppContext appContext,
                             final DebuggerView view,
                             final EventBus eventBus,
                             final DebuggerManager debuggerManager,
                             final JavaRuntimeLocalizationConstant constant,
                             final WorkspaceAgent workspaceAgent,
                             final BreakpointManager breakpointManager,
                             final FqnResolverFactory resolverFactory,
                             final EditorAgent editorAgent,
                             final NotificationManager notificationManager,
                             final DtoFactory dtoFactory,
                             final ProjectExplorerPresenter projectExplorer,
                             final JavaNodeManager javaNodeManager,
                             final JavaRuntimeResources javaRuntimeResources,
                             final @DebuggerToolbar ToolbarPresenter debuggerToolbar) {
        this.appContext = appContext;
        this.view = view;
        this.eventBus = eventBus;
        this.debuggerManager = debuggerManager;
        this.dtoFactory = dtoFactory;
        this.projectExplorer = projectExplorer;
        this.javaRuntimeResources = javaRuntimeResources;
        this.debuggerToolbar = debuggerToolbar;
        this.breakpointManager = breakpointManager;
        this.resolverFactory = resolverFactory;
        this.view.setDelegate(this);
        this.view.setTitle(TITLE);
        this.constant = constant;
        this.workspaceAgent = workspaceAgent;
        this.variables = new ArrayList<>();
        this.editorAgent = editorAgent;
        this.notificationManager = notificationManager;
        this.javaNodeManager = javaNodeManager;

        debuggerManager.getDebugger().addDebuggerObserver(this); // TODO move into correct place
        debuggerManager.addDebuggerObserver(this);
        this.addRule(ProjectPerspective.PROJECT_PERSPECTIVE_ID);
        this.resolverFactory.addFqnResolverObserver(this);
    }

    @Override
    public void onDebuggerConnected(String host, int port) {
        final String address = host + ':' + port;
        final StatusNotification notification = notificationManager.notify(constant.debuggerConnectingTitle(address), PROGRESS, true);

        notification.setTitle(constant.debuggerConnectedTitle());
        notification.setContent(constant.debuggerConnectedDescription(address));
        notification.setStatus(SUCCESS);

        showAndUpdateView();
        showDebuggerPanel();
    }

    @Override
    public void onDebuggerConnectError(String host, int port) {
        final String address = host + ':' + port;
        final StatusNotification notification = notificationManager.notify(constant.debuggerConnectingTitle(address), PROGRESS, true);

        notification.setTitle(constant.failedToConnectToRemoteDebuggerDescription(address));
        notification.setStatus(FAIL);
        notification.setBalloon(true);
    }

    /** Perform some action after disconnecting a debugger. */
    @Override
    public void onDebuggerDisconnected(String host, int port) {
        notificationManager.notify(constant.debuggerDisconnectedTitle(),
                                   constant.debuggerDisconnectedDescription(host + ':' + port),
                                   SUCCESS,
                                   false);

        resetStates();
        showAndUpdateView();
    }

    @Override
    public void onBreakpointAdded() {
        updateBreakPoints();
    }

    @Override
    public void onBreakpointDeleted() {
        updateBreakPoints();
    }

    @Override
    public void onDeleteAllBreakpoints() {
        updateBreakPoints();
    }

    @Override
    public void onStepInto() {
        resetStates();
    }

    @Override
    public void onStepOver() {
        resetStates();
    }

    @Override
    public void onStepOut() {
        resetStates();
    }

    @Override
    public void onResume() {
        resetStates();
    }

    /** {@inheritDoc} */
    @Override
    @NotNull
    public String getTitle() {
        return TITLE;
    }

    /** {@inheritDoc} */
    @Override
    public void setVisible(boolean visible) {
        view.setVisible(visible);
    }

    /** {@inheritDoc} */
    @Override
    public IsWidget getView() {
        return view;
    }

    /** {@inheritDoc} */
    @Override
    public ImageResource getTitleImage() {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public SVGResource getTitleSVGImage() {
        return javaRuntimeResources.debug();
    }

    /** {@inheritDoc} */
    @Override
    public String getTitleToolTip() {
        return "Debug";
    }

    /** {@inheritDoc} */
    @Override
    public void go(AcceptsOneWidget container) {
        view.setBreakpoints(breakpointManager.getBreakpointList());
        view.setVariables(variables);
        container.setWidget(view);
        debuggerToolbar.go(view.getDebuggerToolbarPanel());
    }

    @Override
    public void onFqnResolverAdded(FqnResolver fqnResolver) {
        if (!breakpointManager.getBreakpointList().isEmpty()) {
            updateBreakPoints();
        }
    }

    /**
     * Tries to open file from the project.
     * If fails then method will try to find resource from external dependencies.
     */
    private void openFile(@NotNull final Location location,
                          final List<String> filePaths,
                          final int pathNumber,
                          final AsyncCallback<VirtualFile> callback) {

        if (pathNumber == filePaths.size()) {
            Log.error(DebuggerPresenter.class, "Can't open resource " + location);
            return;
        }

        String filePath = filePaths.get(pathNumber);
        if (!filePath.startsWith("/")) {
            openExternalResource(location, callback);
            return;
        }

        projectExplorer.getNodeByPath(new HasStorablePath.StorablePath(filePath)).then(new Operation<Node>() {
            public HandlerRegistration handlerRegistration;

            @Override
            public void apply(final Node node) throws OperationException {
                if (!(node instanceof FileReferenceNode)) {
                    return;
                }

                handlerRegistration = eventBus.addHandler(ActivePartChangedEvent.TYPE, new ActivePartChangedHandler() {
                    @Override
                    public void onActivePartChanged(ActivePartChangedEvent event) {
                        if (event.getActivePart() instanceof EditorPartPresenter) {
                            final VirtualFile openedFile = ((EditorPartPresenter)event.getActivePart()).getEditorInput().getFile();
                            if (((FileReferenceNode)node).getStorablePath().equals(openedFile.getPath())) {
                                handlerRegistration.removeHandler();
                                // give the editor some time to fully render it's view
                                new Timer() {
                                    @Override
                                    public void run() {
                                        callback.onSuccess((VirtualFile)node);
                                    }
                                }.schedule(300);
                            }
                        }
                    }
                });
                eventBus.fireEvent(new FileEvent((VirtualFile)node, OPEN));
            }
        }).catchError(new Operation<PromiseError>() {
            @Override
            public void apply(PromiseError error) throws OperationException {
                // try another path
                openFile(location, filePaths, pathNumber + 1, callback);
            }
        });
    }

    private void openExternalResource(Location location, final AsyncCallback<VirtualFile> callback) {
        String className = location.getClassName();

        JarEntry jarEntry = dtoFactory.createDto(JarEntry.class);
        jarEntry.setPath(className);
        jarEntry.setName(className.substring(className.lastIndexOf(".") + 1) + ".class");
        jarEntry.setType(JarEntry.JarEntryType.CLASS_FILE);

        final JarFileNode jarFileNode =
                javaNodeManager.getJavaNodeFactory().newJarFileNode(jarEntry,
                                                                    null,
                                                                    appContext.getCurrentProject().getProjectConfig(),
                                                                    javaNodeManager.getJavaSettingsProvider().getSettings());

        editorAgent.openEditor(jarFileNode, new EditorAgent.OpenEditorCallback() {
            @Override
            public void onEditorOpened(EditorPartPresenter editor) {
                // give the editor some time to fully render it's view
                new Timer() {
                    @Override
                    public void run() {
                        callback.onSuccess(jarFileNode);
                    }
                }.schedule(300);
            }

            @Override
            public void onEditorActivated(EditorPartPresenter editor) {
                new Timer() {
                    @Override
                    public void run() {
                        callback.onSuccess(jarFileNode);
                    }
                }.schedule(300);
            }
        });
    }

    private void getStackFrameDump() {
        service.getStackFrameDump(debuggerInfo.getId(),
                                  new AsyncRequestCallback<StackFrameDump>(dtoUnmarshallerFactory.newUnmarshaller(StackFrameDump.class)) {
                                      @Override
                                      protected void onSuccess(StackFrameDump result) {
                                          List<Variable> variables = new ArrayList<>();
                                          variables.addAll(result.getFields());
                                          variables.addAll(result.getLocalVariables());

                                          List<DebuggerVariable> debuggerVariables = getDebuggerVariables(variables);

                                          DebuggerPresenter.this.variables = debuggerVariables;
                                          view.setVariables(debuggerVariables);
                                          if (!variables.isEmpty()) {
                                              view.setExecutionPoint(variables.get(0).isExistInformation(), executionPoint);
                                          }
                                      }

                                      @Override
                                      protected void onFailure(Throwable exception) {
                                          Log.error(DebuggerPresenter.class, exception);
                                      }
                                  });
    }

    @NotNull
    private List<DebuggerVariable> getDebuggerVariables(@NotNull List<Variable> variables) {
        List<DebuggerVariable> debuggerVariables = new ArrayList<>();

        for (Variable variable : variables) {
            debuggerVariables.add(new DebuggerVariable(variable));
        }

        return debuggerVariables;
    }

    /** {@inheritDoc} */
    @Override
    public void onExpandVariablesTree() {
        List<DebuggerVariable> rootVariables = selectedVariable.getVariables();
        if (rootVariables.size() == 0) {
            service.getValue(debuggerInfo.getId(), selectedVariable.getVariable(),
                             new AsyncRequestCallback<Value>(dtoUnmarshallerFactory.newUnmarshaller(Value.class)) {
                                 @Override
                                 protected void onSuccess(Value result) {
                                     List<Variable> variables = result.getVariables();

                                     List<DebuggerVariable> debuggerVariables = getDebuggerVariables(variables);

                                     view.setVariablesIntoSelectedVariable(debuggerVariables);
                                     view.updateSelectedVariable();
                                 }

                                 @Override
                                 protected void onFailure(Throwable exception) {
                                     notificationManager
                                             .notify(constant.failedToGetVariableValueTitle(), exception.getMessage(), FAIL, true);
                                 }
                             });
        }
    }

    /** {@inheritDoc} */
    @Override
    public void onSelectedVariableElement(@NotNull DebuggerVariable variable) {
        this.selectedVariable = variable;
    }

    public void showDebuggerPanel() {
        partStack.setActivePart(this);
    }

    public void hideDebuggerPanel() {
        partStack.hidePart(this);
    }

    public boolean isDebuggerPanelOpened() {
        return partStack.getActivePart() == this;
    }

    public boolean isDebuggerPanelPresent() {
        return partStack != null && partStack.containsPart(this);
    }

    private void resetStates() {
        variables.clear();
        view.setVariables(variables);
        selectedVariable = null;
    }

    public void showAndUpdateView() {
        Debugger debugger = debuggerManager.getDebugger();
        if (debugger != null) {
            view.setVMName(debugger.getVmInfo());
        }

        boolean isCurrentBreakpointExists = breakpointManager.getCurrentBreakpoint() != null;
        if (isCurrentBreakpointExists) {
            getStackFrameDump();
        }

        if (partStack == null || !partStack.containsPart(this)) {
            workspaceAgent.openPart(this, PartStackType.INFORMATION);
        }
    }

    /**
     * Updates breakpoints list.
     * The main idea is to display FQN instead of file path.
     */
    private void updateBreakPoints() {
        List<Breakpoint> breakpoints = breakpointManager.getBreakpointList();
        List<Breakpoint> breakpoints2Display = new ArrayList<Breakpoint>(breakpoints.size());

        for (Breakpoint breakpoint : breakpoints) {
            FqnResolver resolver = resolverFactory.getResolver(breakpoint.getFile().getMediaType());

            breakpoints2Display.add(new Breakpoint(breakpoint.getType(), breakpoint.getLineNumber(),
                                                   resolver == null ? breakpoint.getPath() : resolver.resolveFqn(breakpoint.getFile()),
                                                   breakpoint.getFile(), breakpoint.getMessage(),
                                                   breakpoint.isActive()));
        }

        view.setBreakpoints(breakpoints2Display);
        showAndUpdateView();
    }

    /**
     * Returns selected variable in variables tree on debugger panel or null if no selected variables
     *
     * @return selected variable or null if no selection
     */
    public DebuggerVariable getSelectedVariable() {
        return view.getSelectedDebuggerVariable();
    }

    public ToolbarPresenter getDebuggerToolbar() {
        return debuggerToolbar;
    }

    private void scrollEditorToExecutionPoint(EmbeddedTextEditorPresenter editor) {
        Document document = editor.getDocument();

        if (document != null) {
            TextPosition newPosition = new TextPosition(executionPoint.getLineNumber(), 0);
            document.setCursorPosition(newPosition);
        }
    }

}
