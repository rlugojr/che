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
package org.eclipse.che.ide.ext.github.client.importer.page;

import com.google.gwt.core.client.JsArrayMixed;
import com.google.gwt.http.client.Response;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.AcceptsOneWidget;
import com.google.gwtmockito.GwtMockitoTestRunner;

import org.eclipse.che.api.project.shared.dto.ProjectImporterDescriptor;
import org.eclipse.che.api.promises.client.Promise;
import org.eclipse.che.api.promises.client.PromiseError;
import org.eclipse.che.api.user.gwt.client.UserServiceClient;
import org.eclipse.che.api.user.shared.dto.ProfileDescriptor;
import org.eclipse.che.api.workspace.shared.dto.ProjectConfigDto;
import org.eclipse.che.api.workspace.shared.dto.SourceStorageDto;
import org.eclipse.che.ide.api.app.AppContext;
import org.eclipse.che.ide.api.app.CurrentUser;
import org.eclipse.che.ide.api.notification.NotificationManager;
import org.eclipse.che.ide.api.oauth.OAuth2Authenticator;
import org.eclipse.che.ide.api.oauth.OAuth2AuthenticatorRegistry;
import org.eclipse.che.ide.api.wizard.Wizard;
import org.eclipse.che.ide.commons.exception.UnauthorizedException;
import org.eclipse.che.ide.dto.DtoFactory;
import org.eclipse.che.ide.ext.github.client.GitHubClientService;
import org.eclipse.che.ide.ext.github.client.GitHubLocalizationConstant;
import org.eclipse.che.ide.ext.github.client.load.ProjectData;
import org.eclipse.che.ide.ext.github.shared.GitHubRepository;
import org.eclipse.che.ide.ext.github.shared.GitHubUser;
import org.eclipse.che.ide.rest.AsyncRequestCallback;
import org.eclipse.che.ide.rest.DtoUnmarshallerFactory;
import org.eclipse.che.security.oauth.OAuthStatus;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Matchers;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Testing {@link GithubImporterPagePresenter} functionality.
 *
 * @author Roman Nikitenko
 */
@RunWith(GwtMockitoTestRunner.class)
public class GithubImporterPagePresenterTest {

    @Captor
    private ArgumentCaptor<AsyncCallback<OAuthStatus>> asyncCallbackCaptor;

    @Captor
    private ArgumentCaptor<AsyncRequestCallback<Map<String, List<GitHubRepository>>>> asyncRequestCallbackRepoListCaptor;

    @Mock
    private Wizard.UpdateDelegate           updateDelegate;
    @Mock
    private DtoFactory                      dtoFactory;
    @Mock
    private GithubImporterPageView          view;
    @Mock
    private UserServiceClient               userServiceClient;
    @Mock
    private GitHubClientService             gitHubClientService;
    @Mock
    private DtoUnmarshallerFactory          dtoUnmarshallerFactory;
    @Mock
    private NotificationManager             notificationManager;
    @Mock
    private GitHubLocalizationConstant      locale;
    @Mock
    private ProjectConfigDto                dataObject;
    @Mock
    private SourceStorageDto                source;
    @Mock
    private Map<String, String>             parameters;
    @Mock
    private Promise<GitHubUser>             gitHubUserPromise;
    @Mock
    private Promise<List<GitHubUser>>       gitHubOrgsPromise;
    @Mock
    private Promise<List<GitHubRepository>> gitHubReposPromise;
    @Mock
    private JsArrayMixed                    jsArrayMixed;
    @Mock
    private GitHubUser                      gitHubUser;
    @Mock
    private PromiseError                    promiseError;
    @Mock
    private Response                        response;
    @Mock
    private OAuth2Authenticator             gitHubAuthenticator;
    @Mock
    private OAuth2AuthenticatorRegistry     gitHubAuthenticatorRegistry;
    @Mock
    private AppContext                      appContext;

    private GithubImporterPagePresenter presenter;


    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        when(dataObject.getSource()).thenReturn(source);
        doReturn("AccountName").when(gitHubUser).getName();
        doReturn("AccountLogin").when(gitHubUser).getLogin();
        when(gitHubAuthenticatorRegistry.getAuthenticator(eq("github"))).thenReturn(gitHubAuthenticator);
        presenter = spy(new GithubImporterPagePresenter(view, gitHubAuthenticatorRegistry, gitHubClientService, dtoFactory, "", appContext,
                                                        locale));
        doReturn(Collections.singletonList(gitHubUser)).when(presenter).toOrgList(any(JsArrayMixed.class));
        doReturn(Collections.emptyList()).when(presenter).toRepoList(any(JsArrayMixed.class));
        presenter.setUpdateDelegate(updateDelegate);
        presenter.init(dataObject);
    }

    @Test
    public void delegateShouldBeSet() throws Exception {
        verify(view).setDelegate(any(GithubImporterPagePresenter.class));
    }

    @Test
    public void testGo() throws Exception {
        String importerDescription = "description";
        AcceptsOneWidget container = mock(AcceptsOneWidget.class);
        ProjectImporterDescriptor projectImporter = mock(ProjectImporterDescriptor.class);
        //when(wizardContext.getData(ImportProjectWizard.PROJECT_IMPORTER)).thenReturn(projectImporter);
        when(projectImporter.getDescription()).thenReturn(importerDescription);

        presenter.go(container);

        verify(view).setInputsEnableState(eq(true));
        verify(container).setWidget(eq(view));
        verify(view).focusInUrlInput();
    }

    @Test
    public void onLoadRepoClickedWhenGetUserReposIsSuccessful() throws Exception {
        doAnswer(new Answer() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                presenter.onSuccessRequest(jsArrayMixed);
                return null;
            }
        }).when(presenter).doRequest(any(Promise.class), any(Promise.class), any(Promise.class));
        when(view.getAccountName()).thenReturn("AccountName");

        presenter.onLoadRepoClicked();

        verify(gitHubClientService).getRepositoriesList();
        verify(gitHubClientService).getUserInfo();
        verify(gitHubClientService).getOrganizations();

        verify(notificationManager, never()).notify(anyString(), any(ProjectConfigDto.class));
        verify(view).setLoaderVisibility(eq(true));
        verify(view).setInputsEnableState(eq(false));
        verify(view).setLoaderVisibility(eq(false));
        verify(view).setInputsEnableState(eq(true));
        verify(view).setAccountNames(Matchers.<Set>anyObject());
        verify(view, times(2)).showGithubPanel();
        verify(view).setRepositories(Matchers.<List<ProjectData>>anyObject());
        verify(view).reset();
    }

    @Test
    public void onLoadRepoClickedWhenGetUserReposIsFailed() throws Exception {
        doAnswer(new Answer() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                presenter.onFailRequest(promiseError);
                return null;
            }
        }).when(presenter).doRequest(any(Promise.class), any(Promise.class), any(Promise.class));

        presenter.onLoadRepoClicked();

        verify(gitHubClientService).getRepositoriesList();
        verify(gitHubClientService).getUserInfo();
        verify(gitHubClientService).getOrganizations();

        verify(view).setLoaderVisibility(eq(true));
        verify(view).setInputsEnableState(eq(false));
        verify(view).setLoaderVisibility(eq(false));
        verify(view).setInputsEnableState(eq(true));
        verify(view, never()).setAccountNames((Set<String>)anyObject());
        verify(view, never()).showGithubPanel();
        verify(view, never()).setRepositories(Matchers.<List<ProjectData>>anyObject());
    }

    @Test
    public void onRepositorySelectedTest() {
        ProjectData projectData = new ProjectData("name", "description", "type", new ArrayList<String>(), "repoUrl", "readOnlyUrl");

        presenter.onRepositorySelected(projectData);

        verify(dataObject).setName(eq("name"));
        verify(dataObject).setDescription(eq("description"));
        verify(source).setLocation(eq("repoUrl"));
        verify(view).setProjectName(anyString());
        verify(view).setProjectDescription(anyString());
        verify(view).setProjectUrl(anyString());
        verify(updateDelegate).updateControls();
    }

    @Test
    public void projectUrlStartWithWhiteSpaceEnteredTest() {
        String incorrectUrl = " https://github.com/codenvy/ide.git";
        when(view.getProjectName()).thenReturn("");

        presenter.projectUrlChanged(incorrectUrl);

        verify(view).markURLInvalid();
        verify(view).setURLErrorMessage(eq(locale.importProjectMessageStartWithWhiteSpace()));
        verify(source).setLocation(eq(incorrectUrl));
        verify(view).setProjectName(anyString());
        verify(updateDelegate).updateControls();
    }

    @Test
    public void testUrlMatchScpLikeSyntax() {
        // test for url with an alternative scp-like syntax: [user@]host.xz:path/to/repo.git/
        String correctUrl = "host.xz:path/to/repo.git";
        when(view.getProjectName()).thenReturn("");

        presenter.projectUrlChanged(correctUrl);

        verifyInvocationsForCorrectUrl(correctUrl);
    }

    @Test
    public void testUrlWithoutUsername() {
        String correctUrl = "git@hostname.com:projectName.git";
        when(view.getProjectName()).thenReturn("");

        presenter.projectUrlChanged(correctUrl);

        verifyInvocationsForCorrectUrl(correctUrl);
    }

    @Test
    public void testSshUriWithHostBetweenDoubleSlashAndSlash() {
        //Check for type uri which start with ssh:// and has host between // and /
        String correctUrl = "ssh://host.com/some/path";
        when(view.getProjectName()).thenReturn("");

        presenter.projectUrlChanged(correctUrl);

        verifyInvocationsForCorrectUrl(correctUrl);
    }

    @Test
    public void testSshUriWithHostBetweenDoubleSlashAndColon() {
        //Check for type uri with host between // and :
        String correctUrl = "ssh://host.com:port/some/path";
        when(view.getProjectName()).thenReturn("");

        presenter.projectUrlChanged(correctUrl);

        verifyInvocationsForCorrectUrl(correctUrl);
    }

    @Test
    public void testGitUriWithHostBetweenDoubleSlashAndSlash() {
        //Check for type uri which start with git:// and has host between // and /
        String correctUrl = "git://host.com/user/repo";
        when(view.getProjectName()).thenReturn("");

        presenter.projectUrlChanged(correctUrl);

        verifyInvocationsForCorrectUrl(correctUrl);
    }

    @Test
    public void testSshUriWithHostBetweenAtAndColon() {
        //Check for type uri with host between @ and :
        String correctUrl = "user@host.com:login/repo";
        when(view.getProjectName()).thenReturn("");

        presenter.projectUrlChanged(correctUrl);

        verifyInvocationsForCorrectUrl(correctUrl);
    }

    @Test
    public void testSshUriWithHostBetweenAtAndSlash() {
        //Check for type uri with host between @ and /
        String correctUrl = "ssh://user@host.com/some/path";
        when(view.getProjectName()).thenReturn("");

        presenter.projectUrlChanged(correctUrl);

        verifyInvocationsForCorrectUrl(correctUrl);
    }

    @Test
    public void projectUrlWithIncorrectProtocolEnteredTest() {
        String correctUrl = "htps://github.com/codenvy/ide.git";
        when(view.getProjectName()).thenReturn("");

        presenter.projectUrlChanged(correctUrl);

        verify(view).markURLInvalid();
        verify(view).setURLErrorMessage(eq(locale.importProjectMessageProtocolIncorrect()));
        verify(source).setLocation(eq(correctUrl));
        verify(view).setProjectName(anyString());
        verify(updateDelegate).updateControls();
    }

    @Test
    public void correctProjectNameEnteredTest() {
        String correctName = "angularjs";
        when(view.getProjectName()).thenReturn(correctName);

        presenter.projectNameChanged(correctName);

        verify(dataObject).setName(eq(correctName));
        verify(view).markNameValid();
        verify(view, never()).markNameInvalid();
        verify(updateDelegate).updateControls();
    }

    @Test
    public void correctProjectNameWithPointEnteredTest() {
        String correctName = "Test.project..ForCodenvy";
        when(view.getProjectName()).thenReturn(correctName);

        presenter.projectNameChanged(correctName);

        verify(dataObject).setName(eq(correctName));
        verify(view).markNameValid();
        verify(view, never()).markNameInvalid();
        verify(updateDelegate).updateControls();
    }

    @Test
    public void emptyProjectNameEnteredTest() {
        String emptyName = "";
        when(view.getProjectName()).thenReturn(emptyName);

        presenter.projectNameChanged(emptyName);

        verify(dataObject).setName(eq(emptyName));
        verify(updateDelegate).updateControls();
    }

    @Test
    public void incorrectProjectNameEnteredTest() {
        String incorrectName = "angularjs+";
        when(view.getProjectName()).thenReturn(incorrectName);

        presenter.projectNameChanged(incorrectName);

        verify(dataObject).setName(eq(incorrectName));
        verify(view).markNameInvalid();
        verify(updateDelegate).updateControls();
    }

    @Test
    public void projectDescriptionChangedTest() {
        String description = "description";
        presenter.projectDescriptionChanged(description);

        verify(dataObject).setDescription(eq(description));
    }

    @Test
    public void onLoadRepoClickedWhenAuthorizeIsFailed() throws Exception {
        String userId = "userId";
        CurrentUser user = mock(CurrentUser.class);
        ProfileDescriptor profile = mock(ProfileDescriptor.class);

        when(appContext.getCurrentUser()).thenReturn(user);
        when(user.getProfile()).thenReturn(profile);
        when(profile.getId()).thenReturn(userId);


        final Throwable exception = mock(UnauthorizedException.class);
        doAnswer(new Answer() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                presenter.onFailRequest(promiseError);
                return null;
            }
        }).when(presenter).doRequest(any(Promise.class), any(Promise.class), any(Promise.class));
        doReturn(exception).when(promiseError).getCause();

        presenter.onLoadRepoClicked();

        verify(gitHubClientService).getRepositoriesList();
        verify(gitHubClientService).getUserInfo();
        verify(gitHubClientService).getOrganizations();

        verify(gitHubAuthenticator).authenticate(anyString(), asyncCallbackCaptor.capture());
        AsyncCallback<OAuthStatus> asyncCallback = asyncCallbackCaptor.getValue();
        asyncCallback.onFailure(exception);

        verify(view, times(2)).setLoaderVisibility(eq(true));
        verify(view, times(2)).setInputsEnableState(eq(false));
        verify(view, times(2)).setInputsEnableState(eq(true));
        verify(view, never()).setAccountNames((Set<String>)anyObject());
        verify(view, never()).showGithubPanel();
        verify(view, never()).setRepositories(Matchers.<List<ProjectData>>anyObject());
    }

    @Test
    public void onLoadRepoClickedWhenAuthorizeIsSuccessful() throws Exception {
        doAnswer(new Answer() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                presenter.onFailRequest(promiseError);
                return null;
            }
        }).doAnswer(new Answer() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                presenter.onSuccessRequest(jsArrayMixed);
                return null;
            }
        }).when(presenter).doRequest(any(Promise.class), any(Promise.class), any(Promise.class));
        final Throwable exception = mock(UnauthorizedException.class);
        String userId = "userId";
        CurrentUser user = mock(CurrentUser.class);
        ProfileDescriptor profile = mock(ProfileDescriptor.class);
        doReturn(exception).when(promiseError).getCause();

        when(appContext.getCurrentUser()).thenReturn(user);
        when(user.getProfile()).thenReturn(profile);
        when(profile.getId()).thenReturn(userId);

        presenter.onLoadRepoClicked();

        verify(gitHubClientService).getRepositoriesList();
        verify(gitHubClientService).getUserInfo();
        verify(gitHubClientService).getOrganizations();

        verify(gitHubAuthenticator).authenticate(anyString(), asyncCallbackCaptor.capture());
        AsyncCallback<OAuthStatus> asyncCallback = asyncCallbackCaptor.getValue();
        asyncCallback.onSuccess(null);

        verify(view, times(3)).setLoaderVisibility(eq(true));
        verify(view, times(3)).setInputsEnableState(eq(false));
        verify(view, times(3)).setInputsEnableState(eq(true));
    }

    private void verifyInvocationsForCorrectUrl(String correctUrl) {
        verify(view, never()).markURLInvalid();
        verify(source).setLocation(eq(correctUrl));
        verify(view).markURLValid();
        verify(view).setProjectName(anyString());
        verify(updateDelegate).updateControls();
    }

}
